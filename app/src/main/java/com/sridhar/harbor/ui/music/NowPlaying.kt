package com.sridhar.harbor.ui.music

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sridhar.harbor.R
import com.sridhar.harbor.data.music.Lyrics
import com.sridhar.harbor.data.music.MusicText
import com.sridhar.harbor.data.music.Playlist
import com.sridhar.harbor.data.music.Song
import com.sridhar.harbor.data.music.StructuredLyrics
import com.sridhar.harbor.music.MusicState
import com.sridhar.harbor.music.RepeatMode as Repeat
import com.sridhar.harbor.music.StreamQuality
import com.sridhar.harbor.ui.components.LocalContainer
import com.sridhar.harbor.ui.components.rememberConfig
import com.sridhar.harbor.ui.theme.Harbor
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

// ============================================================ mini player

/** Docked above the nav bar on every tab. Swipe sideways to skip, tap to open the full player. */
@Composable
fun MiniPlayer(onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalContainer.current
    val engine = c.musicEngine
    val s by engine.state.collectAsState()
    val song = s.current ?: return
    val cfg = rememberConfig()
    val art = c.music.coverUrl(cfg, song.coverArt, 200)
    val tint = rememberArtColor(art, song.coverTitle)
    val progress by produceState(0f, song.id) { engine.positionFlow().collect { value = if (s.durationMs > 0) it.toFloat() / engine.player.duration.coerceAtLeast(1) else 0f } }
    val drag = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    Box(modifier.widthIn(max = 640.dp).fillMaxWidth().padding(horizontal = 8.dp).height(60.dp).clip(RoundedCornerShape(10.dp)).background(tint)
        .clickable(onClick = onOpen).testTag("mini_player")
        .pointerInput(song.id) {
            detectHorizontalDragGestures(
                onDragEnd = { scope.launch(com.sridhar.harbor.CrashGuard) { val v = drag.value; if (abs(v) > 120) { if (v < 0) engine.next() else engine.previous() }; drag.animateTo(0f, spring()) } },
                onHorizontalDrag = { _, d -> scope.launch(com.sridhar.harbor.CrashGuard) { drag.snapTo(drag.value + d) } },
            )
        }) {
        Row(Modifier.fillMaxSize().offset { IntOffset(drag.value.roundToInt(), 0) }.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            CoverArt(art, song.coverTitle, Modifier.size(44.dp), RoundedCornerShape(6.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(song.displayTitle, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, modifier = Modifier.basicMarquee())
                Text(song.displayArtist, color = Color.White.copy(.7f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (song.streamUrl == null) LikeButton(song.id in s.likedIds, size = 22.dp) { engine.toggleLike(song) }
            IconButton({ engine.toggle() }, Modifier.testTag("mini_toggle")) {
                if (s.buffering && !s.playing) com.sridhar.harbor.ui.components.JellyLoader(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                else Icon(if (s.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, stringResource(if (s.playing) R.string.pause else R.string.play), tint = Color.White)
            }
        }
        Box(Modifier.align(Alignment.BottomStart).padding(horizontal = 8.dp).fillMaxWidth().height(2.dp).background(Color.White.copy(.2f)))
        Box(Modifier.align(Alignment.BottomStart).padding(horizontal = 8.dp).fillMaxWidth(progress.coerceIn(0f, 1f)).height(2.dp).background(Color.White))
    }
}

@Composable
fun LikeButton(liked: Boolean, size: androidx.compose.ui.unit.Dp = 26.dp, onToggle: () -> Unit) {
    // A springy "pop" when liking, like the real thing.
    val pop by animateFloatAsState(if (liked) 1f else 0f, spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessMedium), label = "like")
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    IconButton({ haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress); onToggle() }, Modifier.testTag("like")) {
        Icon(if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, stringResource(if (liked) R.string.mu_unlike else R.string.mu_like),
            tint = if (liked) Harbor.Sky else Color.White, modifier = Modifier.size(size).graphicsLayer { val k = 1f + 0.25f * (pop - pop * pop) * 4; scaleX = k; scaleY = k })
    }
}

// ============================================================ full player

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NowPlayingScreen(onClose: () -> Unit, onAlbum: (String) -> Unit, onArtist: (List<String>, String) -> Unit) =
    // Album-colour artwork screen: always dark, like the video player.
    com.sridhar.harbor.ui.theme.ForceDark { NowPlayingBody(onClose, onAlbum, onArtist) }

@Composable
private fun NowPlayingBody(onClose: () -> Unit, onAlbum: (String) -> Unit, onArtist: (List<String>, String) -> Unit) {
    val c = LocalContainer.current
    val engine = c.musicEngine
    val s by engine.state.collectAsState()
    val song = s.current ?: run { LaunchedEffect(Unit) { onClose() }; return }
    val cfg = rememberConfig()
    val art = c.music.coverUrl(cfg, song.coverArt, 900)
    val tint = rememberArtColor(art, song.coverTitle)
    val position by produceState(0L, song.id) { engine.positionFlow().collect { value = it } }
    val lyrics by produceState<StructuredLyrics?>(null, song.id) { value = c.music.lyrics(song.id) }
    var sheet by remember { mutableStateOf<String?>(null) }

    // Slowly drifting glow in the album's color – alive, never distracting.
    val drift = rememberInfiniteTransition(label = "drift").animateFloat(0f, 1f, infiniteRepeatable(tween(9_000), RepeatMode.Reverse), label = "d")

    Box(Modifier.fillMaxSize().background(Harbor.Ink).drawBehind {
        drawRect(Brush.verticalGradient(0f to tint, 0.62f to lerp(tint, Harbor.Ink, 0.85f), 1f to Harbor.Ink))
        drawCircle(Brush.radialGradient(listOf(lerp(tint, Color.White, 0.12f).copy(alpha = .35f), Color.Transparent),
            center = Offset(size.width * (0.2f + 0.6f * drift.value), size.height * 0.28f), radius = size.width * 0.9f))
    }.testTag("now_playing")) {
        val header: @Composable () -> Unit = {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClose) { Icon(Icons.Rounded.KeyboardArrowDown, stringResource(R.string.close), tint = Color.White, modifier = Modifier.size(30.dp)) }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.mu_playing_from), color = Color.White.copy(.7f), fontSize = 11.sp, letterSpacing = 1.sp)
                    Text(s.source ?: song.displayAlbum.orEmpty(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton({ sheet = "actions" }) { Icon(Icons.Rounded.MoreVert, stringResource(R.string.more), tint = Color.White) }
            }
        }
        // ---- artwork (swipe to skip; settles back when paused)
        val artScale by animateFloatAsState(if (s.playing) 1f else 0.86f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow), label = "art")
        val drag = remember { Animatable(0f) }
        val scope = rememberCoroutineScope()
        val artwork: @Composable (Modifier) -> Unit = { m ->
            // Keyed on the song itself so the outgoing cover stays the old song's while the new one fades in.
            AnimatedContent(song, m, label = "art", contentKey = { it.id },
                transitionSpec = { (fadeIn(tween(260)) + scaleIn(initialScale = .94f)) togetherWith (fadeOut(tween(160)) + scaleOut(targetScale = 1.04f)) }) { shown ->
                CoverArt(c.music.coverUrl(cfg, shown.coverArt, 900), shown.coverTitle,
                    Modifier.fillMaxWidth().aspectRatio(1f)
                        .graphicsLayer { scaleX = artScale; scaleY = artScale; translationX = drag.value; rotationZ = drag.value / 60f }
                        .shadow(24.dp, RoundedCornerShape(10.dp))
                        .pointerInput(shown.id) {
                            detectHorizontalDragGestures(
                                onDragEnd = { scope.launch(com.sridhar.harbor.CrashGuard) { val v = drag.value; if (abs(v) > 160) { if (v < 0) engine.next() else engine.previous() }; drag.animateTo(0f, spring()) } },
                                onHorizontalDrag = { _, d -> scope.launch(com.sridhar.harbor.CrashGuard) { drag.snapTo(drag.value + d) } },
                            )
                        },
                    RoundedCornerShape(10.dp))
            }
        }
        val controls: @Composable () -> Unit = {
            // ---- title + like
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(song.displayTitle, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.basicMarquee().testTag("np_title"))
                    Text(song.displayArtist, color = Color.White.copy(.72f), fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable { song.artistId?.let { onArtist(listOf(it), song.displayArtist) } })
                }
                if (song.streamUrl == null) LikeButton(song.id in s.likedIds) { engine.toggleLike(song) }
            }
            // ---- scrubber
            if (song.streamUrl == null) Scrubber(position, s.durationMs, onSeek = engine::seekTo)
            else Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                // Radio has no timeline – just show it's live.
                Box(Modifier.clip(RoundedCornerShape(6.dp)).background(Harbor.Rose).padding(horizontal = 8.dp, vertical = 3.dp)) {
                    Text("LIVE", color = Color.White, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 1.sp)
                }
                Spacer(Modifier.width(10.dp))
                EqualizerBars(s.playing, Modifier.size(18.dp), Color.White)
            }
            // ---- transport
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                ToggleIcon(Icons.Rounded.Shuffle, stringResource(R.string.mu_shuffle), s.shuffle) { engine.setShuffle(!s.shuffle) }
                IconButton({ engine.previous() }, Modifier.size(56.dp)) { Icon(Icons.Rounded.SkipPrevious, stringResource(R.string.previous), tint = Color.White, modifier = Modifier.size(40.dp)) }
                val press by animateFloatAsState(if (s.playing) 1f else 0.94f, spring(dampingRatio = .5f), label = "pp")
                val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
                Box(Modifier.size(68.dp).graphicsLayer { scaleX = press; scaleY = press }.clip(CircleShape).background(Color.White)
                    .clickable { haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove); engine.toggle() }.testTag("np_toggle"), Alignment.Center) {
                    AnimatedContent(s.playing, transitionSpec = { scaleIn() togetherWith scaleOut() }, label = "ppi") { playing ->
                        Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, stringResource(if (playing) R.string.pause else R.string.play), tint = Color.Black, modifier = Modifier.size(38.dp))
                    }
                }
                IconButton({ engine.next() }, Modifier.size(56.dp)) { Icon(Icons.Rounded.SkipNext, stringResource(R.string.next), tint = Color.White, modifier = Modifier.size(40.dp)) }
                ToggleIcon(if (s.repeat == Repeat.One) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat, stringResource(R.string.mu_repeat), s.repeat != Repeat.Off) { engine.cycleRepeat() }
            }
            // ---- tools
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                val sleepOn = s.sleepAt != null || s.sleepAfterSong
                ToggleIcon(Icons.Rounded.Bedtime, stringResource(R.string.sleep_timer), sleepOn) { sheet = "sleep" }
                ToggleIcon(Icons.Rounded.Equalizer, stringResource(R.string.mu_sound), false) { sheet = "eq" }
                ToggleIcon(Icons.AutoMirrored.Rounded.QueueMusic, stringResource(R.string.queue), false) { sheet = "queue" }
            }
            s.error?.let { Text(it, color = Harbor.Rose, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 24.dp)) }
            // ---- lyrics card
            LyricsCard(lyrics, position, tint, Modifier.padding(16.dp))
            // ---- up next preview
            if (s.upNext.isNotEmpty()) Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(.06f)).padding(14.dp)) {
                Text(stringResource(R.string.mu_next_in_queue), color = Color.White, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                s.upNext.take(3).forEachIndexed { i, n ->
                    SongRow(n, index = null, current = false, playing = false, onClick = { engine.jumpTo(s.index + 1 + i) }, compact = true)
                }
                TextButton({ sheet = "queue" }) { Text(stringResource(R.string.mu_open_queue), color = Harbor.VioletSoft) }
            }
        }
        // Landscape / tablet: artwork on the left, everything else scrolls on the right.
        BoxWithConstraints(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            val artMax = maxHeight - 80.dp
            if (maxWidth > maxHeight && maxWidth >= 560.dp) {
                Row(Modifier.fillMaxSize()) {
                    Column(Modifier.weight(0.45f).fillMaxHeight(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        header()
                        artwork(Modifier.weight(1f, fill = false).widthIn(max = artMax).padding(horizontal = 24.dp, vertical = 12.dp))
                    }
                    // Centred while it fits; scrolls once lyrics / queue make it taller than the screen.
                    Box(Modifier.weight(0.55f).fillMaxHeight(), contentAlignment = Alignment.CenterStart) {
                        Column(Modifier.verticalScroll(rememberScrollState()).padding(vertical = 16.dp)) { controls() }
                    }
                }
            } else {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                    Column(Modifier.widthIn(max = 560.dp)) {
                        header()
                        artwork(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp))
                        controls()
                    }
                }
            }
        }
    }

    when (sheet) {
        "queue" -> QueueSheet(s, onDismiss = { sheet = null })
        "sleep" -> SleepSheet(s, onDismiss = { sheet = null })
        "eq" -> SoundSheet(onDismiss = { sheet = null })
        "actions" -> SongActionsSheet(song, onDismiss = { sheet = null }, onAlbum = { onClose(); onAlbum(it) }, onArtist = { ids, n -> onClose(); onArtist(ids, n) })
    }
}

