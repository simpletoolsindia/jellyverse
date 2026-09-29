package com.sridhar.harbor.ui.music

import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.sridhar.harbor.ui.components.enterRise
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sridhar.harbor.R
import com.sridhar.harbor.data.music.Album
import com.sridhar.harbor.data.music.ArtistGroup
import com.sridhar.harbor.data.music.Playlist
import com.sridhar.harbor.data.music.Song
import com.sridhar.harbor.ui.components.GradientButton
import com.sridhar.harbor.ui.components.dpadFieldNav
import com.sridhar.harbor.ui.components.LocalContainer
import com.sridhar.harbor.ui.components.MessageState
import com.sridhar.harbor.ui.components.friendly
import com.sridhar.harbor.ui.components.pressable
import com.sridhar.harbor.ui.components.rememberConfig
import com.sridhar.harbor.ui.theme.Harbor
import kotlinx.coroutines.launch
import java.util.Calendar

/** Everything a music screen can navigate to. */
class MusicNav(
    val album: (String) -> Unit,
    val playlist: (String) -> Unit,
    val liked: () -> Unit,
    val downloaded: () -> Unit = {},
    val artist: (List<String>, String) -> Unit,
    val search: () -> Unit,
    val library: () -> Unit,
    val back: () -> Unit,
)

// ============================================================ home

@Composable
fun MusicHomeScreen(nav: MusicNav) {
    val offlineSongs by LocalContainer.current.offlineMusic.songs.collectAsState()
    val c = LocalContainer.current
    val cfg = rememberConfig()
    val vm = viewModel { MusicHomeViewModel(c) }
    if (!cfg.navidromeReady) { NavidromeSignIn(onDone = { vm.load() }); return }
    var filter by remember { mutableIntStateOf(0) }
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val greet = when (hour) { in 5..11 -> R.string.good_morning; in 12..16 -> R.string.good_afternoon; in 17..21 -> R.string.good_evening; else -> R.string.mu_good_night }
    val greetEmoji = when (hour) { in 5..11 -> "☀️"; in 12..16 -> "🌤️"; else -> "🌙" }

    androidx.compose.material3.pulltorefresh.PullToRefreshBox(vm.refreshing, onRefresh = { vm.load(pull = true) }, modifier = Modifier.fillMaxSize().background(Harbor.Ink)) {
    LazyColumn(Modifier.fillMaxSize().testTag("music_home"), contentPadding = PaddingValues(bottom = 170.dp)) {
        item {
            Row(Modifier.statusBarsPadding().padding(start = 16.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(greet) + " " + greetEmoji, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(nav.search) { Icon(Icons.Rounded.Search, stringResource(R.string.search), tint = Harbor.Fg) }
                IconButton(nav.library) { Icon(Icons.Rounded.LibraryMusic, stringResource(R.string.library), tint = Harbor.Fg) }
            }
        }
        item {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(R.string.all, R.string.mu_albums, R.string.mu_playlists).forEachIndexed { i, r ->
                    FilterChip(filter == i, { filter = i }, label = { Text(stringResource(r)) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Harbor.Violet, selectedLabelColor = Harbor.Fg, containerColor = Harbor.SurfaceHigh))
                }
            }
        }
        when {
            vm.loading && vm.recent.isEmpty() && vm.newest.isEmpty() -> item { Box(Modifier.fillMaxWidth().padding(64.dp), Alignment.Center) { com.sridhar.harbor.ui.components.JellyLoader() } }
            vm.error != null && vm.newest.isEmpty() -> {
                // Offline: downloaded songs still play.
                if (offlineSongs.isNotEmpty()) item(key = "downloaded") { DownloadedTile(offlineSongs.size) { nav.downloaded() } }
                item {
                    val online by com.sridhar.harbor.net.NetworkMonitor.online.collectAsState()
                    MessageState(stringResource(R.string.mu_cant_reach), if (!online) stringResource(R.string.mu_offline_hint) else vm.error, onRetry = { vm.load() })
                }
            }
            filter == 2 -> item { PlaylistGrid(vm.playlists, vm.liked.size, nav) }
            filter == 1 -> item { AlbumGrid(vm.newest + vm.frequent, nav) }
            else -> {
                item { QuickPicks(vm.recent.ifEmpty { vm.newest }.take(6), vm.liked.size, nav) }
                if (vm.mixes.isNotEmpty()) item {
                    Shelf(stringResource(R.string.mu_made_for_you)) {
                        items(vm.mixes, key = { it.key }) { m -> MixCard(m) { vm.playMix(m) } }
                    }
                }
                item(key = "radio") { RadioShelf() }
                if (offlineSongs.isNotEmpty()) item(key = "downloaded") { DownloadedTile(offlineSongs.size) { nav.downloaded() } }
                if (vm.recent.isNotEmpty()) item { AlbumShelf(stringResource(R.string.mu_jump_back_in), vm.recent, nav) }
                if (vm.newest.isNotEmpty()) item { AlbumShelf(stringResource(R.string.mu_new_releases), vm.newest, nav) }
                if (vm.artists.isNotEmpty()) item {
                    Shelf(stringResource(R.string.mu_your_artists)) {
                        items(vm.artists.take(16), key = { it.ids.first() }) { a -> ArtistBubble(a) { nav.artist(a.ids, a.name) } }
                    }
                }
                if (vm.frequent.isNotEmpty()) item { AlbumShelf(stringResource(R.string.mu_on_repeat), vm.frequent, nav) }
                if (vm.playlists.isNotEmpty()) item {
                    Shelf(stringResource(R.string.mu_playlists)) { items(vm.playlists, key = { it.id }) { p -> PlaylistCard(p) { nav.playlist(p.id) } } }
                }
                if (vm.forgotten.isNotEmpty()) item { AlbumShelf(stringResource(R.string.mu_rediscover), vm.forgotten, nav) }
            }
        }
    }
}
}

