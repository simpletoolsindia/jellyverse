package com.sridhar.harbor.ui.watch

import androidx.compose.runtime.mutableIntStateOf
import com.sridhar.harbor.L10n
import com.sridhar.harbor.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.sridhar.harbor.ui.components.enterRise
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sridhar.harbor.data.AppContainer
import com.sridhar.harbor.data.jellyfin.BaseItem
import com.sridhar.harbor.ui.components.LocalContainer
import com.sridhar.harbor.ui.components.MessageState
import com.sridhar.harbor.ui.components.PosterCard
import com.sridhar.harbor.ui.components.friendly
import com.sridhar.harbor.ui.components.rememberConfig
import com.sridhar.harbor.ui.theme.Harbor
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

enum class LibSort(@androidx.annotation.StringRes val labelRes: Int, val key: String) {
    Added(R.string.recently_added, "DateCreated"), Name(R.string.name, "SortName"),
    Release(R.string.release, "PremiereDate,ProductionYear"), Rating(R.string.rating, "CommunityRating"), Random(R.string.shuffle, "Random");
    val label: String get() = com.sridhar.harbor.L10n.s(labelRes)
}
enum class LibFilter(@androidx.annotation.StringRes val labelRes: Int, val key: String?) { All(R.string.all, null), Unwatched(R.string.unwatched, "IsUnplayed"), Favorites(R.string.favorites, "IsFavorite");
    val label: String get() = com.sridhar.harbor.L10n.s(labelRes)
}

class LibraryViewModel(private val c: AppContainer, private val parentId: String, collectionType: String?) : ViewModel() {
    private val types = when (collectionType) {
        "movies" -> "Movie"; "tvshows" -> "Series"; "homevideos" -> "Video"; "boxsets" -> "BoxSet"; else -> "Movie,Series,Video"
    }
    var items by mutableStateOf<List<BaseItem>>(emptyList()); private set
    var total by mutableIntStateOf(0); private set
    var loading by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var sort by mutableStateOf(LibSort.Added); private set
    var descending by mutableStateOf(true); private set
    var filter by mutableStateOf(LibFilter.All); private set
    var language by mutableStateOf<String?>(null); private set
    var genre by mutableStateOf<String?>(null); private set
    var decade by mutableStateOf<Int?>(null); private set
    /** Genres / decades / audio languages present in this library (fetched once, cached 30 min). */
    var index by mutableStateOf<com.sridhar.harbor.data.jellyfin.LibraryIndex?>(null); private set
    private var clientIds: List<String>? = null
    private var job: Job? = null

    init {
        reload()
        viewModelScope.launch(com.sridhar.harbor.CrashGuard) { index = runCatching { c.jellyfin.libraryIndex(parentId, types) }.getOrNull() }
    }

    val activeFilters: Int get() = listOfNotNull(language, genre, decade).size

    fun selectLanguage(l: String?) { language = l; reload() }
    fun selectGenre(g: String?) { genre = g; reload() }
    fun selectDecade(d: Int?) { decade = d; reload() }
    fun clearFilters() { language = null; genre = null; decade = null; reload() }

    fun selectSort(s: LibSort) { if (s == sort) descending = !descending else { sort = s; descending = s != LibSort.Name }; reload() }
    fun selectFilter(f: LibFilter) { filter = f; reload() }

    private fun reload() { items = emptyList(); total = 0; clientIds = null; job?.cancel(); loadMore() }

    fun loadMore() {
        if (loading && job?.isActive == true) return
        if (items.isNotEmpty() && items.size >= total) return
        job = viewModelScope.launch(com.sridhar.harbor.CrashGuard) {
            loading = true; error = null
            runCatching {
                if (language != null) {
                    // Jellyfin has no audio-language filter: filter + sort the cached index here, then fetch the page by id.
                    val ids = clientIds ?: com.sridhar.harbor.data.jellyfin.LibraryQuery.apply(
                        (index ?: c.jellyfin.libraryIndex(parentId, types).also { index = it }).entries,
                        com.sridhar.harbor.data.jellyfin.LibraryQuery.Filter(language, genre, decade, filter == LibFilter.Unwatched, filter == LibFilter.Favorites),
                        sort.key, descending,
                    ).map { it.id }.also { clientIds = it }
                    val page = c.jellyfin.itemsByIds(ids.drop(items.size).take(60))
                    items = items + page; total = ids.size
                } else {
                    val r = c.jellyfin.library(parentId, types, sort.key, if (descending) "Descending" else "Ascending", items.size, 60, filter.key,
                        genres = genre, years = com.sridhar.harbor.data.jellyfin.LibraryQuery.yearsParam(decade))
                    items = items + r.items; total = r.total
                }
            }.onFailure { error = it.friendly() }
            loading = false
        }
    }
}

