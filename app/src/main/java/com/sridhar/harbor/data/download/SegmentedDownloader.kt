package com.sridhar.harbor.data.download

import kotlinx.coroutines.sync.withPermit
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.sridhar.harbor.data.HarborJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

enum class DlStatus { Queued, Running, Done, Failed, Cancelled }

/** Live state of one download, for UI and the notification. */
data class DlState(
    val key: String, val title: String, val status: DlStatus, val downloaded: Long, val total: Long,
    val bytesPerSec: Long = 0, val parts: Int = 1, val error: String? = null,
) {
    val fraction: Float get() = if (total > 0) (downloaded.toFloat() / total).coerceIn(0f, 1f) else 0f
    val etaSec: Long get() = if (bytesPerSec > 0 && total > 0) (total - downloaded) / bytesPerSec else -1
}

@Serializable
private data class Seg(val start: Long, val end: Long, var pos: Long)

@Serializable
private data class Meta(val url: String, val total: Long, val segs: List<Seg>, val ranged: Boolean = true)

/**
 * IDM / ADM-style downloader: the file is split into up to [MAX_PARTS] byte ranges fetched in parallel over
 * separate connections (servers that throttle per connection – or a single slow TCP stream – no longer cap the
 * speed). Progress per part is saved next to the file (`.part.meta`), so a download interrupted by a network drop
 * or the app being killed resumes where it stopped. Servers without Range support fall back to one stream.
 * A foreground service keeps it alive and shows speed / ETA in the notification shade.
 */
