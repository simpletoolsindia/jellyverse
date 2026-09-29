package com.sridhar.harbor.tv

import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import com.sridhar.harbor.L10n
import com.sridhar.harbor.R
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sridhar.harbor.data.jellyfin.BaseItem
import com.sridhar.harbor.ui.components.LocalContainer
import com.sridhar.harbor.ui.components.NetImage
import com.sridhar.harbor.ui.components.formatRuntime
import com.sridhar.harbor.ui.components.rememberConfig
import com.sridhar.harbor.ui.player.PlayerActivity
import com.sridhar.harbor.ui.theme.Harbor
import com.sridhar.harbor.ui.watch.WatchHomeViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.lifecycle.viewModelScope

class TvRowsViewModel(private val c: com.sridhar.harbor.data.AppContainer) : androidx.lifecycle.ViewModel() {
    var rows by androidx.compose.runtime.mutableStateOf<List<Pair<String, List<BaseItem>>>>(emptyList()); private set
    init {
        viewModelScope.launch(com.sridhar.harbor.CrashGuard) {
            val jf = c.jellyfin
            val specs = listOf<Pair<String, suspend () -> List<BaseItem>>>(
                L10n.s(R.string.my_list) to { jf.browse("Movie,Series", "DateCreated", filters = "IsFavorite") },
                L10n.s(R.string.top_rated_movies) to { jf.browse("Movie", "CommunityRating") },
                L10n.s(R.string.action_adventure) to { jf.browse("Movie,Series", "Random", genres = "Action|Adventure", filters = "IsUnplayed") },
                L10n.s(R.string.thrillers) to { jf.browse("Movie,Series", "Random", genres = "Thriller|Crime", filters = "IsUnplayed") },
                L10n.s(R.string.comedy) to { jf.browse("Movie,Series", "Random", genres = "Comedy", filters = "IsUnplayed") },
                L10n.s(R.string.romance_drama) to { jf.browse("Movie,Series", "Random", genres = "Romance|Drama", filters = "IsUnplayed") },
                L10n.s(R.string.collections) to { jf.browse("BoxSet", "SortName", "Ascending") },
            )
            // Load rows in parallel and publish as they arrive (first paint stays fast).
            val result = java.util.concurrent.ConcurrentHashMap<Int, List<BaseItem>>()
            kotlinx.coroutines.coroutineScope {
                specs.forEachIndexed { i, (_, load) ->
                    launch {
                        result[i] = runCatching { load() }.getOrDefault(emptyList()).filterNot(c.parental::hideFromHome)
                        rows = specs.indices.mapNotNull { j -> result[j]?.takeIf { it.isNotEmpty() }?.let { specs[j].first to it } }
                    }
                }
            }
        }
    }
}

