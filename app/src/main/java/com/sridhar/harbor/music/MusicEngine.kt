package com.sridhar.harbor.music

import android.content.ComponentName
import android.content.Context
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.net.ConnectivityManager
import android.net.Uri
import androidx.annotation.OptIn
import androidx.core.os.bundleOf
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.sridhar.harbor.data.HarborJson
import com.sridhar.harbor.data.SettingsStore
import com.sridhar.harbor.data.music.NavidromeRepository
import com.sridhar.harbor.data.music.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import okhttp3.OkHttpClient
import java.io.File

enum class RepeatMode { Off, All, One }

/** Music audio quality. Stored by ordinal – only ever append. [ordered] is the display order. */
/** Shared with OfflineMusic (same "music_engine" prefs). */
internal const val DOWNLOAD_QUALITY_KEY = "quality_download"

enum class StreamQuality(val kbps: Int) {
    Original(0), High(320), Normal(192), Saver(128), Low(96);
    companion object {
        val ordered = listOf(Original, High, Normal, Saver, Low)
        internal fun of(i: Int, def: StreamQuality) = entries.getOrElse(i) { def }
    }
}

data class MusicState(
    val queue: List<Song> = emptyList(),
    val index: Int = -1,
    val playing: Boolean = false,
    val buffering: Boolean = false,
    val durationMs: Long = 0,
    val shuffle: Boolean = false,
    val repeat: RepeatMode = RepeatMode.Off,
    val sleepAt: Long? = null,           // epoch ms, or null
    val sleepAfterSong: Boolean = false,
    val source: String? = null,          // "Album · Leo", "Radio · Naa Ready"
    val error: String? = null,
    val likedIds: Set<String> = emptySet(),
) {
    val current: Song? get() = queue.getOrNull(index)
    val upNext: List<Song> get() = if (index < 0) emptyList() else queue.drop(index + 1)
}

/**
 * One music player for the whole process (phone, TV, Android Auto, notification). UI reads [state];
 * [MusicService] wraps [player] in a media session for background playback and system controls.
 */
