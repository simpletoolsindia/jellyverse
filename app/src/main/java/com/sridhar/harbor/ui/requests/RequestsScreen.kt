package com.sridhar.harbor.ui.requests

import com.sridhar.harbor.L10n
import com.sridhar.harbor.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sridhar.harbor.data.AppContainer
import com.sridhar.harbor.data.seerr.MediaStatus
import com.sridhar.harbor.data.seerr.Permission
import com.sridhar.harbor.data.seerr.RequestCount
import com.sridhar.harbor.data.seerr.RequestStatus
import com.sridhar.harbor.data.seerr.SeerrDetails
import com.sridhar.harbor.data.seerr.SeerrRepository
import com.sridhar.harbor.data.seerr.SeerrRepository.Companion.tmdb
import com.sridhar.harbor.data.seerr.SeerrRequest
import com.sridhar.harbor.data.seerr.SeerrUser
import com.sridhar.harbor.ui.components.LocalContainer
import com.sridhar.harbor.ui.components.MessageState
import com.sridhar.harbor.ui.components.NetImage
import com.sridhar.harbor.ui.components.Pill
import com.sridhar.harbor.ui.components.friendly
import com.sridhar.harbor.ui.components.relativeTime
import com.sridhar.harbor.ui.components.rememberConfig
import com.sridhar.harbor.ui.theme.Harbor
import kotlinx.coroutines.launch

enum class RFilter(@androidx.annotation.StringRes val labelRes: Int, val key: String) {
    All(R.string.all, "all"), Pending(R.string.pending, "pending"), Approved(R.string.approved, "approved"),
    Processing(R.string.processing, "processing"), Available(R.string.available, "available"), Failed(R.string.failed, "failed"),;
    val label: String get() = com.sridhar.harbor.L10n.s(labelRes)
}

class RequestsViewModel(private val c: AppContainer) : ViewModel() {
    var requests by mutableStateOf<List<SeerrRequest>>(emptyList()); private set
    var counts by mutableStateOf<RequestCount?>(null); private set
    var me by mutableStateOf<SeerrUser?>(null); private set
    var filter by mutableStateOf(RFilter.All); private set
    var loading by mutableStateOf(false); private set
    var refreshing by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var message by mutableStateOf<String?>(null)
    private var total = Int.MAX_VALUE

    val canManage get() = me?.has(Permission.MANAGE_REQUESTS) == true

    init { reload() }

    fun selectFilter(f: RFilter) { filter = f; reload() }

    fun reload(pull: Boolean = false) = viewModelScope.launch(com.sridhar.harbor.CrashGuard) {
        if (pull) refreshing = true
        requests = emptyList(); total = Int.MAX_VALUE; error = null
        runCatching { me = c.seerr.me(); counts = runCatching { c.seerr.requestCount() }.getOrNull() }
        page()
        refreshing = false
    }

    fun more() = viewModelScope.launch(com.sridhar.harbor.CrashGuard) { page() }

    private suspend fun page() {
        if (loading || requests.size >= total) return
        loading = true
        runCatching { c.seerr.requests(filter.key, requests.size) }
            .onSuccess { p -> requests = requests + p.results; total = p.pageInfo.results }
            .onFailure { error = it.friendly() }
        loading = false
    }

    suspend fun details(r: SeerrRequest): SeerrDetails? =
        runCatching { c.seerr.details(if (r.type == "tv") "tv" else "movie", r.media.tmdbId) }.getOrNull()

    private fun act(msg: String, block: suspend () -> Unit) = viewModelScope.launch(com.sridhar.harbor.CrashGuard) {
        runCatching { block() }.onSuccess { message = msg; reload() }.onFailure { message = it.friendly() }
    }

