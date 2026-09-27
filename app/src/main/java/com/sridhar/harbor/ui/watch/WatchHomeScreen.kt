package com.sridhar.harbor.ui.watch

import com.sridhar.harbor.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.key
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloat
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.SettingsRemote
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sridhar.harbor.data.AppContainer
import com.sridhar.harbor.data.jellyfin.BaseItem
import com.sridhar.harbor.ui.components.LocalContainer
import com.sridhar.harbor.ui.components.MessageState
import com.sridhar.harbor.ui.components.NetImage
import com.sridhar.harbor.ui.components.PosterCard
import com.sridhar.harbor.ui.components.Rail
import com.sridhar.harbor.ui.components.WideCard
import com.sridhar.harbor.ui.components.friendly
import com.sridhar.harbor.ui.components.glass
import com.sridhar.harbor.ui.components.rememberConfig
import com.sridhar.harbor.ui.player.PlayerActivity
import com.sridhar.harbor.ui.theme.Harbor
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

data class LatestShelf(val view: BaseItem, val items: List<BaseItem>)

class WatchHomeViewModel(private val c: AppContainer) : ViewModel() {
    var loading by mutableStateOf(true); private set
    var refreshing by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var hero by mutableStateOf<List<BaseItem>>(emptyList()); private set
    var resume by mutableStateOf<List<BaseItem>>(emptyList()); private set
    var nextUp by mutableStateOf<List<BaseItem>>(emptyList()); private set
    var views by mutableStateOf<List<BaseItem>>(emptyList()); private set
    var shelves by mutableStateOf<List<LatestShelf>>(emptyList()); private set
    var userName by mutableStateOf(""); private set

    init { load() }

    fun load(pull: Boolean = false) = viewModelScope.launch(com.sridhar.harbor.CrashGuard) {
        if (pull) refreshing = true
        error = null
        runCatching {
            coroutineScope {
                val h = async { runCatching { c.jellyfin.heroItems() }.getOrDefault(emptyList()) }
                val r = async { c.jellyfin.resume() }
                val n = async { runCatching { c.jellyfin.nextUp() }.getOrDefault(emptyList()) }
                val v = c.jellyfin.views()
                val latest = v.filter { it.collectionType in setOf("movies", "tvshows", "homevideos", "mixed", null) }
                    .map { view -> async { LatestShelf(view, runCatching { c.jellyfin.latest(view.id) }.getOrDefault(emptyList())) } }
                views = v
                // Parental control: 18+ and locked titles never appear on Home (they live in the 18+ menu).
                val pc = c.parental
                if (pc.state.value.enabled && pc.adultSeries.isEmpty()) pc.refreshAdultSeries { c.jellyfin.ratedTitles() }
                val keep: (BaseItem) -> Boolean = { !pc.hideFromHome(it) }
                hero = h.await().filter(keep); resume = r.await().filter(keep); nextUp = n.await().filter(keep)
                shelves = latest.awaitAll().map { it.copy(items = it.items.filter(keep)) }.filter { it.items.isNotEmpty() }
            }
            userName = c.settings.current().jellyfinUser
        }.onFailure { error = it.friendly() }
        loading = false; refreshing = false
    }
}