@Composable
private fun QuickPicks(albums: List<Album>, likedCount: Int, nav: MusicNav) {
    val c = LocalContainer.current
    val cfg = rememberConfig()
    // null = the "Liked songs" tile.
    val tiles: List<Album?> = ((if (likedCount > 0) listOf<Album?>(null) else emptyList()) + albums).take(6)
    // Two across on phones, three on tablets / landscape (6 tiles fill either evenly).
    val cols = if (com.sridhar.harbor.ui.components.widthClass() == com.sridhar.harbor.ui.components.WidthClass.Compact) 2 else 3
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        tiles.chunked(cols).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { a ->
                    if (a == null) QuickTile(null, stringResource(R.string.mu_liked_songs), Modifier.weight(1f), liked = true, onClick = nav.liked)
                    else QuickTile(c.music.coverUrl(cfg, a.coverArt, 160), a.displayName, Modifier.weight(1f)) { nav.album(a.id) }
                }
                repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun QuickTile(art: String?, title: String, modifier: Modifier, liked: Boolean = false, onClick: () -> Unit) {
    Row(modifier.height(56.dp).clip(RoundedCornerShape(6.dp)).background(Harbor.SurfaceHigh).pressable(0.97f, onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        if (liked) Box(Modifier.size(56.dp).background(Brush.linearGradient(listOf(Harbor.Violet, Harbor.Sky))), Alignment.Center) { Icon(Icons.Rounded.Favorite, null, tint = Color.White) }
        else CoverArt(art, title, Modifier.size(56.dp), RoundedCornerShape(0.dp))
        Text(title, Modifier.padding(horizontal = 8.dp), fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 15.sp)
    }
}

@Composable
private fun Shelf(title: String, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    Column(Modifier.padding(top = 24.dp)) {
        Text(title, fontSize = 21.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp))
        Spacer(Modifier.height(12.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), content = content)
    }
}

@Composable
private fun AlbumShelf(title: String, albums: List<Album>, nav: MusicNav) = Shelf(title) {
    items(albums, key = { it.id }) { a -> AlbumCard(a) { nav.album(a.id) } }
}

@Composable
fun AlbumCard(a: Album, size: androidx.compose.ui.unit.Dp = 148.dp, onClick: () -> Unit) {
    val c = LocalContainer.current
    val cfg = rememberConfig()
    Column(Modifier.width(size).pressable(onClick = onClick).testTag("album_${a.id}")) {
        CoverArt(c.music.coverUrl(cfg, a.coverArt, 400), a.displayName, Modifier.size(size), RoundedCornerShape(6.dp))
        Spacer(Modifier.height(8.dp))
        Text(a.displayName, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(a.displayArtist, color = Harbor.TextDim, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun MixCard(m: Mix, onClick: () -> Unit) {
    val (a, b) = remember(m.key) { seedColors(m.key + "mix") }
    Column(Modifier.width(148.dp).pressable(onClick = onClick).testTag("mix_${m.key}")) {
        Box(Modifier.size(148.dp).clip(RoundedCornerShape(6.dp)).background(Brush.linearGradient(listOf(a, b)))) {
            Icon(Icons.Rounded.MusicNote, null, tint = Harbor.line(.18f), modifier = Modifier.size(120.dp).align(Alignment.BottomEnd).graphicsLayer { translationX = 30f; translationY = 20f; rotationZ = -18f })
            Text(m.title, color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp, lineHeight = 20.sp, modifier = Modifier.align(Alignment.TopStart).padding(12.dp), maxLines = 3)
            Box(Modifier.align(Alignment.BottomStart).padding(10.dp).width(28.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color.White))
        }
        Spacer(Modifier.height(8.dp))
        Text(m.subtitle, color = Harbor.TextDim, fontSize = 13.sp, maxLines = 2)
    }
}

@Composable
fun ArtistBubble(a: ArtistGroup, onClick: () -> Unit) {
    val c = LocalContainer.current
    val cfg = rememberConfig()
    Column(Modifier.width(112.dp).pressable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        CoverArt(c.music.coverUrl(cfg, a.coverArt, 300), a.name, Modifier.size(112.dp), CircleShape)
        Spacer(Modifier.height(8.dp))
        Text(a.name, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun PlaylistCard(p: Playlist, onClick: () -> Unit) {
    val c = LocalContainer.current
    val cfg = rememberConfig()
    Column(Modifier.width(148.dp).pressable(onClick = onClick)) {
        CoverArt(c.music.coverUrl(cfg, p.coverArt, 400), p.name, Modifier.size(148.dp), RoundedCornerShape(6.dp))
        Spacer(Modifier.height(8.dp))
        Text(p.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(stringResource(R.string.mu_songs_count, p.songCount), color = Harbor.TextDim, fontSize = 13.sp)
    }
}

@Composable
private fun AlbumGrid(albums: List<Album>, nav: MusicNav) {
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        albums.distinctBy { it.id }.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                row.forEach { a -> Box(Modifier.weight(1f)) { AlbumCardFill(a) { nav.album(a.id) } } }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun AlbumCardFill(a: Album, onClick: () -> Unit) {
    val c = LocalContainer.current
    val cfg = rememberConfig()
    Column(Modifier.fillMaxWidth().pressable(onClick = onClick)) {
        CoverArt(c.music.coverUrl(cfg, a.coverArt, 400), a.displayName, Modifier.fillMaxWidth().aspectRatio(1f), RoundedCornerShape(6.dp))
        Spacer(Modifier.height(8.dp))
        Text(a.displayName, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(a.displayArtist, color = Harbor.TextDim, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun PlaylistGrid(playlists: List<Playlist>, likedCount: Int, nav: MusicNav) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        LibraryRow(null, stringResource(R.string.mu_liked_songs), stringResource(R.string.mu_songs_count, likedCount), liked = true, onClick = nav.liked)
        playlists.forEach { p -> LibraryRow(p.coverArt, p.name, stringResource(R.string.mu_songs_count, p.songCount)) { nav.playlist(p.id) } }
    }
}

@Composable
private fun LibraryRow(coverId: String?, title: String, subtitle: String, liked: Boolean = false, circle: Boolean = false, onClick: () -> Unit) {
    val c = LocalContainer.current
    val cfg = rememberConfig()
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (liked) Box(Modifier.size(60.dp).clip(RoundedCornerShape(4.dp)).background(Brush.linearGradient(listOf(Harbor.Violet, Harbor.Sky))), Alignment.Center) { Icon(Icons.Rounded.Favorite, null, tint = Color.White) }
        else CoverArt(c.music.coverUrl(cfg, coverId, 200), title, Modifier.size(60.dp), if (circle) CircleShape else RoundedCornerShape(4.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = Harbor.TextDim, fontSize = 13.sp, maxLines = 1)
        }
    }
}

// ============================================================ album / playlist / liked

@Composable
fun CollectionScreen(kind: String, id: String, nav: MusicNav) {
    val c = LocalContainer.current
    val cfg = rememberConfig()
    val vm = viewModel(key = "col-$kind-$id") { CollectionViewModel(c, kind, id) }
    val s by c.musicEngine.state.collectAsState()
    val art = c.music.coverUrl(cfg, vm.cover, 800)
    val tint = if (kind == "liked") lerp(Harbor.Violet, Color.Black, .35f) else rememberArtColor(art, vm.title)
    val list = rememberLazyListState()
    val density = LocalDensity.current
    // How far the header has scrolled away (0..1) – drives cover shrink/fade and the pinned title bar.
    val collapse by remember { derivedStateOf { if (list.firstVisibleItemIndex > 0) 1f else (list.firstVisibleItemScrollOffset / with(density) { 260.dp.toPx() }).coerceIn(0f, 1f) } }
    val playingHere = s.current != null && vm.songs.any { it.id == s.current?.id } && s.source == vm.source
    var actionsFor by remember { mutableStateOf<Song?>(null) }

    Box(Modifier.fillMaxSize().background(Harbor.Ink)) {
        LazyColumn(Modifier.fillMaxSize().testTag("collection"), state = list, contentPadding = PaddingValues(bottom = 170.dp)) {
            item {
                Column(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(tint, Harbor.Ink))).statusBarsPadding().padding(top = 56.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    val cover = @Composable { m: Modifier ->
                        if (kind == "liked") Box(m.background(Brush.linearGradient(listOf(Harbor.Violet, Harbor.Sky))), Alignment.Center) { Icon(Icons.Rounded.Favorite, null, tint = Color.White, modifier = Modifier.size(72.dp)) }
                        else CoverArt(art, vm.title, m, RoundedCornerShape(4.dp))
                    }
                    cover(Modifier.size(230.dp).graphicsLayer { val k = 1f - 0.25f * collapse; scaleX = k; scaleY = k; alpha = 1f - collapse }.shadow(20.dp))
                }
            }
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(vm.title, fontSize = 24.sp, fontWeight = FontWeight.Black)
                    Text(vm.subtitle, color = Harbor.TextDim, fontSize = 13.sp, modifier = Modifier.clickable { vm.artistId?.let { nav.artist(listOf(it), vm.subtitle.substringBefore(" •")) } })
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton({ vm.addAllToQueue() }) { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, stringResource(R.string.mu_add_to_queue), tint = Harbor.TextDim) }
                        // Offline: download the whole album / playlist; tick when every song is on the device.
                        val offlineSongs by c.offlineMusic.songs.collectAsState()
                        val dlProgress by c.offlineMusic.progress.collectAsState()
                        val saved = vm.songs.count { s -> offlineSongs.any { it.id == s.id } && c.offlineMusic.isDownloaded(s.id) }
                        val busy = vm.songs.any { c.offlineMusic.progressOf(it.id) != null }
                        @Suppress("UNUSED_EXPRESSION") dlProgress
                        if (vm.songs.isNotEmpty()) when {
                            busy -> Box(Modifier.size(48.dp), Alignment.Center) {
                                androidx.compose.material3.CircularProgressIndicator(progress = { saved.toFloat() / vm.songs.size }, modifier = Modifier.size(24.dp), strokeWidth = 3.dp, color = Harbor.Sky)
                            }
                            saved == vm.songs.size -> IconButton({ vm.removeDownloads() }) { Icon(Icons.Rounded.DownloadDone, stringResource(R.string.mu_remove_downloads), tint = Harbor.Mint) }
                            else -> IconButton({ vm.download() }) { Icon(Icons.Rounded.Download, stringResource(R.string.mu_download_all), tint = Harbor.TextDim) }
                        }
                        Spacer(Modifier.weight(1f))
                        IconButton({ vm.play(shuffle = true) }) { Icon(Icons.Rounded.Shuffle, stringResource(R.string.mu_shuffle), tint = if (playingHere && s.shuffle) Harbor.Sky else Harbor.TextDim, modifier = Modifier.size(28.dp)) }
                        Spacer(Modifier.width(8.dp))
                        BigPlay(playingHere && s.playing) { if (playingHere) c.musicEngine.toggle() else vm.play() }
                    }
                }
            }
            when {
                vm.loading && vm.songs.isEmpty() -> item { Box(Modifier.fillMaxWidth().padding(48.dp), Alignment.Center) { com.sridhar.harbor.ui.components.JellyLoader() } }
                vm.error != null -> item { MessageState(stringResource(R.string.couldn_t_load), vm.error, onRetry = { vm.load() }) }
                vm.songs.isEmpty() -> item { MessageState(stringResource(R.string.nothing_here), null, icon = Icons.Rounded.MusicNote) }
            }
            itemsIndexed(vm.songs, key = { i, song -> "$i-${song.id}" }) { i, song ->
                Box(Modifier.animateItem().enterRise(i).padding(horizontal = 16.dp)) {
                    SongRow(song, index = i + 1, current = s.current?.id == song.id, playing = s.playing, onClick = { vm.play(i) }, onMore = { actionsFor = song }, showArt = kind != "album")
                }
            }
        }
        // Pinned title bar fades in as the header scrolls away.
        Row(Modifier.fillMaxWidth().background(tint.copy(alpha = collapse)).statusBarsPadding().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(nav.back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back), tint = Harbor.Fg) }
            Text(vm.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).graphicsLayer { alpha = collapse })
        }
    }
    actionsFor?.let { song -> SongActionsSheet(song, onDismiss = { actionsFor = null }, onAlbum = nav.album, onArtist = nav.artist) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    vm.message?.let { msg -> androidx.compose.runtime.LaunchedEffect(msg) { android.widget.Toast.makeText(ctx, msg, android.widget.Toast.LENGTH_SHORT).show(); vm.message = null } }
}

@Composable
fun BigPlay(playing: Boolean, onClick: () -> Unit) {
    val k by animateFloatAsState(if (playing) 1f else 0.95f, label = "bp")
    Box(Modifier.size(56.dp).graphicsLayer { scaleX = k; scaleY = k }.clip(CircleShape).background(Harbor.Violet).clickable(onClick = onClick).testTag("big_play"), Alignment.Center) {
        Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, stringResource(if (playing) R.string.pause else R.string.play), tint = Color.White, modifier = Modifier.size(32.dp))
    }
}

// ============================================================ artist

@Composable
fun ArtistScreen(ids: List<String>, name: String, nav: MusicNav) {
    val c = LocalContainer.current
    val cfg = rememberConfig()
    val vm = viewModel(key = "artist-${ids.joinToString()}") { ArtistViewModel(c, ids, name) }
    val s by c.musicEngine.state.collectAsState()
    val hero = vm.info.largeImageUrl?.takeIf { it.startsWith("http") } ?: c.music.coverUrl(cfg, vm.cover, 900)
    val tint = rememberArtColor(hero, name)
    var actionsFor by remember { mutableStateOf<Song?>(null) }

    LazyColumn(Modifier.fillMaxSize().background(Harbor.Ink), contentPadding = PaddingValues(bottom = 170.dp)) {
        item {
            Box(Modifier.fillMaxWidth().height(320.dp)) {
                CoverArt(hero, name, Modifier.fillMaxSize(), RoundedCornerShape(0.dp))
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Black.copy(.2f), 0.6f to tint.copy(.5f), 1f to Harbor.Ink)))
                IconButton(nav.back, Modifier.statusBarsPadding().padding(4.dp)) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back), tint = Color.White) }
                Text(name, fontSize = 40.sp, fontWeight = FontWeight.Black, lineHeight = 42.sp, modifier = Modifier.align(Alignment.BottomStart).padding(16.dp))
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.mu_albums_count, vm.albums.size), color = Harbor.TextDim, fontSize = 13.sp, modifier = Modifier.weight(1f))
                IconButton({ vm.playTop(shuffle = true) }) { Icon(Icons.Rounded.Shuffle, stringResource(R.string.mu_shuffle), tint = Harbor.TextDim) }
                BigPlay(false) { vm.playTop() }
            }
        }
        if (vm.top.isNotEmpty()) {
            item { Text(stringResource(R.string.mu_popular), fontSize = 21.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp)) }
            itemsIndexed(vm.top.take(5), key = { i, x -> "t$i${x.id}" }) { i, song ->
                Box(Modifier.padding(horizontal = 16.dp)) { SongRow(song, i + 1, s.current?.id == song.id, s.playing, onClick = { vm.playTop(i) }, onMore = { actionsFor = song }) }
            }
        }
        if (vm.albums.isNotEmpty()) item { Shelf(stringResource(R.string.mu_discography)) { items(vm.albums, key = { it.id }) { a -> AlbumCard(a) { nav.album(a.id) } } } }
        if (vm.info.similarArtist.isNotEmpty()) item {
            Shelf(stringResource(R.string.mu_fans_also_like)) {
                items(vm.info.similarArtist, key = { it.id }) { a -> ArtistBubble(ArtistGroup(com.sridhar.harbor.data.music.MusicText.cleanArtist(a.name), listOf(a.id), a.coverArt, 0)) { nav.artist(listOf(a.id), a.name) } }
            }
        }
        if (vm.loading && vm.albums.isEmpty()) item { Box(Modifier.fillMaxWidth().padding(48.dp), Alignment.Center) { com.sridhar.harbor.ui.components.JellyLoader() } }
    }
    actionsFor?.let { song -> SongActionsSheet(song, onDismiss = { actionsFor = null }, onAlbum = nav.album, onArtist = nav.artist) }
}

