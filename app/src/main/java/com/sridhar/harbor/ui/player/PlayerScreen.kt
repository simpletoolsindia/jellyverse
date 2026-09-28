package com.sridhar.harbor.ui.player

import androidx.compose.material.icons.automirrored.rounded.Toc
import com.sridhar.harbor.L10n
import com.sridhar.harbor.R
import androidx.compose.ui.res.stringResource
import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Typeface
import android.media.AudioManager
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.CastConnected
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Downloading
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.focusable
import android.view.KeyEvent
import androidx.compose.ui.composed
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.sridhar.harbor.data.jellyfin.BaseItem
import com.sridhar.harbor.ui.components.LocalContainer
import com.sridhar.harbor.ui.components.NetImage
import com.sridhar.harbor.ui.components.ProgressStrip
import com.sridhar.harbor.ui.components.formatClock
import com.sridhar.harbor.ui.components.rememberConfig
import com.sridhar.harbor.ui.theme.Harbor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

private enum class Panel(@androidx.annotation.StringRes val titleRes: Int) { Audio(R.string.audio_2), Subtitles(R.string.subtitles_2), Settings(R.string.playback), Quality(R.string.quality), Chapters(R.string.chapters), Episodes(R.string.episodes), Channels(R.string.channels);
    val title: String get() = com.sridhar.harbor.L10n.s(titleRes)
}
private enum class SideGesture { Brightness, Volume }

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(vm: PlayerViewModel, inPip: Boolean, onBack: () -> Unit, onPip: () -> Unit) {
    val ui = vm.ui
    val ctx = LocalContext.current
    val activity = ctx as Activity
    val audio = remember { ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    var controls by remember { mutableStateOf(true) }
    var locked by remember { mutableStateOf(false) }
    var panel by remember { mutableStateOf<Panel?>(null) }
    var interaction by remember { mutableIntStateOf(0) }
    var scrubMs by remember { mutableStateOf<Long?>(null) }
    var side by remember { mutableStateOf<SideGesture?>(null) }
    var sideLevel by remember { mutableFloatStateOf(0f) }
    var tapSeek by remember { mutableStateOf<Pair<Boolean, Int>?>(null) }
    var tapSeekStamp by remember { mutableLongStateOf(0L) }
    val holdScope = androidx.compose.runtime.rememberCoroutineScope()
    var showRemaining by remember { mutableStateOf(false) }
    var showCast by remember { mutableStateOf(false) }
    val isTv = remember { ctx.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK) }
    val rootFocus = remember { FocusRequester() }
    val playFocus = remember { FocusRequester() }
    val shownForFocus = controls && !locked && (ui.firstFrame || ui.error != null)
    LaunchedEffect(shownForFocus, panel, ui.zapStamp) {
        delay(60)
        runCatching { if (shownForFocus && panel == null && isTv) playFocus.requestFocus() else if (panel == null) rootFocus.requestFocus() }
    }
    // Back hides controls only when they're really on screen – while loading it must leave the player at once.
    androidx.activity.compose.BackHandler(enabled = panel != null || (controls && isTv && !locked && (ui.firstFrame || ui.error != null))) {
        if (panel != null) panel = null else controls = false
    }

    LaunchedEffect(controls, interaction, ui.isPlaying, panel) {
        if (controls && ui.isPlaying && panel == null) { delay(4000); controls = false }
    }
    LaunchedEffect(tapSeekStamp) { if (tapSeek != null) { delay(700); tapSeek = null } }
    LaunchedEffect(ui.rotation) {
        if (isTv) return@LaunchedEffect   // TVs are always landscape; never touch their orientation
        activity.requestedOrientation = when (ui.rotation) {
            Rotation.Landscape -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            Rotation.Portrait -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            // Follows the phone: turns with the device, and honours the system rotation lock.
            Rotation.Auto -> ActivityInfo.SCREEN_ORIENTATION_FULL_USER
        }
    }

    // Controls are only really on screen once the first frame (or an error) is shown – before that, D-pad keys
    // must drive playback (e.g. zap channels while a stream is still loading) instead of invisible buttons.
    val controlsShown = controls && !locked && (ui.firstFrame || ui.error != null)
    val onRemoteKey: (KeyEvent) -> Boolean = remote@{ ne ->
            if (ne.action != KeyEvent.ACTION_DOWN) return@remote false
            interaction++
            val code = ne.keyCode
            when (code) {
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE -> { vm.togglePlay(); controls = true; return@remote true }
                KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { vm.seekBy(30_000); return@remote true }
                KeyEvent.KEYCODE_MEDIA_REWIND -> { vm.seekBy(-30_000); return@remote true }
                KeyEvent.KEYCODE_MEDIA_NEXT -> { if (ui.live) vm.zap(1) else vm.playNext(); return@remote true }
                KeyEvent.KEYCODE_CHANNEL_UP -> { vm.zap(1); return@remote true }
                KeyEvent.KEYCODE_CHANNEL_DOWN -> { vm.zap(-1); return@remote true }
                KeyEvent.KEYCODE_MEDIA_PREVIOUS -> { vm.playPrevious(); return@remote true }
                KeyEvent.KEYCODE_CAPTIONS -> { if (!vm.toggleSubtitles()) { panel = Panel.Subtitles; vm.openSubSearch() }; controls = true; return@remote true }
            }
            // With controls hidden the remote drives playback directly.
            if (controlsShown || panel != null || locked) return@remote false
            if (ui.live) return@remote when (code) {
                KeyEvent.KEYCODE_DPAD_UP -> { vm.zap(1); true }
                KeyEvent.KEYCODE_DPAD_DOWN -> { vm.zap(-1); true }
                KeyEvent.KEYCODE_DPAD_LEFT -> { panel = Panel.Channels; true }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MENU -> { controls = true; true }
                else -> false
            }
            when (code) {
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_SPACE -> {
                    // Smart OK: acts on whatever card is on screen, like a streaming app.
                    val intro = ui.intro
                    val inIntroNow = !ui.autoSkip && intro != null && ui.positionMs in (intro.startTicks / 10_000) until (intro.endTicks / 10_000 - 1000)
                    val upNextNow = ui.next != null && !ui.upNextDismissed && ui.durationMs > 300_000 && ui.positionMs >= (ui.creditsAtMs ?: (ui.durationMs - 25_000))
                    when {
                        inIntroNow -> vm.skipIntro()
                        upNextNow -> vm.playNext()
                        else -> { vm.togglePlay(); controls = true }
                    }
                    true
                }
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    val forward = code == KeyEvent.KEYCODE_DPAD_RIGHT
                    // Hold to accelerate: 1× step, then 3×, then 6×.
                    val mult = when { ne.repeatCount > 20 -> 6; ne.repeatCount > 6 -> 3; else -> 1 }
                    val secs = ui.seekStepSec * mult
                    vm.seekBy((if (forward) 1 else -1) * secs * 1000L)
                    tapSeek = tapSeek?.takeIf { it.first == forward }?.let { forward to it.second + secs } ?: (forward to secs)
                    tapSeekStamp = System.nanoTime(); true
                }
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_MENU -> { controls = true; true }
                else -> false
            }
    }
    // Keys Compose didn't take (focus lost, e.g. surface rebuilt after a zap) still reach the player.
    val latestKey by androidx.compose.runtime.rememberUpdatedState(onRemoteKey)
    val latestShown by androidx.compose.runtime.rememberUpdatedState(controlsShown)
    androidx.compose.runtime.DisposableEffect(activity) {
        val host = activity as? PlayerActivity
        host?.remoteKeys = { ne ->
            latestKey(ne) || run {
                // Only when focus was truly lost (no focused view at all): put it back so D-pad navigation resumes.
                // At a screen edge focus still exists – leave those keys alone.
                val dpad = ne.keyCode in KeyEvent.KEYCODE_DPAD_UP..KeyEvent.KEYCODE_DPAD_CENTER
                val lost = host?.currentFocus == null
                if (dpad && lost && ne.action == KeyEvent.ACTION_DOWN) runCatching { if (latestShown) playFocus.requestFocus() else rootFocus.requestFocus() }
                dpad && lost
            }
        }
        onDispose { host?.remoteKeys = null }
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .focusRequester(rootFocus).focusable()
            .onPreviewKeyEvent { e -> onRemoteKey(e.nativeKeyEvent) },
    ) {
        // Live TV: a fresh surface per channel so a new resolution never renders over the previous picture.
        androidx.compose.runtime.key(if (ui.live) ui.zapStamp else 0L) {
        AndroidView(
                factory = {
                    PlayerView(it).apply {
                        useController = false
                        player = vm.player
                        keepScreenOn = true
                        setShutterBackgroundColor(android.graphics.Color.BLACK)
                        // Never let the video View take focus – remote keys must stay with the Compose controls.
                        isFocusable = false; isFocusableInTouchMode = false
                        descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                        layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    }
                },
                onRelease = { pv -> pv.player = null },
                update = { pv ->
                    pv.resizeMode = ui.fit.mode
                    pv.subtitleView?.let { sv -> styleSubtitles(sv, ui.subStyle, ui.subScale) }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (inPip) return@Box

        // ---------- Gestures ----------
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val widthPx = constraints.maxWidth.toFloat()
            val heightPx = constraints.maxHeight.toFloat()
            Box(
                Modifier.fillMaxSize()
                    .pointerInput(locked) {
                        if (locked) return@pointerInput
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            var zoom = 1f
                            do {
                                val ev = awaitPointerEvent()
                                if (ev.changes.size >= 2) { zoom *= ev.calculateZoom(); ev.changes.forEach { c -> c.consume() } }
                            } while (ev.changes.any { c -> c.pressed })
                            if (zoom > 1.12f) vm.setFit(Fit.Zoom) else if (zoom < 0.9f) vm.setFit(Fit.Fit)
                        }
                    }
                    .pointerInput(locked, ui.seekStepSec, ui.live) {
                        detectTapGestures(
                            // Hold the left / right half to rewind / fast-forward, speeding up the longer you hold.
                            onPress = { o ->
                                if (locked || ui.live) return@detectTapGestures
                                val forward = o.x > widthPx / 2
                                val hold = holdScope.launch {
                                    delay(viewConfiguration.longPressTimeoutMillis)
                                    var ticks = 0
                                    while (true) {
                                        val stepSec = when { ticks > 16 -> 30; ticks > 6 -> 15; else -> 5 }
                                        vm.seekBy((if (forward) 1 else -1) * stepSec * 1000L)
                                        tapSeek = tapSeek?.takeIf { it.first == forward }?.let { forward to it.second + stepSec } ?: (forward to stepSec)
                                        tapSeekStamp = System.nanoTime()
                                        ticks++; delay(250)
                                    }
                                }
                                tryAwaitRelease()
                                hold.cancel()
                            },
                            onLongPress = { /* handled by the hold job in onPress – keeps a hold from also toggling controls */ },
                            onTap = { controls = !controls; panel = null; interaction++ },
                            onDoubleTap = { o ->
                                if (locked) return@detectTapGestures
                                val forward = o.x > widthPx / 2
                                vm.seekStep(forward)
                                tapSeek = tapSeek?.takeIf { it.first == forward }?.let { forward to it.second + ui.seekStepSec } ?: (forward to ui.seekStepSec)
                                tapSeekStamp = System.nanoTime()
                            },
                        )
                    }
                    .pointerInput(locked) {
                        if (locked) return@pointerInput
                        detectVerticalDragGestures(
                            onDragStart = { o ->
                                side = if (o.x < widthPx / 2) SideGesture.Brightness else SideGesture.Volume
                                sideLevel = if (side == SideGesture.Brightness) activity.window.attributes.screenBrightness.takeIf { it >= 0 } ?: 0.5f
                                else audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                            },
                            onDragEnd = { side = null }, onDragCancel = { side = null },
                            onVerticalDrag = { ch, dy ->
                                ch.consume()
                                sideLevel = (sideLevel - dy / (heightPx * 0.8f)).coerceIn(0f, 1f)
                                if (side == SideGesture.Brightness) activity.window.attributes = activity.window.attributes.apply { screenBrightness = sideLevel.coerceAtLeast(0.01f) }
                                else audio.setStreamVolume(AudioManager.STREAM_MUSIC, (sideLevel * audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)).roundToInt(), 0)
                            },
                        )
                    }
                    .pointerInput(locked, ui.durationMs) {
                        if (locked) return@pointerInput
                        var start = 0L
                        detectHorizontalDragGestures(
                            onDragStart = { start = vm.player.currentPosition; scrubMs = start },
                            onDragEnd = { scrubMs?.let(vm::seekTo); scrubMs = null },
                            onDragCancel = { scrubMs = null },
                            onHorizontalDrag = { ch, dx ->
                                ch.consume()
                                val span = (ui.durationMs / 20).coerceIn(90_000, 600_000)
                                scrubMs = ((scrubMs ?: start) + (dx / widthPx * span).toLong()).coerceIn(0, ui.durationMs)
                            },
                        )
                    },
            )
        }

        // ---------- Transient feedback ----------
        tapSeek?.let { (forward, secs) -> SeekRipple(forward, secs) }
        side?.let { s -> SideIndicator(s, sideLevel, Modifier.align(if (s == SideGesture.Brightness) Alignment.CenterStart else Alignment.CenterEnd)) }
        if (!controls) scrubMs?.let { ms ->
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                ui.trickplay?.let { TrickplayThumb(it, ms, 240.dp) }
                Column(Modifier.padding(top = 8.dp).clip(RoundedCornerShape(16.dp)).background(Color.Black.copy(alpha = .6f)).padding(16.dp, 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(formatClock(ms), style = MaterialTheme.typography.headlineSmall, color = Color.White)
                    val delta = ms - vm.player.currentPosition
                    Text((if (delta >= 0) "+" else "") + formatClock(delta), color = Harbor.VioletSoft)
                }
            }
        }
        if (!ui.firstFrame && ui.error == null && !inPip) LoadingOverlay(ui)
        else if (ui.buffering && scrubMs == null) SlimLoadingBar(Modifier.align(Alignment.TopCenter))
        if (ui.live) {
            var showZap by remember { mutableStateOf(false) }
            LaunchedEffect(ui.zapStamp) { showZap = true; delay(3500); showZap = false }
            AnimatedVisibility(showZap && !controls, Modifier.align(Alignment.BottomStart).safeDrawingPadding().padding(24.dp),
                enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
                Box(Modifier.width(460.dp).clip(RoundedCornerShape(20.dp)).background(Harbor.Ink.copy(.92f)).padding(16.dp)) { LiveInfo(ui) }
            }
        }
        if (ui.showStats) StatsOverlay(ui.stats, Modifier.align(Alignment.TopStart).safeDrawingPadding().padding(start = 16.dp, top = 64.dp))

        // ---------- Controls ----------
        AnimatedVisibility(controls && !locked && (ui.firstFrame || ui.error != null), enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
                0f to Color.Black.copy(alpha = .7f), 0.25f to Color.Transparent, 0.6f to Color.Transparent, 1f to Color.Black.copy(alpha = .88f)))
            ) {
                Row(Modifier.fillMaxWidth().safeDrawingPadding().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back), tint = Color.White) }
                    Column(Modifier.weight(1f).padding(start = 4.dp)) {
                        Text(ui.title, color = Color.White, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        ui.subtitle?.let { Text(it, color = Harbor.TextDim, style = MaterialTheme.typography.bodySmall, maxLines = 1) }
                    }
                    if (ui.live) {
                        QualityTag(stringResource(R.string.live_2))
                        TopIcon(Icons.AutoMirrored.Rounded.List, stringResource(R.string.channels)) { panel = Panel.Channels; interaction++ }
                        val fav = LocalContainer.current.iptv.favorites.collectAsState().value
                        val isFav = ui.channels.getOrNull(ui.channelIndex)?.id in fav
                        TopIcon(if (isFav) Icons.Rounded.Star else Icons.Rounded.StarBorder, stringResource(R.string.favourite), tint = if (isFav) Harbor.Amber else Color.White) { vm.toggleFavoriteChannel() }
                    }
                    if (ui.quality.bitrate != null && !ui.live) QualityTag(stringResource(R.string.transcode))
                    if (ui.offline) QualityTag(stringResource(R.string.offline_2))
                    if (!isTv) DownloadButton(ui.download) { vm.download() }
                    if (!ui.offline && !isTv && !ui.live) TopIcon(if (vm.casting != null) Icons.Rounded.CastConnected else Icons.Rounded.Cast, stringResource(R.string.cast),
                        tint = if (vm.casting != null) Harbor.Sky else Color.White) { if (vm.casting != null) vm.stopCasting() else showCast = true }
                    if (ui.episodes.size > 1) TopIcon(Icons.Rounded.VideoLibrary, stringResource(R.string.episodes)) { panel = Panel.Episodes; interaction++ }
                    if (ui.chapters.isNotEmpty()) TopIcon(Icons.AutoMirrored.Rounded.Toc, stringResource(R.string.chapters)) { panel = Panel.Chapters; interaction++ }
                    if (!ui.live && !ui.offline) FavoriteIcon(ui.favorite) { vm.toggleFavorite(); interaction++ }
                    TopIcon(Icons.Rounded.ClosedCaption, stringResource(R.string.subtitles_2)) { panel = Panel.Subtitles; interaction++ }
                    TopIcon(Icons.Rounded.Audiotrack, stringResource(R.string.audio_2)) { panel = Panel.Audio; interaction++ }
                    TopIcon(Icons.Rounded.Settings, stringResource(R.string.settings)) { panel = Panel.Settings; interaction++ }
                    if (!isTv) TopIcon(Icons.Rounded.PictureInPictureAlt, stringResource(R.string.picture_in_picture), onClick = onPip)
                    if (!isTv) TopIcon(Icons.Rounded.Lock, stringResource(R.string.lock_2)) { locked = true; controls = false; panel = null }
                }

                Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                    if (ui.live) RoundControl(Icons.Rounded.SkipPrevious, 56.dp, label = stringResource(R.string.ch)) { vm.zap(-1); interaction++ }
                    else if (ui.previous != null) RoundControl(Icons.Rounded.SkipPrevious, 48.dp) { vm.playPrevious() }
                    else if (ui.chapters.isNotEmpty()) RoundControl(Icons.Rounded.SkipPrevious, 48.dp) { vm.previousChapter(); interaction++ }
                    if (!ui.live) RoundControl(Icons.Rounded.FastRewind, 56.dp, label = "${ui.seekStepSec}") { vm.seekStep(false); interaction++ }
                    RoundControl(if (ui.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, 80.dp, primary = true,
                        modifier = Modifier.focusRequester(playFocus)) { vm.togglePlay(); interaction++ }
                    if (!ui.live) RoundControl(Icons.Rounded.FastForward, 56.dp, label = "${ui.seekStepSec}") { vm.seekStep(true); interaction++ }
                    if (ui.live) RoundControl(Icons.Rounded.SkipNext, 56.dp, label = stringResource(R.string.ch_2)) { vm.zap(1); interaction++ }
                    else if (ui.next != null) RoundControl(Icons.Rounded.SkipNext, 48.dp) { vm.playNext() }
                    else if (ui.chapters.isNotEmpty()) RoundControl(Icons.Rounded.SkipNext, 48.dp) { vm.nextChapter(); interaction++ }
                }

                if (ui.live) Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().safeDrawingPadding().padding(20.dp)) { LiveInfo(ui) }
                else Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().safeDrawingPadding().padding(horizontal = 20.dp, vertical = 8.dp)) {
                    val chapter = vm.currentChapter()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(formatClock(scrubMs ?: ui.positionMs), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        chapter?.let { Text("  ·  ${it.name}", color = Harbor.VioletSoft, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, false)) }
                        Spacer(Modifier.weight(1f))
                        if (ui.durationMs > 0) {
                            val remaining = ((ui.durationMs - ui.positionMs) / ui.speed).toLong()
                            Text(stringResource(R.string.ends_1_s, DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(System.currentTimeMillis() + remaining))),
                                color = Harbor.TextDim, fontSize = 12.sp)
                        }
                        Text(if (showRemaining) formatClock(-(ui.durationMs - ui.positionMs)) else formatClock(ui.durationMs),
                            color = Harbor.TextDim, fontSize = 13.sp, modifier = Modifier.clickable { showRemaining = !showRemaining })
                    }
                    ScrubArea(ui, scrubMs, onScrub = { scrubMs = it.takeIf { v -> v >= 0 }; interaction++ }, onCommit = { vm.seekTo(it); scrubMs = null })
                    // One line of chips that scrolls sideways when the phone is narrow (portrait) – never wraps.
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                        // Language quick controls: CC toggles subtitles, the audio chip opens the language list.
                        if (!ui.live) {
                            val sub = ui.text.firstOrNull { it.selected }
                            TextChip(Icons.Rounded.ClosedCaption, if (ui.textOff || sub == null) stringResource(R.string.cc_off) else shortLang(sub)) {
                                // No subtitles in this file: jump straight to the online search.
                                if (!vm.toggleSubtitles()) { panel = Panel.Subtitles; vm.openSubSearch() }
                                interaction++
                            }
                            ui.audio.firstOrNull { it.selected }?.let { a -> if (ui.audio.size > 1) TextChip(Icons.Rounded.Audiotrack, shortLang(a)) { panel = Panel.Audio; interaction++ } }
                        }
                        if (!ui.offline && !ui.live) TextChip(Icons.Rounded.HighQuality, shortQuality(ui.quality)) { panel = Panel.Quality; interaction++ }
                        TextChip(Icons.Rounded.AspectRatio, ui.fit.label) { vm.cycleFit(); interaction++ }
                        if (!isTv) TextChip(Icons.Rounded.ScreenRotation, ui.rotation.label) {
                            vm.setRotation(Rotation.entries[(ui.rotation.ordinal + 1) % Rotation.entries.size]); interaction++
                        }
                        if (ui.speed != 1f) TextChip(null, "${ui.speed}×") { panel = Panel.Settings }
                        if (ui.sleepAt != null || ui.sleepEndOfEpisode) TextChip(null, stringResource(R.string.sleep_on)) { panel = Panel.Settings }
                        Spacer(Modifier.weight(1f))
                        if (ui.next != null) TextChip(Icons.Rounded.SkipNext, stringResource(R.string.next_episode)) { vm.playNext() }
                    }
                }
            }
        }

        if (locked) {
            var show by remember { mutableStateOf(true) }
            LaunchedEffect(show) { if (show) { delay(2500); show = false } }
            Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { show = true } })
            AnimatedVisibility(show, Modifier.align(Alignment.TopCenter).safeDrawingPadding().padding(16.dp), enter = fadeIn(), exit = fadeOut()) {
                Row(Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = .15f)).clickable { locked = false; controls = true }
                    .padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.LockOpen, null, tint = Color.White); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.tap_to_unlock), color = Color.White)
                }
            }
        }

        val intro = ui.intro
        val inIntro = !ui.autoSkip && intro != null && ui.positionMs in (intro.startTicks / 10_000) until (intro.endTicks / 10_000 - 1000)
        AnimatedVisibility(inIntro && !locked, Modifier.align(Alignment.BottomEnd).safeDrawingPadding().padding(end = 24.dp, bottom = 110.dp),
            enter = slideInHorizontally { it } + fadeIn(), exit = slideOutHorizontally { it } + fadeOut()) {
            Row(Modifier.clip(RoundedCornerShape(14.dp)).background(Color.White).clickable { vm.skipIntro() }.padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(if (intro?.type == "Recap") stringResource(R.string.skip_recap) else stringResource(R.string.skip_intro), color = Color.Black, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(6.dp)); Icon(Icons.Rounded.SkipNext, null, tint = Color.Black)
            }
        }

        val creditsAt = ui.creditsAtMs ?: (ui.durationMs - 25_000)
        val showUpNext = ui.next != null && !ui.upNextDismissed && ui.durationMs > 300_000 && ui.positionMs >= creditsAt && !locked
        AnimatedVisibility(showUpNext, Modifier.align(Alignment.BottomEnd).safeDrawingPadding().padding(end = 24.dp, bottom = 110.dp),
            enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
            ui.next?.let { UpNextCard(it, ((ui.durationMs - ui.positionMs) / 1000).coerceAtLeast(0), ui.autoPlayNext, vm::playNext, vm::dismissUpNext) }
        }

        AnimatedVisibility(ui.notice != null, Modifier.align(Alignment.TopCenter).safeDrawingPadding().padding(top = 64.dp), enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut()) {
            Text(ui.notice.orEmpty(), color = Color.White, modifier = Modifier.clip(RoundedCornerShape(50)).background(Harbor.Violet.copy(alpha = .85f)).padding(horizontal = 16.dp, vertical = 8.dp))
        }
        vm.pinGate?.let { title ->
            val wrong = stringResource(R.string.pin_wrong)
            com.sridhar.harbor.ui.components.PinDialog(stringResource(R.string.pin_enter), stringResource(R.string.pin_for_title, title), onDismiss = onBack) { pin ->
                if (vm.submitPin(pin)) null else wrong
            }
        }
        ui.error?.let { err ->
            val online by com.sridhar.harbor.net.NetworkMonitor.online.collectAsState()
            Column(Modifier.align(Alignment.Center).padding(24.dp).widthIn(max = 560.dp).clip(RoundedCornerShape(24.dp)).background(Harbor.Surface.copy(alpha = .94f)).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                // Offline → the jellyfish drifts looking for signal; anything else → the play button glitches.
                if (online) com.sridhar.harbor.ui.components.GlitchJelly(Modifier.size(110.dp)) else com.sridhar.harbor.ui.components.AdriftJelly(Modifier.size(110.dp))
                Spacer(Modifier.height(8.dp))
                Text(stringResource(if (online) R.string.play_cant else R.string.net_no_connection), style = MaterialTheme.typography.titleMedium, color = Color.White)
                Spacer(Modifier.height(4.dp))
                Text(if (online) err else stringResource(R.string.net_waiting), color = Harbor.TextDim, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (ui.live) TextChip(Icons.Rounded.Refresh, if (vm.liveRefreshing) stringResource(R.string.iptv_refreshing) else stringResource(R.string.iptv_refresh_channels)) { if (!vm.liveRefreshing) vm.refreshLiveSource() }
                    else if (!ui.offline) TextChip(null, stringResource(R.string.try_transcoding)) { vm.setQuality(Quality.Q8) }
                    TextChip(null, if (ui.softwareDecoding) stringResource(R.string.hardware_decoder) else stringResource(R.string.software_decoder)) { vm.setSoftwareDecoding(!ui.softwareDecoding) }
                    TextChip(null, stringResource(R.string.close), onBack)
                }
            }
        }

        vm.casting?.let { device ->
            val status by LocalContainer.current.cast.status.collectAsState()
            Column(Modifier.align(Alignment.Center).clip(RoundedCornerShape(24.dp)).background(Harbor.Surface.copy(.95f)).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Rounded.CastConnected, null, tint = Harbor.Sky, modifier = Modifier.size(48.dp))
                Text(stringResource(R.string.playing_on_1_s, device), color = Color.White, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                Text(ui.title, color = Harbor.TextDim)
                status?.let { st -> Text("${formatClock(st.positionMs)} / ${formatClock(st.durationMs)}", color = Harbor.TextDim, fontSize = 12.sp) }
                Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    val cast = LocalContainer.current.cast
                    RoundControl(Icons.Rounded.FastRewind, 48.dp) { cast.seekBy(-10_000) }
                    RoundControl(if (status?.playing == true) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, 64.dp, primary = true) { cast.togglePlay() }
                    RoundControl(Icons.Rounded.FastForward, 48.dp) { cast.seekBy(30_000) }
                }
                TextChip(null, stringResource(R.string.continue_on_phone)) { vm.stopCasting() }
            }
        }
        if (showCast) com.sridhar.harbor.cast.CastPicker({ showCast = false }) { d -> showCast = false; vm.castTo(d) }

        AnimatedVisibility(panel != null, Modifier.align(Alignment.CenterEnd),
            enter = slideInHorizontally { it } + fadeIn(), exit = slideOutHorizontally { it } + fadeOut()) {
            SidePanel(panel, ui, vm, onClose = { panel = null })
        }
    }
}

