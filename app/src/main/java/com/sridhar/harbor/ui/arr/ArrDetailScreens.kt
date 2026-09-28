package com.sridhar.harbor.ui.arr

import androidx.compose.material.icons.automirrored.rounded.ManageSearch
import com.sridhar.harbor.L10n
import com.sridhar.harbor.R
import androidx.compose.ui.res.stringResource
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sridhar.harbor.data.AppContainer
import com.sridhar.harbor.data.arr.ArrEpisode
import com.sridhar.harbor.data.arr.ArrKind
import com.sridhar.harbor.data.arr.ArrMovie
import com.sridhar.harbor.data.arr.ArrRelease
import com.sridhar.harbor.data.arr.ArrSeries
import com.sridhar.harbor.data.arr.QualityProfile
import com.sridhar.harbor.data.arr.fanart
import com.sridhar.harbor.data.arr.poster
import com.sridhar.harbor.ui.components.GradientButton
import com.sridhar.harbor.ui.components.GradientProgress
import com.sridhar.harbor.ui.components.LocalContainer
import com.sridhar.harbor.ui.components.MessageState
import com.sridhar.harbor.ui.components.NetImage
import com.sridhar.harbor.ui.components.Pill
import com.sridhar.harbor.ui.components.formatBytes
import com.sridhar.harbor.ui.components.formatRuntime
import com.sridhar.harbor.ui.components.friendly
import com.sridhar.harbor.ui.components.glass
import com.sridhar.harbor.ui.theme.Harbor
import kotlinx.coroutines.launch

// ---------------------------------------------------------------- Movie

class ArrMovieViewModel(private val c: AppContainer, private val id: Int) : ViewModel() {
    var movie by mutableStateOf<Load<ArrMovie>>(Load.Loading); private set
    var profiles by mutableStateOf<List<QualityProfile>>(emptyList()); private set
    var message by mutableStateOf<String?>(null)
    init { load() }
    fun load() = viewModelScope.launch(com.sridhar.harbor.CrashGuard) {
        movie = runCatching { c.radarr.movie(id) }.fold({ Load.Ready(it) }, { Load.Failed(it.friendly()) })
        profiles = runCatching { c.radarr.profiles() }.getOrDefault(emptyList())
    }
    private fun act(msg: String, reload: Boolean = true, block: suspend () -> Unit) = viewModelScope.launch(com.sridhar.harbor.CrashGuard) {
        runCatching { block() }.onSuccess { message = msg; if (reload) load() }.onFailure { message = it.friendly() }
    }
    fun setMonitored(on: Boolean) = act(if (on) L10n.s(R.string.monitoring) else L10n.s(R.string.unmonitored)) { c.radarr.setMovieMonitored(id, on) }
    fun search() = act(L10n.s(R.string.searching_indexers), reload = false) { c.radarr.searchMovie(id) }
    fun grab(r: ArrRelease) = act(L10n.s(R.string.sent_to_download_client), reload = false) { c.radarr.grab(r) }
    suspend fun releases() = c.radarr.movieReleases(id)
    fun delete(files: Boolean, done: () -> Unit) = act(L10n.s(R.string.movie_removed), reload = false) { c.radarr.deleteMovie(id, files); done() }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ArrMovieScreen(id: Int, onBack: () -> Unit) {
    val container = LocalContainer.current
    val ctx = LocalContext.current
    val vm = viewModel(key = "arr-movie-$id") { ArrMovieViewModel(container, id) }
    var manual by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(vm.message) { vm.message?.let { snack.showSnackbar(it); vm.message = null } }

    DetailScaffold(onBack, snack) {
        when (val s = vm.movie) {
            Load.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { com.sridhar.harbor.ui.components.JellyLoader() }
            is Load.Failed -> MessageState(stringResource(R.string.couldn_t_load), s.message, onRetry = { vm.load() })
            is Load.Ready -> {
                val m = s.value
                val list = rememberLazyListState()
                LazyColumn(state = list, contentPadding = PaddingValues(bottom = 48.dp)) {
                    item { Hero(m.images.fanart(), m.images.poster(), m.title, listOfNotNull(m.year.takeIf { it > 0 }?.toString(), formatRuntime(m.runtime), m.certification).joinToString("  ·  "), list) {
                        Pill(if (m.hasFile) stringResource(R.string.downloaded) else if (m.isAvailable) stringResource(R.string.missing) else stringResource(R.string.not_released), if (m.hasFile) Harbor.Mint else Harbor.Amber)
                        Pill(vm.profiles.firstOrNull { it.id == m.qualityProfileId }?.name ?: stringResource(R.string.profile_1_s, m.qualityProfileId), Harbor.Sky)
                    } }
                    item {
                        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            MonitorRow(m.monitored) { vm.setMonitored(it) }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                GradientButton(stringResource(R.string.search), { vm.search() }, Modifier.weight(1f), icon = Icons.Rounded.Search)
                                GradientButton(stringResource(R.string.manual), { manual = true }, Modifier.weight(1f), icon = Icons.AutoMirrored.Rounded.ManageSearch)
                            }
                            m.movieFile?.takeIf { m.hasFile }?.let { f ->
                                Column(Modifier.fillMaxWidth().glass().padding(14.dp)) {
                                    Text(stringResource(R.string.file), style = MaterialTheme.typography.titleMedium)
                                    Text(f.relativePath.orEmpty(), color = Harbor.TextDim, fontSize = 12.sp)
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                                        f.quality?.quality?.name?.let { Pill(it, Harbor.Sky) }; Pill(formatBytes(f.size), Harbor.TextDim)
                                    }
                                }
                            }
                            m.overview?.let { Text(it, color = Harbor.Fg.copy(.85f)) }
                            if (m.genres.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { m.genres.forEach { Pill(it, Harbor.VioletSoft) } }
                            m.youTubeTrailerId?.takeIf { it.isNotBlank() }?.let { yt ->
                                TextButton({ ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$yt"))) }) {
                                    Icon(Icons.Rounded.Movie, null, tint = Harbor.VioletSoft); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.trailer), color = Harbor.VioletSoft)
                                }
                            }
                            m.path?.let { Text(it, color = Harbor.TextDim, fontSize = 12.sp) }
                            TextButton({ confirmDelete = true }) { Icon(Icons.Rounded.Delete, null, tint = Harbor.Rose); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.delete_from_radarr), color = Harbor.Rose) }
                        }
                    }
                }
                if (manual) ReleasesSheet(m.title, { vm.releases() }, { vm.grab(it) }, { manual = false })
                if (confirmDelete) DeleteDialog(m.title, { confirmDelete = false }) { files -> vm.delete(files, onBack) }
            }
        }
    }
}