// ============================================================ search

@Composable
fun MusicSearchScreen(nav: MusicNav) {
    val c = LocalContainer.current
    val vm = viewModel { MusicSearchViewModel(c) }
    val s by c.musicEngine.state.collectAsState()
    var actionsFor by remember { mutableStateOf<Song?>(null) }
    Column(Modifier.fillMaxSize().background(Harbor.Ink).statusBarsPadding()) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(nav.back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back), tint = Harbor.Fg) }
            TextField(vm.query, vm::onQuery, Modifier.weight(1f).testTag("music_search"), placeholder = { Text(stringResource(R.string.mu_search_hint)) }, singleLine = true,
                leadingIcon = { Icon(Icons.Rounded.Search, null) }, shape = RoundedCornerShape(8.dp),
                colors = TextFieldDefaults.colors(focusedContainerColor = Color.White, unfocusedContainerColor = Color.White, focusedTextColor = Color.Black, unfocusedTextColor = Color.Black,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, focusedLeadingIconColor = Color.Black, unfocusedLeadingIconColor = Color.Black, cursorColor = Color.Black))
        }
        val r = vm.result
        if (vm.query.isBlank()) {
            // Spotify's colorful "Browse all" tiles, built from the library's real genres.
            Text(stringResource(R.string.mu_browse_all), fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.padding(16.dp))
            LazyVerticalGrid(GridCells.Adaptive(160.dp), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 170.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(vm.genres, key = { it.value }) { g ->
                    val (a, _) = remember(g.value) { seedColors(g.value + "genre") }
                    Box(Modifier.height(96.dp).clip(RoundedCornerShape(8.dp)).background(a).pressable { vm.playGenre(g) }) {
                        Text(g.value, fontWeight = FontWeight.Black, fontSize = 17.sp, color = Color.White, modifier = Modifier.padding(12.dp))
                        Box(Modifier.align(Alignment.BottomEnd).size(62.dp).graphicsLayer { rotationZ = 25f; translationX = 18f; translationY = 6f }.clip(RoundedCornerShape(4.dp)).background(Brush.linearGradient(listOf(Color.White.copy(.35f), Color.Black.copy(.25f)))))
                    }
                }
            }
        } else LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 170.dp)) {
            if (vm.searching && r == null) item { Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) { com.sridhar.harbor.ui.components.JellyLoader() } }
            if (r != null && r.song.isEmpty() && r.album.isEmpty() && vm.artists.isEmpty()) item { MessageState(stringResource(R.string.no_matches), null, icon = Icons.Rounded.Search) }
            if (vm.artists.isNotEmpty()) item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(vertical = 8.dp)) { items(vm.artists, key = { it.ids.first() }) { a -> ArtistBubble(a) { nav.artist(a.ids, a.name) } } }
            }
            items(r?.song.orEmpty(), key = { "s" + it.id }) { song ->
                SongRow(song, null, s.current?.id == song.id, s.playing, onClick = { c.musicEngine.play(listOf(song) + r!!.song.filter { it.id != song.id }, source = vm.query) }, onMore = { actionsFor = song })
            }
            if (!r?.album.isNullOrEmpty()) item {
                Text(stringResource(R.string.mu_albums), fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(vertical = 12.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) { items(r!!.album, key = { it.id }) { a -> AlbumCard(a, 130.dp) { nav.album(a.id) } } }
            }
        }
    }
    actionsFor?.let { song -> SongActionsSheet(song, onDismiss = { actionsFor = null }, onAlbum = nav.album, onArtist = nav.artist) }
}