@Composable
fun WatchHomeScreen(
    onLive: () -> Unit,
    onItem: (String) -> Unit,
    onLibrary: (String, String, String?) -> Unit,
    onProtected: () -> Unit = {},
    onRemote: () -> Unit = {},
    onSearch: () -> Unit,
    onSetup: () -> Unit,
) {
    val container = LocalContainer.current
    val cfg = rememberConfig()
    if (!cfg.jellyfinReady) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                MessageState(stringResource(R.string.jellyfin_isn_t_connected), stringResource(R.string.add_your_server_to_start_watching), onRetry = onSetup, actionLabel = stringResource(R.string.set_up))
                androidx.compose.material3.TextButton(onLive) { Text(stringResource(R.string.open_live_tv), color = Harbor.Coral) }
            }
        }
        return
    }
    val vm = viewModel { WatchHomeViewModel(container) }
    val ctx = LocalContext.current
    val jf = container.jellyfin
    val play: (BaseItem) -> Unit = { PlayerActivity.start(ctx, it.id) }

    val downloads = rememberFinishedDownloads()
    val parental by container.parental.state.collectAsState()
    // Protection switched on/off or a title (un)locked → Home re-filters.
    val firstParental = remember { parental }
    androidx.compose.runtime.LaunchedEffect(parental) { if (parental != firstParental) vm.load() }
    val online by com.sridhar.harbor.net.NetworkMonitor.online.collectAsState()
    // Coming back online: refresh whatever failed while offline.
    androidx.compose.runtime.LaunchedEffect(online) { if (online && vm.error != null) vm.load() }
    PullToRefreshBox(vm.refreshing, onRefresh = { vm.load(pull = true) }, modifier = Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 120.dp + com.sridhar.harbor.ui.components.LocalMiniPlayerInset.current)) {
            item(key = "hero") {
                if (vm.hero.isNotEmpty()) HeroPager(vm.hero, onItem, play)
                else Spacer(Modifier.statusBarsPadding().height(72.dp))
            }
            // No network (or server unreachable): lead with what's on the device instead of an error.
            val unreachable = !online || (vm.error != null && vm.resume.isEmpty() && vm.hero.isEmpty())
            if (unreachable && downloads.isNotEmpty()) item(key = "offline") { DownloadsShelf(downloads, offline = true) }
            else if (vm.error != null && vm.resume.isEmpty()) item {
                MessageState(stringResource(R.string.couldn_t_reach_jellyfin), vm.error, onRetry = { vm.load() })
            }
            item(key = "libs") {
                if (vm.views.isNotEmpty()) LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item(key = "live") {
                        Row(Modifier.glass(RoundedCornerShape(14.dp)).clickable(onClick = onLive).padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.LiveTv, null, tint = Harbor.Coral, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.live_tv), fontWeight = FontWeight.SemiBold)
                        }
                    }
                    items(vm.views.filter { it.collectionType in setOf("movies", "tvshows", "boxsets", "homevideos", null) }, key = { it.id }) { v ->
                        LibraryChip(v) { onLibrary(v.id, v.name, v.collectionType) }
                    }
                    // Parental control on: 18+ and locked titles live here, behind the PIN.
                    if (parental.enabled && parental.showMenu) item(key = "adult") {
                        Row(Modifier.glass(RoundedCornerShape(14.dp)).clickable(onClick = onProtected).padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text("🔞", fontSize = 16.sp); Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.adult_menu), fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.width(6.dp)); Icon(Icons.Rounded.Lock, null, tint = Harbor.Amber, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
            item(key = "resume") {
                Rail(stringResource(R.string.continue_watching), vm.resume, key = { it.id }) { item ->
                    WideCard(
                        jf.thumbUrl(cfg, item), item.seriesName ?: item.name,
                        listOfNotNull(item.episodeLabel, if (item.seriesName != null) item.name else null).joinToString(" · ").ifBlank { null },
                        item.progress,
                    ) { play(item) }
                }
            }
            if (!unreachable && downloads.isNotEmpty()) item(key = "device") { DownloadsShelf(downloads, offline = false) }
            item(key = "nextup") {
                Rail(stringResource(R.string.next_up), vm.nextUp, key = { it.id }) { item ->
                    WideCard(jf.thumbUrl(cfg, item), item.seriesName ?: item.name,
                        listOfNotNull(item.episodeLabel, item.name).joinToString(" · "), 0f, width = 220.dp) { onItem(item.id) }
                }
            }
            vm.shelves.forEach { shelf ->
                item(key = "shelf-${shelf.view.id}") {
                    Rail(stringResource(R.string.new_in_1_s, shelf.view.name), shelf.items, key = { it.id }, action = stringResource(R.string.see_all),
                        onAction = { onLibrary(shelf.view.id, shelf.view.name, shelf.view.collectionType) }) { item ->
                        PosterCard(
                            jf.posterUrl(cfg, item), item.seriesName ?: item.name,
                            item.year?.toString(), played = item.userData?.played == true,
                            badge = item.userData?.unplayedCount?.takeIf { it > 0 }?.let { n -> { CountBadge(n) } },
                        ) { onItem(item.id) }
                    }
                }
            }
        }
        // Floating top bar
        Row(
            Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Harbor.Ink.copy(alpha = .85f), Color.Transparent)))
                .statusBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            com.sridhar.harbor.ui.components.HarborLogo(34.dp)
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.jellyverse), style = MaterialTheme.typography.headlineSmall.copy(brush = Harbor.accentH), fontWeight = FontWeight.Black)
            Spacer(Modifier.weight(1f))
            IconButton(onRemote, Modifier.glass(RoundedCornerShape(50))) { Icon(Icons.Rounded.SettingsRemote, stringResource(R.string.remote_title), tint = Color.White) }
            Spacer(Modifier.width(8.dp))
            IconButton(onSearch, Modifier.glass(RoundedCornerShape(50))) { Icon(Icons.Rounded.Search, stringResource(R.string.search), tint = Color.White) }
        }
    }
}

