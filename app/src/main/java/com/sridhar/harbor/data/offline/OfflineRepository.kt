package com.sridhar.harbor.data.offline

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import com.sridhar.harbor.data.HarborJson
import com.sridhar.harbor.data.SettingsStore
import com.sridhar.harbor.data.jellyfin.BaseItem
import com.sridhar.harbor.data.jellyfin.JellyfinRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

@Serializable
data class OfflineEntry(
    val itemId: String,
    val name: String,
    val subtitle: String? = null,
    val overview: String? = null,
    val downloadId: Long,
    val filePath: String,
    val posterPath: String? = null,
    val runTimeTicks: Long? = null,
    val addedAt: Long = System.currentTimeMillis(),
    /** Kept so parental control still applies with no network. */
    val officialRating: String? = null,
    val seriesId: String? = null,
)

data class OfflineProgress(val status: Int, val downloaded: Long, val total: Long, val bytesPerSec: Long = 0) {
    val fraction get() = if (total > 0) downloaded.toFloat() / total else 0f
    val done get() = status == DownloadManager.STATUS_SUCCESSFUL
    val failed get() = status == DownloadManager.STATUS_FAILED
    /** Interrupted (app closed / network lost): resumes from where it stopped. */
    val paused get() = status == DownloadManager.STATUS_PAUSED
    /** Actually transferring (or waiting its turn) right now. */
    val active get() = status == DownloadManager.STATUS_RUNNING || status == DownloadManager.STATUS_PENDING
}

