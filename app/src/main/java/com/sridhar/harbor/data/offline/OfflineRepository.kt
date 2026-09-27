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

data class OfflineProgress(val status: Int, val downloaded: Long, val total: Long) {
    val fraction get() = if (total > 0) downloaded.toFloat() / total else 0f
    val done get() = status == DownloadManager.STATUS_SUCCESSFUL
    val failed get() = status == DownloadManager.STATUS_FAILED
}

/** Downloads original media files from Jellyfin for offline playback via the system DownloadManager. */
class OfflineRepository(
    private val context: Context,
    private val settings: SettingsStore,
    private val jellyfin: JellyfinRepository,
) {
    private val dm = context.getSystemService(DownloadManager::class.java)
    private val serializer = ListSerializer(OfflineEntry.serializer())

    val entries: Flow<List<OfflineEntry>> = settings.offlineIndex.map { raw ->
        if (raw.isBlank()) emptyList() else runCatching { HarborJson.decodeFromString(serializer, raw) }.getOrDefault(emptyList())
    }

    private suspend fun save(list: List<OfflineEntry>) = settings.setOfflineIndex(HarborJson.encodeToString(serializer, list))

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
        val request = DownloadManager.Request(Uri.parse(jellyfin.downloadUrl(cfg, item.id)))
            .addRequestHeader("Authorization", jellyfin.authHeader(cfg))
            .setTitle(title)
            .setDescription("JellyVerse offline download")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationUri(Uri.fromFile(file))
            .setAllowedOverMetered(true)
        val id = dm.enqueue(request)

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

    suspend fun remove(entry: OfflineEntry) = withContext(Dispatchers.IO) {
        runCatching { dm.remove(entry.downloadId) }
        File(entry.filePath).delete()
        entry.posterPath?.let { File(it).delete() }
        save(entries.first().filterNot { it.itemId == entry.itemId })
    }
}