@OptIn(UnstableApi::class)
class MusicEngine(private val context: Context, private val repo: NavidromeRepository, private val settings: SettingsStore, http: OkHttpClient,
                  private val offline: () -> com.sridhar.harbor.data.music.OfflineMusic) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + com.sridhar.harbor.CrashGuard)
    private val prefs = context.getSharedPreferences("music_engine", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(MusicState())
    val state: StateFlow<MusicState> = _state.asStateFlow()

    /** Played songs are kept on disk (LRU, 512 MB): instant replays, gapless prefetch, spotty-network resilience. */
    private val cache by lazy {
        SimpleCache(File(context.cacheDir, "music"), LeastRecentlyUsedCacheEvictor(512L * 1024 * 1024), StandaloneDatabaseProvider(context))
    }

    val player: ExoPlayer by lazy {
        // DefaultDataSource routes file:// (offline downloads) to local storage and http(s) to OkHttp.
        val upstream = androidx.media3.datasource.DefaultDataSource.Factory(context, OkHttpDataSource.Factory(http))
        // Live radio has no length: skip the disk cache for it (writing an endless stream into the song cache stalled tuning).
        val cached = CacheDataSource.Factory().setCache(cache).setUpstreamDataSourceFactory(upstream)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR or CacheDataSource.FLAG_IGNORE_CACHE_FOR_UNSET_LENGTH_REQUESTS)
        // Radio recordings are raw MP3/AAC files with no length header: estimate duration (and allow seeking) from the
        // constant bitrate, so Now Playing shows played / total time. Only for recordings – on a live AAC station the
        // extractor first reads ~1000 frames (≈25 s of audio arriving in real time) to average them: stations hung.
        val recordings = DefaultMediaSourceFactory(cached, androidx.media3.extractor.DefaultExtractorsFactory()
            .setConstantBitrateSeekingEnabled(true).setConstantBitrateSeekingAlwaysEnabled(true))
        val everything = DefaultMediaSourceFactory(cached)
        val sources = object : androidx.media3.exoplayer.source.MediaSource.Factory by everything {
            override fun createMediaSource(mediaItem: MediaItem) =
                (if (mediaItem.mediaId.startsWith("rec:")) recordings else everything).createMediaSource(mediaItem)
        }
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(sources)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
            .setHandleAudioBecomingNoisy(true)
            // Start after 1 s of audio instead of 2.5 s – station switches feel instant.
            .setLoadControl(androidx.media3.exoplayer.DefaultLoadControl.Builder().setBufferDurationsMs(15_000, 50_000, 1_000, 2_000).build())
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build().also { p -> p.addListener(listener); p.pauseAtEndOfMediaItems = false }
    }

    /** Player's duration, or – when the file doesn't say (some recordings) – the length we know from the song itself. */
    private fun knownDuration(): Long = player.duration.takeIf { it != C.TIME_UNSET && it > 0 }
        ?: _state.value.queue.getOrNull(player.currentMediaItemIndex)?.duration?.takeIf { it > 0 }?.let { it * 1000L } ?: 0L

    /** A radio station won't play: ask for its current link (the directory may have fixed it). Set by AppContainer. */
    var radioRelocate: (suspend (String) -> com.sridhar.harbor.data.music.RadioStation?)? = null
    /** Stations already re-pulled after an error this session – one retry each, never a loop. */
    private val radioRetried = mutableSetOf<String>()
    private var controller: MediaController? = null
    private var ticker: Job? = null
    private var scrobbled = false
    private var equalizer: Equalizer? = null
    private var bass: BassBoost? = null

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.update { it.copy(playing = isPlaying) }
            if (isPlaying) startTicker() else persist()
        }
        override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
            _state.update { it.copy(durationMs = knownDuration()) }
        }
        override fun onPlaybackStateChanged(s: Int) {
            if (s == Player.STATE_READY) _state.value.current?.id?.let { radioRetried.remove(it.removePrefix("radio:")) }
            _state.update { it.copy(buffering = s == Player.STATE_BUFFERING, durationMs = knownDuration()) }
        }
        override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
            if (_state.value.sleepAfterSong && reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) { player.pause(); _state.update { it.copy(sleepAfterSong = false) } }
            _state.update { it.copy(index = player.currentMediaItemIndex, durationMs = knownDuration(), error = null) }
            scrobbled = false
            _state.value.current?.takeIf { it.streamUrl == null }?.let { s -> scope.launch { repo.scrobble(s.id, submission = false) } }
            persist()
        }
        override fun onShuffleModeEnabledChanged(on: Boolean) = _state.update { it.copy(shuffle = on) }
        override fun onPlayerError(e: PlaybackException) {
            val cur = _state.value.queue.getOrNull(player.currentMediaItemIndex)
            // Live radio: re-pull the station list and retry with the fresh link (once), instead of jumping stations.
            if (cur != null && cur.isLive && cur.id.startsWith("radio:")) { retryRadio(cur, player.currentMediaItemIndex, e); return }
            _state.update { it.copy(error = com.sridhar.harbor.L10n.s(com.sridhar.harbor.R.string.mu_cant_play, e.errorCodeName)) }
            if (player.hasNextMediaItem()) { player.seekToNextMediaItem(); player.prepare(); player.play() }
        }
        override fun onAudioSessionIdChanged(id: Int) = attachEffects(id)
    }

    private fun retryRadio(cur: Song, index: Int, e: PlaybackException) {
        val stationId = cur.id.removePrefix("radio:")
        val failed = { _state.update { it.copy(error = com.sridhar.harbor.L10n.s(com.sridhar.harbor.R.string.mu_cant_play, e.errorCodeName), buffering = false) } }
        val relocate = radioRelocate
        if (relocate == null || !radioRetried.add(stationId)) { failed(); return }
        _state.update { it.copy(buffering = true, error = null) }
        scope.launch {
            val fixed = runCatching { relocate(stationId) }.getOrNull()
            // The user may have moved on while we were fetching.
            if (_state.value.queue.getOrNull(index)?.id != cur.id || player.currentMediaItemIndex != index) return@launch
            if (fixed == null) { failed(); return@launch }
            val song = fixed.toSong()
            player.replaceMediaItem(index, toItem(song))
            _state.update { s -> s.copy(queue = s.queue.toMutableList().also { q -> if (index in q.indices) q[index] = song }) }
            player.prepare(); player.play()
        }
    }

    // ---------------- queue ----------------

    private var playJob: Job? = null

    /**
     * Plays [songs] from [startIndex]. The chosen one starts on its own right away – the old one stops at once and
     * the UI switches immediately – and the rest of the queue (e.g. every radio station, each with rendered
     * artwork) is built off the main thread and added around it. Tapping another station while one is still
     * loading cancels the first.
     */
    fun play(songs: List<Song>, startIndex: Int = 0, shuffle: Boolean = false, source: String? = null) {
        if (songs.isEmpty()) return
        val start = (if (shuffle && startIndex == 0) songs.indices.random() else startIndex).coerceIn(songs.indices)
        playJob?.cancel()
        radioRetried.clear()   // a fresh tap may re-pull a station's link again
        player.stop()
        _state.update { it.copy(queue = songs, index = start, source = source, shuffle = shuffle, error = null, playing = false, buffering = true) }
        playJob = scope.launch {
            ensureService()
            player.shuffleModeEnabled = shuffle
            val first = kotlinx.coroutines.withContext(Dispatchers.Default) { toItem(songs[start]) }
            player.setMediaItem(first)
            player.prepare(); player.play()
            if (songs.size > 1) {
                val before = kotlinx.coroutines.withContext(Dispatchers.Default) { songs.subList(0, start).map { toItem(it) } }
                val after = kotlinx.coroutines.withContext(Dispatchers.Default) { songs.subList(start + 1, songs.size).map { toItem(it) } }
                if (after.isNotEmpty()) player.addMediaItems(after)
                if (before.isNotEmpty()) player.addMediaItems(0, before)
                _state.update { it.copy(index = player.currentMediaItemIndex) }
            }
        }
    }

    /** "Song radio": this song, then similar songs (falls back to random picks). */
    fun radio(seed: Song) = scope.launch {
        val similar = runCatching { repo.similarSongs(seed.id, 40) }.getOrDefault(emptyList())
        val extra = if (similar.size >= 10) similar else similar + runCatching { repo.randomSongs(40) }.getOrDefault(emptyList())
        play(listOf(seed) + extra.filter { it.id != seed.id }.distinctBy { it.id }, 0, source = com.sridhar.harbor.L10n.s(com.sridhar.harbor.R.string.mu_src_radio, seed.displayTitle))
    }

    fun playNext(song: Song) = insert(listOf(song), (_state.value.index + 1).coerceAtLeast(0))
    fun addToQueue(songs: List<Song>) = insert(songs, _state.value.queue.size)

    private fun insert(songs: List<Song>, at: Int) = scope.launch {
        if (_state.value.queue.isEmpty()) { play(songs); return@launch }
        player.addMediaItems(at, songs.map { toItem(it) })
        _state.update { s -> s.copy(queue = s.queue.toMutableList().apply { addAll(at.coerceAtMost(size), songs) }) }
        persist()
    }

    fun removeAt(i: Int) {
        if (i !in _state.value.queue.indices || i == _state.value.index) return
        player.removeMediaItem(i)
        _state.update { s -> s.copy(queue = s.queue.toMutableList().apply { removeAt(i) }, index = player.currentMediaItemIndex) }
    }

    fun move(from: Int, to: Int) {
        val q = _state.value.queue; if (from !in q.indices || to !in q.indices) return
        player.moveMediaItem(from, to)
        _state.update { s -> s.copy(queue = s.queue.toMutableList().apply { add(to, removeAt(from)) }, index = player.currentMediaItemIndex) }
    }

    fun jumpTo(i: Int) { player.seekTo(i, 0); player.play() }

    // ---------------- transport ----------------

    fun toggle() { if (player.isPlaying) player.pause() else { ensureService(); if (player.playbackState == Player.STATE_IDLE) player.prepare(); player.play() } }
    fun next() { if (player.hasNextMediaItem()) player.seekToNextMediaItem() }
    /** Like every music app: restart the song if we're past 3 s, otherwise go back. */
    fun previous() { if (player.currentPosition > 3_000 || !player.hasPreviousMediaItem()) player.seekTo(0) else player.seekToPreviousMediaItem() }
    fun seekTo(ms: Long) = player.seekTo(ms)
    fun setShuffle(on: Boolean) { player.shuffleModeEnabled = on }
    fun cycleRepeat() {
        val next = when (_state.value.repeat) { RepeatMode.Off -> RepeatMode.All; RepeatMode.All -> RepeatMode.One; RepeatMode.One -> RepeatMode.Off }
        player.repeatMode = when (next) { RepeatMode.Off -> Player.REPEAT_MODE_OFF; RepeatMode.All -> Player.REPEAT_MODE_ALL; RepeatMode.One -> Player.REPEAT_MODE_ONE }
        _state.update { it.copy(repeat = next) }
    }

    fun position(): Long = player.currentPosition

    /** Emits the playback position ~4× a second for progress bars and lyrics. */
    fun positionFlow(): Flow<Long> = flow { while (currentCoroutineContext().isActive) { emit(player.currentPosition); delay(250) } }

    // ---------------- sleep timer (with a gentle fade-out) ----------------

    private var sleepJob: Job? = null

    fun sleepIn(minutes: Int?) {
        sleepJob?.cancel(); player.volume = 1f
        if (minutes == null) { _state.update { it.copy(sleepAt = null, sleepAfterSong = false) }; return }
        val at = System.currentTimeMillis() + minutes * 60_000L
        _state.update { it.copy(sleepAt = at, sleepAfterSong = false) }
        sleepJob = scope.launch {
            delay((at - System.currentTimeMillis() - 15_000).coerceAtLeast(0))
            for (i in 15 downTo 1) { player.volume = i / 15f; delay(1_000) }   // 15 s fade
            player.pause(); player.volume = 1f
            _state.update { it.copy(sleepAt = null) }
        }
    }

    fun sleepAfterThisSong() { sleepJob?.cancel(); _state.update { it.copy(sleepAfterSong = true, sleepAt = null) } }

    // ---------------- likes ----------------

    fun setLikedIds(ids: Set<String>) = _state.update { it.copy(likedIds = ids) }

    fun toggleLike(song: Song) {
        if (song.streamUrl != null) return   // radio stations aren't Navidrome songs
        val liked = song.id !in _state.value.likedIds
        _state.update { s -> s.copy(likedIds = if (liked) s.likedIds + song.id else s.likedIds - song.id) }
        scope.launch { runCatching { repo.setLiked(song.id, liked) }.onFailure { _state.update { s -> s.copy(likedIds = if (liked) s.likedIds - song.id else s.likedIds + song.id) } } }
    }

    // ---------------- quality / data saver ----------------

    /** Streaming quality on Wi-Fi / Ethernet. */
    var quality: StreamQuality
        get() = StreamQuality.of(prefs.getInt("quality", 0), StreamQuality.Original)
        set(v) = prefs.edit().putInt("quality", v.ordinal).apply()
    /** Streaming quality on mobile data (or any metered network). Before 2.15 this was the "data saver" switch. */
    var mobileQuality: StreamQuality
        get() = if (prefs.contains("quality_mobile")) StreamQuality.of(prefs.getInt("quality_mobile", 3), StreamQuality.Saver)
            else if (saveDataOnMobile) StreamQuality.Saver else quality
        set(v) = prefs.edit().putInt("quality_mobile", v.ordinal).apply()
    /** Quality of songs saved for offline (Original = the file as it is on the server). */
    var downloadQuality: StreamQuality
        get() = StreamQuality.of(prefs.getInt(DOWNLOAD_QUALITY_KEY, 0), StreamQuality.Original)
        set(v) = prefs.edit().putInt(DOWNLOAD_QUALITY_KEY, v.ordinal).apply()
    var saveDataOnMobile: Boolean
        get() = prefs.getBoolean("data_saver", true)
        set(v) = prefs.edit().putBoolean("data_saver", v).apply()

    private fun bitrateNow(): Int {
        val metered = (context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager).isActiveNetworkMetered
        return if (metered) mobileQuality.kbps else quality.kbps
    }

    private suspend fun toItem(s: Song): MediaItem {
        val cfg = settings.current()
        // Radio recording: a local file with a real timeline.
        if (s.id.startsWith("rec:")) s.streamUrl?.let { url ->
            return MediaItem.Builder().setMediaId(s.id).setUri(url)
                .setMediaMetadata(MediaMetadata.Builder().setTitle(s.title).setArtist(s.artist).setIsPlayable(true).setIsBrowsable(false)
                    .setArtworkData(RadioArt.png(s.title, recording = true), MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC).setExtras(bundleOf("song" to HarborJson.encodeToString(Song.serializer(), s))).build())
                .build()
        }
        s.streamUrl?.let { url ->
            // Live radio: straight from the station, never cached.
            return MediaItem.Builder().setMediaId(s.id).setUri(url)
                .setMediaMetadata(MediaMetadata.Builder().setTitle(s.title).setArtist(s.artist).setStation(s.title)
                    .setArtworkData(RadioArt.png(s.title, recording = false), MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                    .setIsPlayable(true).setIsBrowsable(false).setMediaType(MediaMetadata.MEDIA_TYPE_RADIO_STATION)
                    .setExtras(bundleOf("song" to HarborJson.encodeToString(Song.serializer(), s))).build())
                .build()
        }
        // Downloaded for offline: play the local file (works with no network, no data used).
        offline().localFile(s.id)?.let { f ->
            return MediaItem.Builder().setMediaId(s.id).setUri(Uri.fromFile(f))
                .setMediaMetadata(MediaMetadata.Builder().setTitle(s.displayTitle).setArtist(s.displayArtist).setAlbumTitle(s.displayAlbum)
                    .setArtworkUri((offline().coverFile(s.coverArt)?.let(Uri::fromFile)) ?: repo.coverUrl(cfg, s.coverArt, 800)?.let(Uri::parse))
                    .setIsPlayable(true).setIsBrowsable(false).setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                    .setExtras(bundleOf("song" to HarborJson.encodeToString(Song.serializer(), s))).build()).build()
        }
        return MediaItem.Builder()
            .setMediaId(s.id)
            .setUri(repo.streamUrl(cfg, s.id, bitrateNow()))
            // Cache key without the rotating auth salt, so a cached song is reused across sessions.
            .setCustomCacheKey("song-${s.id}-${bitrateNow()}")
            .setMediaMetadata(
                MediaMetadata.Builder().setTitle(s.displayTitle).setArtist(s.displayArtist).setAlbumTitle(s.displayAlbum)
                    .setArtworkUri(repo.coverUrl(cfg, s.coverArt, 800)?.let(Uri::parse))
                    .setIsPlayable(true).setIsBrowsable(false).setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                    .setExtras(bundleOf("song" to HarborJson.encodeToString(Song.serializer(), s))).build(),
            ).build()
    }

    // ---------------- equalizer ----------------

    val eqPresets: List<String> get() = equalizer?.let { e -> (0 until e.numberOfPresets).map { e.getPresetName(it.toShort()) } }.orEmpty()
    var eqPreset: Int
        get() = prefs.getInt("eq_preset", -1)
        set(v) { prefs.edit().putInt("eq_preset", v).apply(); applyEq() }
    var bassBoost: Int  // 0..1000
        get() = prefs.getInt("bass", 0)
        set(v) { prefs.edit().putInt("bass", v).apply(); runCatching { bass?.setStrength(v.toShort()); bass?.enabled = v > 0 } }

    private fun attachEffects(sessionId: Int) {
        runCatching { equalizer?.release(); bass?.release() }
        if (sessionId == C.AUDIO_SESSION_ID_UNSET) return
        equalizer = runCatching { Equalizer(0, sessionId) }.getOrNull()
        bass = runCatching { BassBoost(0, sessionId) }.getOrNull()
        applyEq(); bassBoost = bassBoost
    }

    private fun applyEq() = runCatching {
        val e = equalizer ?: return@runCatching
        val p = eqPreset
        if (p in 0 until e.numberOfPresets) { e.usePreset(p.toShort()); e.enabled = true } else e.enabled = false
    }

    // ---------------- background service, scrobbling, resume ----------------

    fun ensureService() {
        if (controller != null) return
        val token = SessionToken(context, ComponentName(context, MusicService::class.java))
        val f = MediaController.Builder(context, token).buildAsync()
        f.addListener({ controller = runCatching { f.get() }.getOrNull() }, androidx.core.content.ContextCompat.getMainExecutor(context))
    }

    private fun startTicker() {
        if (ticker?.isActive == true) return
        ticker = scope.launch {
            var n = 0
            while (isActive && player.isPlaying) {
                val d = player.duration; val p = player.currentPosition
                // Subsonic/Last.fm rule: a play counts after half the song or 4 minutes.
                if (!scrobbled && d > 30_000 && (p >= d / 2 || p >= 240_000)) { scrobbled = true; _state.value.current?.takeIf { it.streamUrl == null }?.let { s -> launch { repo.scrobble(s.id, true) } } }
                if (++n % 10 == 0) persist()
                delay(1_000)
            }
        }
    }

    private fun persist() {
        val s = _state.value
        if (s.queue.isEmpty()) return
        prefs.edit()
            .putString("queue", HarborJson.encodeToString(ListSerializer(Song.serializer()), s.queue.take(500)))
            .putInt("index", s.index).putLong("pos", player.currentPosition).putString("source", s.source)
            .apply()
    }

    /** Restores the last queue paused at the last position – "continue listening". */
    fun restore() = scope.launch {
        if (_state.value.queue.isNotEmpty() || !settings.current().navidromeReady) return@launch
        val q = runCatching { HarborJson.decodeFromString(ListSerializer(Song.serializer()), prefs.getString("queue", null) ?: return@launch) }.getOrNull() ?: return@launch
        val idx = prefs.getInt("index", 0).coerceIn(0, (q.size - 1).coerceAtLeast(0))
        player.setMediaItems(q.map { toItem(it) }, idx, prefs.getLong("pos", 0))
        player.prepare()
        _state.update { it.copy(queue = q, index = idx, source = prefs.getString("source", null)) }
    }

    fun stopAndClear() {
        player.stop(); player.clearMediaItems(); sleepIn(null)
        prefs.edit().remove("queue").apply()
        _state.update { MusicState(likedIds = it.likedIds) }
    }
}
