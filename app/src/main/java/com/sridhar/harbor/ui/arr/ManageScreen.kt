package com.sridhar.harbor.ui.arr

import androidx.compose.material.icons.automirrored.rounded.ManageSearch
import com.sridhar.harbor.L10n
import com.sridhar.harbor.R
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sridhar.harbor.data.arr.ArrEntry
import com.sridhar.harbor.data.arr.ArrKind
import com.sridhar.harbor.data.arr.ArrMovie
import com.sridhar.harbor.data.arr.ArrSeries
import com.sridhar.harbor.data.arr.fanart
import com.sridhar.harbor.data.arr.poster
import com.sridhar.harbor.ui.components.GradientProgress
import com.sridhar.harbor.ui.components.LocalContainer
import com.sridhar.harbor.ui.components.MessageState
import com.sridhar.harbor.ui.components.NetImage
import com.sridhar.harbor.ui.components.Pill
import com.sridhar.harbor.ui.components.enterRise
import com.sridhar.harbor.ui.components.formatBytes
import com.sridhar.harbor.ui.components.pressable
import com.sridhar.harbor.ui.components.rememberConfig
import com.sridhar.harbor.ui.theme.Harbor
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun ManageScreen(onMovie: (Int) -> Unit, onSeries: (Int) -> Unit, onSetup: () -> Unit, onProfile: () -> Unit) {
    val container = LocalContainer.current
    val cfg = rememberConfig()
    if (!cfg.arrReady) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            MessageState(stringResource(R.string.sonarr_radarr_aren_t_connected), stringResource(R.string.connect_them_to_manage_the_download),
                icon = Icons.Rounded.Tune, onRetry = onSetup, actionLabel = stringResource(R.string.set_up))
        }
        return
    }
    val vm = viewModel { ManageViewModel(container) }
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(vm.message) { vm.message?.let { snack.showSnackbar(it); vm.message = null } }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(vm.tab, lifecycle) {
        if (vm.tab == ManageTab.Queue) lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { while (true) { delay(5000); vm.load(ManageTab.Queue, quiet = true) } }
    }
    var releasesFor by remember { mutableStateOf<ArrEntry?>(null) }
    var removing by remember { mutableStateOf<Queued?>(null) }

    Box(Modifier.fillMaxSize().background(Harbor.Ink)) {
        // Tablets / landscape: a readable centred column, like Settings, instead of edge-to-edge lists.
        Column(Modifier.fillMaxHeight().widthIn(max = com.sridhar.harbor.ui.components.ReadableWidth).fillMaxWidth().align(Alignment.TopCenter)) {
            Column(Modifier.statusBarsPadding().padding(start = 20.dp, end = 20.dp, top = 12.dp)) {
                Text(stringResource(R.string.manage), style = MaterialTheme.typography.headlineLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                    vm.versions.forEach { (k, v) -> Pill("${k.label} ${v.substringBeforeLast('.')}", if (v == "offline") Harbor.Rose else Harbor.Mint) }
                }
            }
            OverviewStrip(vm)
            TabStrip(vm.tab) { vm.select(it) }
            AnimatedContent(
                vm.tab,
                transitionSpec = {
                    val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                    (slideInHorizontally(spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)) { it / 5 * dir } + fadeIn(tween(220))) togetherWith
                        (slideOutHorizontally(tween(180)) { -it / 8 * dir } + fadeOut(tween(150)))
                },
                label = "manage-tab", modifier = Modifier.weight(1f),
            ) { tab ->
                when (tab) {
                    ManageTab.Queue -> Loaded(vm.queue, { vm.load(tab) }) { list -> QueueList(list, onRemove = { removing = it }) }
                    ManageTab.Upcoming -> Loaded(vm.upcoming, { vm.load(tab) }) { list -> UpcomingList(list, onMovie, onSeries) }
                    ManageTab.Wanted -> Loaded(vm.wanted, { vm.load(tab) }) { list -> WantedList(list, vm, onMovie, onSeries, onManual = { releasesFor = it }) }
                    ManageTab.Movies -> Loaded(vm.movies, { vm.load(tab) }) { list -> MovieGrid(list, onMovie) }
                    ManageTab.Series -> Loaded(vm.series, { vm.load(tab) }) { list -> SeriesGrid(list, onSeries) }
                }
            }
        }
        SnackbarHost(snack, Modifier.align(Alignment.BottomCenter).padding(bottom = 110.dp + com.sridhar.harbor.ui.components.LocalMiniPlayerInset.current))
    }

    releasesFor?.let { e ->
        ReleasesSheet(listOfNotNull(e.title, e.subtitle).joinToString(" · "), { vm.releasesFor(e) }, { r -> vm.grab(e.kind, r) }, { releasesFor = null })
    }
    removing?.let { q ->
        var fromClient by remember { mutableStateOf(true) }
        var blocklist by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text(stringResource(R.string.remove_from_queue)) },
            text = {
                Column {
                    Text(q.item.title.orEmpty(), style = MaterialTheme.typography.bodySmall, color = Harbor.TextDim)
                    CheckRow(stringResource(R.string.remove_from_download_client), fromClient) { fromClient = it }
                    CheckRow(stringResource(R.string.blocklist_release_search_for_another), blocklist) { blocklist = it }
                }
            },
            confirmButton = { TextButton({ vm.remove(q, fromClient, blocklist); removing = null }) { Text(stringResource(R.string.remove), color = Harbor.Rose) } },
            dismissButton = { TextButton({ removing = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun CheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) =
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked, onChange); Text(label, style = MaterialTheme.typography.bodyMedium)
    }

