package com.sridhar.harbor.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sridhar.harbor.R
import com.sridhar.harbor.data.music.Lyrics
import com.sridhar.harbor.data.music.MusicText
import com.sridhar.harbor.data.music.StructuredLyrics
import com.sridhar.harbor.music.RepeatMode
import com.sridhar.harbor.ui.components.LocalContainer
import com.sridhar.harbor.ui.components.rememberConfig
import com.sridhar.harbor.ui.music.CollectionViewModel
import com.sridhar.harbor.ui.music.CoverArt
import com.sridhar.harbor.ui.music.EqualizerBars
import com.sridhar.harbor.ui.music.MusicHomeViewModel
import com.sridhar.harbor.ui.music.NavidromeSignIn
import com.sridhar.harbor.ui.music.rememberArtColor
import com.sridhar.harbor.ui.music.seedColors
import com.sridhar.harbor.ui.theme.Harbor

@Composable
private fun MusicTile(title: String, subtitle: String?, art: String?, onClick: () -> Unit) {
    Column(Modifier.width(180.dp)) {
        CoverArt(art, title, Modifier.size(180.dp).tvFocusable(RoundedCornerShape(10.dp), onClick = onClick), RoundedCornerShape(10.dp))
        Spacer(Modifier.height(10.dp))
        Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        subtitle?.let { Text(it, color = Harbor.TextDim, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
}

@Composable
fun TvMusicHome(onCollection: (String, String) -> Unit, onNowPlaying: () -> Unit) {
    val c = LocalContainer.current
    val cfg = rememberConfig()
    val vm = viewModel { MusicHomeViewModel(c) }
    if (!cfg.navidromeReady) { Box(Modifier.fillMaxSize().padding(horizontal = 280.dp)) { NavidromeSignIn(onDone = { vm.load() }) }; return }
    val s by c.musicEngine.state.collectAsState()
    val song = s.current
    val heroArt = song?.let { c.music.coverUrl(cfg, it.coverArt, 600) }
    val tint = rememberArtColor(heroArt, song?.coverTitle ?: "music")

    LazyColumn(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(lerp(tint, Harbor.Ink, .55f), Harbor.Ink, Harbor.Ink))), contentPadding = PaddingValues(start = 120.dp, top = 40.dp, bottom = 60.dp, end = 48.dp)) {
        item {
            Text(stringResource(R.string.tv_music), color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(20.dp))
        }
        if (song != null) item {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White.copy(.06f)).padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                CoverArt(heroArt, song.coverTitle, Modifier.size(150.dp), RoundedCornerShape(12.dp))
                Spacer(Modifier.width(28.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { EqualizerBars(s.playing, color = Harbor.Sky); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.now_playing), color = Harbor.Sky, fontSize = 14.sp) }
                    Text(song.displayTitle, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(song.displayArtist, color = Harbor.TextDim, fontSize = 18.sp, maxLines = 1)
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        TvButton(stringResource(if (s.playing) R.string.pause else R.string.play), if (s.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, primary = true) { c.musicEngine.toggle() }
                        TvButton(stringResource(R.string.next), Icons.Rounded.SkipNext) { c.musicEngine.next() }
                        TvButton(stringResource(R.string.now_playing), null) { onNowPlaying() }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        if (vm.loading && vm.newest.isEmpty()) item { Box(Modifier.fillMaxWidth().padding(64.dp), Alignment.Center) { com.sridhar.harbor.ui.components.JellyLoader() } }
        if (vm.mixes.isNotEmpty()) item {
            TvRow(stringResource(R.string.mu_made_for_you), vm.mixes, key = { it.key }) { m ->
                val (a, b) = remember(m.key) { seedColors(m.key + "mix") }
                Column(Modifier.width(180.dp)) {
                    Box(Modifier.size(180.dp).tvFocusable(RoundedCornerShape(10.dp)) { vm.playMix(m); onNowPlaying() }.clip(RoundedCornerShape(10.dp)).background(Brush.linearGradient(listOf(a, b))).padding(14.dp)) {
                        Text(m.title, color = Color.White, fontWeight = FontWeight.Black, fontSize = 22.sp)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(m.subtitle, color = Harbor.TextDim, fontSize = 13.sp)
                }
            }
        }
        if (vm.recent.isNotEmpty()) item { TvRow(stringResource(R.string.mu_jump_back_in), vm.recent, key = { it.id }) { a -> MusicTile(a.displayName, a.displayArtist, c.music.coverUrl(cfg, a.coverArt, 360)) { onCollection("album", a.id) } } }
        if (vm.newest.isNotEmpty()) item { TvRow(stringResource(R.string.mu_new_releases), vm.newest, key = { it.id }) { a -> MusicTile(a.displayName, a.displayArtist, c.music.coverUrl(cfg, a.coverArt, 360)) { onCollection("album", a.id) } } }
        if (vm.playlists.isNotEmpty() || vm.liked.isNotEmpty()) item {
            val items = (if (vm.liked.isNotEmpty()) listOf<Any>("liked") else emptyList()) + vm.playlists
            TvRow(stringResource(R.string.mu_playlists), items, key = { (it as? com.sridhar.harbor.data.music.Playlist)?.id ?: "liked" }) { p ->
                if (p is com.sridhar.harbor.data.music.Playlist) MusicTile(p.name, stringResource(R.string.mu_songs_count, p.songCount), c.music.coverUrl(cfg, p.coverArt, 360)) { onCollection("playlist", p.id) }
                else MusicTile(stringResource(R.string.mu_liked_songs), stringResource(R.string.mu_songs_count, vm.liked.size), null) { onCollection("liked", "liked") }
            }
        }
        if (vm.frequent.isNotEmpty()) item { TvRow(stringResource(R.string.mu_on_repeat), vm.frequent, key = { it.id }) { a -> MusicTile(a.displayName, a.displayArtist, c.music.coverUrl(cfg, a.coverArt, 360)) { onCollection("album", a.id) } } }
    }
}

@Composable
fun TvMusicCollection(kind: String, id: String, onNowPlaying: () -> Unit) {
    val c = LocalContainer.current
    val cfg = rememberConfig()
    val vm = viewModel(key = "tvcol-$kind-$id") { CollectionViewModel(c, kind, id) }
    val s by c.musicEngine.state.collectAsState()
    val art = c.music.coverUrl(cfg, vm.cover, 600)
    val tint = rememberArtColor(art, vm.title)
    val first = remember { FocusRequester() }
    Row(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(lerp(tint, Harbor.Ink, .4f), Harbor.Ink))).padding(start = 120.dp, top = 48.dp, end = 48.dp)) {
        Column(Modifier.width(340.dp)) {
            CoverArt(art, vm.title, Modifier.size(300.dp), RoundedCornerShape(14.dp))
            Spacer(Modifier.height(20.dp))
            Text(vm.title, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black, maxLines = 2)
            Text(vm.subtitle, color = Harbor.TextDim, fontSize = 15.sp, maxLines = 2)
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvButton(stringResource(R.string.play), Icons.Rounded.PlayArrow, primary = true, modifier = Modifier.focusRequester(first)) { vm.play(); onNowPlaying() }
                TvButton(stringResource(R.string.mu_shuffle), Icons.Rounded.Shuffle) { vm.play(shuffle = true); onNowPlaying() }
            }
        }
        Spacer(Modifier.width(40.dp))
        LazyColumn(Modifier.weight(1f).fillMaxHeight(), contentPadding = PaddingValues(bottom = 48.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            itemsIndexed(vm.songs, key = { i, x -> "$i${x.id}" }) { i, song ->
                val current = s.current?.id == song.id
                Row(Modifier.fillMaxWidth().tvFocusable(RoundedCornerShape(10.dp), focusedScale = 1.02f) { vm.play(i); onNowPlaying() }
                    .clip(RoundedCornerShape(10.dp)).background(Color.White.copy(if (current) .10f else .04f)).padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(36.dp)) { if (current) EqualizerBars(s.playing, color = Harbor.Sky) else Text("${i + 1}", color = Harbor.TextDim, fontSize = 16.sp) }
                    Column(Modifier.weight(1f)) {
                        Text(song.displayTitle, color = if (current) Harbor.Sky else Color.White, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(song.displayArtist, color = Harbor.TextDim, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(MusicText.duration(song.duration), color = Harbor.TextDim, fontSize = 14.sp)
                }
            }
            if (vm.loading) item { com.sridhar.harbor.ui.components.JellyLoader() }
        }
    }
    LaunchedEffect(vm.loading) { if (!vm.loading) runCatching { first.requestFocus() } }
}

/** Big-screen player: artwork left, live synced lyrics right, remote-friendly controls. */
@Composable
fun TvNowPlaying() {
    val c = LocalContainer.current
    val cfg = rememberConfig()
    val engine = c.musicEngine
    val s by engine.state.collectAsState()
    val song = s.current ?: run { Box(Modifier.fillMaxSize(), Alignment.Center) { Text(stringResource(R.string.nothing_here), color = Harbor.TextDim) }; return }
    val art = c.music.coverUrl(cfg, song.coverArt, 800)
    val tint = rememberArtColor(art, song.coverTitle)
    val pos by produceState(0L, song.id) { engine.positionFlow().collect { value = it } }
    val lyrics by produceState<StructuredLyrics?>(null, song.id) { value = c.music.lyrics(song.id) }
    val play = remember { FocusRequester() }

    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
    // TVs are ~540 dp tall: size the artwork from the height so title and controls always fit.
    val artSize = (maxHeight - 250.dp).coerceIn(180.dp, 420.dp)
    Row(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(tint, lerp(tint, Harbor.Ink, .8f), Harbor.Ink))).padding(horizontal = 56.dp, vertical = 32.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.width(artSize + 40.dp)) {
            CoverArt(art, song.coverTitle, Modifier.size(artSize), RoundedCornerShape(16.dp))
            Spacer(Modifier.height(16.dp))
            Text(song.displayTitle, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.displayArtist, color = Color.White.copy(.75f), fontSize = 18.sp, maxLines = 1)
            Spacer(Modifier.height(16.dp))
            val frac = if (s.durationMs > 0) (pos.toFloat() / s.durationMs).coerceIn(0f, 1f) else 0f
            Box(Modifier.width(artSize).height(5.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(.25f))) { Box(Modifier.fillMaxWidth(frac).height(5.dp).background(Color.White)) }
            Row(Modifier.width(artSize).padding(top = 6.dp)) {
                Text(MusicText.duration((pos / 1000).toInt()), color = Color.White.copy(.7f), fontSize = 13.sp); Spacer(Modifier.weight(1f))
                Text(MusicText.duration((s.durationMs / 1000).toInt()), color = Color.White.copy(.7f), fontSize = 13.sp)
            }
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                TvIcon(Icons.Rounded.Shuffle, s.shuffle) { engine.setShuffle(!s.shuffle) }
                TvIcon(Icons.Rounded.SkipPrevious) { engine.previous() }
                TvIcon(if (s.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, big = true, modifier = Modifier.focusRequester(play)) { engine.toggle() }
                TvIcon(Icons.Rounded.SkipNext) { engine.next() }
                TvIcon(if (s.repeat == RepeatMode.One) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat, s.repeat != RepeatMode.Off) { engine.cycleRepeat() }
            }
        }
        Spacer(Modifier.width(64.dp))
        val lines = Lyrics.clean(lyrics?.line.orEmpty())
        if (lines.isEmpty()) Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.mu_next_in_queue), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            s.upNext.take(7).forEach { n -> Text("${n.displayTitle}  ·  ${n.displayArtist}", color = Color.White.copy(.7f), fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(vertical = 6.dp)) }
        } else {
            val active = if (lyrics?.synced == true) Lyrics.activeIndex(lines, pos, lyrics?.offset ?: 0) else -1
            val list = rememberLazyListState()
            LaunchedEffect(active) { if (active > 2) list.animateScrollToItem(active - 2) }
            LazyColumn(Modifier.weight(1f).fillMaxHeight(), state = list, contentPadding = PaddingValues(vertical = 80.dp)) {
                itemsIndexed(lines) { i, l ->
                    Text(l.value, fontSize = 34.sp, lineHeight = 44.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(vertical = 8.dp),
                        color = when { i == active -> Color.White; active < 0 -> Color.White.copy(.85f); i < active -> Color.White.copy(.35f); else -> Color.White.copy(.5f) })
                }
            }
        }
    }
    }
    LaunchedEffect(Unit) { runCatching { play.requestFocus() } }
}

@Composable
private fun TvIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, on: Boolean = false, big: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val size = if (big) 76.dp else 56.dp
    Box(modifier.size(size).tvFocusable(RoundedCornerShape(50), onClick = onClick).clip(RoundedCornerShape(50)).background(if (big) Color.White else Color.White.copy(.08f)), Alignment.Center) {
        Icon(icon, null, tint = if (big) Color.Black else if (on) Harbor.Sky else Color.White, modifier = Modifier.size(if (big) 40.dp else 28.dp))
    }
}