@Composable
fun LibraryScreen(id: String, name: String, collectionType: String?, onItem: (String) -> Unit, onBack: () -> Unit) {
    val container = LocalContainer.current
    val cfg = rememberConfig()
    val vm = viewModel(key = "lib-$id") { LibraryViewModel(container, id, collectionType) }
    val grid = rememberLazyGridState()
    val scroll = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val nearEnd by remember { derivedStateOf { (grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) > grid.layoutInfo.totalItemsCount - 12 } }
    LaunchedEffect(nearEnd) { if (nearEnd) vm.loadMore() }

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        containerColor = Harbor.Ink,
        topBar = {
            TopAppBar(
                title = { Column { Text(name); if (vm.total > 0) Text(stringResource(R.string.s_1_s_titles, vm.total), style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = Harbor.TextDim) } },
                navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) } },
                scrollBehavior = scroll,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Harbor.Ink, scrolledContainerColor = Harbor.Surface),
            )
        },
    ) { pad ->
        LazyVerticalGrid(
            GridCells.Adaptive(112.dp), Modifier.fillMaxSize().padding(pad), grid,
            contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(LibSort.entries) { s ->
                            FilterChip(vm.sort == s, { vm.selectSort(s) }, { Text(s.label) },
                                trailingIcon = if (vm.sort == s && s != LibSort.Random) ({
                                    Icon(if (vm.descending) Icons.Rounded.ArrowDownward else Icons.Rounded.ArrowUpward, null)
                                }) else null)
                        }
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(LibFilter.entries) { f -> FilterChip(vm.filter == f, { vm.selectFilter(f) }, { Text(f.label) }) }
                    }
                    LibraryFilterBar(vm) { label, active, dropdown, onClick ->
                        FilterChip(active, onClick, { Text(label, maxLines = 1, softWrap = false) },
                            trailingIcon = if (dropdown) ({ Icon(Icons.Rounded.ArrowDropDown, null) }) else ({ Icon(Icons.Rounded.Close, null) }))
                    }
                }
            }
            itemsIndexed(vm.items, key = { _, it -> it.id }) { i, item ->
                Box(Modifier.animateItem().enterRise(i)) { PosterCard(
                    container.jellyfin.posterUrl(cfg, item), item.name, item.year?.toString(), width = 200.dp,
                    progress = item.progress, played = item.userData?.played == true,
                    badge = item.userData?.unplayedCount?.takeIf { it > 0 }?.let { n -> { CountBadge(n) } },
                ) { onItem(item.id) } }
            }
            if (vm.loading) item(span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { com.sridhar.harbor.ui.components.JellyLoader() }
            }
            vm.error?.let { e -> item(span = { GridItemSpan(maxLineSpan) }) { MessageState(stringResource(R.string.something_went_wrong), e, onRetry = vm::loadMore) } }
        }
    }
}

class SearchViewModel(private val c: AppContainer) : ViewModel() {
    var query by mutableStateOf("")
    var results by mutableStateOf<List<BaseItem>>(emptyList()); private set
    var loading by mutableStateOf(false); private set
    var smart by mutableStateOf(false); private set

    fun search(q: String) = viewModelScope.launch(com.sridhar.harbor.CrashGuard) {
        if (q.length < 2) { results = emptyList(); return@launch }
        loading = true; smart = false
        results = runCatching { c.jellyfin.fuzzyFind(q) }.getOrDefault(emptyList())
        loading = false
    }

    fun smartSearch() = viewModelScope.launch(com.sridhar.harbor.CrashGuard) {
        loading = true; smart = true
        results = runCatching { c.assistant.smartSearch(query) }.getOrDefault(emptyList())
        loading = false
    }
}

@OptIn(FlowPreview::class)
@Composable
fun SearchScreen(onItem: (String) -> Unit, onBack: () -> Unit) {
    val container = LocalContainer.current
    val cfg = rememberConfig()
    val vm = viewModel { SearchViewModel(container) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        focus.requestFocus()
        snapshotFlow { vm.query.trim() }.debounce(350).distinctUntilChanged().filter { true }.collect { vm.search(it) }
    }
    Scaffold(containerColor = Harbor.Ink, topBar = {
        TopAppBar(
            navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) } },
            title = {
                OutlinedTextField(
                    vm.query, { vm.query = it }, Modifier.fillMaxWidth().padding(end = 12.dp).focusRequester(focus),
                    placeholder = { Text(stringResource(R.string.movies_shows_episodes)) }, singleLine = true, shape = RoundedCornerShape(16.dp),
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                )
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Harbor.Ink),
        )
    }) { pad ->
        if (vm.query.trim().contains(' ')) Row(Modifier.padding(pad).padding(horizontal = 16.dp)) {
            FilterChip(vm.smart, { vm.smartSearch() }, { Text(if (vm.loading && vm.smart) stringResource(R.string.thinking) else stringResource(R.string.smart_search)) },
                colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(selectedContainerColor = Harbor.Violet.copy(.35f)))
        }
        if (vm.query.length >= 2 && !vm.loading && vm.results.isEmpty())
            MessageState(stringResource(R.string.no_matches), stringResource(R.string.nothing_in_your_library_matches_1, vm.query), Modifier.padding(pad), icon = Icons.Rounded.Search)
        LazyVerticalGrid(
            GridCells.Adaptive(112.dp), Modifier.fillMaxSize().padding(pad).padding(top = if (vm.query.trim().contains(' ')) 44.dp else 0.dp),
            contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            itemsIndexed(vm.results, key = { _, it -> it.id }) { i, item ->
                Box(Modifier.animateItem().enterRise(i)) { PosterCard(
                    container.jellyfin.posterUrl(cfg, item), item.name,
                    listOfNotNull(item.type.takeIf { it != "Movie" }, item.seriesName, item.year?.toString()).joinToString(" · "),
                    width = 200.dp,
                ) { onItem(item.id) } }
            }
        }
    }
}