class SegmentedDownloader(private val context: Context, private val http: OkHttpClient) {
    companion object {
        const val MAX_PARTS = 6
        private const val MIN_PART = 8L shl 20        // don't split below 8 MB per part
        private const val RETRIES = 6
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = ConcurrentHashMap<String, Job>()
    private val _state = MutableStateFlow<Map<String, DlState>>(emptyMap())
    val state: StateFlow<Map<String, DlState>> = _state.asStateFlow()
    private val onDone = ConcurrentHashMap<String, (Boolean) -> Unit>()
    /** At most 3 files at a time (each already split into parts); the rest wait as Queued. */
    private val slots = kotlinx.coroutines.sync.Semaphore(3)

    fun isActive(key: String) = jobs[key]?.isActive == true

    /** Start (or resume) downloading [url] into [dest]. [onFinished] runs with true on success. */
    fun enqueue(key: String, title: String, url: String, dest: File, headers: Map<String, String> = emptyMap(), onFinished: (Boolean) -> Unit = {}) {
        if (isActive(key)) return
        onDone[key] = onFinished
        put(DlState(key, title, DlStatus.Queued, 0, 0))
        runCatching { ContextCompat.startForegroundService(context, Intent(context, DownloadService::class.java)) }   // not allowed from the background on 12+
        jobs[key] = scope.launch {
            val ok = runCatching { slots.withPermit { run(key, title, url, dest, headers) } }.fold({ true }, { e ->
                val cancelled = e is kotlinx.coroutines.CancellationException
                _state.update { m -> m[key]?.let { m + (key to it.copy(status = if (cancelled) DlStatus.Cancelled else DlStatus.Failed, bytesPerSec = 0, error = e.message)) } ?: m }
                false
            })
            if (ok) _state.update { m -> m[key]?.let { m + (key to it.copy(status = DlStatus.Done, downloaded = it.total, bytesPerSec = 0)) } ?: m }
            jobs.remove(key)
            onDone.remove(key)?.invoke(ok)
        }
    }

    /** Stop a download; with [discard] its partial file is deleted too. */
    fun cancel(key: String, dest: File? = null, discard: Boolean = true) {
        jobs.remove(key)?.cancel()
        if (discard && dest != null) { partOf(dest).delete(); metaOf(dest).delete() }
        _state.update { it - key }
    }

    /** Forget finished / failed rows (the notification and UI use live rows only). */
    fun clear(key: String) { if (!isActive(key)) _state.update { it - key } }

    fun hasPartial(dest: File) = metaOf(dest).exists()

    private fun partOf(dest: File) = File(dest.path + ".part")
    private fun metaOf(dest: File) = File(dest.path + ".part.meta")
    private fun put(s: DlState) = _state.update { it + (s.key to s) }

    private fun request(url: String, headers: Map<String, String>, range: String? = null) =
        Request.Builder().url(url).apply { headers.forEach { (k, v) -> header(k, v) }; range?.let { header("Range", "bytes=$it") } }.build()

    private suspend fun run(key: String, title: String, url: String, dest: File, headers: Map<String, String>) {
        dest.parentFile?.mkdirs()
        val part = partOf(dest); val metaFile = metaOf(dest)
        // Resume from saved progress when the same URL's partial file is still there.
        var meta = runCatching { HarborJson.decodeFromString(Meta.serializer(), metaFile.readText()) }.getOrNull()
            ?.takeIf { it.url == url && part.exists() && part.length() == it.total }
        if (meta == null) {
            // Probe size and Range support with a 1-byte request.
            val (total, ranged) = http.newCall(request(url, headers, "0-0")).execute().use { r ->
                if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
                val cr = r.header("Content-Range")?.substringAfter('/')?.toLongOrNull()
                if (r.code == 206 && cr != null) cr to true else (r.body?.contentLength() ?: -1L) to false
            }
            val n = if (!ranged || total <= 0) 1 else (total / MIN_PART).coerceIn(1, MAX_PARTS.toLong()).toInt()
            val size = if (total > 0) total / n else -1
            val segs = if (total <= 0) listOf(Seg(0, -1, 0)) else (0 until n).map { i ->
                val s = i * size; Seg(s, if (i == n - 1) total - 1 else s + size - 1, s)
            }
            meta = Meta(url, total, segs, ranged)
            RandomAccessFile(part, "rw").use { if (total > 0) it.setLength(total) else it.setLength(0) }
            metaFile.writeText(HarborJson.encodeToString(Meta.serializer(), meta))
        }
        val m = meta
        if (!m.ranged) m.segs.forEach { it.pos = it.start }   // no Range support: a restart can only begin at 0
        val done = AtomicLong(m.segs.sumOf { it.pos - it.start })
        put(DlState(key, title, DlStatus.Running, done.get(), m.total, parts = m.segs.size))

        coroutineScope {
            // Speed meter + progress checkpoint, once a second.
            val meter = launch {
                var last = done.get(); var ema = 0.0
                while (isActive) {
                    delay(1000)
                    val now = done.get(); val inst = (now - last).toDouble(); last = now
                    ema = if (ema == 0.0) inst else ema * 0.6 + inst * 0.4
                    put(DlState(key, title, DlStatus.Running, now, m.total, ema.toLong(), m.segs.size))
                    runCatching { metaFile.writeText(HarborJson.encodeToString(Meta.serializer(), m)) }
                }
            }
            m.segs.filter { it.end < 0 || it.pos <= it.end }.map { seg ->
                async {
                    var attempt = 0
                    while (true) {
                        try { fetch(url, headers, part, seg, done, m.ranged); break }
                        catch (e: IOException) {
                            if (++attempt > RETRIES || !m.ranged) throw e
                            delay(1000L * attempt * attempt)   // network hiccup: back off and resume this part
                        }
                    }
                }
            }.awaitAll()
            meter.cancel()
        }
        metaFile.delete()
        if (dest.exists()) dest.delete()
        if (!part.renameTo(dest)) throw IOException("Couldn't save ${dest.name}")
    }

    private suspend fun fetch(url: String, headers: Map<String, String>, part: File, seg: Seg, done: AtomicLong, ranged: Boolean) = coroutineScope {
        val range = if (!ranged) null else "${seg.pos}-${seg.end}"
        http.newCall(request(url, headers, range)).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            if (ranged && r.code != 206) throw IOException("Server ignored the byte range")
            RandomAccessFile(part, "rw").use { raf ->
                raf.seek(seg.pos)
                val input = r.body!!.byteStream(); val buf = ByteArray(128 * 1024)
                while (true) {
                    ensureActive()
                    val n = input.read(buf); if (n < 0) break
                    raf.write(buf, 0, n); seg.pos += n; done.addAndGet(n.toLong())
                }
            }
        }
        if (ranged && seg.pos <= seg.end) throw IOException("Connection closed early")
    }
}