// ============================================================ library

@Composable
fun MusicLibraryScreen(nav: MusicNav) {
    val c = LocalContainer.current
    val vm = viewModel { MusicLibraryViewModel(c) }
    var tab by remember { mutableIntStateOf(0) }
    var creating by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(Harbor.Ink).statusBarsPadding()) {
        Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(nav.back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back), tint = Harbor.Fg) }
            Text(stringResource(R.string.mu_your_library), fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton({ creating = true }) { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, stringResource(R.string.mu_create_playlist), tint = Harbor.Fg) }
        }
        Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(R.string.mu_playlists, R.string.mu_albums, R.string.mu_artists).forEachIndexed { i, r ->
                FilterChip(tab == i, { tab = i }, label = { Text(stringResource(r)) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Harbor.Violet, selectedLabelColor = Harbor.Fg, containerColor = Harbor.SurfaceHigh))
            }
        }
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 170.dp)) {
            when (tab) {
                0 -> {
                    item { LibraryRow(null, stringResource(R.string.mu_liked_songs), stringResource(R.string.mu_songs_count, vm.likedCount), liked = true, onClick = nav.liked) }
                    items(vm.playlists, key = { it.id }) { p -> LibraryRow(p.coverArt, p.name, stringResource(R.string.mu_songs_count, p.songCount)) { nav.playlist(p.id) } }
                }
                1 -> items(vm.albums, key = { it.id }) { a -> LibraryRow(a.coverArt, a.displayName, a.displayArtist) { nav.album(a.id) } }
                else -> items(vm.artists, key = { it.ids.first() }) { a -> LibraryRow(a.coverArt, a.name, stringResource(R.string.mu_albums_count, a.albumCount), circle = true) { nav.artist(a.ids, a.name) } }
            }
            if (vm.loading) item { Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) { com.sridhar.harbor.ui.components.JellyLoader() } }
        }
    }
    if (creating) com.sridhar.harbor.ui.admin.TextPromptDialog(stringResource(R.string.mu_create_playlist), stringResource(R.string.name), stringResource(R.string.create), onDismiss = { creating = false }) { vm.createPlaylist(it); creating = false }
}