// ---------------------------------------------------------------- Series

class ArrSeriesViewModel(private val c: AppContainer, private val id: Int) : ViewModel() {
    var series by mutableStateOf<Load<ArrSeries>>(Load.Loading); private set
    var episodes by mutableStateOf<List<ArrEpisode>>(emptyList()); private set
    var message by mutableStateOf<String?>(null)
    init { load() }
    fun load() = viewModelScope.launch(com.sridhar.harbor.CrashGuard) {
        series = runCatching { c.sonarr.seriesById(id) }.fold({ Load.Ready(it) }, { Load.Failed(it.friendly()) })
        episodes = runCatching { c.sonarr.episodes(id) }.getOrDefault(emptyList())
    }
    private fun act(msg: String, reload: Boolean = true, block: suspend () -> Unit) = viewModelScope.launch(com.sridhar.harbor.CrashGuard) {
        runCatching { block() }.onSuccess { message = msg; if (reload) load() }.onFailure { message = it.friendly() }
    }
    fun setMonitored(on: Boolean) = act(if (on) L10n.s(R.string.monitoring) else L10n.s(R.string.unmonitored)) { c.sonarr.setSeriesMonitored(id, on) }
    fun toggleEpisode(e: ArrEpisode) = act(if (!e.monitored) L10n.s(R.string.episode_monitored) else L10n.s(R.string.episode_unmonitored)) { c.sonarr.setEpisodesMonitored(listOf(e.id), !e.monitored) }
    fun searchAll() = act(L10n.s(R.string.searching_all_episodes), false) { c.sonarr.searchSeries(id) }
    fun searchSeason(n: Int) = act(L10n.s(R.string.searching_season_1_s, n), false) { c.sonarr.searchSeason(id, n) }
    fun searchEpisode(e: ArrEpisode) = act(L10n.s(R.string.searching_1_s, e.code), false) { c.sonarr.searchEpisodes(listOf(e.id)) }
    fun grab(r: ArrRelease) = act(L10n.s(R.string.sent_to_download_client), false) { c.sonarr.grab(r) }
    fun delete(files: Boolean, done: () -> Unit) = act(L10n.s(R.string.series_removed), false) { c.sonarr.deleteSeries(id, files); done() }
    suspend fun seasonReleases(n: Int) = c.sonarr.seasonReleases(id, n)
    suspend fun episodeReleases(e: ArrEpisode) = c.sonarr.episodeReleases(e.id)
}