@Composable
fun CountBadge(n: Int) {
    Box(Modifier.clip(RoundedCornerShape(50)).background(Harbor.accentH).padding(horizontal = 7.dp, vertical = 2.dp)) {
        Text("$n", style = MaterialTheme.typography.labelSmall, color = Color.White)
    }
}

@Composable
private fun LibraryChip(v: BaseItem, onClick: () -> Unit) {
    val icon = when (v.collectionType) { "movies" -> Icons.Rounded.Movie; "tvshows" -> Icons.Rounded.Tv; else -> Icons.Rounded.VideoLibrary }
    Row(
        Modifier.glass(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Harbor.VioletSoft, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(v.name, fontWeight = FontWeight.SemiBold)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HeroPager(items: List<BaseItem>, onItem: (String) -> Unit, onPlay: (BaseItem) -> Unit) {
    val cfg = rememberConfig()
    val container = LocalContainer.current
    val jf = container.jellyfin
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val pager = rememberPagerState { items.size }
    val favs = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateMapOf<String, Boolean>() }
    var trailerOn by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    LaunchedEffect(pager, items.size, trailerOn) {
        while (true) {
            delay(if (trailerOn) 45_000 else 6000)
            if (!pager.isScrollInProgress) pager.animateScrollToPage((pager.currentPage + 1) % items.size)
        }
    }
    if (com.sridhar.harbor.ui.components.widthClass() != com.sridhar.harbor.ui.components.WidthClass.Compact) {
        WideHero(items, pager, favs, onItem, onPlay, onTrailer = { trailerOn = it }); return
    }
    Column(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 64.dp)) {
        // Hotstar-style spotlight: centred card, neighbours peek and shrink.
        HorizontalPager(pager, Modifier.fillMaxWidth().height(470.dp), contentPadding = PaddingValues(horizontal = 36.dp), pageSpacing = 12.dp) { page ->
            val item = items[page]
            val offset = ((pager.currentPage - page) + pager.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)
            Box(
                Modifier.fillMaxSize().graphicsLayer {
                    val s = 1f - 0.08f * offset; scaleX = s; scaleY = s; alpha = 1f - 0.35f * offset
                }.clip(RoundedCornerShape(22.dp)).clickable { onItem(item.id) },
            ) {
                NetImage(jf.posterUrl(cfg, item, 900), Modifier.fillMaxSize(), fallback = item.name)
                // The settled spotlight card plays its trailer (muted), like Hotstar.
                if (page == pager.currentPage && !pager.isScrollInProgress) com.sridhar.harbor.ui.components.TrailerPreview(item, Modifier.fillMaxSize(), delayMs = 1800, onPlaying = { trailerOn = it })
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Transparent, 0.5f to Color.Transparent, 1f to Color.Black.copy(alpha = .92f))))
                Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    val logo = jf.logoUrl(cfg, item)
                    if (logo != null) NetImage(logo, Modifier.width(220.dp).height(70.dp), contentScale = ContentScale.Fit, fallback = item.name)
                    else Text(item.name, style = MaterialTheme.typography.headlineMedium, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Spacer(Modifier.height(6.dp))
                    Text(listOfNotNull(item.year?.toString(), item.genres.take(2).joinToString(" • ").ifBlank { null }, item.officialRating,
                        item.communityRating?.let { "★ %.1f".format(it) }).joinToString("  ·  "), color = Color.White.copy(.8f), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        val current = items.getOrNull(pager.currentPage)
        Row(Modifier.fillMaxWidth().padding(horizontal = 36.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f).height(50.dp).clip(RoundedCornerShape(12.dp)).background(Harbor.accentH)
                    .clickable { current?.let { if (it.type == "Movie") onPlay(it) else onItem(it.id) } },
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.PlayArrow, null, tint = Color.White); Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.watch_now), color = Color.White, fontWeight = FontWeight.Bold)
            }
            val fav = current?.let { favs[it.id] ?: (it.userData?.isFavorite == true) } == true
            Box(Modifier.size(50.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(.14f)).clickable {
                current?.let { c -> favs[c.id] = !fav; scope.launch(com.sridhar.harbor.CrashGuard) { runCatching { jf.setFavorite(c.id, !fav) } } }
            }, contentAlignment = Alignment.Center) {
                Icon(if (fav) Icons.Rounded.Check else Icons.Rounded.Add, stringResource(R.string.watchlist), tint = Color.White)
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.Center) {
            repeat(items.size) { i ->
                val active = i == pager.currentPage
                Box(Modifier.padding(horizontal = 3.dp).height(5.dp).width(if (active) 18.dp else 5.dp).clip(CircleShape)
                    .background(if (active) Color.White else Color.White.copy(alpha = .3f)))
            }
        }
    }
}