@OptIn(UnstableApi::class)
private fun styleSubtitles(sv: SubtitleView, style: SubStyle, scale: Float) {
    sv.setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * scale)
    if (style == SubStyle.Embedded) {
        sv.setApplyEmbeddedStyles(true); sv.setUserDefaultStyle(); return
    }
    sv.setApplyEmbeddedStyles(false)
    sv.setStyle(when (style) {
        SubStyle.Box -> CaptionStyleCompat(android.graphics.Color.WHITE, 0xB0000000.toInt(), android.graphics.Color.TRANSPARENT,
            CaptionStyleCompat.EDGE_TYPE_NONE, android.graphics.Color.TRANSPARENT, Typeface.DEFAULT_BOLD)
        SubStyle.Yellow -> CaptionStyleCompat(0xFFFFE14D.toInt(), android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT,
            CaptionStyleCompat.EDGE_TYPE_OUTLINE, android.graphics.Color.BLACK, Typeface.DEFAULT_BOLD)
        else -> CaptionStyleCompat(android.graphics.Color.WHITE, android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT,
            CaptionStyleCompat.EDGE_TYPE_OUTLINE, android.graphics.Color.BLACK, Typeface.DEFAULT_BOLD)
    })
}

// ---------------- trickplay ----------------

/** Loads trickplay tile sheets once and crops the thumbnail for [positionMs]. */
@Composable
fun TrickplayThumb(tp: Trickplay, positionMs: Long, width: Dp) {
    val ctx = LocalContext.current
    val cfg = rememberConfig()
    val jf = LocalContainer.current.jellyfin
    val cache = remember(tp.itemId) { mutableStateMapOf<Int, ImageBitmap>() }
    val info = tp.info
    val index = (positionMs / info.interval.coerceAtLeast(1)).toInt().coerceIn(0, (info.thumbnailCount - 1).coerceAtLeast(0))
    val perTile = (info.tileWidth * info.tileHeight).coerceAtLeast(1)
    val tile = index / perTile
    val inTile = index % perTile
    LaunchedEffect(tile, tp.itemId) {
        if (tile !in cache) {
            val req = ImageRequest.Builder(ctx).data(jf.trickplayTileUrl(cfg, tp.itemId, tp.mediaSourceId, info.width, tile)).allowHardware(false).build()
            (SingletonImageLoader.get(ctx).execute(req) as? SuccessResult)?.image?.toBitmap()?.let { cache[tile] = it.asImageBitmap() }
        }
    }
    val bmp = cache[tile] ?: cache[tile - 1]
    Box(Modifier.width(width).aspectRatio(info.width.toFloat() / info.height.coerceAtLeast(1)).clip(RoundedCornerShape(12.dp))
        .border(2.dp, Color.White.copy(alpha = .85f), RoundedCornerShape(12.dp)).background(Color.Black)) {
        if (bmp != null && cache[tile] != null) Canvas(Modifier.fillMaxSize()) {
            drawImage(bmp, srcOffset = IntOffset((inTile % info.tileWidth) * info.width, (inTile / info.tileWidth) * info.height),
                srcSize = IntSize(info.width, info.height), dstSize = IntSize(size.width.toInt(), size.height.toInt()))
        } else com.sridhar.harbor.ui.components.JellyLoader(Modifier.align(Alignment.Center).size(20.dp), strokeWidth = 2.dp, color = Harbor.VioletSoft)
    }
}

