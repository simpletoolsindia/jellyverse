package com.sridhar.harbor.data.music

import android.content.Context
import com.sridhar.harbor.data.HarborJson
import com.sridhar.harbor.data.SettingsStore
import com.sridhar.harbor.data.download.DlState
import com.sridhar.harbor.data.download.DlStatus
import com.sridhar.harbor.data.download.SegmentedDownloader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/**
 * Songs saved for offline listening: the original file from Navidrome (via [SegmentedDownloader]) plus its cover,
 * in app storage. [MusicEngine] plays the local file whenever one exists, so these work with no network.
 */
class OfflineMusic(
    private val context: Context, private val settings: SettingsStore, private val repo: NavidromeRepository,
    private val downloader: SegmentedDownloader, private val http: OkHttpClient,
) {
    private val dir = File(context.filesDir, "music").apply { mkdirs() }
    private val covers = File(dir, "covers").apply { mkdirs() }
    private val index = File(dir, "index.json")
    private val ser = ListSerializer(Song.serializer())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _songs = MutableStateFlow(runCatching { HarborJson.decodeFromString(ser, index.readText()) }.getOrDefault(emptyList()))
    /** Every song saved (or being saved) offline, newest first. */
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    private fun key(id: String) = "song:$id"
    fun fileOf(s: Song) = File(dir, "${s.id}.${s.suffix ?: "mp3"}")
    fun coverFile(coverArt: String?) = coverArt?.let { File(covers, "$it.jpg") }?.takeIf { it.exists() }

    /** The playable local file, or null if the song isn't fully downloaded. */
    fun localFile(songId: String): File? = _songs.value.firstOrNull { it.id == songId }?.let(::fileOf)?.takeIf { it.exists() && !downloader.isActive(key(songId)) }

    fun isDownloaded(id: String) = localFile(id) != null
    fun progressOf(id: String): DlState? = downloader.state.value[key(id)]?.takeIf { it.status == DlStatus.Running || it.status == DlStatus.Queued }
    val progress get() = downloader.state

    private fun save() = runCatching { index.writeText(HarborJson.encodeToString(ser, _songs.value)) }

    fun download(list: List<Song>) {
        val todo = list.filter { it.streamUrl == null && !isDownloaded(it.id) }
        if (todo.isEmpty()) return
        scope.launch {
            val cfg = settings.current()
            _songs.value = todo + _songs.value.filterNot { s -> todo.any { it.id == s.id } }
            save()
            todo.forEach { s ->
                downloader.enqueue(key(s.id), "♪ ${s.displayTitle}", repo.downloadUrl(cfg, s.id), fileOf(s))
                // Cover art once per album, for offline Now Playing and lock screen.
                s.coverArt?.let { c -> File(covers, "$c.jpg").takeIf { !it.exists() }?.let { f ->
                    runCatching { http.newCall(Request.Builder().url(repo.coverUrl(cfg, c, 600)!!).build()).execute().use { r ->
                        if (r.isSuccessful) r.body?.byteStream()?.use { i -> f.outputStream().use { i.copyTo(it) } } } }
                } }
            }
        }
    }

    fun remove(list: List<Song>) {
        list.forEach { s -> downloader.cancel(key(s.id), fileOf(s)); fileOf(s).delete() }
        _songs.value = _songs.value.filterNot { s -> list.any { it.id == s.id } }
        save()
    }

    /** Continue song downloads interrupted by the app closing. */
    fun resume() = download(_songs.value.filter { !fileOf(it).exists() })

    val totalBytes: Long get() = _songs.value.sumOf { fileOf(it).length() }
}