@Composable
fun ArrSeriesScreen(id: Int, onBack: () -> Unit) {
    val container = LocalContainer.current
    val vm = viewModel(key = "arr-series-$id") { ArrSeriesViewModel(container, id) }
    var manual by remember { mutableStateOf<Pair<String, suspend () -> List<ArrRelease>>?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var open by remember { mutableStateOf(setOf<Int>()) }
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(vm.message) { vm.message?.let { snack.showSnackbar(it); vm.message = null } }

    DetailScaffold(onBack, snack) {
        when (val st = vm.series) {
            Load.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { com.sridhar.harbor.ui.components.JellyLoader() }
            is Load.Failed -> MessageState(stringResource(R.string.couldn_t_load), st.message, onRetry = { vm.load() })
            is Load.Ready -> {
                val s = st.value
                val list = rememberLazyListState()
                val bySeason = vm.episodes.groupBy { it.seasonNumber }
                LazyColumn(state = list, contentPadding = PaddingValues(bottom = 48.dp)) {
                    item { Hero(s.images.fanart(), s.images.poster(), s.title, listOfNotNull(s.year.takeIf { it > 0 }?.toString(), s.network, s.status?.replaceFirstChar { it.uppercase() }).joinToString("  ·  "), list) {
                        s.statistics?.let { Pill(stringResource(R.string.s_1_s_2_s_episodes, it.episodeFileCount, it.episodeCount), if (it.percentOfEpisodes >= 100) Harbor.Mint else Harbor.Amber); Pill(formatBytes(it.sizeOnDisk), Harbor.TextDim) }
                    } }
                    item {
                        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            MonitorRow(s.monitored) { vm.setMonitored(it) }
                            GradientButton(stringResource(R.string.search_all_monitored), { vm.searchAll() }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Search)
                            s.overview?.let { Text(it, color = Harbor.Fg.copy(.85f), maxLines = 5, overflow = TextOverflow.Ellipsis) }
                        }
                    }
                    s.seasons.sortedByDescending { it.seasonNumber }.forEach { season ->
                        val n = season.seasonNumber
                        val eps = bySeason[n].orEmpty().sortedBy { it.episodeNumber }
                        val expanded = n in open
                        item(key = "season-$n") {
                            val rot by animateFloatAsState(if (expanded) 180f else 0f, label = "chev")
                            Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Harbor.Surface)
                                .clickable { open = if (expanded) open - n else open + n }.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(if (n == 0) stringResource(R.string.specials) else stringResource(R.string.season_1_s, n), fontWeight = FontWeight.Bold)
                                        val ss = season.statistics
                                        Text(stringResource(R.string.s_1_s_2_s_episodes_3, ss?.episodeFileCount ?: 0, ss?.episodeCount ?: eps.size, formatBytes(ss?.sizeOnDisk ?: 0)), color = Harbor.TextDim, fontSize = 12.sp)
                                    }
                                    IconButton({ vm.searchSeason(n) }) { Icon(Icons.Rounded.Search, stringResource(R.string.search_season), tint = Harbor.VioletSoft) }
                                    IconButton({ manual = "${s.title} · Season $n" to suspend { vm.seasonReleases(n) } }) { Icon(Icons.AutoMirrored.Rounded.ManageSearch, stringResource(R.string.manual_search), tint = Harbor.Coral) }
                                    Icon(Icons.Rounded.ExpandMore, null, modifier = Modifier.graphicsLayer { rotationZ = rot })
                                }
                                GradientProgress(((season.statistics?.percentOfEpisodes ?: 0.0) / 100).toFloat(), height = 4.dp, modifier = Modifier.padding(top = 8.dp))
                                AnimatedVisibility(expanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                                    Column(Modifier.padding(top = 8.dp)) {
                                        eps.forEach { e ->
                                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Icon(if (e.hasFile) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, null,
                                                    tint = if (e.hasFile) Harbor.Mint else Harbor.TextDim, modifier = Modifier.size(18.dp))
                                                Spacer(Modifier.width(10.dp))
                                                Column(Modifier.weight(1f)) {
                                                    Text("${e.episodeNumber}. ${e.title.orEmpty()}", fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                    e.airDateUtc?.let { Text(it.take(10), color = Harbor.TextDim, fontSize = 11.sp) }
                                                }
                                                IconButton({ vm.toggleEpisode(e) }, Modifier.size(34.dp)) {
                                                    Icon(if (e.monitored) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder, stringResource(R.string.monitor),
                                                        tint = if (e.monitored) Harbor.VioletSoft else Harbor.TextDim, modifier = Modifier.size(18.dp))
                                                }
                                                IconButton({ vm.searchEpisode(e) }, Modifier.size(34.dp)) { Icon(Icons.Rounded.Search, stringResource(R.string.search), modifier = Modifier.size(18.dp)) }
                                                IconButton({ manual = "${s.title} · ${e.code}" to suspend { vm.episodeReleases(e) } }, Modifier.size(34.dp)) {
                                                    Icon(Icons.AutoMirrored.Rounded.ManageSearch, stringResource(R.string.manual), tint = Harbor.Coral, modifier = Modifier.size(18.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    item {
                        TextButton({ confirmDelete = true }, Modifier.padding(16.dp)) {
                            Icon(Icons.Rounded.Delete, null, tint = Harbor.Rose); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.delete_from_sonarr), color = Harbor.Rose)
                        }
                    }
                }
                manual?.let { (title, loader) -> ReleasesSheet(title, loader, { vm.grab(it) }, { manual = null }) }
                if (confirmDelete) DeleteDialog(s.title, { confirmDelete = false }) { files -> vm.delete(files, onBack) }
            }
        }
    }
}

// ---------------------------------------------------------------- shared pieces

@Composable
private fun DetailScaffold(onBack: () -> Unit, snack: SnackbarHostState, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(Harbor.Ink)) {
        content()
        IconButton(onBack, Modifier.statusBarsPadding().padding(12.dp).glass(RoundedCornerShape(50))) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back), tint = Harbor.Fg)
        }
        SnackbarHost(snack, Modifier.align(Alignment.BottomCenter).padding(24.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Hero(backdrop: String?, poster: String?, title: String, meta: String, list: androidx.compose.foundation.lazy.LazyListState, pills: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().height(400.dp)) {
        NetImage(backdrop, Modifier.fillMaxSize().graphicsLayer {
            val off = if (list.firstVisibleItemIndex == 0) list.firstVisibleItemScrollOffset.toFloat() else 0f
            translationY = off * 0.5f
        })
        Box(Modifier.fillMaxSize().background(Harbor.scrimBottom()))
        Row(Modifier.align(Alignment.BottomStart).padding(20.dp), verticalAlignment = Alignment.Bottom) {
            Box(Modifier.width(104.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(14.dp))) { NetImage(poster, Modifier.fillMaxSize(), fallback = title) }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(title, style = MaterialTheme.typography.headlineSmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Text(meta, color = Harbor.TextDim, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { pills() }
            }
        }
    }
}

@Composable
private fun MonitorRow(monitored: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().glass(RoundedCornerShape(16.dp)).clickable { onChange(!monitored) }.padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(if (monitored) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder, null, tint = if (monitored) Harbor.VioletSoft else Harbor.TextDim)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(if (monitored) stringResource(R.string.monitored) else stringResource(R.string.not_monitored), fontWeight = FontWeight.SemiBold)
            Text(if (monitored) stringResource(R.string.grabs_new_releases_automatically) else stringResource(R.string.ignored_by_automatic_searches), color = Harbor.TextDim, fontSize = 12.sp)
        }
        Switch(monitored, onChange)
    }
}

@Composable
private fun DeleteDialog(title: String, onDismiss: () -> Unit, onConfirm: (Boolean) -> Unit) {
    var files by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_1_s, title)) },
        text = {
            Row(Modifier.fillMaxWidth().clickable { files = !files }, verticalAlignment = Alignment.CenterVertically) {
                Checkbox(files, { files = it }); Text(stringResource(R.string.also_delete_files_from_disk))
            }
        },
        confirmButton = { TextButton({ onDismiss(); onConfirm(files) }) { Text(stringResource(R.string.delete), color = Harbor.Rose) } },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