/** Seek bar plus a floating trickplay/time bubble that follows the scrub position. */
@Composable
private fun ScrubArea(ui: PlayerUi, scrubMs: Long?, onScrub: (Long) -> Unit, onCommit: (Long) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val w = maxWidth
        val bubbleW = if (ui.trickplay != null) 180.dp else 72.dp
        Column {
            Box(Modifier.fillMaxWidth().height(if (scrubMs != null && ui.trickplay != null) 118.dp else if (scrubMs != null) 34.dp else 0.dp)) {
                if (scrubMs != null && ui.durationMs > 0) {
                    val frac = (scrubMs.toFloat() / ui.durationMs).coerceIn(0f, 1f)
                    val x = (w * frac - bubbleW / 2).coerceIn(0.dp, w - bubbleW)
                    Column(Modifier.offset(x = x).width(bubbleW), horizontalAlignment = Alignment.CenterHorizontally) {
                        ui.trickplay?.let { TrickplayThumb(it, scrubMs, bubbleW) }
                        Text(formatClock(scrubMs), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                            modifier = Modifier.padding(top = 4.dp).clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(.6f)).padding(horizontal = 8.dp, vertical = 2.dp))
                    }
                }
            }
            SeekBar(scrubMs ?: ui.positionMs, ui.durationMs, ui.bufferedMs, ui.chapters.map { it.startMs },
                ui.intro?.let { it.startTicks / 10_000 to it.endTicks / 10_000 }, onScrub, onCommit)
        }
    }
}

