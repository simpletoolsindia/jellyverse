package com.sridhar.harbor.ui.watch

import androidx.compose.foundation.layout.sizeIn

import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.rounded.AutoFixHigh
import kotlinx.coroutines.async
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
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
                    items = items + page.filterNot(c.parental::hideAdult); total = ids.size
                } else {
                    val r = c.jellyfin.library(parentId, types, sort.key, if (descending) "Descending" else "Ascending", items.size, 60, filter.key,
                        genres = genre, years = com.sridhar.harbor.data.jellyfin.LibraryQuery.yearsParam(decade))
                    items = items + r.items.filterNot(c.parental::hideAdult); total = r.total
                }
            }.onFailure { error = it.friendly() }
            loading = false
        }
    }
}

@Composable
fun LibraryScreen(id: String, name: String, collectionType: String?, onItem: (String) -> Unit, onBack: () -> Unit, onDoctor: (String) -> Unit = {}) {
    val container = LocalContainer.current
    val cfg = rememberConfig()
    val vm = viewModel(key = "lib-$id") { LibraryViewModel(container, id, collectionType) }
    val grid = rememberLazyGridState()
    // Admins: long-press a poster → run Library Doctor on just that title.
    val isAdmin by androidx.compose.runtime.produceState(false) { value = runCatching { container.jellyfin.isAdmin() }.getOrDefault(false) }
    var doctorFor by remember { androidx.compose.runtime.mutableStateOf<com.sridhar.harbor.data.jellyfin.BaseItem?>(null) }
    doctorFor?.let { target ->
        androidx.compose.material3.ModalBottomSheet(onDismissRequest = { doctorFor = null }, containerColor = Harbor.Surface) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
                Text(target.name, style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))
                androidx.compose.material3.OutlinedButton({ doctorFor = null; onDoctor(target.id) }, Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.AutoFixHigh, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.doctor_fix_this))
                }
            }
        }
    }
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
                    onLongClick = if (isAdmin && item.type in setOf("Movie", "Series")) ({ doctorFor = item }) else null,   // Library Doctor handles films and shows
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
    /** "Did you mean…" – closest library titles (and online spellings) when the query matches nothing well. */
    var suggestions by mutableStateOf<List<String>>(emptyList()); private set

    /** Jellyseerr matches that aren't in the library yet – requestable right from Search. */
    var seerr by mutableStateOf<List<com.sridhar.harbor.data.seerr.SeerrMedia>>(emptyList()); private set
    /** tmdb id → request state ("sent" / error text) for the TV's one-press Request. */
    val requested = androidx.compose.runtime.mutableStateMapOf<Int, String>()

    fun search(q: String) = viewModelScope.launch(com.sridhar.harbor.CrashGuard) {
        if (q.length < 2) { results = emptyList(); seerr = emptyList(); suggestions = emptyList(); return@launch }
        loading = true; smart = false
        kotlinx.coroutines.coroutineScope {
            val s = async {
                if (!c.settings.current().seerrReady) emptyList()
                else runCatching { c.seerr.search(q).results }.getOrDefault(emptyList())
                    .filter { it.mediaType == "movie" || it.mediaType == "tv" }
                    .filter { it.status != com.sridhar.harbor.data.seerr.MediaStatus.Available }
                    .filterNot(c.parental::hideAdult)
            }
            val found = runCatching { c.jellyfin.searchTitles(q) }.getOrNull()
            results = found?.items.orEmpty().filterNot(c.parental::hideAdult)
            var online = s.await()
            // Online (TMDB via Jellyseerr): a misspelt query often finds nothing – retry with the closest spelling.
            if (online.isEmpty() && c.settings.current().seerrReady) found?.suggestions?.firstOrNull()?.let { alt ->
                online = runCatching { c.seerr.search(alt).results }.getOrDefault(emptyList())
                    .filter { (it.mediaType == "movie" || it.mediaType == "tv") && it.status != com.sridhar.harbor.data.seerr.MediaStatus.Available }
            }
            seerr = online
            // Suggestions: library near-misses, plus what TMDB thinks you meant when the library has nothing.
            val fromTmdb = if (results.isEmpty()) online.mapNotNull { it.displayTitle.takeIf { t -> t.isNotBlank() } }.take(2) else emptyList()
            val hiddenNames = if (c.parental.hidingAdult) (found?.items.orEmpty().filter(c.parental::hideAdult).map { it.name.lowercase() }).toSet() else emptySet()
            suggestions = (found?.suggestions.orEmpty().filterNot { it.lowercase() in hiddenNames } + fromTmdb).distinctBy { it.lowercase() }
                .filter { com.sridhar.harbor.data.jellyfin.TitleMatcher.match(q, it) < 0.97f }.take(4)
        }
        loading = false
    }

    fun request(m: com.sridhar.harbor.data.seerr.SeerrMedia) = viewModelScope.launch(com.sridhar.harbor.CrashGuard) {
        requested[m.id] = "…"
        runCatching {
            val seasons = if (m.mediaType == "tv") c.seerr.details("tv", m.id).seasons.map { it.seasonNumber }.filter { it > 0 } else null
            c.seerr.request(m.mediaType, m.id, seasons)
        }.onSuccess { requested[m.id] = "sent" }.onFailure { requested[m.id] = it.message ?: "failed" }
    }

    fun smartSearch() = viewModelScope.launch(com.sridhar.harbor.CrashGuard) {
        loading = true; smart = true
        results = runCatching { c.assistant.smartSearch(query) }.getOrDefault(emptyList())
        loading = false
    }
}