/** Loading → content → error, animated only when the branch changes (not on every refresh). */
@Composable
private fun <T> Loaded(state: Load<T>, retry: () -> Unit, content: @Composable (T) -> Unit) {
    AnimatedContent(state, contentKey = { it.branch }, transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(120)) }, label = "load") { s ->
        when (s) {
            Load.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { com.sridhar.harbor.ui.components.JellyLoader(color = Harbor.VioletSoft) }
            is Load.Failed -> MessageState(stringResource(R.string.couldn_t_load), s.message, onRetry = retry)
            is Load.Ready -> content(s.value)
        }
    }
}

@Composable
private fun OverviewStrip(vm: ManageViewModel) {
    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(vm.disks, key = { it.path + it.totalSpace }) { d ->
            val used = 1f - d.freeSpace.toFloat() / d.totalSpace
            Column(Modifier.width(170.dp).clip(RoundedCornerShape(18.dp)).background(Harbor.Surface).padding(14.dp)) {
                Text(d.label?.takeIf { it.isNotBlank() } ?: d.path, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(stringResource(R.string.s_1_s_free_of_2_s, formatBytes(d.freeSpace), formatBytes(d.totalSpace)), color = Harbor.TextDim, fontSize = 11.sp, maxLines = 1)
                Spacer(Modifier.height(8.dp))
                GradientProgress(used, height = 6.dp, brush = if (used > .9f) androidx.compose.ui.graphics.SolidColor(Harbor.Rose) else Harbor.accentH)
            }
        }
        items(vm.health, key = { it.first.name + it.second.message }) { (k, h) ->
            Row(Modifier.width(250.dp).clip(RoundedCornerShape(18.dp)).background((if (h.type == "error") Harbor.Rose else Harbor.Amber).copy(alpha = .12f)).padding(14.dp)) {
                Icon(Icons.Rounded.Warning, null, tint = if (h.type == "error") Harbor.Rose else Harbor.Amber, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("${k.label}: ${h.message}", fontSize = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Segmented tabs with a spring-animated sliding indicator (offset + width from one target). */
@Composable
private fun TabStrip(selected: ManageTab, onSelect: (ManageTab) -> Unit) {
    BoxWithConstraints(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(44.dp).clip(RoundedCornerShape(22.dp)).background(Harbor.Surface)) {
        val w = maxWidth / ManageTab.entries.size
        val fontScale = androidx.compose.ui.platform.LocalDensity.current.fontScale   // segmented control: grows at most 10%
        val x by animateDpAsState(w * selected.ordinal, spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow), label = "indicator")
        Box(Modifier.offset { androidx.compose.ui.unit.IntOffset(x.roundToPx(), 0) }.width(w).fillMaxHeight().padding(4.dp).clip(RoundedCornerShape(18.dp)).background(Harbor.accentH))
        Row(Modifier.fillMaxSize()) {
            ManageTab.entries.forEach { t ->
                Box(Modifier.weight(1f).fillMaxHeight().clickable { onSelect(t) }, contentAlignment = Alignment.Center) {
                    Text(t.label, fontSize = (12f * minOf(fontScale, 1.1f) / fontScale).sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 4.dp),
                        color = if (t == selected) Color.White else Harbor.TextDim)
                }
            }
        }
    }
}

@Composable
private fun QueueList(list: List<Queued>, onRemove: (Queued) -> Unit) {
    if (list.isEmpty()) { MessageState(stringResource(R.string.queue_is_clear), stringResource(R.string.nothing_is_downloading_through_sonarr_or), icon = Icons.Rounded.CloudDone); return }
    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 130.dp + com.sridhar.harbor.ui.components.LocalMiniPlayerInset.current), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        itemsIndexed(list, key = { _, q -> "${q.kind}-${q.item.id}" }) { i, q ->
            val it = q.item
            val poster = it.movie?.images?.poster() ?: it.series?.images?.poster()
            Row(Modifier.animateItem().enterRise(i).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Harbor.Surface).padding(12.dp)) {
                Box(Modifier.width(56.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(10.dp))) { NetImage(poster, Modifier.fillMaxSize(), fallback = it.title) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(it.movie?.title ?: it.series?.title ?: it.title.orEmpty(), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    it.episode?.let { e -> Text("${e.code} · ${e.title.orEmpty()}", color = Harbor.TextDim, fontSize = 12.sp, maxLines = 1) }
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Pill(q.kind.label, if (q.kind == ArrKind.Radarr) Harbor.Amber else Harbor.Sky)
                        Pill((it.trackedDownloadState ?: it.status).replaceFirstChar { c -> c.uppercase() }, if (it.hasIssue) Harbor.Rose else Harbor.Mint)
                        it.quality?.quality?.name?.let { n -> Pill(n, Harbor.TextDim) }
                    }
                    Spacer(Modifier.height(8.dp))
                    GradientProgress(it.progress, height = 5.dp)
                    Text("${(it.progress * 100).toInt()}% · ${formatBytes(it.size.toLong())}" + (it.timeleft?.let { t -> stringResource(R.string.s_1_s_left, t) } ?: ""),
                        color = Harbor.TextDim, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                    val msgs = it.statusMessages.flatMap { m -> m.messages } + listOfNotNull(it.errorMessage)
                    if (msgs.isNotEmpty()) Text(msgs.first(), color = Harbor.Amber, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                IconButton({ onRemove(q) }) { Icon(Icons.Rounded.Delete, stringResource(R.string.remove), tint = Harbor.TextDim) }
            }
        }
    }
}

private fun dayLabel(iso: String?): String {
    val d = iso?.let { runCatching { Instant.parse(it).atZone(ZoneId.systemDefault()).toLocalDate() }.getOrNull() } ?: return "Unknown date"
    val today = LocalDate.now()
    return when (d) {
        today -> L10n.s(R.string.today); today.plusDays(1) -> L10n.s(R.string.tomorrow); today.minusDays(1) -> L10n.s(R.string.yesterday)
        else -> d.format(DateTimeFormatter.ofPattern("EEEE, d MMM", Locale.getDefault()))
    }
}

@Composable
private fun UpcomingList(list: List<ArrEntry>, onMovie: (Int) -> Unit, onSeries: (Int) -> Unit) {
    if (list.isEmpty()) { MessageState(stringResource(R.string.nothing_scheduled), stringResource(R.string.no_monitored_releases_in_the_next), icon = Icons.Rounded.Event); return }
    val grouped = list.groupBy { dayLabel(it.date) }
    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 130.dp + com.sridhar.harbor.ui.components.LocalMiniPlayerInset.current), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        grouped.forEach { (day, entries) ->
            item(key = "h-$day") { Text(day.uppercase(), style = MaterialTheme.typography.labelSmall, color = Harbor.VioletSoft, modifier = Modifier.padding(top = 10.dp, start = 4.dp)) }
            itemsIndexed(entries, key = { _, e -> e.key }) { i, e -> EntryRow(e, Modifier.enterRise(i), onClick = { e.open(onMovie, onSeries) }) }
        }
    }
}

private fun ArrEntry.open(onMovie: (Int) -> Unit, onSeries: (Int) -> Unit) { movieId?.let(onMovie) ?: seriesId?.let(onSeries) }

@Composable
private fun EntryRow(e: ArrEntry, modifier: Modifier, onClick: () -> Unit, trailing: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Harbor.Surface).pressable(0.98f, onClick = onClick).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(46.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(8.dp))) { NetImage(e.poster, Modifier.fillMaxSize(), fallback = e.title) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(e.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            e.subtitle?.let { Text(it, color = Harbor.TextDim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                Pill(if (e.kind == ArrKind.Radarr) stringResource(R.string.movie) else stringResource(R.string.episode), if (e.kind == ArrKind.Radarr) Harbor.Amber else Harbor.Sky)
                if (e.hasFile) Pill(stringResource(R.string.downloaded), Harbor.Mint, icon = Icons.Rounded.CheckCircle)
            }
        }
        trailing?.invoke()
    }
}

@Composable
private fun WantedList(list: List<ArrEntry>, vm: ManageViewModel, onMovie: (Int) -> Unit, onSeries: (Int) -> Unit, onManual: (ArrEntry) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 130.dp + com.sridhar.harbor.ui.components.LocalMiniPlayerInset.current), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                vm.versions.keys.forEach { k ->
                    Row(Modifier.clip(RoundedCornerShape(50)).background(Harbor.accentH).pressable { vm.searchAllMissing(k) }.padding(horizontal = 14.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Search, null, tint = Color.White, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp))
                        Text(if (k == ArrKind.Radarr) stringResource(R.string.search_all_missing_movies) else stringResource(R.string.search_all_missing_episodes), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        if (list.isEmpty()) item { MessageState(stringResource(R.string.nothing_missing), stringResource(R.string.every_monitored_title_has_a_file), icon = Icons.Rounded.CheckCircle) }
        itemsIndexed(list, key = { _, e -> e.key }) { i, e ->
            EntryRow(e, Modifier.enterRise(i), onClick = { e.open(onMovie, onSeries) }) {
                IconButton({ vm.search(e) }) { Icon(Icons.Rounded.Search, stringResource(R.string.automatic_search), tint = Harbor.VioletSoft) }
                IconButton({ onManual(e) }) { Icon(Icons.AutoMirrored.Rounded.ManageSearch, stringResource(R.string.manual_search), tint = Harbor.Coral) }
            }
        }
    }
}

private enum class LibFilter(@androidx.annotation.StringRes val labelRes: Int) { All(R.string.all), Missing(R.string.missing), Downloaded(R.string.downloaded), Unmonitored(R.string.unmonitored);
    val label: String get() = com.sridhar.harbor.L10n.s(labelRes)
}

@Composable
private fun <T> LibraryGrid(
    items: List<T>, key: (T) -> Any, title: (T) -> String, matches: (T, LibFilter) -> Boolean,
    card: @Composable (T, Modifier) -> Unit,
) {
    var filter by remember { mutableStateOf(LibFilter.All) }
    var query by remember { mutableStateOf("") }
    val shown = items.filter { matches(it, filter) && (query.isBlank() || title(it).contains(query, true)) }
    LazyVerticalGrid(GridCells.Adaptive(108.dp), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 130.dp + com.sridhar.harbor.ui.components.LocalMiniPlayerInset.current),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(16.dp),
                    placeholder = { Text(stringResource(R.string.filter_1_s_titles, items.size)) }, leadingIcon = { Icon(Icons.Rounded.Search, null) })
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                    items(LibFilter.entries) { f ->
                        FilterChip(filter == f, { filter = f }, { Text("${f.label} ${items.count { matches(it, f) }}") },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Harbor.Violet.copy(alpha = .3f)))
                    }
                }
            }
        }
        itemsIndexed(shown, key = { _, it -> key(it) }) { i, it -> card(it, Modifier.animateItem().enterRise(i)) }
    }
}