@Composable
private fun SeekBar(
    position: Long, duration: Long, buffered: Long, marks: List<Long>, intro: Pair<Long, Long>?,
    onScrub: (Long) -> Unit, onCommit: (Long) -> Unit,
) {
    var dragging by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    var keyScrub by remember { mutableStateOf<Long?>(null) }
    val thumb by animateFloatAsState(if (dragging || focused) 10f else 6f, spring(), label = "thumb")
    val violet = Harbor.Violet; val coral = Harbor.Coral
    Canvas(
        Modifier.fillMaxWidth().height(28.dp)
            .onFocusChanged { focused = it.isFocused; if (!it.isFocused && keyScrub != null) { keyScrub = null; onScrub(-1) } }
            .onKeyEvent { e ->
                if (duration <= 0) return@onKeyEvent false
                when (e.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        if (e.type != KeyEventType.KeyDown) return@onKeyEvent true
                        val step = (duration / 100).coerceIn(10_000, 60_000) * (if (e.nativeKeyEvent.repeatCount > 8) 3 else 1)
                        val base = keyScrub ?: position
                        keyScrub = (base + if (e.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) step else -step).coerceIn(0, duration)
                        onScrub(keyScrub!!); true
                    }
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                        if (e.type == KeyEventType.KeyUp) keyScrub?.let { onCommit(it); keyScrub = null }
                        keyScrub != null
                    }
                    else -> false
                }
            }
            .focusable()
            .pointerInput(duration) { detectTapGestures { o -> if (duration > 0) onCommit((o.x / size.width * duration).toLong()) } }
            .pointerInput(duration) {
                var last = 0L
                detectHorizontalDragGestures(
                    onDragStart = { o -> dragging = true; last = (o.x / size.width * duration).toLong(); onScrub(last) },
                    onDragEnd = { dragging = false; onCommit(last) },
                    onDragCancel = { dragging = false },
                    onHorizontalDrag = { ch, _ -> ch.consume(); last = (ch.position.x / size.width * duration).toLong().coerceIn(0, duration); onScrub(last) },
                )
            },
    ) {
        val cy = size.height / 2; val h = if (dragging) 6.dp.toPx() else 4.dp.toPx()
        fun x(ms: Long) = if (duration > 0) (ms.toFloat() / duration).coerceIn(0f, 1f) * size.width else 0f
        val r = CornerRadius(h / 2)
        drawRoundRect(Color.White.copy(alpha = .2f), Offset(0f, cy - h / 2), Size(size.width, h), r)
        drawRoundRect(Color.White.copy(alpha = .35f), Offset(0f, cy - h / 2), Size(x(buffered), h), r)
        intro?.let { (s, e) -> drawRect(Harbor.Amber.copy(alpha = .5f), Offset(x(s), cy - h / 2), Size(x(e) - x(s), h)) }
        drawRoundRect(Brush.horizontalGradient(listOf(violet, coral), 0f, size.width), Offset(0f, cy - h / 2), Size(x(position), h), r)
        marks.forEach { m -> drawRect(Color.Black.copy(alpha = .7f), Offset(x(m) - 1.dp.toPx(), cy - h / 2), Size(2.dp.toPx(), h)) }
        drawCircle(Color.White, thumb.dp.toPx(), Offset(x(position), cy))
    }
}

