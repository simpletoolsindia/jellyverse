package com.sridhar.harbor.ui.player

import com.sridhar.harbor.L10n
import com.sridhar.harbor.R
import android.app.Application
import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import com.sridhar.harbor.HarborApp
import com.sridhar.harbor.data.jellyfin.BaseItem
import com.sridhar.harbor.data.jellyfin.MediaSegment
import com.sridhar.harbor.data.jellyfin.PlaybackReport
import com.sridhar.harbor.data.jellyfin.TICKS_PER_MS
import com.sridhar.harbor.data.jellyfin.TrickplayInfo
import com.sridhar.harbor.ui.components.friendly
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

enum class Quality(@androidx.annotation.StringRes val labelRes: Int, val bitrate: Int?) {
    Original(R.string.original_direct, null), Q20(R.string.s_20_mbps_1080p, 20_000_000), Q8(R.string.s_8_mbps_1080p, 8_000_000),
    Q4(R.string.s_4_mbps_720p, 4_000_000), Q2(R.string.s_2_mbps_480p, 2_000_000),;
    val label: String get() = com.sridhar.harbor.L10n.s(labelRes)
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
enum class Fit(@androidx.annotation.StringRes val labelRes: Int, val mode: Int) {
    Fit(R.string.fit, AspectRatioFrameLayout.RESIZE_MODE_FIT),
    Zoom(R.string.zoom, AspectRatioFrameLayout.RESIZE_MODE_ZOOM),
    Stretch(R.string.stretch, AspectRatioFrameLayout.RESIZE_MODE_FILL),;
    val label: String get() = com.sridhar.harbor.L10n.s(labelRes)
}

enum class DownloadState { Unavailable, None, Queued, Done }

/** A selectable audio/subtitle track: an ExoPlayer track (direct play) or a Jellyfin stream index (transcoding). */
/** In-player online subtitle search (through the server's subtitle provider, e.g. Open Subtitles). */
data class SubSearchUi(
    val language: String, val loading: Boolean = false, val results: List<com.sridhar.harbor.data.jellyfin.RemoteSubtitle>? = null,
    val error: String? = null, val downloadingId: String? = null, val isAdmin: Boolean = false, val installed: Boolean = false,
)

data class TrackOption(val label: String, val selected: Boolean, val groupIndex: Int, val trackIndex: Int, val language: String? = null, val streamIndex: Int? = null, val needsServer: Boolean = false)
data class ChapterMark(val index: Int, val name: String, val startMs: Long, val imageUrl: String?)
data class Trickplay(val itemId: String, val mediaSourceId: String, val info: TrickplayInfo)

data class PlayerStats(
    val video: String = "–", val audio: String = "–", val decoder: String = "–",
    val bitrate: String = "–", val dropped: Int = 0, val bufferSec: Float = 0f, val method: String = "–",
)

data class PlayerUi(
    val itemId: String? = null,
    val title: String = "",
    val subtitle: String? = null,
    val isPlaying: Boolean = false,
    val buffering: Boolean = true,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val bufferedMs: Long = 0,
    val error: String? = null,
    val notice: String? = null,
    val quality: Quality = Quality.Original,
    /** Jellyfin favourite (♥) for the title being played. */
    val favorite: Boolean = false,
    val fit: Fit = Fit.Fit,
    val speed: Float = 1f,
    val audio: List<TrackOption> = emptyList(),
    val text: List<TrackOption> = emptyList(),
    val textOff: Boolean = true,
    /** Server-side track choice for transcoded streams (Jellyfin stream indexes). */
    val audioIndex: Int? = null,
    val subIndex: Int? = null,
    /** True when we switched to the server only to decode an audio track – undone when a playable track is picked. */
    val serverForAudio: Boolean = false,
    val intro: MediaSegment? = null,
    val creditsAtMs: Long? = null,
    val chapters: List<ChapterMark> = emptyList(),
    val trickplay: Trickplay? = null,
    val episodes: List<BaseItem> = emptyList(),
    val next: BaseItem? = null,
    val previous: BaseItem? = null,
    val upNextDismissed: Boolean = false,
    val sleepAt: Long? = null,
    val sleepEndOfEpisode: Boolean = false,
    val offline: Boolean = false,
    val download: DownloadState = DownloadState.Unavailable,
    val autoSkip: Boolean = false,
    val autoPlayNext: Boolean = true,
    val softwareDecoding: Boolean = false,
    val backgroundPlay: Boolean = false,
    val seekStepSec: Int = 10,
    val subScale: Float = 1f,
    val subStyle: SubStyle = SubStyle.Embedded,
    val rotation: Rotation = Rotation.Auto,
    val showStats: Boolean = false,
    val stats: PlayerStats = PlayerStats(),
    // Live TV (IPTV)
    val live: Boolean = false,
    val channels: List<com.sridhar.harbor.data.iptv.Channel> = emptyList(),
    val channelIndex: Int = 0,
    val nowNext: com.sridhar.harbor.data.iptv.NowNext? = null,
    val zapStamp: Long = 0,
    /** False until the first video frame is on screen – drives the loading overlay. */
    val firstFrame: Boolean = false,
    val loadingArt: String? = null,
    val loadStartedAt: Long = System.currentTimeMillis(),
)

@OptIn(UnstableApi::class)
class PlayerViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as HarborApp).container
    private val prefs = PlayerPrefs(app)
    var ui by mutableStateOf(PlayerUi(quality = prefs.quality)); private set

    @Volatile private var preferSoftware = prefs.softwareDecoding
    /** Title waiting for the parental PIN (null = nothing gated). */
    var pinGate by mutableStateOf<String?>(null); private set
    private var afterPin: (() -> Unit)? = null

    /** Returns true when the PIN unlocked playback. */
    fun submitPin(pin: String): Boolean {
        if (!c.parental.verify(pin)) return false
        pinGate = null; afterPin?.invoke(); afterPin = null
        return true
    }

    /** Per-title playback recovery state (see onPlayerError); reset whenever a new title loads. */
    private var recoveryStep = 0
    private var reencode = false
    private val codecSelector = MediaCodecSelector { mime, secure, tunneling ->
        val infos = MediaCodecUtil.getDecoderInfos(mime, secure, tunneling)
        if (preferSoftware) infos.sortedBy { it.hardwareAccelerated } else infos
    }

    private val httpFactory = OkHttpDataSource.Factory(c.http)

    /**
     * Platform decoders first, FFmpeg for audio formats the device can't decode (AC3/E-AC3/DTS/TrueHD).
     * Unless the user opted into passthrough, the audio sink only accepts PCM, so Dolby/DTS is decoded here rather
     * than bitstreamed over HDMI – passthrough's audio clock is what stalls video and breaks audio on budget boxes.
     */
    private fun renderersFactory(ctx: android.content.Context) = object : DefaultRenderersFactory(ctx) {
        override fun buildAudioSink(context: android.content.Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): androidx.media3.exoplayer.audio.AudioSink? {
            val b = androidx.media3.exoplayer.audio.DefaultAudioSink.Builder(context)
                .setEnableFloatOutput(enableFloatOutput).setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
            @Suppress("DEPRECATION")
            if (!prefs.passthrough) b.setAudioCapabilities(androidx.media3.exoplayer.audio.AudioCapabilities.DEFAULT_AUDIO_CAPABILITIES)
            return b.build()
        }
    }.setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON).setEnableDecoderFallback(true).setMediaCodecSelector(codecSelector)

    val player: ExoPlayer = ExoPlayer.Builder(app)
        .setRenderersFactory(renderersFactory(app))
        .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(app, httpFactory)))
        .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
        .setHandleAudioBecomingNoisy(true)
        // Start after ~1 s of buffer (default 2.5 s); cap memory on low-RAM TVs.
        .setLoadControl(androidx.media3.exoplayer.DefaultLoadControl.Builder()
            // Budget boxes on Wi-Fi: a little more in hand before starting / resuming avoids stop-start playback.
            .setBufferDurationsMs(if (c.lowEnd) 20_000 else 15_000, 50_000, if (c.lowEnd) 2_000 else 1_000, if (c.lowEnd) 4_000 else 2_500)
            .setTargetBufferBytes(if (c.lowRam) 24 shl 20 else androidx.media3.common.C.LENGTH_UNSET)
            .setPrioritizeTimeOverSizeThresholds(!c.lowRam)
            .build())
        .build()

    private var item: BaseItem? = null
    private var mediaSourceId: String? = null
    private var playSessionId = c.jellyfin.newPlaySessionId()
    private var started = false
    private var tickJob: Job? = null
    private var reportJob: Job? = null
    private var reportedStart = false
    private var skippedSegmentStart: Long? = null
    private var decoderName = "–"
    private var dropped = 0
    /** Stutter watchdog: frames dropped in the current 10 s window, and whether we already stepped down this title. */
    private var droppedAtWindow = 0
    private var windowStart = 0L
    private var smoothSwitched = false
    /** Controls on screen: the position needs 4 updates a second; otherwise once a second is plenty (less UI work). */
    var fastTick = true

    init {
        // Remembered language choices apply to every title, before the first frame.
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setPreferredAudioLanguage(prefs.audioLang).setPreferredTextLanguage(prefs.subLang)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !prefs.subsOn).build()
        ui = ui.copy(
            autoSkip = prefs.autoSkip, autoPlayNext = prefs.autoPlayNext, softwareDecoding = prefs.softwareDecoding,
            backgroundPlay = prefs.backgroundPlay, seekStepSec = prefs.seekStepSec, subScale = prefs.subScale,
            subStyle = prefs.subStyle, rotation = prefs.rotation, speed = prefs.speed,
        )
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { ui = ui.copy(isPlaying = isPlaying); report() }
            override fun onPlaybackStateChanged(state: Int) {
                ui = ui.copy(buffering = state == Player.STATE_BUFFERING)
                if (state == Player.STATE_READY) {
                    ui = ui.copy(durationMs = player.duration.coerceAtLeast(0))
                    if (!reportedStart) { reportedStart = true; viewModelScope.launch(com.sridhar.harbor.CrashGuard) { item?.let { c.jellyfin.reportStart(reportBody()) } } }
                }
                if (state == Player.STATE_ENDED) onEnded()
            }
            override fun onTracksChanged(tracks: Tracks) = onTracks(tracks)
            override fun onRenderedFirstFrame() { ui = ui.copy(firstFrame = true) }
            override fun onPlayerError(error: PlaybackException) {
                if (ui.live) {
                    // Extension-less IPTV URLs are often HLS: retry once as HLS before giving up.
                    if (!liveHlsRetry) { liveHlsRetry = true; prepareLive(forceHls = true); return }
                    ui = ui.copy(error = "Channel unavailable – ${error.errorCodeName.removePrefix("ERROR_CODE_").replace('_', ' ').lowercase()}"); return
                }
                val pos = player.currentPosition
                // Recovery ladder before giving up: direct play → server remux → software decoder → full re-encode.
                when {
                    ui.quality == Quality.Original && !ui.offline -> {
                        notice(L10n.s(R.string.direct_play_failed_switching_to_transcoding))
                        setQuality(Quality.Q20)
                    }
                    recoveryStep == 0 && !preferSoftware -> {
                        recoveryStep = 1; preferSoftware = true
                        notice(L10n.s(R.string.play_retry_software))
                        if (ui.offline) { player.prepare(); player.seekTo(pos) } else prepare(pos)
                    }
                    recoveryStep <= 1 && !reencode && !ui.offline -> {
                        recoveryStep = 2; reencode = true
                        notice(L10n.s(R.string.play_retry_reencode))
                        playSessionId = c.jellyfin.newPlaySessionId()
                        prepare(pos)
                    }
                    else -> ui = ui.copy(error = error.errorCodeName.removePrefix("ERROR_CODE_").replace('_', ' ').lowercase()
                        .replaceFirstChar { it.titlecase() })
                }
            }
        })
        player.addAnalyticsListener(object : AnalyticsListener {
            override fun onVideoDecoderInitialized(e: AnalyticsListener.EventTime, name: String, initMs: Long, durMs: Long) {
                decoderName = name
                // A weak TV that fell back to a software decoder for HD (HEVC 10-bit, 4K…) can't keep up: ask the
                // server for H.264 its hardware decodes, once, instead of stuttering through the whole film.
                val sw = name.startsWith("c2.android.") || name.startsWith("OMX.google.") || name.contains("ffmpeg", true)
                if (sw && c.lowEnd && !prefs.softwareDecoding && !preferSoftware && ui.quality == Quality.Original && !ui.offline && !ui.live
                    && (player.videoFormat?.height ?: 0) >= 700 && !smoothSwitched) {
                    smoothSwitched = true
                    viewModelScope.launch(com.sridhar.harbor.CrashGuard) { notice(L10n.s(R.string.play_smoother)); setQuality(Quality.Q8) }
                }
            }
            override fun onDroppedVideoFrames(e: AnalyticsListener.EventTime, count: Int, elapsedMs: Long) { dropped += count }
            override fun onAudioDecoderInitialized(e: AnalyticsListener.EventTime, name: String, initMs: Long, durMs: Long) {
                if (com.sridhar.harbor.BuildConfig.DEBUG) android.util.Log.d("Player", "audio decoder $name, video decoder $decoderName")
            }
        })
    }

    /** A new video was chosen while this player (e.g. in PiP) was still open. */
    fun replace(itemId: String, offlinePath: String?, offlineTitle: String?, fromStart: Boolean) {
        stopReport()
        player.stop()
        item = null
        ui = ui.copy(itemId = null, error = null, next = null, previous = null, episodes = emptyList(), chapters = emptyList(), trickplay = null, intro = null)
        started = false
        start(itemId, offlinePath, offlineTitle, fromStart)
    }

    fun start(itemId: String, offlinePath: String?, offlineTitle: String?, fromStart: Boolean) {
        if (started) return
        started = true
        if (offlinePath != null) {
            ui = ui.copy(itemId = itemId, title = offlineTitle ?: "Offline", offline = true, download = DownloadState.Done, firstFrame = false, loadStartedAt = System.currentTimeMillis())
            viewModelScope.launch(com.sridhar.harbor.CrashGuard) {
                val e = c.offline.entry(itemId)
                val pc = c.parental
                val protectedTitle = pc.state.value.enabled && (pc.isLocked(itemId) || pc.isLocked(e?.seriesId) ||
                    (pc.state.value.protectAdult && com.sridhar.harbor.data.parental.Ratings.isAdult(e?.officialRating)))
                val go = { startOffline(itemId, offlinePath, offlineTitle) }
                if (protectedTitle && !pc.isUnlocked()) { pinGate = offlineTitle ?: e?.name ?: ""; afterPin = go } else go()
            }
            return
        }
        load(itemId, fromStart)
    }

    private fun startOffline(itemId: String, offlinePath: String, offlineTitle: String?) {
        run {
            player.setMediaItem(MediaItem.Builder().setUri(Uri.fromFile(java.io.File(offlinePath)))
                .setMediaMetadata(MediaMetadata.Builder().setTitle(offlineTitle).build()).build())
            player.playbackParameters = PlaybackParameters(ui.speed)
            player.prepare(); player.play()
            startTicker()
            // Sync progress / chapters with the server if it happens to be reachable.
            viewModelScope.launch(com.sridhar.harbor.CrashGuard) {
                runCatching { c.jellyfin.item(itemId) }.onSuccess { applyItem(it, offline = true); startReporting() }
            }
        }
    }

    private fun applyItem(it: BaseItem, offline: Boolean) {
        item = it
        mediaSourceId = it.mediaSources.firstOrNull()?.id ?: it.id
        val cfg = c.config.value
        val tp = it.trickplay[mediaSourceId]?.let { widths ->
            widths.entries.minByOrNull { (w, _) -> kotlin.math.abs((w.toIntOrNull() ?: 0) - 320) }?.value
        }
        ui = ui.copy(
            itemId = it.id,
            favorite = it.userData?.isFavorite == true,
            title = it.seriesName ?: it.name,
            subtitle = if (it.type == "Episode") listOfNotNull(it.episodeLabel, it.name).joinToString(" · ") else it.year?.toString(),
            chapters = it.chapters.mapIndexed { i, ch ->
                ChapterMark(i, ch.name ?: "Chapter ${i + 1}", ch.startTicks / TICKS_PER_MS,
                    ch.imageTag?.let { tag -> cfg?.let { cc -> c.jellyfin.chapterImageUrl(cc, it.id, i, tag) } })
            },
            trickplay = if (offline) null else tp?.let { t -> Trickplay(it.id, mediaSourceId!!, t) },
        )
    }

    private fun load(itemId: String, fromStart: Boolean) = viewModelScope.launch(com.sridhar.harbor.CrashGuard) {
        // New title: forget the previous title's server-side track picks.
        autoLangTried = false
        ui = ui.copy(audioIndex = null, subIndex = null, serverForAudio = false, quality = if (ui.serverForAudio) prefs.quality else ui.quality)
        recoveryStep = 0; reencode = false; preferSoftware = prefs.softwareDecoding; smoothSwitched = false; dropped = 0; droppedAtWindow = 0
        ui = ui.copy(itemId = itemId, error = null, upNextDismissed = false, intro = null, creditsAtMs = null, next = null, previous = null, offline = false,
            firstFrame = false, loadStartedAt = System.currentTimeMillis(), buffering = true)
        reportedStart = false; skippedSegmentStart = null; dropped = 0
        playSessionId = c.jellyfin.newPlaySessionId()
        val requested = runCatching { c.jellyfin.item(itemId) }.getOrElse { e -> ui = ui.copy(error = e.friendly()); return@launch }
        // A collection / series / season can't be streamed itself – play the right movie or episode inside it.
        val it = if (!requested.isFolderish) requested else runCatching { c.jellyfin.playable(requested)?.let { p -> c.jellyfin.item(p.id) } }.getOrNull()
            ?: run { ui = ui.copy(error = L10n.s(R.string.play_nothing_inside, requested.name)); return@launch }
        applyItem(it, offline = false)
        ui = ui.copy(download = if (c.offline.isDownloaded(it.id)) DownloadState.Done else DownloadState.None)
        val resumeMs = if (fromStart) 0 else (it.userData?.positionTicks ?: 0) / TICKS_PER_MS
        val go = { prepare(resumeMs); startTicker(); startReporting() }
        // Parental control: 18+ / locked titles wait for the PIN before a single frame plays.
        if (c.parental.needsPin(it)) { pinGate = it.seriesName ?: it.name; afterPin = go } else go()

        launch {
            val segs = c.jellyfin.segments(it.id)
            ui = ui.copy(
                intro = segs.firstOrNull { s -> s.type == "Intro" || s.type == "Recap" },
                creditsAtMs = segs.firstOrNull { s -> s.type == "Outro" }?.startTicks?.div(TICKS_PER_MS),
            )
        }
        if (it.type == "Episode" && it.seriesId != null) launch {
            val eps = runCatching { c.jellyfin.episodes(it.seriesId, null) }.getOrDefault(emptyList())
            val idx = eps.indexOfFirst { e -> e.id == it.id }
            ui = ui.copy(
                episodes = eps.filter { e -> e.seasonId == it.seasonId }.ifEmpty { eps },
                next = eps.getOrNull(idx + 1)?.takeIf { idx >= 0 },
                previous = eps.getOrNull(idx - 1)?.takeIf { idx > 0 },
            )
        }
    }

    private fun prepare(startMs: Long) {
        val cfg = c.config.value ?: return
        val it = item ?: return
        val src = it.mediaSources.firstOrNull()
        val msId = mediaSourceId ?: it.id
        val meta = MediaMetadata.Builder().setTitle(ui.title).setSubtitle(ui.subtitle).setArtist(ui.subtitle)
            .setArtworkUri(Uri.parse(c.jellyfin.posterUrl(cfg, it))).build()
        val mediaItem = if (ui.quality.bitrate == null) {
            val subs = src?.streams.orEmpty().filter { s -> s.type == "Subtitle" && s.isExternal && s.isTextSubtitle }.map { s ->
                MediaItem.SubtitleConfiguration.Builder(Uri.parse(c.jellyfin.subtitleUrl(cfg, it.id, msId, s.index)))
                    .setMimeType(MimeTypes.TEXT_VTT).setLanguage(s.language).setLabel(s.displayTitle).build()
            }
            MediaItem.Builder().setMediaId(it.id).setUri(c.jellyfin.directStreamUrl(cfg, it.id, msId)).setSubtitleConfigurations(subs).setMediaMetadata(meta).build()
        } else {
            MediaItem.Builder().setMediaId(it.id)
                .setUri(c.jellyfin.hlsUrl(cfg, it.id, msId, playSessionId, ui.quality.bitrate!!, serverAudioIndex(src), serverSubIndex(src), reencode))
                .setMimeType(MimeTypes.APPLICATION_M3U8).setMediaMetadata(meta).build()
        }
        ui = ui.copy(firstFrame = false, loadStartedAt = System.currentTimeMillis(), loadingArt = c.jellyfin.backdropUrl(cfg, it, 1280))
        // A format the player can't build a source for must land on the error screen, never crash the app.
        runCatching {
            player.setMediaItem(mediaItem, startMs)
            player.playbackParameters = PlaybackParameters(ui.speed)
            player.prepare()
            player.play()
        }.onFailure { e -> android.util.Log.e("Player", "prepare failed", e); ui = ui.copy(error = e.friendly()) }
    }

    private fun streams(src: com.sridhar.harbor.data.jellyfin.MediaSource?, type: String) = src?.streams.orEmpty().filter { it.type == type }

    /** Transcoding: the audio stream to ask Jellyfin for – the user's pick, else their language, else the file's default. */
    private fun serverAudioIndex(src: com.sridhar.harbor.data.jellyfin.MediaSource?): Int? = ui.audioIndex ?: streams(src, "Audio").let { a ->
        (a.firstOrNull { langKey(it.language) == prefs.audioLang && prefs.audioLang != null } ?: a.firstOrNull { it.isDefault } ?: a.firstOrNull())?.index
    }?.also { ui = ui.copy(audioIndex = it) }

    /** Transcoding: subtitles are burned in by the server, only when the user has them on. */
    private fun serverSubIndex(src: com.sridhar.harbor.data.jellyfin.MediaSource?): Int? {
        ui.subIndex?.let { return it.takeIf { i -> i >= 0 } }
        if (!prefs.subsOn) return null
        val subs = streams(src, "Subtitle")
        return (subs.firstOrNull { langKey(it.language) == prefs.subLang } ?: subs.firstOrNull())?.index?.also { ui = ui.copy(subIndex = it) }
    }

    private var autoLangTried = false

    /** "Tamil · 5.1 · EAC3" – language first; ripper tags like "www.site.com - [AAC 2.0]" are dropped. */
    private fun trackLabel(language: String?, raw: String?, extras: List<String>, n: Int): String {
        val lang = langKey(language)?.let { java.util.Locale.forLanguageTag(it).displayLanguage.takeIf { d -> d.isNotBlank() && d.length > 3 } }
        val clean = raw?.trim()?.takeUnless { r -> r.isBlank() || Regex("""(?i)www\.|\.(com|net|org|in|immo|dev|rocks|co)\b|\[""").containsMatchIn(r) || (lang != null && r.equals(lang, true)) }
        return (listOfNotNull(lang ?: clean ?: "${L10n.s(R.string.track_1_s, n + 1)}", clean.takeIf { lang != null }) + extras).distinct().joinToString(" · ")
    }

    private fun onTracks(tracks: Tracks) {
        if (ui.quality == Quality.Original && !ui.offline && !tracks.isEmpty) {
            val audio = tracks.groups.filter { g -> g.type == C.TRACK_TYPE_AUDIO }
            val video = tracks.groups.filter { g -> g.type == C.TRACK_TYPE_VIDEO }
            if ((audio.isNotEmpty() && audio.none { g -> g.isSupported }) || (video.isNotEmpty() && video.none { g -> g.isSupported })) {
                notice(L10n.s(R.string.this_device_can_t_decode_the))
                setQuality(Quality.Q20); return
            }
        }
        // Jellyfin stream index for the n-th audio/subtitle group (container order), so any track can be requested from the server.
        val src = item?.mediaSources?.firstOrNull()
        fun serverIndexOf(type: Int, ordinal: Int) = streams(src, if (type == C.TRACK_TYPE_AUDIO) "Audio" else "Subtitle").filter { !it.isExternal }.getOrNull(ordinal)?.index
        fun options(type: Int) = tracks.groups.withIndex().filter { (_, g) -> g.type == type }.withIndex().flatMap { (ordinal, ig) ->
            val (gi, g) = ig
            (0 until g.length).mapNotNull { t ->
                val f = g.getTrackFormat(t)
                val supported = g.isTrackSupported(t)
                // Audio the device can't decode (e.g. E-AC3 5.1) is still offered – the server transcodes it.
                if (!supported && type != C.TRACK_TYPE_AUDIO) return@mapNotNull null
                val extra = if (type == C.TRACK_TYPE_AUDIO && f.channelCount > 0) when (f.channelCount) {
                    1 -> L10n.s(R.string.mono); 2 -> L10n.s(R.string.stereo); 6 -> "5.1"; 8 -> "7.1"; else -> "${f.channelCount}ch"
                } else null
                val codec = f.sampleMimeType?.substringAfter('/')?.uppercase()?.takeIf { type == C.TRACK_TYPE_AUDIO }
                val label = trackLabel(f.language, f.label, listOfNotNull(extra, codec, if (!supported) L10n.s(R.string.via_server) else null), t)
                TrackOption(label, supported && g.isTrackSelected(t), gi, t, langKey(f.language), serverIndexOf(type, ordinal), needsServer = !supported)
            }
        }
        if (ui.quality.bitrate != null) {
            // Transcoded HLS carries one audio track and burned-in subtitles: offer Jellyfin's streams instead.
            val src = item?.mediaSources?.firstOrNull()
            val a = streams(src, "Audio").map { st -> TrackOption(trackLabel(st.language, st.displayTitle, listOfNotNull(st.codec?.uppercase()), st.index), st.index == ui.audioIndex, -1, -1, langKey(st.language), st.index) }
            val t = streams(src, "Subtitle").map { st -> TrackOption(trackLabel(st.language, st.displayTitle, emptyList(), st.index), st.index == ui.subIndex, -1, -1, langKey(st.language), st.index) }
            ui = ui.copy(audio = a, text = t, textOff = ui.subIndex == null || ui.subIndex!! < 0)
            return
        }
        val text = options(C.TRACK_TYPE_TEXT)
        val audio = options(C.TRACK_TYPE_AUDIO)
        ui = ui.copy(audio = audio, text = text, textOff = text.none { it.selected })
        // Remembered language only exists as an undecodable track (e.g. Tamil DD+ 5.1)? Switch to it through the server, once.
        val want = prefs.audioLang
        if (!autoLangTried && want != null && audio.none { it.selected && it.language == want }) {
            autoLangTried = true
            audio.firstOrNull { it.language == want && it.needsServer }?.let { opt -> notice(L10n.s(R.string.via_server_notice, opt.label.substringBefore(" · "))); selectTrack(C.TRACK_TYPE_AUDIO, opt) }
        }
    }

    fun selectTrack(type: Int, opt: TrackOption?) {
        // Remember the choice so the next title starts the same way.
        if (type == C.TRACK_TYPE_AUDIO) opt?.language?.let { prefs.audioLang = it }
        if (type == C.TRACK_TYPE_TEXT) { prefs.subsOn = opt != null; opt?.language?.let { prefs.subLang = it } }
        if (opt?.needsServer == true && ui.quality.bitrate == null) {
            // Device can't decode this track: keep the picture, let Jellyfin transcode the audio.
            ui = ui.copy(quality = Quality.Q20, audioIndex = opt.streamIndex, serverForAudio = true)
            val pos = player.currentPosition
            playSessionId = c.jellyfin.newPlaySessionId()
            prepare(pos)
            return
        }
        // Back to a track the device plays natively: return to direct play (no server load, full quality).
        if (ui.serverForAudio && type == C.TRACK_TYPE_AUDIO && opt != null) {
            val codec = streams(item?.mediaSources?.firstOrNull(), "Audio").firstOrNull { it.index == opt.streamIndex }?.codec?.lowercase()
            if (codec in setOf("aac", "mp3", "opus", "vorbis", "flac")) {
                ui = ui.copy(quality = Quality.Original, audioIndex = null, serverForAudio = false)
                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setPreferredAudioLanguage(opt.language).build()
                val pos = player.currentPosition
                prepare(pos)
                return
            }
        }
        if (ui.quality.bitrate != null) {
            ui = if (type == C.TRACK_TYPE_AUDIO) ui.copy(audioIndex = opt?.streamIndex ?: ui.audioIndex) else ui.copy(subIndex = opt?.streamIndex ?: -1)
            val pos = player.currentPosition
            playSessionId = c.jellyfin.newPlaySessionId()
            prepare(pos)
            return
        }
        val params = player.trackSelectionParameters.buildUpon()
            .setPreferredAudioLanguage(prefs.audioLang).setPreferredTextLanguage(prefs.subLang)
        if (opt == null) params.setTrackTypeDisabled(type, true)
        else {
            val group = player.currentTracks.groups[opt.groupIndex].mediaTrackGroup
            params.setTrackTypeDisabled(type, false).setOverrideForType(TrackSelectionOverride(group, opt.trackIndex))
        }
        player.trackSelectionParameters = params.build()
    }

    /** One-tap CC: off ↔ the remembered (or first available) subtitle language. Returns false when the title has none. */
    fun toggleSubtitles(): Boolean {
        if (!ui.textOff) { selectTrack(C.TRACK_TYPE_TEXT, null); return true }
        val pick = ui.text.firstOrNull { it.language != null && it.language == prefs.subLang } ?: ui.text.firstOrNull() ?: return false
        selectTrack(C.TRACK_TYPE_TEXT, pick)
        return true
    }

    var subSearch by mutableStateOf<SubSearchUi?>(null); private set

    fun openSubSearch() {
        val lang = prefs.subLang ?: langKey(java.util.Locale.getDefault().language) ?: "eng"
        subSearch = SubSearchUi(lang)
        viewModelScope.launch(com.sridhar.harbor.CrashGuard) { subSearch = subSearch?.copy(isAdmin = c.jellyfin.isAdmin()) }
        searchSubtitles(lang)
    }

    fun closeSubSearch() { subSearch = null }

    fun searchSubtitles(language: String) = viewModelScope.launch(com.sridhar.harbor.CrashGuard) {
        val id = item?.id ?: return@launch
        subSearch = subSearch?.copy(language = language, loading = true, results = null, error = null)
        runCatching { c.jellyfin.searchSubtitles(id, language) }
            .onSuccess { r -> subSearch = subSearch?.copy(loading = false, results = r) }
            .onFailure { e -> subSearch = subSearch?.copy(loading = false, error = e.friendly()) }
    }

    /** Server downloads + stores the file next to the media; we reload the item and switch the new track on. */
    fun downloadSubtitle(r: com.sridhar.harbor.data.jellyfin.RemoteSubtitle) = viewModelScope.launch(com.sridhar.harbor.CrashGuard) {
        val id = item?.id ?: return@launch
        val lang = langKey(r.language) ?: subSearch?.language
        subSearch = subSearch?.copy(downloadingId = r.id, error = null)
        runCatching {
            c.jellyfin.downloadSubtitle(id, r.id)
            item = c.jellyfin.item(id)
        }.onSuccess {
            prefs.subsOn = true; lang?.let { prefs.subLang = it }
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).setPreferredTextLanguage(lang).build()
            if (ui.quality.bitrate != null) ui = ui.copy(subIndex = item?.mediaSources?.firstOrNull()?.streams.orEmpty()
                .filter { it.type == "Subtitle" && (lang == null || langKey(it.language) == lang) }.maxOfOrNull { it.index })
            subSearch = null
            notice(L10n.s(R.string.subs_added))
            val pos = player.currentPosition
            playSessionId = c.jellyfin.newPlaySessionId()
            prepare(pos)
        }.onFailure { e -> subSearch = subSearch?.copy(downloadingId = null, error = e.friendly()) }
    }

    fun installSubtitleProvider() = viewModelScope.launch(com.sridhar.harbor.CrashGuard) {
        runCatching { c.admin.installPlugin("Open Subtitles") }
            .onSuccess { subSearch = subSearch?.copy(installed = true) }
            .onFailure { e -> subSearch = subSearch?.copy(error = e.friendly()) }
    }

    /** ♥ from the player: optimistic toggle, reverted if the server refuses. */
    fun toggleFavorite() {
        val id = ui.itemId ?: return
        val now = !ui.favorite
        ui = ui.copy(favorite = now)
        notice(L10n.s(if (now) R.string.fav_added else R.string.fav_removed))
        viewModelScope.launch(com.sridhar.harbor.CrashGuard) {
            runCatching { c.jellyfin.setFavorite(id, now) }.onFailure { ui = ui.copy(favorite = !now); notice(it.friendly()) }
        }
    }

    /** User pick from the Quality panel: applied now and remembered for the next video. */
    fun chooseQuality(q: Quality) { prefs.quality = q; recoveryStep = 0; reencode = false; smoothSwitched = true /* the user decides now */; setQuality(q) }

    fun setQuality(q: Quality) {
        if (ui.offline) return
        val pos = player.currentPosition
        ui = ui.copy(quality = q, error = null)
        playSessionId = c.jellyfin.newPlaySessionId()
        prepare(pos)
    }

    // ---------------- preferences ----------------
    fun setSpeed(s: Float) { ui = ui.copy(speed = s); prefs.speed = s; player.playbackParameters = PlaybackParameters(s) }
    fun setFit(f: Fit) { ui = ui.copy(fit = f) }
    fun cycleFit() = setFit(Fit.entries[(ui.fit.ordinal + 1) % Fit.entries.size])
    fun setAutoSkip(v: Boolean) { prefs.autoSkip = v; ui = ui.copy(autoSkip = v) }
    fun setAutoPlayNext(v: Boolean) { prefs.autoPlayNext = v; ui = ui.copy(autoPlayNext = v) }
    fun setBackgroundPlay(v: Boolean) { prefs.backgroundPlay = v; ui = ui.copy(backgroundPlay = v) }
    fun setSeekStep(s: Int) { prefs.seekStepSec = s; ui = ui.copy(seekStepSec = s) }
    fun setSubScale(s: Float) { prefs.subScale = s; ui = ui.copy(subScale = s) }
    fun setSubStyle(s: SubStyle) { prefs.subStyle = s; ui = ui.copy(subStyle = s) }
    fun setRotation(r: Rotation) { prefs.rotation = r; ui = ui.copy(rotation = r) }
    fun toggleStats() { ui = ui.copy(showStats = !ui.showStats) }

    fun setSoftwareDecoding(v: Boolean) {
        prefs.softwareDecoding = v; preferSoftware = v
        ui = ui.copy(softwareDecoding = v)
        // Codecs are chosen at prepare time – re-prepare in place.
        val pos = player.currentPosition
        if (ui.offline) { player.prepare(); player.seekTo(pos) } else prepare(pos)
        notice(if (v) L10n.s(R.string.software_decoding) else L10n.s(R.string.hardware_decoding))
    }

    fun setSleep(minutes: Int?, endOfEpisode: Boolean = false) {
        ui = ui.copy(sleepAt = minutes?.let { System.currentTimeMillis() + it * 60_000L }, sleepEndOfEpisode = endOfEpisode)
        notice(when { endOfEpisode -> L10n.s(R.string.sleeping_after_this_episode); minutes != null -> L10n.s(R.string.sleep_in_1_s_min, minutes); else -> L10n.s(R.string.sleep_timer_off) })
    }

    // ---------------- live TV ----------------
    private var liveHlsRetry = false

    fun startLive(playlistId: String, channelId: String) {
        if (started) return
        started = true
        viewModelScope.launch(com.sridhar.harbor.CrashGuard) {
            val playlist = c.iptv.playlists.value.firstOrNull { it.id == playlistId } ?: return@launch
            livePlaylistId = playlistId
            val all = runCatching { c.iptv.channels(playlist) }.getOrElse { e -> ui = ui.copy(error = e.friendly()); return@launch }
            val start = all.indexOfFirst { it.id == channelId }.coerceAtLeast(0)
            // Zapping stays within the channel's group, like a set-top box.
            val group = all[start].group
            val list = all.filter { it.group == group }
            ui = ui.copy(live = true, channels = list, channelIndex = list.indexOfFirst { it.id == channelId }.coerceAtLeast(0), download = DownloadState.Unavailable)
            prepareLive()
            startTicker()
            launch { runCatching { c.iptv.loadEpg(playlist) }; refreshNowNext() }
        }
    }

    private var livePlaylistId: String? = null
    var liveRefreshing by mutableStateOf(false); private set

    /**
     * The channel won't play: pull the latest list from the playlist source (public lists rotate URLs),
     * find this channel again and retune. Users fix dead streams themselves without leaving the player.
     */
    fun refreshLiveSource() = viewModelScope.launch(com.sridhar.harbor.CrashGuard) {
        val playlist = c.iptv.playlists.value.firstOrNull { it.id == livePlaylistId } ?: return@launch
        val current = ui.channels.getOrNull(ui.channelIndex) ?: return@launch
        liveRefreshing = true
        val fresh = runCatching { c.iptv.channels(playlist, refresh = true) }.getOrElse { e -> liveRefreshing = false; notice(e.friendly()); return@launch }
        liveRefreshing = false
        val found = c.iptv.relocate(current, fresh)
        if (found == null) { notice(L10n.s(R.string.iptv_channel_gone)); return@launch }
        val list = fresh.filter { it.group == found.group }
        ui = ui.copy(channels = list, channelIndex = list.indexOfFirst { it.id == found.id }.coerceAtLeast(0))
        notice(if (found.url != current.url) L10n.s(R.string.iptv_new_link) else L10n.s(R.string.iptv_same_link))
        liveHlsRetry = false
        prepareLive()
    }

    private fun refreshNowNext() { ui.channels.getOrNull(ui.channelIndex)?.let { ui = ui.copy(nowNext = c.iptv.nowNext(it)) } }

    private fun prepareLive(forceHls: Boolean = false) {
        val ch = ui.channels.getOrNull(ui.channelIndex) ?: return
        c.iptv.lastChannel = ch.id
        httpFactory.setUserAgent(ch.userAgent ?: "JellyVerse/2.1 (Android) ExoPlayer")
        httpFactory.setDefaultRequestProperties(buildMap { ch.referrer?.let { put("Referer", it) } })
        val builder = MediaItem.Builder().setUri(ch.url).setMediaMetadata(MediaMetadata.Builder().setTitle(ch.name).setArtworkUri(ch.logo?.let(Uri::parse)).build())
        if (forceHls || ch.url.contains(".m3u8", true) || ch.url.contains("type=m3u8", true)) builder.setMimeType(MimeTypes.APPLICATION_M3U8)
        ui = ui.copy(title = ch.name, subtitle = ch.group, error = null, zapStamp = System.nanoTime(),
            firstFrame = false, loadStartedAt = System.currentTimeMillis(), loadingArt = ch.logo)
        refreshNowNext()
        // Full reset between channels: different resolutions must not reuse the old frame/surface size.
        player.stop(); player.clearMediaItems()
        runCatching { player.setMediaItem(builder.build()); player.prepare(); player.play() }
            .onFailure { e -> android.util.Log.e("Player", "live prepare failed", e); ui = ui.copy(error = e.friendly()) }
    }

    fun zap(delta: Int) {
        if (!ui.live || ui.channels.isEmpty()) return
        ui = ui.copy(channelIndex = (ui.channelIndex + delta).mod(ui.channels.size))
        liveHlsRetry = false; prepareLive()
    }

    fun tuneTo(index: Int) { if (ui.live) { ui = ui.copy(channelIndex = index); liveHlsRetry = false; prepareLive() } }

    fun toggleFavoriteChannel() { ui.channels.getOrNull(ui.channelIndex)?.let { c.iptv.toggleFavorite(it) } }

    // ---------------- cast ----------------
    var casting by mutableStateOf<String?>(null); private set

    fun castTo(deviceHint: String) {
        val it = item ?: return notice(L10n.s(R.string.wait_for_the_video_to_load))
        viewModelScope.launch(com.sridhar.harbor.CrashGuard) {
            runCatching {
                val device = c.cast.connect(deviceHint)
                val cfg = c.config.value!!
                c.cast.load(it.id, c.jellyfin.castUrl(it.id), ui.title, ui.subtitle, c.jellyfin.posterUrl(cfg, it), player.currentPosition)
                device
            }.onSuccess { d -> player.pause(); casting = d; notice(L10n.s(R.string.casting_to_1_s, d)) }.onFailure { e -> notice(e.message ?: "Cast failed") }
        }
    }

    /** Stop casting and continue on the phone where the TV left off. */
    fun stopCasting() {
        val pos = c.cast.position()
        c.cast.disconnect(); casting = null
        if (pos > 0) player.seekTo(pos)
        player.play()
    }

    // ---------------- download ----------------
    fun download() {
        val it = item ?: return
        if (ui.download != DownloadState.None) return
        ui = ui.copy(download = DownloadState.Queued)
        viewModelScope.launch(com.sridhar.harbor.CrashGuard) {
            runCatching { c.offline.download(it, c.http) }
                .onSuccess { notice(L10n.s(R.string.downloading_for_offline_see_you_offline)) }
                .onFailure { e -> ui = ui.copy(download = DownloadState.None); notice(e.friendly()) }
        }
    }

    // ---------------- transport ----------------
    fun togglePlay() { if (player.isPlaying) player.pause() else { if (player.playbackState == Player.STATE_ENDED) player.seekTo(0); player.play() } }
    fun seekStep(forward: Boolean) = seekBy((if (forward) 1 else -1) * ui.seekStepSec * 1000L)
    fun seekBy(ms: Long) = seekTo(player.currentPosition + ms)
    fun seekTo(ms: Long) { player.seekTo(ms.coerceIn(0, player.duration.takeIf { it > 0 } ?: Long.MAX_VALUE)); tick() }
    fun skipIntro() { ui.intro?.let { seekTo(it.endTicks / TICKS_PER_MS) } }
    fun dismissUpNext() { ui = ui.copy(upNextDismissed = true) }

    fun currentChapter(): ChapterMark? = ui.chapters.lastOrNull { it.startMs <= ui.positionMs + 500 }
    fun nextChapter() { ui.chapters.firstOrNull { it.startMs > ui.positionMs + 1000 }?.let { seekTo(it.startMs) } }
    fun previousChapter() {
        val cur = currentChapter() ?: return seekTo(0)
        // Within 3 s of a chapter start → jump to the one before, like a CD player.
        val target = if (ui.positionMs - cur.startMs > 3000) cur else ui.chapters.getOrNull(cur.index - 1) ?: cur
        seekTo(target.startMs)
    }

    fun playNext() = playEpisode(ui.next)
    fun playPrevious() = playEpisode(ui.previous)
    fun playEpisode(ep: BaseItem?) {
        ep ?: return
        stopReport()
        player.stop()
        load(ep.id, fromStart = false)
    }

    private fun onEnded() {
        if (ui.sleepEndOfEpisode) { ui = ui.copy(sleepEndOfEpisode = false); stopReport(); return }
        if (ui.next != null && !ui.upNextDismissed && ui.autoPlayNext) playNext() else stopReport()
    }

    fun notice(msg: String?) {
        ui = ui.copy(notice = msg)
        if (msg != null) viewModelScope.launch(com.sridhar.harbor.CrashGuard) { delay(3000); if (ui.notice == msg) ui = ui.copy(notice = null) }
    }

    private fun tick() {
        val pos = player.currentPosition.coerceAtLeast(0)
        ui = ui.copy(positionMs = pos, durationMs = player.duration.takeIf { it > 0 } ?: ui.durationMs, bufferedMs = player.bufferedPosition)
        ui.sleepAt?.let { if (System.currentTimeMillis() >= it) { player.pause(); ui = ui.copy(sleepAt = null); notice(L10n.s(R.string.sleep_timer_paused)) } }
        // Auto-skip intro/recap once per segment.
        ui.intro?.let { seg ->
            val s = seg.startTicks / TICKS_PER_MS; val e = seg.endTicks / TICKS_PER_MS
            if (ui.autoSkip && pos in s until e - 1000 && skippedSegmentStart != s) {
                skippedSegmentStart = s; player.seekTo(e); notice(L10n.s(R.string.skipped_1_s, seg.type.lowercase()))
            }
        }
        if (ui.showStats) ui = ui.copy(stats = stats())
        stutterWatch()
    }

    /** Direct play dropping ≥ 60 frames in 10 s (≈ 2.5 s of a 24 fps film) → one step down to a server stream. */
    private fun stutterWatch() {
        val now = android.os.SystemClock.elapsedRealtime()
        if (!player.isPlaying || ui.buffering) { windowStart = now; droppedAtWindow = dropped; return }
        if (now - windowStart < 10_000) return
        val lost = dropped - droppedAtWindow
        windowStart = now; droppedAtWindow = dropped
        if (lost >= 60 && !smoothSwitched && ui.quality == Quality.Original && !ui.offline && !ui.live) {
            smoothSwitched = true
            notice(L10n.s(R.string.play_smoother)); setQuality(Quality.Q8)
        }
    }

    private fun describe(f: Format?, video: Boolean): String = f?.let {
        val codec = it.codecs ?: it.sampleMimeType?.substringAfter('/') ?: "?"
        if (video) "${it.width}×${it.height} · $codec" + (if (it.frameRate > 0) " · %.0f fps".format(it.frameRate) else "")
        else "$codec · ${it.channelCount}ch · ${it.sampleRate / 1000} kHz" + (it.language?.let { l -> " · $l" } ?: "")
    } ?: "–"

    private fun stats() = PlayerStats(
        video = describe(player.videoFormat, true),
        audio = describe(player.audioFormat, false),
        decoder = decoderName + if (preferSoftware) " (SW pref)" else "",
        bitrate = (player.videoFormat?.bitrate?.takeIf { it > 0 } ?: item?.mediaSources?.firstOrNull()?.bitrate?.toInt())
            ?.let { "%.1f Mbps".format(it / 1_000_000f) } ?: "–",
        dropped = dropped,
        bufferSec = ((player.bufferedPosition - player.currentPosition) / 1000f).coerceAtLeast(0f),
        method = when { ui.offline -> L10n.s(R.string.offline_file); ui.quality.bitrate == null -> L10n.s(R.string.direct_play); else -> L10n.s(R.string.transcode_1_s, ui.quality.label) },
    )

    private fun startTicker() {
        tickJob?.cancel()
        tickJob = viewModelScope.launch(com.sridhar.harbor.CrashGuard) { while (isActive) { tick(); delay(if (fastTick) 250 else 1000) } }
    }

    private fun startReporting() {
        reportJob?.cancel()
        reportJob = viewModelScope.launch(com.sridhar.harbor.CrashGuard) { while (isActive) { delay(10_000); if (player.isPlaying) report() } }
    }

    private fun reportBody() = PlaybackReport(
        itemId = item!!.id, mediaSourceId = mediaSourceId, playSessionId = playSessionId,
        positionTicks = player.currentPosition * TICKS_PER_MS, isPaused = !player.isPlaying,
        playMethod = if (ui.quality.bitrate == null) "DirectPlay" else "Transcode",
    )

    private fun report() {
        if (item == null || !reportedStart) return
        val body = reportBody()
        viewModelScope.launch(com.sridhar.harbor.CrashGuard) { c.jellyfin.reportProgress(body) }
    }

    private fun stopReport() {
        // Nothing to report if playback never started (e.g. closed at the parental PIN or on a load error).
        if (item == null || ui.live || !reportedStart) return
        val body = reportBody()
        c.scope.launch(com.sridhar.harbor.CrashGuard) { c.jellyfin.reportStop(body) }
    }

    override fun onCleared() {
        stopReport()
        player.release()
    }
}