    fun approve(r: SeerrRequest) = act(L10n.s(R.string.approved_sent_to_radarr_sonarr)) { c.seerr.approve(r.id) }
    fun decline(r: SeerrRequest) = act(L10n.s(R.string.declined)) { c.seerr.decline(r.id) }
    fun retry(r: SeerrRequest) = act(L10n.s(R.string.retrying)) { c.seerr.retry(r.id) }
    fun delete(r: SeerrRequest) = act(L10n.s(R.string.request_removed)) { c.seerr.deleteRequest(r.id) }
}

@Composable
fun RequestsScreen(onOpen: (String, Int) -> Unit, onSetup: () -> Unit) {
    val container = LocalContainer.current
    val cfg = rememberConfig()
    if (!cfg.seerrReady) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            MessageState(stringResource(R.string.jellyseerr_isn_t_connected), stringResource(R.string.requests_live_in_jellyseerr), icon = Icons.Rounded.Inbox, onRetry = onSetup, actionLabel = stringResource(R.string.set_up))
        }
        return
    }
    val vm = viewModel { RequestsViewModel(container) }
    val list = rememberLazyListState()
    val nearEnd by remember { derivedStateOf { (list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) > list.layoutInfo.totalItemsCount - 5 } }
    LaunchedEffect(nearEnd) { if (nearEnd) vm.more() }
    val snack = remember { androidx.compose.material3.SnackbarHostState() }
    LaunchedEffect(vm.message) { vm.message?.let { snack.showSnackbar(it); vm.message = null } }

    PullToRefreshBox(vm.refreshing, { vm.reload(pull = true) }, Modifier.fillMaxSize().background(Harbor.Ink)) {
        LazyColumn(Modifier.fillMaxSize(), list, contentPadding = PaddingValues(bottom = 120.dp + com.sridhar.harbor.ui.components.LocalMiniPlayerInset.current)) {
            item {
                Column(Modifier.statusBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
                    Text(stringResource(R.string.requests), style = MaterialTheme.typography.headlineLarge)
                    vm.me?.let { Text(if (vm.canManage) stringResource(R.string.managing_requests_for_everyone) else stringResource(R.string.your_requests), color = Harbor.TextDim) }
                }
            }
            vm.counts?.let { c ->
                item {
                    Row(Modifier.padding(horizontal = 20.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatTile(stringResource(R.string.pending), c.pending, Harbor.Amber, Modifier.weight(1f)) { vm.selectFilter(RFilter.Pending) }
                        StatTile(stringResource(R.string.processing), c.processing, Harbor.Sky, Modifier.weight(1f)) { vm.selectFilter(RFilter.Processing) }
                        StatTile(stringResource(R.string.available), c.available, Harbor.Mint, Modifier.weight(1f)) { vm.selectFilter(RFilter.Available) }
                    }
                }
            }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(RFilter.entries) { f ->
                        FilterChip(vm.filter == f, { vm.selectFilter(f) }, { Text(f.label) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Harbor.Coral.copy(alpha = .3f)))
                    }
                }
            }
            if (vm.error != null && vm.requests.isEmpty()) item { MessageState(stringResource(R.string.couldn_t_load_requests), vm.error, onRetry = { vm.reload() }) }
            else if (!vm.loading && vm.requests.isEmpty()) item { MessageState(stringResource(R.string.no_requests), stringResource(R.string.find_something_in_discover_and_request), icon = Icons.Rounded.Inbox) }
            items(vm.requests, key = { it.id }) { r ->
                RequestCard(r, vm, cfg, onOpen)
            }
            if (vm.loading) item { Box(Modifier.fillMaxWidth().padding(24.dp), Alignment.Center) { com.sridhar.harbor.ui.components.JellyLoader() } }
        }
        androidx.compose.material3.SnackbarHost(snack, Modifier.align(Alignment.BottomCenter).padding(bottom = 100.dp + com.sridhar.harbor.ui.components.LocalMiniPlayerInset.current))
    }
}