// ---------------- small pieces ----------------

@Composable
private fun DownloadButton(state: DownloadState, onClick: () -> Unit) {
    when (state) {
        DownloadState.Unavailable -> Unit
        DownloadState.None -> TopIcon(Icons.Rounded.Download, stringResource(R.string.download), onClick = onClick)
        DownloadState.Queued -> TopIcon(Icons.Rounded.Downloading, stringResource(R.string.downloading), tint = Harbor.Amber) {}
        DownloadState.Done -> TopIcon(Icons.Rounded.DownloadDone, stringResource(R.string.downloaded), tint = Harbor.Mint) {}
    }
}

@Composable
private fun StatsOverlay(s: PlayerStats, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = .65f)).padding(12.dp)) {
        listOf(L10n.s(R.string.method) to s.method, L10n.s(R.string.video) to s.video, L10n.s(R.string.audio_2) to s.audio, L10n.s(R.string.decoder) to s.decoder, L10n.s(R.string.bitrate) to s.bitrate,
            L10n.s(R.string.buffer) to "%.1f s".format(s.bufferSec), L10n.s(R.string.dropped) to "${s.dropped} frames").forEach { (k, v) ->
            Row { Text(k.padEnd(8), fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Harbor.VioletSoft); Text(v, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Color.White) }
        }
    }
}

