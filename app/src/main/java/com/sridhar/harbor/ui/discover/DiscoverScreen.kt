package com.sridhar.harbor.ui.discover

import com.sridhar.harbor.R
import androidx.compose.ui.res.stringResource
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.sridhar.harbor.ui.components.enterRise
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sridhar.harbor.data.AppContainer
import com.sridhar.harbor.data.seerr.MediaStatus
import com.sridhar.harbor.data.seerr.SeerrMedia
import com.sridhar.harbor.data.seerr.SeerrRepository.Companion.tmdb
import com.sridhar.harbor.ui.components.LocalContainer
import com.sridhar.harbor.ui.components.MessageState
import com.sridhar.harbor.ui.components.NetImage
import com.sridhar.harbor.ui.components.PosterCard
import com.sridhar.harbor.ui.components.Rail
import com.sridhar.harbor.ui.components.friendly
import com.sridhar.harbor.ui.components.rememberConfig
import com.sridhar.harbor.ui.theme.Harbor
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

class DiscoverViewModel(private val c: AppContainer) : ViewModel() {
    var trending by mutableStateOf<List<SeerrMedia>>(emptyList()); private set
    var movies by mutableStateOf<List<SeerrMedia>>(emptyList()); private set
    var tv by mutableStateOf<List<SeerrMedia>>(emptyList()); private set
    var upcomingMovies by mutableStateOf<List<SeerrMedia>>(emptyList()); private set
    var upcomingTv by mutableStateOf<List<SeerrMedia>>(emptyList()); private set
    var error by mutableStateOf<String?>(null); private set
    var query by mutableStateOf("")
    var results by mutableStateOf<List<SeerrMedia>>(emptyList()); private set
    var searching by mutableStateOf(false); private set

    init { load() }

    fun load() = viewModelScope.launch(com.sridhar.harbor.CrashGuard) {
        error = null
        runCatching {
            coroutineScope {
                val a = async { c.seerr.trending().results }
                val b = async { c.seerr.popularMovies().results }
                val d = async { c.seerr.popularTv().results }
                val e = async { runCatching { c.seerr.upcomingMovies().results }.getOrDefault(emptyList()) }
                val f = async { runCatching { c.seerr.upcomingTv().results }.getOrDefault(emptyList()) }
                trending = a.await().filter { it.mediaType != "person" }; movies = b.await(); tv = d.await()
                upcomingMovies = e.await(); upcomingTv = f.await()
            }
        }.onFailure { error = it.friendly() }
    }

    fun search(q: String) = viewModelScope.launch(com.sridhar.harbor.CrashGuard) {
        if (q.isBlank()) { results = emptyList(); return@launch }
        searching = true
        results = runCatching { c.seerr.search(q).results.filter { it.mediaType != "person" } }.getOrDefault(emptyList())
        searching = false
    }
}

@Composable
fun StatusBadge(status: MediaStatus) {
    val (color, icon) = when (status) {
        MediaStatus.Available -> Harbor.Mint to Icons.Rounded.CheckCircle
        MediaStatus.Partial -> Harbor.Mint.copy(alpha = .7f) to Icons.Rounded.CheckCircle
        MediaStatus.Pending -> Harbor.Amber to Icons.Rounded.HourglassTop
        MediaStatus.Processing -> Harbor.Sky to Icons.Rounded.Sync
        else -> return
    }
    Box(Modifier.size(24.dp).clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = .6f)), contentAlignment = Alignment.Center) {
        Icon(icon, status.label, tint = color, modifier = Modifier.size(18.dp))
    }
}

@Composable
fun SeerrPoster(m: SeerrMedia, width: androidx.compose.ui.unit.Dp = 124.dp, onOpen: (String, Int) -> Unit) =
    PosterCard(tmdb(m.posterPath, "w342"), m.displayTitle, listOfNotNull(m.year, if (m.mediaType == "tv") stringResource(R.string.series) else null).joinToString(" · "),
        width = width, badge = { StatusBadge(m.status) }) { onOpen(m.mediaType, m.id) }