/**
 * Tablet / landscape spotlight: a cinematic backdrop that slowly drifts (Ken Burns), the title block on the left
 * and a rail of the other picks on the right, so a 16:10 screen isn't one giant stretched poster.
 */
@Composable
private fun WideHero(
    items: List<BaseItem>, pager: androidx.compose.foundation.pager.PagerState, favs: MutableMap<String, Boolean>,
    onItem: (String) -> Unit, onPlay: (BaseItem) -> Unit, onTrailer: (Boolean) -> Unit = {},
) {
    val cfg = rememberConfig()
    val jf = LocalContainer.current.jellyfin
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val screenH = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp
    val drift = androidx.compose.animation.core.rememberInfiniteTransition(label = "kb").animateFloat(
        1.04f, 1.12f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(14_000), androidx.compose.animation.core.RepeatMode.Reverse), label = "z")
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth().height(if (screenH < 500.dp) screenH * 0.86f else (screenH * 0.62f).coerceIn(300.dp, 560.dp)).clipToBounds()) {
        // Short (landscape phone) heroes get a tighter title block; the poster rail only shows what fits beside the text.
        val short = screenH < 500.dp
        val fit = (((maxWidth * 0.46f) - 24.dp) / 76.dp).toInt().coerceIn(3, items.size)
        val first = (pager.currentPage - fit / 2).coerceIn(0, (items.size - fit).coerceAtLeast(0))
        HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
            val item = items[page]
            Box(Modifier.fillMaxSize().clickable { onItem(item.id) }) {
                NetImage(jf.backdropUrl(cfg, item, 1920), Modifier.fillMaxSize().graphicsLayer { scaleX = drift.value; scaleY = drift.value }, fallback = item.name)
                if (page == pager.currentPage && !pager.isScrollInProgress) com.sridhar.harbor.ui.components.TrailerPreview(item, Modifier.fillMaxSize(), delayMs = 1800, onPlaying = onTrailer)
            }
        }
        // Scrims: left for the text, bottom to melt into the rows below.
        Box(Modifier.matchParentSize().background(Brush.horizontalGradient(0f to Harbor.Ink.copy(alpha = .95f), 0.45f to Harbor.Ink.copy(alpha = .55f), 0.75f to Color.Transparent)))
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to Harbor.Ink)))
        val current = items.getOrNull(pager.currentPage) ?: return@BoxWithConstraints
        androidx.compose.animation.AnimatedContent(current, Modifier.align(Alignment.BottomStart).fillMaxWidth(0.5f).padding(start = 32.dp, bottom = 28.dp),
            contentKey = { it.id }, label = "heroText",
            transitionSpec = {
                (androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(420, 120)) + androidx.compose.animation.slideInVertically { it / 6 }) togetherWith
                    androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(160))
            }) { item ->
            Column {
                val logo = jf.logoUrl(cfg, item)
                if (logo != null) NetImage(logo, Modifier.width(if (short) 220.dp else 300.dp).height(if (short) 60.dp else 96.dp), contentScale = ContentScale.Fit, alignment = Alignment.CenterStart, fallback = item.name)
                else Text(item.name, style = if (short) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Black, maxLines = if (short) 1 else 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(if (short) 4.dp else 10.dp))
                Text(listOfNotNull(item.year?.toString(), item.genres.take(3).joinToString(" • ").ifBlank { null }, item.officialRating,
                    item.communityRating?.let { "★ %.1f".format(it) }).joinToString("  ·  "), color = Color.White.copy(.85f), style = MaterialTheme.typography.bodyMedium)
                item.overview?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = Harbor.TextDim, style = MaterialTheme.typography.bodyMedium, maxLines = if (short) 2 else 3, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.height(48.dp).clip(RoundedCornerShape(12.dp)).background(Harbor.accentH)
                        .clickable { if (item.type == "Movie") onPlay(item) else onItem(item.id) }.padding(horizontal = 28.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.PlayArrow, null, tint = Color.White); Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.watch_now), color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    val fav = favs[item.id] ?: (item.userData?.isFavorite == true)
                    Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(.14f)).clickable {
                        favs[item.id] = !fav; scope.launch(com.sridhar.harbor.CrashGuard) { runCatching { jf.setFavorite(item.id, !fav) } }
                    }, contentAlignment = Alignment.Center) {
                        Icon(if (fav) Icons.Rounded.Check else Icons.Rounded.Add, stringResource(R.string.watchlist), tint = Color.White)
                    }
                }
            }
        }
        // Poster rail: the active pick is lifted and outlined; tap any to jump there.
        Row(Modifier.align(Alignment.BottomEnd).padding(end = 24.dp, bottom = 28.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
            items.subList(first, (first + fit).coerceAtMost(items.size)).forEachIndexed { k, item ->
                val i = first + k
                val active = i == pager.currentPage
                val lift by androidx.compose.animation.core.animateFloatAsState(if (active) 1f else 0f, androidx.compose.animation.core.spring(dampingRatio = .7f), label = "lift")
                key(item.id) { Box(Modifier.width((if (short) 52.dp else 64.dp) + 20.dp * lift).aspectRatio(2f / 3f).graphicsLayer { alpha = 0.55f + 0.45f * lift }
                    .clip(RoundedCornerShape(10.dp))
                    .then(if (active) Modifier.border(2.dp, Color.White, RoundedCornerShape(10.dp)) else Modifier)
                    .clickable { scope.launch(com.sridhar.harbor.CrashGuard) { pager.animateScrollToPage(i) } }) {
                    NetImage(jf.posterUrl(cfg, item, 300), Modifier.fillMaxSize(), fallback = item.name)
                } }
            }
        }
    }
}