@Composable
private fun ToggleIcon(icon: ImageVector, label: String, on: Boolean, onClick: () -> Unit) {
    Box(contentAlignment = Alignment.BottomCenter) {
        IconButton(onClick) { Icon(icon, label, tint = if (on) Harbor.Sky else Color.White.copy(.85f), modifier = Modifier.size(26.dp)) }
        if (on) Box(Modifier.padding(bottom = 4.dp).size(4.dp).clip(CircleShape).background(Harbor.Sky))
    }
}

@Composable
fun Scrubber(positionMs: Long, durationMs: Long, onSeek: (Long) -> Unit) {
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    val frac = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        // Thin Spotify-style scrubber: 4 dp track, small thumb that grows while dragging.
        val thumb by animateFloatAsState(if (dragging) 16f else 12f, label = "thumb")
        Slider(
            value = if (dragging) dragValue else frac,
            onValueChange = { dragging = true; dragValue = it },
            onValueChangeFinished = { onSeek((dragValue * durationMs).toLong()); dragging = false },
            modifier = Modifier.testTag("scrubber").height(28.dp),
            thumb = { Box(Modifier.size(thumb.dp).clip(CircleShape).background(Color.White)) },
            track = { st ->
                val f = (st.value - st.valueRange.start) / (st.valueRange.endInclusive - st.valueRange.start)
                Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(.25f))) {
                    Box(Modifier.fillMaxWidth(f.coerceIn(0f, 1f)).height(4.dp).background(Color.White))
                }
            },
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            val shown = if (dragging) (dragValue * durationMs).toLong() else positionMs
            Text(MusicText.duration((shown / 1000).toInt()), color = Color.White.copy(.7f), fontSize = 12.sp)
            Spacer(Modifier.weight(1f))
            Text("-" + MusicText.duration(((durationMs - shown).coerceAtLeast(0) / 1000).toInt()), color = Color.White.copy(.7f), fontSize = 12.sp)
        }
    }
}