@Composable
private fun MovieGrid(list: List<ArrMovie>, onMovie: (Int) -> Unit) = LibraryGrid(
    list, { it.id }, { it.title },
    matches = { m, f -> when (f) { LibFilter.All -> true; LibFilter.Missing -> m.monitored && !m.hasFile; LibFilter.Downloaded -> m.hasFile; LibFilter.Unmonitored -> !m.monitored } },
) { m, mod ->
    ArrPoster(m.images.poster(), m.title, m.year.toString(), mod, status = when { m.hasFile -> Harbor.Mint; !m.monitored -> Harbor.TextDim; else -> Harbor.Amber }) { onMovie(m.id) }
}

@Composable
private fun SeriesGrid(list: List<ArrSeries>, onSeries: (Int) -> Unit) = LibraryGrid(
    list, { it.id }, { it.title },
    matches = { s, f ->
        val pct = s.statistics?.percentOfEpisodes ?: 0.0
        when (f) { LibFilter.All -> true; LibFilter.Missing -> s.monitored && pct < 100; LibFilter.Downloaded -> pct >= 100; LibFilter.Unmonitored -> !s.monitored }
    },
) { s, mod ->
    val st = s.statistics
    ArrPoster(s.images.poster(), s.title, st?.let { "${it.episodeFileCount}/${it.episodeCount} eps" } ?: s.year.toString(), mod,
        status = if ((st?.percentOfEpisodes ?: 0.0) >= 100) Harbor.Mint else if (!s.monitored) Harbor.TextDim else Harbor.Amber,
        progress = ((st?.percentOfEpisodes ?: 0.0) / 100).toFloat()) { onSeries(s.id) }
}

@Composable
private fun ArrPoster(url: String?, title: String, subtitle: String, modifier: Modifier, status: Color, progress: Float? = null, onClick: () -> Unit) {
    Column(modifier.pressable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(14.dp))) {
            NetImage(url, Modifier.fillMaxSize(), fallback = title)
            Box(Modifier.align(Alignment.TopEnd).padding(6.dp).size(12.dp).clip(CircleShape).background(status).border(2.dp, Color.Black.copy(.5f), CircleShape))
            progress?.let { p -> Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(4.dp).drawBehind {
                drawRect(Color.Black.copy(.5f)); drawRect(status, size = size.copy(width = size.width * p))
            }) }
        }
        Text(title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
        Text(subtitle, color = Harbor.TextDim, fontSize = 11.sp, maxLines = 1)
    }
}