@Composable
private fun ClockGreeting(name: String) {
    var now by remember { mutableStateOf(java.util.Calendar.getInstance()) }
    LaunchedEffect(Unit) { while (true) { delay(20_000); now = java.util.Calendar.getInstance() } }
    val h = now.get(java.util.Calendar.HOUR_OF_DAY)
    val greet = when (h) { in 5..11 -> L10n.s(R.string.good_morning); in 12..16 -> L10n.s(R.string.good_afternoon); in 17..21 -> L10n.s(R.string.good_evening); else -> L10n.s(R.string.late_night_movie) } + " " + when (h) { in 5..11 -> "☀️"; in 12..16 -> "🌤️"; in 17..21 -> "🌙"; else -> "🍿" }
    Column(horizontalAlignment = Alignment.End) {
        Text(java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(now.time), color = Harbor.Fg, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text("$greet, ${name.substringBefore('@').replaceFirstChar { it.uppercase() }}", color = Harbor.Fg.copy(.7f), fontSize = 14.sp)
    }
}

@Composable
fun TvHome(onOpen: (String) -> Unit) {
    val container = LocalContainer.current
    val cfg = rememberConfig()
    val ctx = LocalContext.current
    val jf = container.jellyfin
    val vm = viewModel { WatchHomeViewModel(container) }
    val extra = viewModel { TvRowsViewModel(container) }
    var display by remember { mutableStateOf<BaseItem?>(null) }
    var heroIndex by remember { mutableIntStateOf(0) }
    var heroFocused by remember { mutableStateOf(true) }
    // A trailer playing holds the spotlight (Hotstar-style), up to 45 s, then rotation resumes.
    var trailerOn by remember { mutableStateOf(false) }
    val watchFocus = remember { FocusRequester() }
    val look = com.sridhar.harbor.ui.theme.Looks.look
    val billboard = look.home == com.sridhar.harbor.ui.theme.HomeStyle.Billboard
    val recs by container.reco.recs.collectAsState()
    LaunchedEffect(vm.resume) { container.reco.refresh() }

    LaunchedEffect(vm.hero) { if (vm.hero.isNotEmpty() && display == null) { display = vm.hero.first(); runCatching { delay(150); watchFocus.requestFocus() } } }
    // Spotlight auto-rotates while the hero buttons have focus.
    val resumed = com.sridhar.harbor.ui.components.rememberResumed()   // no rotating (and preview churn) under the player
    LaunchedEffect(heroFocused, vm.hero.size, trailerOn, resumed) {
        while (resumed && heroFocused && vm.hero.size > 1) { delay(if (trailerOn) 45_000 else 8000); heroIndex = (heroIndex + 1) % vm.hero.size; display = vm.hero[heroIndex] }
    }

    var removing by remember { mutableStateOf<BaseItem?>(null) }
    removing?.let { r ->
        val focusRemove = remember { androidx.compose.ui.focus.FocusRequester() }
        LaunchedEffect(r) { runCatching { kotlinx.coroutines.delay(100); focusRemove.requestFocus() } }
        androidx.compose.ui.window.Dialog(onDismissRequest = { removing = null }) {
            Column(Modifier.clip(RoundedCornerShape(24.dp)).background(Harbor.Surface).padding(32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(r.seriesName ?: r.name, color = Harbor.Fg, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.resume_remove_hint), color = Harbor.TextDim, fontSize = 16.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    TvButton(stringResource(R.string.resume_remove), Icons.Rounded.Delete, primary = true, modifier = Modifier.focusRequester(focusRemove)) {
                        vm.removeFromResume(r); removing = null
                    }
                    TvButton(stringResource(R.string.cancel), null) { removing = null }
                }
            }
        }
    }
    Box(Modifier.fillMaxSize()) {
        AmbientBackdrop(display?.let { jf.backdropUrl(cfg, it, if (container.lowRam) 1280 else 1920) }, drift = !container.lowRam, preview = display, onTrailer = { trailerOn = it })
        if (vm.loading && vm.hero.isEmpty()) Column(Modifier.fillMaxSize().padding(start = 120.dp, top = 360.dp)) {
            com.sridhar.harbor.ui.components.SkeletonShelf(300.dp, 16f / 9f, 5)
            com.sridhar.harbor.ui.components.SkeletonShelf(150.dp, 2f / 3f, 8)
        }
        Box(Modifier.align(Alignment.TopEnd).padding(top = 28.dp, end = 40.dp)) { ClockGreeting(cfg.jellyfinUser) }
        Column(Modifier.fillMaxSize()) {
            // Info panel – follows the focused title
            // Fixed height so rows below never jump when a slide swaps a logo for a two-line title.
            // Sized by its content (fixed title/overview slots keep it steady between slides), so larger system
            // text or a smaller screen pushes the rows down instead of overlapping them.
            Row(Modifier.fillMaxWidth().padding(start = 48.dp, top = 28.dp, end = 40.dp), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f).padding(end = 24.dp)) {
                AnimatedContent(display, transitionSpec = { (fadeIn(tween(350)) + slideInVertically(tween(350)) { it / 12 }) togetherWith fadeOut(tween(150)) }, label = "info") { item ->
                    if (item != null) Column {
                        val logo = jf.logoUrl(cfg, item)
                        Box(Modifier.height(96.dp).fillMaxWidth(), contentAlignment = Alignment.BottomStart) {
                            if (logo != null) Box(Modifier.width(340.dp).fillMaxHeight()) { NetImage(logo, Modifier.fillMaxSize(), contentScale = ContentScale.Fit, fallback = item.name, alignment = Alignment.BottomStart) }
                            else Text(item.seriesName ?: item.name, color = Harbor.Fg, fontSize = 36.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 40.sp)
                        }
                        Spacer(Modifier.height(12.dp))
                        MetaLine(listOf(item.year?.toString(), item.communityRating?.let { "★ %.1f".format(it) }, formatRuntime(item.runtimeMinutes),
                            item.genres.firstOrNull(), item.episodeLabel))
                        Spacer(Modifier.height(12.dp))
                        Text(item.overview.orEmpty(), color = Harbor.Fg.copy(.8f), fontSize = 15.sp, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 22.sp)
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.onFocusChanged { heroFocused = it.hasFocus }, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    TvButton(stringResource(R.string.watch), Icons.Rounded.PlayArrow, primary = true, modifier = Modifier.focusRequester(watchFocus)) {
                        display?.let { d -> if (d.type == "Series") onOpen(d.id) else PlayerActivity.start(ctx, d.id) }
                    }
                    TvButton(stringResource(R.string.details), Icons.Rounded.Info) { display?.let { onOpen(it.seriesId ?: it.id) } }
                }
            }
            // Billboard: the backdrop and title own the top; Spotlight adds the poster strip beside them.
            if (vm.hero.size > 1 && !billboard) TvMarquee(vm.hero, heroIndex, rotating = heroFocused && !trailerOn,
                onFocus = { i -> heroIndex = i; display = vm.hero[i] }, onOpen = { onOpen(it.seriesId ?: it.id) })
            }
            LazyColumn(Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(top = 18.dp, bottom = 48.dp)) {
                if (look.shows(com.sridhar.harbor.ui.theme.HomeSection.Continue)) item(key = "resume") {
                    TvRow(stringResource(R.string.continue_watching_2), vm.resume, key = { it.id }) { it2 ->
                        LandscapeTile(it2.seriesName ?: it2.name, it2.episodeLabel ?: it2.year?.toString(), jf.thumbUrl(cfg, it2, 600), progress = it2.progress,
                            onFocus = { display = it2 }, onMenu = { removing = it2 }) { PlayerActivity.start(ctx, it2.id) }
                    }
                }
                if (look.shows(com.sridhar.harbor.ui.theme.HomeSection.ForYou) && recs.forYou.isNotEmpty()) item(key = "foryou") {
                    TvRow(stringResource(R.string.reco_for_you), recs.forYou, key = { it.item.id }) { p ->
                        PosterTile(p.item.name, jf.posterUrl(cfg, p.item, 300), onFocus = { display = p.item }) { onOpen(p.item.id) }
                    }
                }
                if (look.shows(com.sridhar.harbor.ui.theme.HomeSection.Top10) && vm.top10.isNotEmpty()) item(key = "top10") {
                    TvTop10Row(vm.top10, onFocus = { display = it }) { onOpen(it.seriesId ?: it.id) }
                }
                if (look.shows(com.sridhar.harbor.ui.theme.HomeSection.NextUp)) item(key = "next") {
                    TvRow(stringResource(R.string.next_up_2), vm.nextUp, key = { it.id }) { it2 ->
                        LandscapeTile(it2.seriesName ?: it2.name, listOfNotNull(it2.episodeLabel, it2.name).joinToString(" · "), jf.thumbUrl(cfg, it2, 600),
                            onFocus = { display = it2 }) { PlayerActivity.start(ctx, it2.id) }
                    }
                }
                if (vm.picks.size >= 6) item(key = "picks") {
                    TvGlideRow(stringResource(R.string.marquee_discover), vm.picks, onFocus = { display = it }) { onOpen(it.seriesId ?: it.id) }
                }
                if (look.shows(com.sridhar.harbor.ui.theme.HomeSection.ForYou)) recs.because.forEach { row ->
                    item(key = "because-${row.seed.id}") {
                        TvRow(stringResource(R.string.reco_because, row.seed.name), row.picks, key = { it.item.id }) { p ->
                            PosterTile(p.item.name, jf.posterUrl(cfg, p.item, 300), onFocus = { display = p.item }) { onOpen(p.item.id) }
                        }
                    }
                }
                if (look.shows(com.sridhar.harbor.ui.theme.HomeSection.Latest)) vm.shelves.forEach { shelf ->
                    item(key = "shelf-${shelf.view.id}") {
                        TvRow(stringResource(R.string.latest_1_s, shelf.view.name), shelf.items, key = { it.id }) { it2 ->
                            PosterTile(it2.seriesName ?: it2.name, jf.posterUrl(cfg, it2, 300),
                                badge = it2.userData?.unplayedCount?.takeIf { n -> n > 0 }?.toString(), onFocus = { display = it2 }) { onOpen(it2.seriesId ?: it2.id) }
                        }
                    }
                }
                extra.rows.forEach { (title, items) ->
                    item(key = "row-$title") {
                        TvRow(title, items, key = { it.id }) { it2 ->
                            PosterTile(it2.name, jf.posterUrl(cfg, it2, 300), progress = it2.progress,
                                onFocus = { display = it2 }) { onOpen(it2.id) }
                        }
                    }
                }
                item(key = "spot") {
                    TvRow(stringResource(R.string.spotlight), vm.hero, key = { it.id }) { it2 ->
                        PosterTile(it2.name, jf.posterUrl(cfg, it2, 300), onFocus = { display = it2 }) { onOpen(it2.id) }
                    }
                }
            }
        }
    }
}

