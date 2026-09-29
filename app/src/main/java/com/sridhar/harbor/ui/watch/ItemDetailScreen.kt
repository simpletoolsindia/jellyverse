package com.sridhar.harbor.ui.watch

import androidx.compose.material.icons.rounded.Downloading
import androidx.compose.runtime.mutableIntStateOf
import com.sridhar.harbor.L10n
import com.sridhar.harbor.R
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.testTag
import androidx.compose.material.icons.rounded.MoreVert
import android.widget.Toast
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sridhar.harbor.data.AppContainer
import com.sridhar.harbor.data.jellyfin.BaseItem
import com.sridhar.harbor.ui.components.GradientButton
import com.sridhar.harbor.ui.components.LocalContainer
import com.sridhar.harbor.ui.components.MessageState
import com.sridhar.harbor.ui.components.NetImage
import com.sridhar.harbor.ui.components.Pill
import com.sridhar.harbor.ui.components.PosterCard
import com.sridhar.harbor.ui.components.ProgressStrip
import com.sridhar.harbor.ui.components.Rail
import com.sridhar.harbor.ui.components.formatBytes
import com.sridhar.harbor.ui.components.formatRuntime
import com.sridhar.harbor.ui.components.friendly
import com.sridhar.harbor.ui.components.glass
import com.sridhar.harbor.ui.components.rememberConfig
import com.sridhar.harbor.ui.player.PlayerActivity
import com.sridhar.harbor.ui.theme.Harbor
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

class ItemDetailViewModel(private val c: AppContainer, private val id: String) : ViewModel() {
    var item by mutableStateOf<BaseItem?>(null); private set
    var error by mutableStateOf<String?>(null); private set
    var seasons by mutableStateOf<List<BaseItem>>(emptyList()); private set
    var selectedSeason by mutableIntStateOf(0); private set
    var episodes by mutableStateOf<List<BaseItem>>(emptyList()); private set
    var episodesLoading by mutableStateOf(false); private set
    var similar by mutableStateOf<List<BaseItem>>(emptyList()); private set
    var nextUp by mutableStateOf<BaseItem?>(null); private set
    val offline = c.offline.entries
    var isAdmin by mutableStateOf(false); private set
    var deleted by mutableStateOf(false); private set

    init { load(); viewModelScope.launch(com.sridhar.harbor.CrashGuard) { isAdmin = c.jellyfin.isAdmin() } }

    /** Admin: re-download metadata and artwork for this item. */
    fun refreshMetadata(toast: (String) -> Unit) = viewModelScope.launch(com.sridhar.harbor.CrashGuard) {
        runCatching { c.jellyfin.refreshItem(id) }.onSuccess { toast(L10n.s(R.string.refreshing_metadata)) }.onFailure { toast(it.friendly()) }
    }

    /** Admin: delete the item and its files from the server. */
    fun deleteFromServer(toast: (String) -> Unit) = viewModelScope.launch(com.sridhar.harbor.CrashGuard) {
        runCatching { c.admin.deleteItem(id) }.onSuccess { toast(L10n.s(R.string.deleted_from_server)); deleted = true }.onFailure { toast(it.friendly()) }
    }

    fun load() = viewModelScope.launch(com.sridhar.harbor.CrashGuard) {
        error = null
        runCatching {
            var it = c.jellyfin.item(id)
            var preferSeason: String? = null
            if (it.type == "Season" && it.seriesId != null) { preferSeason = it.id; it = c.jellyfin.item(it.seriesId!!) }
            item = it
            val sim = async { runCatching { c.jellyfin.similar(it.id) }.getOrDefault(emptyList()) }
            if (it.type == "Series") {
                val next = async { runCatching { c.jellyfin.nextUpFor(it.id) }.getOrNull() }
                seasons = c.jellyfin.seasons(it.id)
                nextUp = next.await()
                val target = preferSeason ?: nextUp?.seasonId
                selectedSeason = seasons.indexOfFirst { s -> s.id == target }.coerceAtLeast(0)
                loadEpisodes()
            }
            similar = sim.await()
        }.onFailure { error = it.friendly() }
    }

    fun selectSeason(i: Int) { selectedSeason = i; loadEpisodes() }

    private fun loadEpisodes() = viewModelScope.launch(com.sridhar.harbor.CrashGuard) {
        val series = item ?: return@launch
        val season = seasons.getOrNull(selectedSeason) ?: return@launch
        episodesLoading = true
        episodes = runCatching { c.jellyfin.episodes(series.id, season.id) }.getOrDefault(emptyList())
        episodesLoading = false
    }