// ============================================================ sign in

@Composable
fun NavidromeSignIn(onDone: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalContainer.current
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    fun submit() {
        if (busy || url.isBlank() || user.isBlank() || pass.isBlank()) return
        busy = true; error = null
        scope.launch(com.sridhar.harbor.CrashGuard) { runCatching { c.music.signIn(url, user, pass) }.onSuccess { onDone() }.onFailure { error = it.friendly() }; busy = false }
    }
    // On TV, start on the first field so the remote fills the form top-down.
    val first = remember { androidx.compose.ui.focus.FocusRequester() }
    val isTv = (androidx.compose.ui.platform.LocalConfiguration.current.uiMode and android.content.res.Configuration.UI_MODE_TYPE_MASK) == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
    androidx.compose.runtime.LaunchedEffect(isTv) { if (isTv) runCatching { first.requestFocus() } }
    Column(modifier.fillMaxSize().background(Harbor.Ink).statusBarsPadding().wrapContentWidth().widthIn(max = 520.dp).padding(24.dp), verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)).background(Harbor.Violet), Alignment.Center) { Icon(Icons.Rounded.MusicNote, null, tint = Color.White, modifier = Modifier.size(36.dp)) }
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.mu_connect_title), fontSize = 26.sp, fontWeight = FontWeight.Black)
        Text(stringResource(R.string.mu_connect_body), color = Harbor.TextDim)
        Spacer(Modifier.height(20.dp))
        val focus = androidx.compose.ui.platform.LocalFocusManager.current
        val nextField = androidx.compose.foundation.text.KeyboardActions(onNext = { focus.moveFocus(androidx.compose.ui.focus.FocusDirection.Down) })
        OutlinedTextField(url, { url = it }, Modifier.fillMaxWidth().focusRequester(first).dpadFieldNav().testTag("nd_url"), label = { Text(stringResource(R.string.server_url)) }, placeholder = { Text("http://192.168.1.10:4533") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = androidx.compose.ui.text.input.ImeAction.Next), keyboardActions = nextField)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(user, { user = it }, Modifier.fillMaxWidth().dpadFieldNav().testTag("nd_user"), label = { Text(stringResource(R.string.username)) }, singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Next), keyboardActions = nextField)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(pass, { pass = it }, Modifier.fillMaxWidth().dpadFieldNav().testTag("nd_pass"), label = { Text(stringResource(R.string.password)) }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = androidx.compose.ui.text.input.ImeAction.Done),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { focus.clearFocus(); submit() }))
        error?.let { Text(it, color = Harbor.Rose, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp)) }
        Spacer(Modifier.height(16.dp))
        GradientButton(if (busy) stringResource(R.string.signing_in) else stringResource(R.string.connect), { submit() }, Modifier.fillMaxWidth().testTag("nd_connect"), enabled = !busy && url.isNotBlank() && user.isNotBlank() && pass.isNotBlank())
        Text(stringResource(R.string.mu_token_note), color = Harbor.TextDim, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
    }
}


/** "Downloaded · 42 songs" – opens the offline collection (plays with no network). */
@Composable
private fun DownloadedTile(count: Int, onClick: () -> Unit) {
    Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp))
        .background(Brush.horizontalGradient(listOf(Harbor.Mint.copy(alpha = .22f), Harbor.Surface))).clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(Harbor.Mint), Alignment.Center) {
            Icon(Icons.Rounded.DownloadDone, null, tint = Color.White)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.mu_downloaded), fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.mu_downloaded_hint, count), color = Harbor.TextDim, fontSize = 13.sp)
        }
    }
}