/** "Original", "8 Mbps"… – compact label for the quality chip. */
@Composable
private fun shortQuality(q: Quality): String =
    if (q.bitrate == null) stringResource(R.string.quality_original_short) else "${q.bitrate / 1_000_000} Mbps"

@Composable
private fun QualityTag(text: String) {
    Text(text, color = Harbor.Coral, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp,
        modifier = Modifier.padding(end = 6.dp).clip(RoundedCornerShape(6.dp)).background(Harbor.Coral.copy(alpha = .15f)).padding(horizontal = 6.dp, vertical = 2.dp))
}

/** Visible focus for D-pad users: white ring + slight scale. */
internal fun Modifier.focusRing(shape: androidx.compose.ui.graphics.Shape = CircleShape): Modifier = composed {
    var f by remember { mutableStateOf(false) }
    val sc by animateFloatAsState(if (f) 1.12f else 1f, spring(), label = "fr")
    this.onFocusChanged { f = it.isFocused }.graphicsLayer { scaleX = sc; scaleY = sc }
        .border(if (f) 2.5.dp else 0.dp, if (f) Color.White else Color.Transparent, shape)
}

/** ♥ that pops when switched on – the quickest way to save what you're watching. */
@Composable
private fun FavoriteIcon(on: Boolean, onClick: () -> Unit) {
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val pop by androidx.compose.animation.core.animateFloatAsState(if (on) 1.18f else 1f,
        androidx.compose.animation.core.spring(dampingRatio = 0.35f, stiffness = 500f), label = "fav")
    IconButton({ haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress); onClick() }, Modifier.focusRing()) {
        Icon(if (on) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, stringResource(if (on) R.string.fav_remove else R.string.fav_add),
            tint = if (on) Harbor.Rose else Color.White, modifier = Modifier.graphicsLayer { scaleX = pop; scaleY = pop })
    }
}

@Composable
private fun TopIcon(icon: ImageVector, desc: String, tint: Color = Color.White, onClick: () -> Unit) =
    IconButton(onClick, Modifier.focusRing()) { Icon(icon, desc, tint = tint) }

@Composable
private fun RoundControl(icon: ImageVector, size: Dp, primary: Boolean = false, label: String? = null, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.size(size).focusRing().clip(CircleShape)
            .background(if (primary) Brush.linearGradient(listOf(Harbor.Violet, Harbor.Coral)) else Brush.linearGradient(listOf(Color.White.copy(.12f), Color.White.copy(.12f))))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(size * if (label != null) 0.42f else 0.5f))
            label?.let { Text(it, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

private val subLanguages = listOf("eng", "tam", "hin", "tel", "mal", "kan", "spa", "fre", "ara", "chi", "kor", "jpn")

/** Online subtitle search inside the Subtitles panel (works with touch and the TV remote). */
@kotlin.OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun SubtitleSearch(vm: PlayerViewModel) {
    val st = vm.subSearch ?: return
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.subs_search), color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        androidx.compose.material3.TextButton({ vm.closeSubSearch() }) { Text(stringResource(R.string.back)) }
    }
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        (listOf(st.language) + subLanguages).distinct().forEach { l ->
            Seg(java.util.Locale.forLanguageTag(l).displayLanguage.ifBlank { l }, st.language == l, Modifier.padding(bottom = 6.dp).widthIn(min = 64.dp)) { vm.searchSubtitles(l) }
        }
    }
    when {
        st.loading -> Box(Modifier.fillMaxWidth().padding(24.dp), Alignment.Center) { com.sridhar.harbor.ui.components.JellyLoader(color = Harbor.Sky) }
        st.results.isNullOrEmpty() && st.error == null -> {
            Text(stringResource(R.string.subs_none), color = Harbor.TextDim, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp))
            if (st.installed) Text(stringResource(R.string.subs_installed), color = Harbor.Mint, style = MaterialTheme.typography.bodySmall)
            else if (st.isAdmin) Choice(stringResource(R.string.subs_install), false) { vm.installSubtitleProvider() }
        }
        else -> st.results.orEmpty().take(25).forEach { r ->
            val meta = listOfNotNull(r.provider, r.format?.uppercase(), r.downloads?.let { "⬇ $it" }, if (r.hashMatch == true) "✓ " + stringResource(R.string.subs_perfect) else null).joinToString(" · ")
            Choice((if (st.downloadingId == r.id) "⏳ " else "") + (r.name ?: r.id).take(80) + "\n" + meta, false) { if (st.downloadingId == null) vm.downloadSubtitle(r) }
        }
    }
    st.error?.let { Text(it, color = Harbor.Rose, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp)) }
}

/** "Tamil" / "English" for a track, falling back to its label. */
private fun shortLang(o: TrackOption): String =
    o.language?.let { java.util.Locale.forLanguageTag(it).displayLanguage.takeIf { d -> d.isNotBlank() && d.length > 3 } } ?: o.label.substringBefore(" · ").take(18)

@Composable
private fun TextChip(icon: ImageVector?, text: String, onClick: () -> Unit) {
    Row(Modifier.padding(end = 8.dp).focusRing(RoundedCornerShape(50)).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = .12f)).clickable(onClick = onClick)
        .padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)) }
        Text(text, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
    }
}