/**
 * Spotlight marquee: a strip of posters beside the hero text. The active pick is lifted and outlined, with a
 * thin line that fills until the next slide; the strip glides so the active poster stays in view. D-pad onto it
 * to browse (rotation pauses), OK opens.
 */
@Composable
private fun TvMarquee(items: List<BaseItem>, active: Int, rotating: Boolean, onFocus: (Int) -> Unit, onOpen: (BaseItem) -> Unit) {
    val cfg = rememberConfig()
    val jf = LocalContainer.current.jellyfin
    val list = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(active) { runCatching { list.animateScrollToItem((active - 1).coerceAtLeast(0)) } }
    // Restarts for every slide; only drawn while the spotlight is auto-rotating.
    val progress = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(active, rotating) {
        progress.snapTo(0f)
        if (rotating) progress.animateTo(1f, tween(8000, easing = androidx.compose.animation.core.LinearEasing))
    }
    Column(Modifier.width(470.dp)) {
        Text(stringResource(R.string.spotlight).uppercase(), color = Harbor.Fg.copy(.6f), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        Spacer(Modifier.height(10.dp))
        androidx.compose.foundation.lazy.LazyRow(state = list, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom,
            contentPadding = PaddingValues(end = 24.dp)) {
            items(items.size, key = { items[it].id }) { i ->
                val item = items[i]
                val on = i == active
                var focused by remember { mutableStateOf(false) }
                val lift by animateFloatAsState(if (on || focused) 1f else 0f, spring(dampingRatio = .7f, stiffness = Spring.StiffnessMediumLow), label = "lift")
                Column(Modifier.width(78.dp + 34.dp * lift)) {
                    Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f)
                        .graphicsLayer { alpha = 0.5f + 0.5f * lift; shadowElevation = 24f * lift; shape = RoundedCornerShape(12.dp); clip = true }
                        .border(if (focused) 3.dp else 2.dp, if (focused) Color.White else Color.White.copy(alpha = .8f * lift), RoundedCornerShape(12.dp))
                        .onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocus(i) }
                        .clickable { onOpen(item) }) {
                        NetImage(jf.posterUrl(cfg, item, 300), Modifier.fillMaxSize(), fallback = item.name)
                    }
                    Spacer(Modifier.height(6.dp))
                    Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = if (on && rotating) .2f else 0f))) {
                        if (on && rotating) Box(Modifier.fillMaxHeight().fillMaxWidth(progress.value).background(Harbor.Sky))
                    }
                }
            }
        }
    }
}