/** Spotify-style lyrics card: active line bright, auto-scrolls; plain lyrics when not synced. */
@Composable
fun LyricsCard(lyrics: StructuredLyrics?, positionMs: Long, tint: Color, modifier: Modifier = Modifier) {
    val lines = Lyrics.clean(lyrics?.line.orEmpty())
    if (lines.isEmpty()) return
    val active = if (lyrics?.synced == true) Lyrics.activeIndex(lines, positionMs, lyrics.offset) else -1
    val list = rememberLazyListState()
    LaunchedEffect(active) { if (active > 1) list.animateScrollToItem((active - 1).coerceAtLeast(0)) }
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(lerp(tint, Color.White, 0.08f)).padding(18.dp).testTag("lyrics")) {
        Text(stringResource(R.string.mu_lyrics), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Spacer(Modifier.height(10.dp))
        LazyColumn(Modifier.heightIn(max = 320.dp), state = list, userScrollEnabled = true) {
            itemsIndexed(lines) { i, l ->
                val done = active >= 0 && i < active
                Text(l.value, fontSize = 21.sp, fontWeight = FontWeight.Bold, lineHeight = 28.sp,
                    color = when { i == active -> Color.White; done -> Color.White.copy(.55f); active >= 0 -> Color.Black.copy(.55f); else -> Color.White },
                    modifier = Modifier.padding(vertical = 4.dp))
            }
        }
    }
}