@Composable
private fun SeekRipple(forward: Boolean, secs: Int) {
    Box(Modifier.fillMaxSize(), contentAlignment = if (forward) Alignment.CenterEnd else Alignment.CenterStart) {
        Box(
            Modifier.fillMaxHeight().fillMaxWidth(0.38f)
                .clip(if (forward) RoundedCornerShape(topStartPercent = 100, bottomStartPercent = 100) else RoundedCornerShape(topEndPercent = 100, bottomEndPercent = 100))
                .background(Color.White.copy(alpha = .1f)),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(if (forward) Icons.Rounded.FastForward else Icons.Rounded.FastRewind, null, tint = Color.White, modifier = Modifier.size(40.dp))
                Text("${if (forward) "+" else "−"}$secs s", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SideIndicator(side: SideGesture, level: Float, modifier: Modifier) {
    Column(modifier.padding(horizontal = 48.dp).clip(RoundedCornerShape(24.dp)).background(Color.Black.copy(alpha = .55f)).padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(if (side == SideGesture.Brightness) Icons.Rounded.BrightnessMedium else Icons.AutoMirrored.Rounded.VolumeUp, null, tint = Color.White)
        Spacer(Modifier.height(10.dp))
        Box(Modifier.width(6.dp).height(140.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = .2f)), contentAlignment = Alignment.BottomCenter) {
            Box(Modifier.fillMaxWidth().fillMaxHeight(level).background(Brush.verticalGradient(listOf(Harbor.Coral, Harbor.Violet))))
        }
        Spacer(Modifier.height(8.dp))
        Text("${(level * 100).roundToInt()}", color = Color.White, fontSize = 12.sp)
    }
}

@Composable
private fun UpNextCard(next: BaseItem, secondsLeft: Long, autoplay: Boolean, onPlay: () -> Unit, onDismiss: () -> Unit) {
    val cfg = rememberConfig()
    val jf = LocalContainer.current.jellyfin
    Row(Modifier.width(380.dp).clip(RoundedCornerShape(18.dp)).background(Harbor.Surface.copy(alpha = .95f)).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(128.dp).height(72.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onPlay)) {
            NetImage(jf.thumbUrl(cfg, next, 400), Modifier.fillMaxSize())
            Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.align(Alignment.Center).size(32.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(if (autoplay) stringResource(R.string.up_next_1_ss, secondsLeft) else stringResource(R.string.up_next), style = MaterialTheme.typography.labelSmall, color = Harbor.VioletSoft)
            Text(listOfNotNull(next.episodeLabel, next.name).joinToString(" · "), color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row {
                Text(stringResource(R.string.play_now), color = Harbor.Coral, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(onClick = onPlay).padding(vertical = 6.dp))
                Spacer(Modifier.width(16.dp))
                Text(stringResource(R.string.dismiss), color = Harbor.TextDim, modifier = Modifier.clickable(onClick = onDismiss).padding(vertical = 6.dp))
            }
        }
    }
}

// ---------------- side panel ----------------

@Composable
private fun SidePanel(panel: Panel?, ui: PlayerUi, vm: PlayerViewModel, onClose: () -> Unit) {
    Column(Modifier.fillMaxHeight().width(360.dp).background(Harbor.Ink.copy(alpha = .95f)).safeDrawingPadding().padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(panel?.title.orEmpty(), style = MaterialTheme.typography.titleLarge, color = Color.White, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.done), color = Harbor.VioletSoft, modifier = Modifier.clickable(onClick = onClose).padding(8.dp))
        }
        Spacer(Modifier.height(8.dp))
        when (panel) {
            Panel.Chapters -> ChapterList(ui, vm, onClose)
            Panel.Channels -> ChannelList(ui, vm, onClose)
            Panel.Episodes -> EpisodeList(ui, vm, onClose)
            else -> Column(Modifier.verticalScroll(rememberScrollState())) {
                when (panel) {
                    Panel.Quality -> {
                        Text(stringResource(R.string.quality_auto_hint), color = Harbor.TextDim, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 8.dp))
                        Quality.entries.forEach { q -> Choice(q.label, ui.quality == q) { vm.chooseQuality(q) } }
                        Text(stringResource(R.string.quality_remembered), color = Harbor.TextDim, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 8.dp))
                    }
                    Panel.Audio -> {
                        if (ui.audio.isEmpty()) Text(stringResource(R.string.no_selectable_audio_tracks), color = Harbor.TextDim)
                        ui.audio.forEach { o -> Choice(o.label, o.selected) { vm.selectTrack(C.TRACK_TYPE_AUDIO, o) } }
                    }
                    Panel.Subtitles -> if (vm.subSearch != null) SubtitleSearch(vm) else {
                        Choice(stringResource(R.string.off), ui.textOff) { vm.selectTrack(C.TRACK_TYPE_TEXT, null) }
                        ui.text.forEach { o -> Choice(o.label, o.selected) { vm.selectTrack(C.TRACK_TYPE_TEXT, o) } }
                        if (!ui.live && !ui.offline) Choice("🔍  " + stringResource(R.string.subs_search), false) { vm.openSubSearch() }
                        if (ui.quality.bitrate != null) Text(stringResource(R.string.transcoded_streams_carry_no_subtitle_tracks), color = Harbor.TextDim, style = MaterialTheme.typography.bodySmall)
                        Label(stringResource(R.string.size_1_s, (ui.subScale * 100).roundToInt()))
                        Slider(ui.subScale, { vm.setSubScale((it * 20).roundToInt() / 20f) }, valueRange = 0.6f..2f,
                            colors = SliderDefaults.colors(thumbColor = Harbor.Coral, activeTrackColor = Harbor.Violet))
                        Label(stringResource(R.string.style))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { SubStyle.entries.forEach { s -> Seg(s.label, ui.subStyle == s, Modifier.weight(1f)) { vm.setSubStyle(s) } } }
                    }
                    else -> SettingsPanel(ui, vm)
                }
            }
        }
    }
}

@Composable
private fun SettingsPanel(ui: PlayerUi, vm: PlayerViewModel) {
    Label(stringResource(R.string.speed))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { s -> Seg("${s}×".replace(".0×", "×"), ui.speed == s, Modifier.weight(1f)) { vm.setSpeed(s) } }
    }
    if (!ui.offline) {
        Label(stringResource(R.string.quality))
        Quality.entries.forEach { q -> Choice(q.label, ui.quality == q) { vm.chooseQuality(q) } }
    }
    Label(stringResource(R.string.seek_step))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf(5, 10, 15, 30).forEach { s -> Seg("${s}s", ui.seekStepSec == s, Modifier.weight(1f)) { vm.setSeekStep(s) } } }
    Label(stringResource(R.string.aspect))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Fit.entries.forEach { f -> Seg(f.label, ui.fit == f, Modifier.weight(1f)) { vm.setFit(f) } } }
    Label(stringResource(R.string.orientation))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Rotation.entries.forEach { r -> Seg(r.label.substringBefore('-'), ui.rotation == r, Modifier.weight(1f)) { vm.setRotation(r) } } }
    Label(stringResource(R.string.sleep_timer))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Seg(stringResource(R.string.off), ui.sleepAt == null && !ui.sleepEndOfEpisode, Modifier.weight(1f)) { vm.setSleep(null) }
        listOf(15, 30, 60).forEach { m -> Seg("${m}m", false, Modifier.weight(1f)) { vm.setSleep(m) } }
        Seg(stringResource(R.string.end), ui.sleepEndOfEpisode, Modifier.weight(1f)) { vm.setSleep(null, endOfEpisode = true) }
    }
    ui.sleepAt?.let { Text(stringResource(R.string.pausing_at_1_s, DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it))), color = Harbor.VioletSoft, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp)) }
    Label(stringResource(R.string.behaviour))
    Toggle(stringResource(R.string.auto_skip_intros_recaps), stringResource(R.string.uses_jellyfin_media_segments), ui.autoSkip, vm::setAutoSkip)
    Toggle(stringResource(R.string.autoplay_next_episode), null, ui.autoPlayNext, vm::setAutoPlayNext)
    Toggle(stringResource(R.string.background_audio), stringResource(R.string.keep_playing_when_you_leave_the), ui.backgroundPlay, vm::setBackgroundPlay)
    Toggle(stringResource(R.string.prefer_software_decoder), stringResource(R.string.try_this_if_video_stutters_or), ui.softwareDecoding, vm::setSoftwareDecoding)
    Toggle(stringResource(R.string.playback_stats), stringResource(R.string.codec_decoder_bitrate_buffer_dropped_frames), ui.showStats) { vm.toggleStats() }
}