@Composable
private fun StatTile(label: String, value: Int, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(18.dp)).background(color.copy(alpha = .1f)).border(1.dp, color.copy(alpha = .25f), RoundedCornerShape(18.dp))
        .clickable(onClick = onClick).padding(14.dp)) {
        Text("$value", fontSize = 26.sp, fontWeight = FontWeight.Black, color = color)
        Text(label, style = MaterialTheme.typography.bodySmall, color = Harbor.TextDim)
    }
}

@Composable
private fun RequestCard(r: SeerrRequest, vm: RequestsViewModel, cfg: com.sridhar.harbor.data.ServerConfig, onOpen: (String, Int) -> Unit) {
    val details by produceState<SeerrDetails?>(null, r.id) { value = vm.details(r) }
    val rs = RequestStatus.of(r.status)
    val ms = MediaStatus.of(r.media.status)
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    Box(Modifier.padding(horizontal = 20.dp, vertical = 6.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Harbor.Surface)
        .clickable { onOpen(if (r.type == "tv") "tv" else "movie", r.media.tmdbId) }) {
        NetImage(tmdb(details?.backdropPath, "w780"), Modifier.matchParentSize())
        Box(Modifier.matchParentSize().background(Brush.horizontalGradient(listOf(Harbor.Surface, Harbor.Surface.copy(alpha = .92f), Harbor.Surface.copy(alpha = .7f)))))
        Row(Modifier.padding(14.dp)) {
            Box(Modifier.width(72.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(10.dp))) {
                NetImage(tmdb(details?.posterPath, "w185"), Modifier.fillMaxSize(), fallback = details?.displayTitle)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(details?.displayTitle ?: stringResource(R.string.loading_2), fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(listOfNotNull(details?.year, if (r.type == "tv") stringResource(R.string.series) else stringResource(R.string.movie), if (r.is4k) "4K" else null).joinToString(" · "),
                    color = Harbor.TextDim, style = MaterialTheme.typography.bodySmall)
                if (r.type == "tv" && r.seasons.isNotEmpty()) Text(stringResource(R.string.seasons_2) + r.seasons.joinToString(", ") { "${it.seasonNumber}" },
                    color = Harbor.TextDim, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill(rs.label, when (rs) { RequestStatus.Pending -> Harbor.Amber; RequestStatus.Approved, RequestStatus.Completed -> Harbor.Mint; else -> Harbor.Rose })
                    if (rs == RequestStatus.Approved && ms != MediaStatus.Unknown) Pill(ms.label, if (ms == MediaStatus.Available) Harbor.Mint else Harbor.Sky)
                }
                Spacer(Modifier.height(8.dp))
                r.requestedBy?.let { u ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(20.dp).clip(CircleShape)) { NetImage(SeerrRepository.avatar(cfg, u.avatar), Modifier.fillMaxSize(), fallback = u.name.take(1)) }
                        Spacer(Modifier.width(6.dp))
                        Text("${u.name} · ${relativeTime(r.createdAt)}", style = MaterialTheme.typography.bodySmall, color = Harbor.TextDim, maxLines = 1)
                    }
                }
                val own = r.requestedBy?.id == vm.me?.id
                if (vm.canManage || (own && rs == RequestStatus.Pending)) {
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (vm.canManage && rs == RequestStatus.Pending) {
                            ActionPill(Icons.Rounded.Check, stringResource(R.string.approve), Harbor.Mint) { vm.approve(r) }
                            ActionPill(Icons.Rounded.Close, stringResource(R.string.decline), Harbor.Rose) { vm.decline(r) }
                        }
                        if (vm.canManage && rs == RequestStatus.Failed) ActionPill(Icons.Rounded.Refresh, stringResource(R.string.retry), Harbor.Sky) { vm.retry(r) }
                        ActionPill(Icons.Rounded.Delete, stringResource(R.string.remove), Harbor.TextDim) { vm.delete(r) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionPill(icon: ImageVector, label: String, color: Color, onClick: () -> Unit) {
    Row(Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = .15f)).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = color, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp))
        Text(label, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}