// ============================================================ song rows & sheets

@Composable
fun SongRow(song: Song, index: Int?, current: Boolean, playing: Boolean, onClick: () -> Unit, onMore: (() -> Unit)? = null, compact: Boolean = false, showArt: Boolean = true) {
    val c = LocalContainer.current
    val cfg = rememberConfig()
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = if (compact) 4.dp else 6.dp).testTag("song_${song.id}"), verticalAlignment = Alignment.CenterVertically) {
        when {
            index != null && !showArt -> Box(Modifier.width(28.dp), Alignment.Center) {
                if (current) EqualizerBars(playing) else Text("$index", color = Harbor.TextDim, fontSize = 14.sp)
            }
            else -> CoverArt(c.music.coverUrl(cfg, song.coverArt, 120), song.coverTitle, Modifier.size(if (compact) 40.dp else 48.dp), RoundedCornerShape(4.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (current && showArt) { EqualizerBars(playing, color = Harbor.Sky); Spacer(Modifier.width(6.dp)) }
                Text(song.displayTitle, color = if (current) Harbor.Sky else Color.White, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(song.displayArtist, color = Harbor.TextDim, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (onMore != null) IconButton(onMore) { Icon(Icons.Rounded.MoreVert, stringResource(R.string.more), tint = Harbor.TextDim) }
    }
}

@Composable
fun QueueSheet(s: MusicState, onDismiss: () -> Unit) {
    val engine = LocalContainer.current.musicEngine
    ModalBottomSheet(onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Harbor.Surface) {
        LazyColumn(Modifier.fillMaxWidth().navigationBarsPadding(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
            item { Text(stringResource(R.string.now_playing), fontWeight = FontWeight.Bold, fontSize = 18.sp) }
            s.current?.let { cur -> item { SongRow(cur, null, current = true, playing = s.playing, onClick = {}) } }
            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
                    Text(stringResource(R.string.mu_next_from, s.source ?: ""), fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                    TextButton({ engine.stopAndClear(); onDismiss() }) { Text(stringResource(R.string.mu_clear_queue), color = Harbor.Rose) }
                }
            }
            itemsIndexed(s.upNext, key = { i, song -> "${s.index + 1 + i}-${song.id}" }) { i, song ->
                val qi = s.index + 1 + i
                val dismiss = rememberSwipeToDismissBoxState(confirmValueChange = { v -> if (v != SwipeToDismissBoxValue.Settled) { engine.removeAt(qi); true } else false })
                SwipeToDismissBox(dismiss, backgroundContent = {
                    Box(Modifier.fillMaxSize().background(Harbor.Rose.copy(.25f)).padding(horizontal = 16.dp), Alignment.CenterEnd) { Icon(Icons.Rounded.Close, stringResource(R.string.remove), tint = Harbor.Rose) }
                }) {
                    Box(Modifier.background(Harbor.Surface)) { SongRow(song, null, current = false, playing = false, onClick = { engine.jumpTo(qi) }, compact = true) }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
fun SleepSheet(s: MusicState, onDismiss: () -> Unit) {
    val engine = LocalContainer.current.musicEngine
    ModalBottomSheet(onDismiss, containerColor = Harbor.Surface) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 20.dp)) {
            Text(stringResource(R.string.sleep_timer), fontWeight = FontWeight.Bold, fontSize = 18.sp)
            val left = s.sleepAt?.let { ((it - System.currentTimeMillis()) / 60_000).coerceAtLeast(0) }
            Text(when { s.sleepAfterSong -> stringResource(R.string.mu_sleep_after_song); left != null -> stringResource(R.string.sleep_in_1_s_min, left); else -> stringResource(R.string.mu_sleep_hint) },
                color = Harbor.TextDim, fontSize = 13.sp)
            Spacer(Modifier.height(12.dp))
            listOf(5, 15, 30, 45, 60, 90).chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                    row.forEach { m -> FilterChip(false, { engine.sleepIn(m); onDismiss() }, label = { Text(stringResource(R.string.mu_minutes, m)) }, modifier = Modifier.weight(1f)) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                FilterChip(s.sleepAfterSong, { engine.sleepAfterThisSong(); onDismiss() }, label = { Text(stringResource(R.string.mu_end_of_song)) }, modifier = Modifier.weight(1f))
                FilterChip(false, { engine.sleepIn(null); onDismiss() }, label = { Text(stringResource(R.string.off)) }, modifier = Modifier.weight(1f))
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun SoundSheet(onDismiss: () -> Unit) {
    val engine = LocalContainer.current.musicEngine
    var preset by remember { mutableIntStateOf(engine.eqPreset) }
    var bass by remember { mutableFloatStateOf(engine.bassBoost / 1000f) }
    var quality by remember { mutableStateOf(engine.quality) }
    var saver by remember { mutableStateOf(engine.saveDataOnMobile) }
    val chip = FilterChipDefaults.filterChipColors(selectedContainerColor = Harbor.Violet, selectedLabelColor = Color.White)
    ModalBottomSheet(onDismiss, containerColor = Harbor.Surface) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 20.dp)) {
            Text(stringResource(R.string.mu_sound), fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.mu_equalizer), color = Harbor.TextDim, fontSize = 13.sp)
            val presets = engine.eqPresets
            if (presets.isEmpty()) Text(stringResource(R.string.mu_eq_unavailable), color = Harbor.TextDim, fontSize = 12.sp)
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(preset < 0, { preset = -1; engine.eqPreset = -1 }, label = { Text(stringResource(R.string.off)) }, colors = chip)
                presets.forEachIndexed { i, name -> FilterChip(preset == i, { preset = i; engine.eqPreset = i }, label = { Text(name) }, colors = chip) }
            }
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.mu_bass_boost), color = Harbor.TextDim, fontSize = 13.sp)
            Slider(bass, { bass = it }, onValueChangeFinished = { engine.bassBoost = (bass * 1000).toInt() })
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.mu_stream_quality), color = Harbor.TextDim, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StreamQuality.entries.forEach { q ->
                    FilterChip(quality == q, { quality = q; engine.quality = q },
                        label = { Text(if (q == StreamQuality.Original) stringResource(R.string.original) else "${q.kbps}k") }, colors = chip)
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.mu_data_saver), fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.mu_data_saver_hint), color = Harbor.TextDim, fontSize = 12.sp)
                }
                Switch(saver, { saver = it; engine.saveDataOnMobile = it })
            }
            Text(stringResource(R.string.mu_quality_note), color = Harbor.TextDim, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
fun SongActionsSheet(song: Song, onDismiss: () -> Unit, onAlbum: (String) -> Unit, onArtist: (List<String>, String) -> Unit) {
    val c = LocalContainer.current
    val engine = c.musicEngine
    val s by engine.state.collectAsState()
    val cfg = rememberConfig()
    val scope = rememberCoroutineScope()
    var pickPlaylist by remember { mutableStateOf<List<Playlist>?>(null) }
    ModalBottomSheet(onDismiss, containerColor = Harbor.Surface) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp)) {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                CoverArt(c.music.coverUrl(cfg, song.coverArt, 160), song.coverTitle, Modifier.size(52.dp), RoundedCornerShape(4.dp))
                Spacer(Modifier.width(12.dp))
                Column { Text(song.displayTitle, fontWeight = FontWeight.Bold, maxLines = 1); Text(song.displayArtist, color = Harbor.TextDim, fontSize = 13.sp, maxLines = 1) }
            }
            val pl = pickPlaylist
            if (pl != null) {
                Text(stringResource(R.string.mu_add_to_playlist), fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                pl.forEach { p -> ActionRow(Icons.AutoMirrored.Rounded.QueueMusic, p.name) { scope.launch(com.sridhar.harbor.CrashGuard) { runCatching { c.music.addToPlaylist(p.id, listOf(song.id)) } }; onDismiss() } }
                ActionRow(Icons.Rounded.Add, stringResource(R.string.mu_new_playlist_named, song.displayTitle)) { scope.launch(com.sridhar.harbor.CrashGuard) { runCatching { c.music.createPlaylist(song.displayTitle, listOf(song.id)) } }; onDismiss() }
            } else {
                ActionRow(if (song.id in s.likedIds) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, stringResource(if (song.id in s.likedIds) R.string.mu_unlike else R.string.mu_like)) { engine.toggleLike(song); onDismiss() }
                ActionRow(Icons.AutoMirrored.Rounded.QueueMusic, stringResource(R.string.mu_play_next)) { engine.playNext(song); onDismiss() }
                ActionRow(Icons.AutoMirrored.Rounded.PlaylistAdd, stringResource(R.string.mu_add_to_queue)) { engine.addToQueue(listOf(song)); onDismiss() }
                ActionRow(Icons.Rounded.Radio, stringResource(R.string.mu_start_radio)) { engine.radio(song); onDismiss() }
                ActionRow(Icons.AutoMirrored.Rounded.PlaylistAdd, stringResource(R.string.mu_add_to_playlist)) { scope.launch(com.sridhar.harbor.CrashGuard) { pickPlaylist = runCatching { c.music.playlists() }.getOrDefault(emptyList()) } }
                song.albumId?.let { id -> ActionRow(Icons.Rounded.Album, stringResource(R.string.mu_go_to_album)) { onDismiss(); onAlbum(id) } }
                song.artistId?.let { id -> ActionRow(Icons.Rounded.Person, stringResource(R.string.mu_go_to_artist)) { onDismiss(); onArtist(listOf(id), song.displayArtist) } }
            }
        }
    }
}

@Composable
private fun ActionRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Harbor.TextDim)
        Spacer(Modifier.width(18.dp))
        Text(label, fontSize = 15.sp)
    }
}