@Composable
private fun ChapterList(ui: PlayerUi, vm: PlayerViewModel, onClose: () -> Unit) {
    val current = vm.currentChapter()
    val list = rememberLazyListState(initialFirstVisibleItemIndex = (current?.index ?: 0).coerceAtLeast(0))
    LazyColumn(state = list, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(ui.chapters, key = { it.index }) { ch ->
            val active = ch.index == current?.index
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (active) Harbor.Violet.copy(alpha = .25f) else Color.White.copy(alpha = .04f))
                .clickable { vm.seekTo(ch.startMs); onClose() }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(112.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp)).background(Harbor.Surface)) {
                    when {
                        ch.imageUrl != null -> NetImage(ch.imageUrl, Modifier.fillMaxSize())
                        ui.trickplay != null -> TrickplayThumb(ui.trickplay, ch.startMs + 5_000, 112.dp)
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(ch.name, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(formatClock(ch.startMs), color = Harbor.TextDim, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun EpisodeList(ui: PlayerUi, vm: PlayerViewModel, onClose: () -> Unit) {
    val cfg = rememberConfig()
    val jf = LocalContainer.current.jellyfin
    val currentIdx = ui.episodes.indexOfFirst { it.id == ui.itemId }.coerceAtLeast(0)
    val list = rememberLazyListState(initialFirstVisibleItemIndex = currentIdx)
    LazyColumn(state = list, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(ui.episodes, key = { it.id }) { ep ->
            val active = ep.id == ui.itemId
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (active) Harbor.Violet.copy(alpha = .25f) else Color.White.copy(alpha = .04f))
                .clickable(enabled = !active) { vm.playEpisode(ep); onClose() }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(112.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp))) {
                    NetImage(jf.thumbUrl(cfg, ep, 320), Modifier.fillMaxSize(), fallback = ep.name)
                    if (ep.progress > 0f) ProgressStrip(ep.progress, Modifier.align(Alignment.BottomCenter))
                    if (active) Box(Modifier.fillMaxSize().background(Color.Black.copy(.4f)), Alignment.Center) { Text(stringResource(R.string.now), color = Color.White, fontWeight = FontWeight.Black, fontSize = 11.sp) }
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("${ep.indexNumber ?: ""}. ${ep.name}", color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    ep.runtimeMinutes?.let { Text("${it}m" + if (ep.userData?.played == true) stringResource(R.string.watched) else "", color = Harbor.TextDim, fontSize = 12.sp) }
                }
            }
        }
    }
}

/** Shown until the first frame renders: art, title, animated progress bar, elapsed time and hints for slow streams. */
@Composable
private fun LoadingOverlay(ui: PlayerUi) {
    var elapsed by remember(ui.loadStartedAt) { mutableIntStateOf(0) }
    LaunchedEffect(ui.loadStartedAt) { while (true) { delay(1000); elapsed++ } }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (!ui.live && ui.loadingArt != null) NetImage(ui.loadingArt, Modifier.fillMaxSize().graphicsLayer { alpha = 0.35f })
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(.3f), Color.Black.copy(.85f)))))
        Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (ui.live && ui.loadingArt != null) Box(Modifier.size(96.dp).clip(RoundedCornerShape(22.dp)).background(Color.White.copy(.08f))) {
                NetImage(ui.loadingArt, Modifier.fillMaxSize().padding(10.dp), contentScale = androidx.compose.ui.layout.ContentScale.Fit, fallback = ui.title.take(3))
            }
            if (ui.live) Spacer(Modifier.height(16.dp))
            Text(ui.title.ifBlank { stringResource(R.string.jellyverse) }, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 2,
                overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            ui.subtitle?.let { Text(it, color = Harbor.TextDim, fontSize = 14.sp) }
            Spacer(Modifier.height(22.dp))
            IndeterminateBar(Modifier.width(280.dp))
            Spacer(Modifier.height(12.dp))
            Text(
                when { ui.live -> stringResource(R.string.tuning); ui.offline -> stringResource(R.string.opening); else -> stringResource(R.string.loading) } + "…  ${elapsed}s",
                color = Color.White.copy(.8f), fontSize = 13.sp,
            )
            if (elapsed >= 10) Text(
                if (ui.live) stringResource(R.string.this_stream_is_slow_to_answer) else stringResource(R.string.taking_longer_than_usual_still_trying),
                color = Harbor.Amber, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

/** Gradient sweep bar (indeterminate); animation runs in the draw phase only. */
@Composable
private fun IndeterminateBar(modifier: Modifier = Modifier, height: Dp = 5.dp) =
    com.sridhar.harbor.ui.components.TideBar(modifier, color = Harbor.Violet, accent = Harbor.Sky, thickness = height)

@Composable
private fun SlimLoadingBar(modifier: Modifier) = com.sridhar.harbor.ui.components.TideBar(modifier.fillMaxWidth().safeDrawingPadding(), rider = false)

@Composable
private fun LiveInfo(ui: PlayerUi) {
    val ch = ui.channels.getOrNull(ui.channelIndex) ?: return
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(64.dp).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(.08f)), contentAlignment = Alignment.Center) {
            NetImage(ch.logo, Modifier.fillMaxSize().padding(6.dp), contentScale = androidx.compose.ui.layout.ContentScale.Fit, fallback = ch.name.take(3))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${ui.channelIndex + 1}", color = Harbor.Coral, fontWeight = FontWeight.Black, fontSize = 18.sp)
                Spacer(Modifier.width(8.dp))
                Text(ch.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val now = ui.nowNext?.now; val next = ui.nowNext?.next
            if (now != null) {
                Text(stringResource(R.string.now_1_s, now.title), color = Color.White.copy(.9f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                com.sridhar.harbor.ui.components.GradientProgress(now.progress, height = 3.dp, modifier = Modifier.padding(vertical = 4.dp))
            } else Text(ch.group, color = Harbor.TextDim, fontSize = 13.sp)
            next?.let { Text(stringResource(R.string.next_1_s_2_s, DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it.start)), it.title), color = Harbor.TextDim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

@Composable
private fun ChannelList(ui: PlayerUi, vm: PlayerViewModel, onClose: () -> Unit) {
    val iptv = LocalContainer.current.iptv
    val list = rememberLazyListState(initialFirstVisibleItemIndex = (ui.channelIndex - 2).coerceAtLeast(0))
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(80); runCatching { first.requestFocus() } }
    LazyColumn(state = list, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(ui.channels.size) { i ->
            val ch = ui.channels[i]
            val active = i == ui.channelIndex
            val nn = remember(ch.id) { iptv.nowNext(ch) }
            Row(Modifier.fillMaxWidth().then(if (active) Modifier.focusRequester(first) else Modifier).focusRing(RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp)).background(if (active) Harbor.Violet.copy(.25f) else Color.White.copy(.04f))
                .clickable { vm.tuneTo(i); onClose() }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${i + 1}", color = Harbor.TextDim, fontSize = 12.sp, modifier = Modifier.width(30.dp))
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(.06f))) {
                    NetImage(ch.logo, Modifier.fillMaxSize().padding(3.dp), contentScale = androidx.compose.ui.layout.ContentScale.Fit, fallback = ch.name.take(2))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(ch.name, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    nn.now?.let { Text(it.title, color = Harbor.TextDim, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }
        }
    }
}

@Composable private fun Label(t: String) = Text(t.uppercase(), style = MaterialTheme.typography.labelSmall, color = Harbor.TextDim, modifier = Modifier.padding(top = 18.dp, bottom = 8.dp))

@Composable
private fun Toggle(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 14.sp)
            subtitle?.let { Text(it, color = Harbor.TextDim, fontSize = 11.sp) }
        }
        Switch(checked, onChange)
    }
}

@Composable
private fun Choice(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (selected) Harbor.Violet.copy(alpha = .2f) else Color.Transparent)
        .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (selected) Icon(Icons.Rounded.Check, null, tint = Harbor.VioletSoft)
    }
}

@Composable
private fun Seg(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.clip(RoundedCornerShape(10.dp)).background(if (selected) Harbor.Violet else Color.White.copy(alpha = .08f)).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center) { Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) }
}
