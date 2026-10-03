package com.sridhar.harbor.tv

import com.sridhar.harbor.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.focus.onFocusChanged
import kotlinx.coroutines.launch
import com.sridhar.harbor.ui.components.enterRise
import androidx.compose.runtime.collectAsState
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import com.sridhar.harbor.ui.watch.ItemDetailViewModel

@Composable
fun TvDetail(id: String, onOpen: (String) -> Unit) {
    val container = LocalContainer.current
    val cfg = rememberConfig()
    val ctx = LocalContext.current
    val jf = container.jellyfin
    val vm = viewModel(key = "tvitem-$id") { ItemDetailViewModel(container, id) }
    // Parental lock: changing it needs the PIN (or creating one first).
    val parentalState by container.parental.state.collectAsState()
    var lockTarget by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var lockStep by remember { mutableStateOf(0) }
    val wrongPin = stringResource(R.string.pin_wrong)
    LaunchedEffect(lockStep) { if (lockStep == 3) { lockTarget?.let { (i, l) -> container.parental.setLocked(i, l) }; lockStep = 0; lockTarget = null } }
    when (lockStep) {
        1 -> com.sridhar.harbor.ui.components.CreatePinDialog(onDismiss = { lockStep = 0 }) { pin -> container.parental.setPin(pin); lockStep = 3 }
        2 -> com.sridhar.harbor.ui.components.PinDialog(stringResource(R.string.pin_enter), null, onDismiss = { lockStep = 0 }) { if (container.parental.verify(it)) { lockStep = 3; null } else wrongPin }
    }
    val item = vm.item ?: return Box(Modifier.fillMaxSize(), Alignment.Center) { com.sridhar.harbor.ui.components.JellyLoader(color = Harbor.VioletSoft) }
    val playFocus = remember { FocusRequester() }
    var focusedEp by remember { mutableStateOf<BaseItem?>(null) }
    LaunchedEffect(item.id) { runCatching { playFocus.requestFocus() } }

    Box(Modifier.fillMaxSize()) {
        AmbientBackdrop(jf.backdropUrl(cfg, focusedEp ?: item, if (container.lowRam) 1280 else 1920), preview = if (focusedEp == null) item else null)
        val list = androidx.compose.foundation.lazy.rememberLazyListState()
        val scope = androidx.compose.runtime.rememberCoroutineScope()
        LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(top = 64.dp, bottom = 48.dp)) {
            item {
                Column(Modifier.padding(start = 64.dp, end = 140.dp).enterRise(0)) {
                    val logo = jf.logoUrl(cfg, item)
                    if (logo != null) NetImage(logo, Modifier.width(420.dp).height(140.dp), contentScale = ContentScale.Fit, fallback = item.name)
                    else Text(item.name, color = Color.White, fontSize = 52.sp, fontWeight = FontWeight.Black, lineHeight = 56.sp)
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RatingBadge(item.officialRating); if (!item.officialRating.isNullOrBlank()) Spacer(Modifier.width(12.dp))
                        MetaLine(listOf(item.year?.toString(), item.communityRating?.let { "★ %.1f".format(it) }, formatRuntime(item.runtimeMinutes),
                            item.genres.take(3).joinToString(" · ").ifBlank { null }))
                    }
                    Spacer(Modifier.height(14.dp))
                    Text((focusedEp ?: item).overview.orEmpty(), color = Harbor.Fg.copy(.82f), fontSize = 17.sp, maxLines = 3, modifier = Modifier.width(560.dp), overflow = TextOverflow.Ellipsis, lineHeight = 24.sp)
                    Spacer(Modifier.height(24.dp))
                    val target = if (item.type == "Series") vm.nextUp ?: vm.episodes.firstOrNull() else item
                    val resume = (target?.userData?.positionTicks ?: 0) > 0
                    // Scrolls sideways on narrower TVs so every action (lock, watched…) stays reachable; focus pulls it along.
                    // Back on the buttons (e.g. up from Cast): bring the title and overview back into view too.
                    Row(Modifier.onFocusChanged { if (it.hasFocus && list.firstVisibleItemIndex + list.firstVisibleItemScrollOffset > 0) scope.launch {
                        kotlinx.coroutines.delay(220)   // after focus's own bring-into-view scroll, which would cancel ours
                        list.animateScrollToItem(0) } }
                        .horizontalScroll(rememberScrollState()).padding(vertical = 6.dp, horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        TvButton(when { item.type == "Series" && target != null -> "Play ${target.episodeLabel ?: ""}".trim(); resume -> stringResource(R.string.resume); else -> stringResource(R.string.play) },
                            Icons.Rounded.PlayArrow, primary = true, modifier = Modifier.focusRequester(playFocus)) { target?.let { PlayerActivity.start(ctx, it.id) } }
                        if (resume && item.type != "Series") TvButton(stringResource(R.string.start_over), Icons.Rounded.Replay) { PlayerActivity.start(ctx, item.id, fromStart = true) }
                        val fav = item.userData?.isFavorite == true
                        TvButton(if (fav) stringResource(R.string.favourite) else stringResource(R.string.add_to_list), if (fav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder) { vm.toggleFavorite() }
                        val played = item.userData?.played == true
                        TvButton(if (played) stringResource(R.string.watched_2) else stringResource(R.string.mark_watched), if (played) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked) { vm.togglePlayed() }
                        val lockedNow = parentalState.locked.contains(item.id)
                        TvButton(stringResource(if (lockedNow) R.string.unlock_title else R.string.lock_title), if (lockedNow) Icons.Rounded.Lock else Icons.Rounded.LockOpen) {
                            lockTarget = item.id to !lockedNow
                            lockStep = when { !parentalState.enabled -> 1; !container.parental.isUnlocked() -> 2; else -> 3 }
                        }
                    }
                }
                Spacer(Modifier.height(36.dp))
            }
            if (item.type == "Series" && vm.seasons.isNotEmpty()) {
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 64.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        itemsIndexed(vm.seasons) { i, s -> Chip(s.name, i == vm.selectedSeason) { vm.selectSeason(i) } }
                    }
                    Spacer(Modifier.height(16.dp))
                }
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 64.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        itemsIndexed(vm.episodes, key = { _, e -> e.id }) { _, ep ->
                            LandscapeTile("${ep.indexNumber ?: ""}. ${ep.name}", formatRuntime(ep.runtimeMinutes), jf.thumbUrl(cfg, ep, 600), width = 320.dp,
                                progress = ep.progress, onFocus = { focusedEp = ep }) { PlayerActivity.start(ctx, ep.id) }
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                }
            }
            if (item.people.isNotEmpty()) item {
                TvRow(stringResource(R.string.cast), item.people.filter { it.type == "Actor" }.take(15), key = { it.id + it.role }) { p ->
                    Column(Modifier.width(120.dp)) {
                        PosterTile(p.name, jf.personUrl(cfg, p), width = 120.dp, onFocus = {}) {}
                        Text(p.name, color = Harbor.Fg, fontSize = 13.sp, maxLines = 1, modifier = Modifier.padding(top = 6.dp))
                        Text(p.role.orEmpty(), color = Harbor.TextDim, fontSize = 12.sp, maxLines = 1)
                    }
                }
            }
            item {
                TvRow(stringResource(R.string.more_like_this), vm.similar, key = { it.id }) { s ->
                    PosterTile(s.name, jf.posterUrl(cfg, s, 300), onFocus = {}) { onOpen(s.id) }
                }
            }
        }
    }
}