@OptIn(FlowPreview::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(onItem: (String) -> Unit, onBack: () -> Unit, onSeerr: (String, Int) -> Unit = { _, _ -> }) {
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
        if (vm.query.length >= 2 && !vm.loading && vm.results.isEmpty() && vm.seerr.isEmpty() && vm.suggestions.isEmpty())
            MessageState(stringResource(R.string.no_matches), stringResource(R.string.nothing_in_your_library_matches_1, vm.query), Modifier.padding(pad), icon = Icons.Rounded.Search)
        LazyVerticalGrid(
            GridCells.Adaptive(112.dp), Modifier.fillMaxSize().padding(pad).padding(top = if (vm.query.trim().contains(' ')) 44.dp else 0.dp),
            contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // "Did you mean…" – tap a spelling to search for it.
            if (vm.suggestions.isNotEmpty()) item(key = "didyoumean", span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                Column(Modifier.animateItem()) {
                    Text(stringResource(R.string.search_did_you_mean), color = Harbor.TextDim, style = androidx.compose.material3.MaterialTheme.typography.labelLarge)
                    androidx.compose.foundation.layout.FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        vm.suggestions.forEach { sug ->
                            androidx.compose.material3.SuggestionChip({ vm.query = sug }, { Text(sug, maxLines = 1) },
                                icon = { Icon(Icons.Rounded.Search, null, Modifier.sizeIn(maxWidth = 16.dp, maxHeight = 16.dp)) })
                        }
                    }
                }
            }
            itemsIndexed(vm.results, key = { _, it -> it.id }) { i, item ->
                Box(Modifier.animateItem().enterRise(i)) { PosterCard(
                    container.jellyfin.posterUrl(cfg, item), item.name,
                    listOfNotNull(item.type.takeIf { it != "Movie" }, item.seriesName, item.year?.toString()).joinToString(" · "),
                    width = 200.dp,
                ) { onItem(item.id) } }
            }
            // Not in the library: Jellyseerr matches, one tap from a request.
            if (vm.seerr.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "seerr-head") {
                    Column(Modifier.padding(top = if (vm.results.isEmpty()) 0.dp else 8.dp)) {
                        Text(stringResource(R.string.search_seerr_title), style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.search_seerr_hint), color = Harbor.TextDim, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                    }
                }
                itemsIndexed(vm.seerr, key = { _, m -> "seerr-${m.mediaType}-${m.id}" }) { i, m ->
                    Box(Modifier.animateItem().enterRise(i)) {
                        PosterCard(
                            com.sridhar.harbor.data.seerr.SeerrRepository.tmdb(m.posterPath, "w342"), m.displayTitle,
                            listOfNotNull(if (m.mediaType == "tv") stringResource(R.string.series) else stringResource(R.string.movie), m.year).joinToString(" · "),
                            width = 200.dp,
                            badge = { SeerrBadge(m.status) },
                        ) { onSeerr(m.mediaType, m.id) }
                    }
                }
            }
        }
    }
}

/** Corner badge on a Jellyseerr result: requestable, or where its request stands. */
@Composable
fun SeerrBadge(status: com.sridhar.harbor.data.seerr.MediaStatus) {
    val (text, color) = when (status) {
        com.sridhar.harbor.data.seerr.MediaStatus.Unknown -> stringResource(R.string.search_seerr_request) to Harbor.Violet
        com.sridhar.harbor.data.seerr.MediaStatus.Pending, com.sridhar.harbor.data.seerr.MediaStatus.Processing -> stringResource(R.string.search_seerr_requested) to Harbor.Amber
        com.sridhar.harbor.data.seerr.MediaStatus.Partial -> stringResource(R.string.search_seerr_partial) to Harbor.Mint
        else -> status.label to Harbor.TextDim
    }
    Text(text, color = androidx.compose.ui.graphics.Color.White, fontSize = 10.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
        modifier = Modifier.padding(6.dp).clip(RoundedCornerShape(6.dp)).background(color).padding(horizontal = 6.dp, vertical = 2.dp))
}