/** Downloads original media files from Jellyfin for offline playback via the system DownloadManager. */
class OfflineRepository(
    private val context: Context,
    private val settings: SettingsStore,
    private val jellyfin: JellyfinRepository,
    private val downloader: com.sridhar.harbor.data.download.SegmentedDownloader,
) {
    private fun key(itemId: String) = "media:$itemId"
    private val dm = context.getSystemService(DownloadManager::class.java)
    private val serializer = ListSerializer(OfflineEntry.serializer())

    val entries: Flow<List<OfflineEntry>> = settings.offlineIndex.map { raw ->
        (if (raw.isBlank()) emptyList() else runCatching { HarborJson.decodeFromString(serializer, raw) }.getOrDefault(emptyList())).also { cached = it }
    }

    private suspend fun save(list: List<OfflineEntry>) = settings.setOfflineIndex(HarborJson.encodeToString(serializer, list))

    /** Synchronous snapshot for UI that already observes [entries]. */
    fun entriesNow(): List<OfflineEntry> = cached
    @Volatile private var cached: List<OfflineEntry> = emptyList()

    suspend fun isDownloaded(itemId: String) = entries.first().any { it.itemId == itemId }
    suspend fun entry(itemId: String) = entries.first().firstOrNull { it.itemId == itemId }

    suspend fun download(item: BaseItem, http: OkHttpClient) = withContext(Dispatchers.IO) {
        val cfg = settings.current()
        val container = item.mediaSources.firstOrNull()?.container?.substringBefore(',') ?: "mkv"
        val safe = item.name.replace(Regex("[^A-Za-z0-9 ._-]"), "").take(60).ifBlank { item.id }
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)!!
        val file = File(dir, "${safe}_${item.id.take(8)}.$container")

        // Grab a poster so the offline shelf still has art without the server.
        val poster = File(context.filesDir, "posters/${item.id}.jpg").apply { parentFile?.mkdirs() }
        runCatching {
            http.newCall(Request.Builder().url(jellyfin.posterUrl(cfg, item, 400)).build()).execute().use { r ->
                r.body?.byteStream()?.use { input -> poster.outputStream().use { input.copyTo(it) } }
            }
        }

        val title = listOfNotNull(item.seriesName, item.episodeLabel, item.name).joinToString(" · ")
        // Multi-part parallel download (see SegmentedDownloader); downloadId -2 marks the new engine.
        downloader.enqueue(key(item.id), title, jellyfin.downloadUrl(cfg, item.id), file, mapOf("Authorization" to jellyfin.authHeader(cfg)))
        val id = -2L

        val entry = OfflineEntry(
            itemId = item.id, name = item.name,
            subtitle = listOfNotNull(item.seriesName, item.episodeLabel).joinToString(" · ").ifBlank { item.year?.toString() },
            officialRating = item.officialRating, seriesId = item.seriesId,
            overview = item.overview, downloadId = id, filePath = file.absolutePath,
            posterPath = poster.takeIf { it.exists() && it.length() > 0 }?.absolutePath,
            runTimeTicks = item.runTimeTicks,
        )
        save(entries.first().filterNot { it.itemId == item.id } + entry)
    }

    fun progress(entry: OfflineEntry): OfflineProgress {
        if (entry.downloadId < 0) {
            downloader.state.value[key(entry.itemId)]?.let { st ->
                val status = when (st.status) {
                    com.sridhar.harbor.data.download.DlStatus.Queued -> DownloadManager.STATUS_PENDING
                    com.sridhar.harbor.data.download.DlStatus.Running -> DownloadManager.STATUS_RUNNING
                    com.sridhar.harbor.data.download.DlStatus.Done -> DownloadManager.STATUS_SUCCESSFUL
                    else -> DownloadManager.STATUS_FAILED
                }
                return OfflineProgress(status, st.downloaded, st.total, st.bytesPerSec)
            }
            val f = File(entry.filePath)
            return when {
                downloader.hasPartial(f) -> OfflineProgress(DownloadManager.STATUS_PAUSED, File(f.path + ".part").length().coerceAtLeast(0), 0)
                f.exists() -> OfflineProgress(DownloadManager.STATUS_SUCCESSFUL, f.length(), f.length())
                else -> OfflineProgress(DownloadManager.STATUS_FAILED, 0, 0)
            }
        }
        dm.query(DownloadManager.Query().setFilterById(entry.downloadId)).use { c ->
            if (c != null && c.moveToFirst()) {
                return OfflineProgress(
                    status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)),
                    downloaded = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)),
                    total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)),
                )
            }
        }
        // Row gone from DownloadManager: trust the file on disk.
        val f = File(entry.filePath)
        return if (f.exists()) OfflineProgress(DownloadManager.STATUS_SUCCESSFUL, f.length(), f.length())
        else OfflineProgress(DownloadManager.STATUS_FAILED, 0, 0)
    }

    /** Continue unfinished parallel downloads (called at app start and from the Retry button). */
    suspend fun resume(entry: OfflineEntry? = null) = withContext(Dispatchers.IO) {
        val cfg = settings.current()
        (entry?.let { listOf(it) } ?: entries.first()).filter { it.downloadId < 0 && !File(it.filePath).exists() }.forEach { e ->
            downloader.enqueue(key(e.itemId), listOfNotNull(e.name, e.subtitle).joinToString(" · "), jellyfin.downloadUrl(cfg, e.itemId),
                File(e.filePath), mapOf("Authorization" to jellyfin.authHeader(cfg)))
        }
    }

    /** Cancel from the notification (by downloader key): stops it and removes the half-downloaded file. */
    suspend fun cancelByKey(downloadKey: String): Boolean {
        val entry = entries.first().firstOrNull { key(it.itemId) == downloadKey } ?: return false
        remove(entry); return true
    }

    /** Cancel every download that is running or waiting (the "Downloading…" banner's Cancel). */
    suspend fun cancelActive() = entries.first().filter { progress(it).active }.forEach { remove(it) }

    suspend fun remove(entry: OfflineEntry) = withContext(Dispatchers.IO) {
        if (entry.downloadId < 0) downloader.cancel(key(entry.itemId), File(entry.filePath))
        else runCatching { dm.remove(entry.downloadId) }
        File(entry.filePath).delete()
        entry.posterPath?.let { File(it).delete() }
        save(entries.first().filterNot { it.itemId == entry.itemId })
    }
}
