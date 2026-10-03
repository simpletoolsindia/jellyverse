package com.sridhar.harbor.radio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.sridhar.harbor.HarborApp
import com.sridhar.harbor.L10n
import com.sridhar.harbor.MainActivity
import com.sridhar.harbor.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.net.URI
import java.util.concurrent.atomic.AtomicLong

/**
 * Records a radio stream to a file for a fixed time.
 *
 * Built to stay cool on long recordings: the stream's bytes are written to disk as they arrive – no decoding,
 * no re-encoding – so the CPU is mostly idle (similar to a slow download). Plain Icecast/Shoutcast streams are
 * copied as-is (MP3/AAC); HLS stations (.m3u8) are recorded by appending each new segment. The notification is
 * refreshed every 5 s (elapsed, size, Stop) rather than continuously.
 */
class RecordService : Service() {
    companion object {
        const val CHANNEL = "jv_radio_rec"
        const val CHANNEL_DONE = "jv_radio_done"
        private const val ID = 7_400
        private const val ACTION_STOP = "jv.rec.stop"

        fun start(context: Context, station: String, url: String, durationMin: Int, scheduleId: String? = null) {
            ContextCompat.startForegroundService(context, Intent(context, RecordService::class.java)
                .putExtra("station", station).putExtra("url", url).putExtra("min", durationMin).putExtra("schedule", scheduleId))
        }

        fun stop(context: Context) = context.startService(Intent(context, RecordService::class.java).setAction(ACTION_STOP))

        fun channels(ctx: Context) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, L10n.s(R.string.rec_channel), NotificationManager.IMPORTANCE_LOW))
            nm.createNotificationChannel(NotificationChannel(CHANNEL_DONE, L10n.s(R.string.rec_channel_done), NotificationManager.IMPORTANCE_DEFAULT))
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var wake: PowerManager.WakeLock? = null
    private val nm by lazy { getSystemService(NotificationManager::class.java) }
    private val http: OkHttpClient by lazy {
        HarborApp.instance!!.container.http.newBuilder().readTimeout(30, java.util.concurrent.TimeUnit.SECONDS).build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        channels(this)
        if (intent?.action == ACTION_STOP) { job?.cancel(); return START_NOT_STICKY }
        val busy = job?.isActive == true
        val station = intent?.getStringExtra("station")
        val url = intent?.getStringExtra("url")
        val durationMs = (intent?.getIntExtra("min", 60) ?: 60) * 60_000L
        val scheduleId = intent?.getStringExtra("schedule")
        // Every start must call startForeground; while recording, keep showing the recording in progress.
        val live = RadioLibrary.live.value
        val shown = if (busy && live != null) progress(live.station, System.currentTimeMillis() - live.startedAt, live.durationMs, live.bytes)
            else progress(station ?: "Radio", 0, durationMs, 0)
        ServiceCompat.startForeground(this, ID, shown, if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0)
        if (busy) return START_NOT_STICKY   // one recording at a time
        if (station == null || url == null) { stopSelf(); return START_NOT_STICKY }
        wake = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "JellyVerse:radio-rec")
            .apply { setReferenceCounted(false); acquire(durationMs + 10 * 60_000L) }
        job = scope.launch { record(station, url, durationMs, scheduleId) }
        return START_NOT_STICKY
    }

    private suspend fun record(station: String, url: String, durationMs: Long, scheduleId: String?) {
        val started = System.currentTimeMillis()
        val safe = station.replace(Regex("[^A-Za-z0-9 ._-]"), "").trim().take(40).ifBlank { "Radio" }
        val stamp = java.text.SimpleDateFormat("yyyy-MM-dd HH-mm", java.util.Locale.US).format(started)
        var file = File(RadioLibrary.dir, "$safe $stamp.part")
        val bytes = AtomicLong(0)
        RadioLibrary.live.value = LiveRecording(station, started, durationMs, 0, scheduleId)
        var ext = "mp3"
        var error: String? = null
        val ticker = scope.launch {
            while (isActive) {
                delay(5000)
                val el = System.currentTimeMillis() - started
                RadioLibrary.live.value = LiveRecording(station, started, durationMs, bytes.get(), scheduleId)
                nm.notify(ID, progress(station, el, durationMs, bytes.get()))
            }
        }
        try {
            FileOutputStream(file).use { out ->
                val deadline = started + durationMs
                var attempt = 0
                while (System.currentTimeMillis() < deadline) {
                    try {
                        ext = if (isHls(url)) recordHls(url, out, bytes, deadline) else recordStream(url, out, bytes, deadline)
                        break
                    } catch (e: java.io.IOException) {
                        // Station dropped: reconnect (backing off) until the time is up.
                        if (++attempt > 30) throw e
                        delay(2000L * attempt.coerceAtMost(5))
                    }
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Stopped by the user: keep what was recorded.
        } catch (e: Exception) {
            error = e.message ?: "Recording failed"
        } finally {
            ticker.cancel()
        }
        val elapsed = System.currentTimeMillis() - started
        val done = File(RadioLibrary.dir, "$safe $stamp.$ext")
        if (bytes.get() > 16_000 && file.renameTo(done)) {
            file = done
            RadioLibrary.addRecording(Recording(station = station, path = done.absolutePath, startedAt = started, durationMs = elapsed, bytes = bytes.get()))
            notifyDone(station, elapsed, null)
        } else {
            file.delete()
            notifyDone(station, elapsed, error ?: L10n.s(R.string.rec_nothing))
        }
        RadioLibrary.live.value = null
        wake?.let { if (it.isHeld) it.release() }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun isHls(url: String) = url.substringBefore('?').lowercase().let { it.endsWith(".m3u8") || it.endsWith(".m3u8/") }

    /** Icecast/Shoutcast: copy the body as it arrives (no ICY metadata requested, so it's clean audio). */
    private suspend fun recordStream(url: String, out: OutputStream, bytes: AtomicLong, deadline: Long): String = kotlinx.coroutines.coroutineScope {
        http.newCall(Request.Builder().url(url).header("User-Agent", "JellyVerse").build()).execute().use { r ->
            if (!r.isSuccessful) throw java.io.IOException("HTTP ${r.code}")
            val type = r.header("Content-Type").orEmpty().lowercase()
            val ext = when { "aac" in type -> "aac"; "ogg" in type -> "ogg"; "mp4" in type || "m4a" in type -> "m4a"; else -> "mp3" }
            val input = r.body!!.byteStream(); val buf = ByteArray(64 * 1024)
            while (System.currentTimeMillis() < deadline) {
                ensureActive()
                val n = input.read(buf); if (n < 0) throw java.io.IOException("Stream ended")
                out.write(buf, 0, n); bytes.addAndGet(n.toLong())
            }
            ext
        }
    }

    /** HLS: follow the live playlist and append each new segment (plus the init segment once, for fMP4). */
    private suspend fun recordHls(master: String, out: OutputStream, bytes: AtomicLong, deadline: Long): String = kotlinx.coroutines.coroutineScope {
        fun get(u: String) = http.newCall(Request.Builder().url(u).header("User-Agent", "JellyVerse").build()).execute().use { r ->
            if (!r.isSuccessful) throw java.io.IOException("HTTP ${r.code}"); r.body!!.string()
        }
        fun abs(base: String, ref: String) = URI(base).resolve(ref.trim()).toString()
        var playlistUrl = master
        var text = get(master)
        if ("#EXT-X-STREAM-INF" in text) {   // master playlist → first (usually only) audio variant
            val lines = text.lines(); val i = lines.indexOfFirst { it.startsWith("#EXT-X-STREAM-INF") }
            playlistUrl = abs(master, lines.drop(i + 1).first { it.isNotBlank() && !it.startsWith("#") }); text = get(playlistUrl)
        }
        val seen = LinkedHashSet<String>()
        var ext = "ts"; var wroteInit = false
        while (System.currentTimeMillis() < deadline) {
            ensureActive()
            Regex("#EXT-X-MAP:URI=\"([^\"]+)\"").find(text)?.let { m ->
                if (!wroteInit) { ext = "m4a"; download(abs(playlistUrl, m.groupValues[1]), out, bytes); wroteInit = true }
            }
            val segs = text.lines().filter { it.isNotBlank() && !it.startsWith("#") }
            for (s in segs) {
                val u = abs(playlistUrl, s)
                if (!seen.add(u)) continue
                if (!wroteInit && seen.size == 1) ext = when { ".aac" in u -> "aac"; ".mp3" in u -> "mp3"; ".m4s" in u || ".mp4" in u -> "m4a"; else -> "ts" }
                download(u, out, bytes)
                if (System.currentTimeMillis() >= deadline) break
            }
            while (seen.size > 200) seen.remove(seen.first())
            val target = Regex("#EXT-X-TARGETDURATION:(\\d+)").find(text)?.groupValues?.get(1)?.toLongOrNull() ?: 6
            delay((target * 1000 / 2).coerceIn(1000, 6000))
            text = get(playlistUrl)
        }
        ext
    }

    private fun download(u: String, out: OutputStream, bytes: AtomicLong) {
        http.newCall(Request.Builder().url(u).header("User-Agent", "JellyVerse").build()).execute().use { r ->
            if (!r.isSuccessful) throw java.io.IOException("HTTP ${r.code}")
            val n = r.body!!.byteStream().copyTo(out, 64 * 1024); bytes.addAndGet(n)
        }
    }

    private fun clock(ms: Long): String { val s = ms / 1000; return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s % 3600 / 60, s % 60) else "%d:%02d".format(s / 60, s % 60) }
    private fun mb(b: Long) = "%.1f MB".format(b / 1e6)

    private fun openApp(req: Int, extra: String) = PendingIntent.getActivity(this, req,
        Intent(this, MainActivity::class.java).setAction(Intent.ACTION_VIEW).setData(android.net.Uri.parse("jellyverse://open/$extra")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    private fun progress(station: String, elapsed: Long, total: Long, bytes: Long): Notification =
        NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_rec)
            .setLargeIcon(Avatar.bitmap(this))
            .setContentTitle(L10n.s(R.string.rec_now, station))
            .setContentText("${clock(elapsed)} / ${clock(total)} · ${mb(bytes)}")
            .setProgress(1000, (elapsed * 1000 / total.coerceAtLeast(1)).toInt(), false)
            .setOngoing(true).setOnlyAlertOnce(true).setSilent(true)
            .setContentIntent(openApp(1, "recordings"))
            .addAction(0, L10n.s(R.string.rec_stop), PendingIntent.getService(this, 2,
                Intent(this, RecordService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE))
            .build()

    private fun notifyDone(station: String, elapsed: Long, error: String?) {
        nm.notify(("done$station${System.currentTimeMillis()}").hashCode(), NotificationCompat.Builder(this, CHANNEL_DONE)
            .setSmallIcon(R.drawable.ic_stat_rec)
            .setLargeIcon(Avatar.bitmap(this))
            .setContentTitle(if (error == null) L10n.s(R.string.rec_saved_title, station) else L10n.s(R.string.rec_failed_title, station))
            .setContentText(error ?: L10n.s(R.string.rec_saved_body, clock(elapsed)))
            .setContentIntent(openApp(3, "recordings")).setAutoCancel(true).build())
    }

    override fun onDestroy() { scope.cancel(); wake?.let { if (it.isHeld) it.release() }; super.onDestroy() }
}