/** Numbered Top 10: big outlined rank beside each poster; focus lifts the poster. */
@Composable
private fun TvTop10Row(items: List<BaseItem>, onFocus: (BaseItem) -> Unit, onOpen: (BaseItem) -> Unit) {
    val cfg = rememberConfig()
    val jf = LocalContainer.current.jellyfin
    Column(Modifier.padding(bottom = 22.dp)) {
        Text(stringResource(R.string.top10_title), color = Harbor.Fg, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 48.dp, bottom = 12.dp))
        androidx.compose.foundation.lazy.LazyRow(contentPadding = PaddingValues(horizontal = 40.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(items.size, key = { items[it].id }) { i ->
                val item = items[i]
                Box(Modifier.width(if (i == 9) 250.dp else 220.dp).height(210.dp)) {
                    Text("${i + 1}", modifier = Modifier.align(Alignment.BottomStart).offset(y = 30.dp),
                        style = androidx.compose.ui.text.TextStyle(fontSize = 170.sp, fontWeight = FontWeight.Black, letterSpacing = (-14).sp,
                            color = Harbor.Fg.copy(alpha = .85f), drawStyle = androidx.compose.ui.graphics.drawscope.Stroke(width = 6f)))
                    Box(Modifier.align(Alignment.BottomEnd)) {
                        PosterTile(item.name, jf.posterUrl(cfg, item, 300), width = 130.dp, onFocus = { onFocus(item) }) { onOpen(item) }
                    }
                }
            }
        }
    }
}

/** Poster marquee that glides by itself until the remote moves into it, then behaves like a normal row. */
@Composable
private fun TvGlideRow(title: String, items: List<BaseItem>, onFocus: (BaseItem) -> Unit, onOpen: (BaseItem) -> Unit) {
    val cfg = rememberConfig()
    val jf = LocalContainer.current.jellyfin
    val state = androidx.compose.foundation.lazy.rememberLazyListState(initialFirstVisibleItemIndex = items.size * 40)
    var inside by remember { mutableStateOf(false) }
    val resumed = com.sridhar.harbor.ui.components.rememberResumed()
    LaunchedEffect(inside, resumed) {
        if (inside || !resumed) return@LaunchedEffect
        delay(1500)
        while (true) state.animateScrollBy(200f, tween(4000, easing = androidx.compose.animation.core.LinearEasing))
    }
    Column(Modifier.padding(bottom = 22.dp)) {
        Text(title, color = Harbor.Fg, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 48.dp, bottom = 12.dp))
        androidx.compose.foundation.lazy.LazyRow(Modifier.onFocusChanged { inside = it.hasFocus }, state = state,
            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            items(items.size * 80) { i ->
                val item = items[i % items.size]
                PosterTile(item.name, jf.posterUrl(cfg, item, 300), width = 140.dp, onFocus = { onFocus(item) }) { onOpen(item) }
            }
        }
    }
}