    fun togglePlayed() = viewModelScope.launch(com.sridhar.harbor.CrashGuard) {
        val it = item ?: return@launch
        val now = !(it.userData?.played ?: false)
        runCatching { c.jellyfin.setPlayed(it.id, now) }.onSuccess { load() }
    }

    fun toggleFavorite() = viewModelScope.launch(com.sridhar.harbor.CrashGuard) {
        val it = item ?: return@launch
        val now = !(it.userData?.isFavorite ?: false)
        runCatching { c.jellyfin.setFavorite(it.id, now) }
            .onSuccess { _ -> item = it.copy(userData = (it.userData ?: com.sridhar.harbor.data.jellyfin.UserData()).copy(isFavorite = now)) }
    }

    fun toggleEpisodePlayed(ep: BaseItem) = viewModelScope.launch(com.sridhar.harbor.CrashGuard) {
        runCatching { c.jellyfin.setPlayed(ep.id, !(ep.userData?.played ?: false)) }.onSuccess { loadEpisodes() }
    }

    fun download(items: List<BaseItem>, onDone: (String) -> Unit) = viewModelScope.launch(com.sridhar.harbor.CrashGuard) {
        var ok = 0
        items.forEach { stub ->
            runCatching {
                // Episodes from list calls may lack MediaSources; fetch full item for container info.
                val full = if (stub.mediaSources.isEmpty()) c.jellyfin.item(stub.id) else stub
                c.offline.download(full, c.http)
            }.onSuccess { ok++ }
        }
        onDone(if (ok == items.size) "Downloading $ok item${if (ok == 1) "" else "s"}" else "Queued $ok of ${items.size}")
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ItemDetailScreen(id: String, onItem: (String) -> Unit, onBack: () -> Unit, onDoctor: (String) -> Unit = {}) {
    val container = LocalContainer.current
    val cfg = rememberConfig()
    val ctx = LocalContext.current
    val vm = viewModel(key = "item-$id") { ItemDetailViewModel(container, id) }
    val jf = container.jellyfin
    val offline by vm.offline.collectAsState(emptyList())
    val parental by container.parental.state.collectAsState()
    val ctxLock = androidx.compose.ui.platform.LocalContext.current
    var lockTarget by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var lockStep by remember { mutableStateOf(0) }   // 1 = create PIN, 2 = verify PIN, 3 = apply
    val lockedMsg = stringResource(R.string.locked_toast); val unlockedMsg = stringResource(R.string.unlocked_toast); val wrongPin = stringResource(R.string.pin_wrong)
    androidx.compose.runtime.LaunchedEffect(lockStep) {
        if (lockStep == 3) lockTarget?.let { (id, lock) -> container.parental.setLocked(id, lock); android.widget.Toast.makeText(ctxLock, if (lock) lockedMsg else unlockedMsg, android.widget.Toast.LENGTH_SHORT).show() }
        if (lockStep == 3) { lockStep = 0; lockTarget = null }
    }
    when (lockStep) {
        1 -> com.sridhar.harbor.ui.components.CreatePinDialog(onDismiss = { lockStep = 0 }) { pin -> container.parental.setPin(pin); lockStep = 3 }
        2 -> com.sridhar.harbor.ui.components.PinDialog(stringResource(R.string.pin_enter), null, onDismiss = { lockStep = 0 }) { if (container.parental.verify(it)) { lockStep = 3; null } else wrongPin }
    }
    // Finished downloads vs ones still in progress (the offline index lists both).
    val dlState by container.downloader.state.collectAsState()
    val downloadedIds = remember(offline, dlState) { offline.filter { container.offline.progress(it).done }.map { it.itemId }.toSet() }
    val downloadingIds = remember(offline, dlState) { offline.filter { container.offline.progress(it).let { p -> !p.done } }.map { it.itemId }.toSet() }
    val list = rememberLazyListState()
    val toast: (String) -> Unit = { Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show() }

    val item = vm.item
    Box(Modifier.fillMaxSize().background(Harbor.Ink)) {
        if (item == null) {
            if (vm.error != null) MessageState(stringResource(R.string.couldn_t_load), vm.error, Modifier.align(Alignment.Center), onRetry = { vm.load() })
            else com.sridhar.harbor.ui.components.JellyLoader(Modifier.align(Alignment.Center))
        } else LazyColumn(Modifier.fillMaxSize(), list, contentPadding = PaddingValues(bottom = 48.dp)) {
            item(key = "header") {
                Box(Modifier.fillMaxWidth().height(440.dp)) {
                    NetImage(jf.backdropUrl(cfg, item, 1600), Modifier.fillMaxSize().graphicsLayer {
                        // Parallax: backdrop drifts slower than the list
                        val off = if (list.firstVisibleItemIndex == 0) list.firstVisibleItemScrollOffset.toFloat() else 0f
                        translationY = off * 0.5f
                        val z = 1f + (off / 4000f).coerceIn(0f, 0.12f); scaleX = z; scaleY = z
                        alpha = 1f - (off / 900f).coerceIn(0f, 0.6f)
                    })
                    Box(Modifier.fillMaxSize().background(Harbor.scrimBottom()))
                    Row(Modifier.align(Alignment.BottomStart).padding(20.dp), verticalAlignment = Alignment.Bottom) {
                        Box(Modifier.width(110.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(14.dp))) {
                            NetImage(jf.posterUrl(cfg, item), Modifier.fillMaxSize(), fallback = item.name)
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            if (item.seriesName != null) Text(item.seriesName, color = Harbor.VioletSoft, fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.clickable { item.seriesId?.let(onItem) })
                            Text(item.name, style = MaterialTheme.typography.headlineMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                listOfNotNull(item.episodeLabel, item.year?.toString(), formatRuntime(item.runtimeMinutes), item.officialRating)
                                    .joinToString("  ·  "),
                                color = Harbor.TextDim, style = MaterialTheme.typography.bodyMedium,
                            )
                            item.communityRating?.let { r ->
                                Spacer(Modifier.height(6.dp)); Pill("★ %.1f".format(r), Harbor.Amber)
                            }
                        }
                    }
                }
            }
            item(key = "actions") {
                val playTarget = if (item.type == "Series") vm.nextUp else item
                val resumeMs = (playTarget?.userData?.positionTicks ?: 0) / 10_000
                Column(Modifier.padding(horizontal = 20.dp)) {
                    if (playTarget != null) {
                        GradientButton(
                            when {
                                item.type == "Series" -> "Play ${playTarget.episodeLabel ?: ""}".trim()
                                resumeMs > 0 -> stringResource(R.string.resume)
                                else -> stringResource(R.string.play)
                            },
                            onClick = { PlayerActivity.start(ctx, playTarget.id) },
                            modifier = Modifier.fillMaxWidth(), icon = Icons.Rounded.PlayArrow,
                        )
                        if (item.type != "Series" && item.progress > 0f) {
                            Spacer(Modifier.height(8.dp))
                            com.sridhar.harbor.ui.components.GradientProgress(item.progress, height = 4.dp)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.fillMaxWidth().widthIn(max = 520.dp).align(Alignment.CenterHorizontally)) {
                        if (item.type != "Series" && resumeMs > 0)
                            ActionIcon(Icons.Rounded.Replay, stringResource(R.string.start_over)) { PlayerActivity.start(ctx, item.id, fromStart = true) }
                        val played = item.userData?.played == true
                        ActionIcon(if (played) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                            if (played) stringResource(R.string.watched_2) else stringResource(R.string.mark_watched), if (played) Harbor.Mint else Harbor.Fg) { vm.togglePlayed() }
                        val fav = item.userData?.isFavorite == true
                        ActionIcon(if (fav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, stringResource(R.string.favorite),
                            if (fav) Harbor.Rose else Harbor.Fg) { vm.toggleFavorite() }
                        // Parental lock: needs the PIN to change, and then to play.
                        val locked = parental.locked.contains(item.id)
                        ActionIcon(if (locked) Icons.Rounded.Lock else Icons.Rounded.LockOpen, stringResource(if (locked) R.string.unlock_title else R.string.lock_title),
                            if (locked) Harbor.Amber else Harbor.Fg) {
                            lockTarget = item.id to !locked
                            lockStep = when { !parental.enabled -> 1; !container.parental.isUnlocked() -> 2; else -> 3 }
                        }
                        when {
                            item.type == "Series" -> ActionIcon(Icons.Rounded.Download, stringResource(R.string.get_season)) {
                                vm.download(vm.episodes.filter { it.id !in downloadedIds }, toast)
                            }
                            item.id in downloadedIds -> ActionIcon(Icons.Rounded.DownloadDone, stringResource(R.string.downloaded), Harbor.Mint) { toast(L10n.s(R.string.already_saved_offline)) }
                            item.id in downloadingIds -> ActionIcon(Icons.Rounded.Downloading, container.offline.entriesNow().firstOrNull { it.itemId == item.id }
                                ?.let { "${(container.offline.progress(it).fraction * 100).toInt()}%" } ?: stringResource(R.string.downloading_short), Harbor.Sky) { toast(L10n.s(R.string.dl_in_progress)) }
                            else -> ActionIcon(Icons.Rounded.Download, stringResource(R.string.download)) { vm.download(listOf(item), toast) }
                        }
                    }
                }
            }
            item(key = "overview") {
                Column(Modifier.padding(20.dp)) {
                    item.taglines.firstOrNull()?.let {
                        Text(it, style = MaterialTheme.typography.titleMedium, color = Harbor.VioletSoft); Spacer(Modifier.height(8.dp))
                    }
                    var expanded by remember { mutableStateOf(false) }
                    item.overview?.let {
                        Text(it, color = Harbor.Fg.copy(alpha = .85f), maxLines = if (expanded) Int.MAX_VALUE else 4,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.animateContentSize().clickable { expanded = !expanded })
                    }
                    if (item.genres.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            item.genres.forEach { Pill(it, Harbor.VioletSoft) }
                        }
                    }
                }
            }
            if (item.type == "Series" && vm.seasons.isNotEmpty()) {
                item(key = "seasons") {
                    ScrollableTabRow(
                        vm.selectedSeason, containerColor = Color.Transparent, edgePadding = 20.dp, divider = {},
                        indicator = { pos ->
                            if (vm.selectedSeason < pos.size) Box(Modifier.tabIndicatorOffset(pos[vm.selectedSeason]).height(3.dp)
                                .padding(horizontal = 12.dp).clip(RoundedCornerShape(50)).background(Harbor.accentH))
                        },
                    ) {
                        vm.seasons.forEachIndexed { i, s ->
                            Tab(i == vm.selectedSeason, { vm.selectSeason(i) }, text = { Text(s.name, fontWeight = FontWeight.SemiBold) })
                        }
                    }
                }
                if (vm.episodesLoading) item { Box(Modifier.fillMaxWidth().padding(24.dp), Alignment.Center) { com.sridhar.harbor.ui.components.JellyLoader() } }
                items(vm.episodes, key = { "ep-${it.id}" }) { ep ->
                    EpisodeRow(
                        ep, jf.thumbUrl(cfg, ep, 480), ep.id in downloadedIds,
                        onPlay = { PlayerActivity.start(ctx, ep.id) },
                        onDownload = { vm.download(listOf(ep), toast) },
                        onTogglePlayed = { vm.toggleEpisodePlayed(ep) },
                    )
                }
            }
            if (item.people.isNotEmpty()) item(key = "cast") {
                Rail(stringResource(R.string.cast_crew), item.people.take(20), key = { it.id + it.role }) { p ->
                    Column(Modifier.width(84.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(72.dp).clip(CircleShape)) { NetImage(jf.personUrl(cfg, p), Modifier.fillMaxSize(), fallback = p.name.take(1)) }
                        Spacer(Modifier.height(6.dp))
                        Text(p.name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(p.role ?: p.type.orEmpty(), style = MaterialTheme.typography.bodySmall, color = Harbor.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            item.mediaSources.firstOrNull()?.let { src ->
                item(key = "media") { MediaInfoCard(src) }
            }
            item(key = "similar") {
                Rail(stringResource(R.string.more_like_this), vm.similar, key = { it.id }) { s ->
                    PosterCard(jf.posterUrl(cfg, s), s.name, s.year?.toString()) { onItem(s.id) }
                }
            }
        }
        IconButton(onBack, Modifier.statusBarsPadding().padding(12.dp).glass(RoundedCornerShape(50))) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back), tint = Harbor.Fg)
        }
        if (vm.isAdmin && item != null) AdminItemMenu(
            name = item.name,
            onRefresh = { vm.refreshMetadata(toast) },
            onDelete = { vm.deleteFromServer(toast) },
            onDoctor = if (item.type in setOf("Movie", "Series", "Episode")) ({ onDoctor(item.seriesId ?: item.id) }) else null,
            modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.dp),
        )
    }
    LaunchedEffect(vm.deleted) { if (vm.deleted) onBack() }
}

@Composable
private fun AdminItemMenu(name: String, onRefresh: () -> Unit, onDelete: () -> Unit, onDoctor: (() -> Unit)?, modifier: Modifier) {
    var open by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    Box(modifier) {
        IconButton({ open = true }, Modifier.glass(RoundedCornerShape(50)).testTag("item_admin_menu")) { Icon(Icons.Rounded.MoreVert, stringResource(R.string.admin_actions), tint = Harbor.Fg) }
        androidx.compose.material3.DropdownMenu(open, { open = false }) {
            androidx.compose.material3.DropdownMenuItem({ Text(stringResource(R.string.refresh_metadata)) }, { open = false; onRefresh() })
            if (onDoctor != null) androidx.compose.material3.DropdownMenuItem({ Text(stringResource(R.string.doctor_fix_this)) }, { open = false; onDoctor() })
            androidx.compose.material3.DropdownMenuItem({ Text(stringResource(R.string.delete_from_server), color = Harbor.Rose) }, { open = false; confirm = true })
        }
    }
    if (confirm) androidx.compose.material3.AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text(stringResource(R.string.delete_1_s_2, name)) },
        text = { Text(stringResource(R.string.the_media_files_are_permanently_deleted)) },
        confirmButton = { androidx.compose.material3.TextButton({ confirm = false; onDelete() }) { Text(stringResource(R.string.delete), color = Harbor.Rose) } },
        dismissButton = { androidx.compose.material3.TextButton({ confirm = false }) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.ActionIcon(icon: ImageVector, label: String, tint: Color = Harbor.Fg, onClick: () -> Unit) {
    // Equal share of the row on every width; labels wrap between words, never mid-word ("Downlo-ad").
    Column(
        Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, label, tint = tint, modifier = Modifier.size(26.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall.copy(lineBreak = androidx.compose.ui.text.style.LineBreak.Heading),
            color = Harbor.TextDim, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
private fun EpisodeRow(
    ep: BaseItem, thumb: String, downloaded: Boolean,
    onPlay: () -> Unit, onDownload: () -> Unit, onTogglePlayed: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onPlay).padding(horizontal = 20.dp, vertical = 10.dp)) {
        Box(Modifier.width(150.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp))) {
            NetImage(thumb, Modifier.fillMaxSize(), fallback = ep.name)
            Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.align(Alignment.Center)
                .size(34.dp).background(Color.Black.copy(alpha = .45f), CircleShape).padding(4.dp))
            if (ep.progress > 0f) ProgressStrip(ep.progress, Modifier.align(Alignment.BottomCenter))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("${ep.indexNumber ?: ""}. ${ep.name}", fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(formatRuntime(ep.runtimeMinutes).orEmpty(), style = MaterialTheme.typography.bodySmall, color = Harbor.TextDim)
            ep.overview?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Harbor.TextDim, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            Row(Modifier.offset(x = (-12).dp)) {
                IconButton(onTogglePlayed, Modifier.size(36.dp)) {
                    val played = ep.userData?.played == true
                    Icon(if (played) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, stringResource(R.string.watched_2),
                        tint = if (played) Harbor.Mint else Harbor.TextDim, modifier = Modifier.size(20.dp))
                }
                IconButton(onDownload, Modifier.size(36.dp), enabled = !downloaded) {
                    Icon(if (downloaded) Icons.Rounded.DownloadDone else Icons.Rounded.Download, stringResource(R.string.download),
                        tint = if (downloaded) Harbor.Mint else Harbor.TextDim, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MediaInfoCard(src: com.sridhar.harbor.data.jellyfin.MediaSource) {
    val video = src.streams.firstOrNull { it.type == "Video" }
    val audio = src.streams.filter { it.type == "Audio" }
    val subs = src.streams.filter { it.type == "Subtitle" }
    Column(Modifier.padding(20.dp).fillMaxWidth().glass().padding(16.dp)) {
        Text(stringResource(R.string.media), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            video?.let { v ->
                val res = when { (v.height ?: 0) >= 2000 -> "4K"; (v.height ?: 0) >= 1000 -> "1080p"; (v.height ?: 0) >= 700 -> "720p"; else -> "${v.height}p" }
                Pill(res, Harbor.Sky); v.codec?.let { Pill(it.uppercase(), Harbor.Sky) }
            }
            audio.firstOrNull()?.codec?.let { Pill(it.uppercase(), Harbor.Coral) }
            src.container?.let { Pill(it.uppercase(), Harbor.TextDim) }
            src.size?.let { Pill(formatBytes(it), Harbor.TextDim) }
        }
        if (audio.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.audio) + audio.joinToString { it.displayTitle ?: it.language ?: L10n.s(R.string.track_1_s, it.index) }, style = MaterialTheme.typography.bodySmall, color = Harbor.TextDim)
        }
        if (subs.isNotEmpty()) Text(stringResource(R.string.subtitles) + subs.joinToString { it.displayTitle ?: it.language ?: L10n.s(R.string.track_1_s, it.index) },
            style = MaterialTheme.typography.bodySmall, color = Harbor.TextDim, maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
}