@OptIn(FlowPreview::class)
@Composable
fun DiscoverScreen(onOpen: (String, Int) -> Unit, onSetup: () -> Unit) {
    val container = LocalContainer.current
    val cfg = rememberConfig()
    if (!cfg.seerrReady) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            MessageState(stringResource(R.string.jellyseerr_isn_t_connected), stringResource(R.string.connect_it_to_discover_and_request), icon = Icons.Rounded.Explore, onRetry = onSetup, actionLabel = stringResource(R.string.set_up))
        }
        return
    }
    val vm = viewModel { DiscoverViewModel(container) }
    LaunchedEffect(Unit) { snapshotFlow { vm.query.trim() }.debounce(400).distinctUntilChanged().collect { vm.search(it) } }

    Column(Modifier.fillMaxSize().background(Harbor.Ink)) {
        Column(Modifier.statusBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text(stringResource(R.string.discover), style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                vm.query, { vm.query = it }, Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(18.dp),
                placeholder = { Text(stringResource(R.string.search_any_movie_or_series)) }, leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = if (vm.query.isNotEmpty()) ({ IconButton({ vm.query = "" }) { Icon(Icons.Rounded.Close, stringResource(R.string.clear)) } }) else null,
                colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = Harbor.Surface, focusedContainerColor = Harbor.Surface, unfocusedBorderColor = Color.Transparent),
            )
        }
        if (vm.query.isNotBlank()) {
            LazyVerticalGrid(GridCells.Adaptive(112.dp), Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp + com.sridhar.harbor.ui.components.LocalMiniPlayerInset.current),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                itemsIndexed(vm.results, key = { _, it -> "${it.mediaType}-${it.id}" }) { i, it -> Box(Modifier.animateItem().enterRise(i)) { SeerrPoster(it, 200.dp, onOpen) } }
            }
            return@Column
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 120.dp + com.sridhar.harbor.ui.components.LocalMiniPlayerInset.current)) {
            if (vm.error != null) item { MessageState(stringResource(R.string.couldn_t_reach_jellyseerr), vm.error, onRetry = { vm.load() }) }
            item(key = "spot") {
                if (vm.trending.isNotEmpty()) LazyRow(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(vm.trending.take(10), key = { "${it.mediaType}-${it.id}" }) { m -> SpotlightCard(m) { onOpen(m.mediaType, m.id) } }
                }
            }
            item(key = "t") { Rail(stringResource(R.string.trending_now), vm.trending.drop(10), key = { "${it.mediaType}-${it.id}" }) { SeerrPoster(it, onOpen = onOpen) } }
            item(key = "m") { Rail(stringResource(R.string.popular_movies), vm.movies, key = { "m-${it.id}" }) { SeerrPoster(it.copy(mediaType = "movie"), onOpen = onOpen) } }
            item(key = "s") { Rail(stringResource(R.string.popular_series), vm.tv, key = { "tv-${it.id}" }) { SeerrPoster(it.copy(mediaType = "tv"), onOpen = onOpen) } }
            item(key = "um") { Rail(stringResource(R.string.coming_soon_to_cinemas), vm.upcomingMovies, key = { "um-${it.id}" }) { SeerrPoster(it.copy(mediaType = "movie"), onOpen = onOpen) } }
            item(key = "ut") { Rail(stringResource(R.string.upcoming_series), vm.upcomingTv, key = { "ut-${it.id}" }) { SeerrPoster(it.copy(mediaType = "tv"), onOpen = onOpen) } }
        }
    }
}

@Composable
private fun SpotlightCard(m: SeerrMedia, onClick: () -> Unit) {
    Box(Modifier.width(300.dp).height(170.dp).clip(RoundedCornerShape(22.dp)).clickable(onClick = onClick)) {
        NetImage(tmdb(m.backdropPath, "w780"), Modifier.fillMaxSize(), fallback = m.displayTitle)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .85f)))))
        Column(Modifier.align(Alignment.BottomStart).padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (m.mediaType == "tv") stringResource(R.string.series_2) else stringResource(R.string.movie_2), style = MaterialTheme.typography.labelSmall, color = Harbor.Coral)
                m.voteAverage?.takeIf { it > 0 }?.let { Text("  ★ %.1f".format(it), style = MaterialTheme.typography.labelSmall, color = Harbor.Amber) }
            }
            Text(m.displayTitle, style = MaterialTheme.typography.titleLarge, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.align(Alignment.TopEnd).padding(10.dp)) { StatusBadge(m.status) }
        if (m.status != MediaStatus.Unknown) Text(m.status.label, color = Color.White, fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(Alignment.TopStart).padding(10.dp)
                .clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = .5f)).padding(horizontal = 8.dp, vertical = 3.dp))
    }
}
