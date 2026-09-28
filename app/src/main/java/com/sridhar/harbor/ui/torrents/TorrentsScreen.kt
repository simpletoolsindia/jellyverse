package com.sridhar.harbor.ui.torrents

import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf
import com.sridhar.harbor.L10n
import com.sridhar.harbor.R
import androidx.compose.ui.res.stringResource
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sridhar.harbor.data.IncomingTorrent
import com.sridhar.harbor.data.qbit.AddTorrentRequest
import com.sridhar.harbor.data.qbit.Torrent
import com.sridhar.harbor.data.qbit.TorrentPhase
import com.sridhar.harbor.ui.components.GradientButton
import com.sridhar.harbor.ui.components.GradientProgress
import com.sridhar.harbor.ui.components.LocalContainer
import com.sridhar.harbor.ui.components.MessageState
import com.sridhar.harbor.ui.components.Pill
import com.sridhar.harbor.ui.components.formatBytes
import com.sridhar.harbor.ui.components.formatEta
import com.sridhar.harbor.ui.components.formatSpeed
import com.sridhar.harbor.ui.components.glass
import com.sridhar.harbor.ui.components.rememberConfig
import com.sridhar.harbor.ui.theme.Harbor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun TorrentPhase.color() = when (this) {
    TorrentPhase.Downloading -> Harbor.Sky
    TorrentPhase.Waiting -> Harbor.Amber
    TorrentPhase.Seeding -> Harbor.Mint
    TorrentPhase.Paused -> Harbor.TextDim
    TorrentPhase.Done -> Harbor.VioletSoft
    TorrentPhase.Error -> Harbor.Rose
}

/** Downloads tab: qBittorrent and/or aria2 behind a switcher. */
@Composable
fun TorrentsScreen(onSetup: () -> Unit) {
    val cfg = rememberConfig()
    var client by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(if (cfg.qbitReady || !cfg.aria2Ready) "qbit" else "aria2") }
    val switcher: @Composable () -> Unit = {
        if (cfg.qbitReady && cfg.aria2Ready) Row(Modifier.clip(RoundedCornerShape(50)).background(Harbor.Surface).padding(4.dp)) {
            listOf("qbit" to "qBittorrent", "aria2" to "aria2").forEach { (k, label) ->
                val active = client == k
                Text(label, color = if (active) Color.White else Harbor.TextDim, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(if (active) Harbor.accentH else SolidColor(Color.Transparent))
                        .clickable { client = k }.padding(horizontal = 16.dp, vertical = 8.dp))
            }
        }
    }
    if (client == "aria2" && cfg.aria2Ready) Aria2Pane(switcher) else QbitScreen(onSetup, switcher)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QbitScreen(onSetup: () -> Unit, switcher: @Composable () -> Unit) {
    val container = LocalContainer.current
    val cfg = rememberConfig()
    if (!cfg.qbitReady) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            MessageState(stringResource(R.string.qbittorrent_isn_t_connected), stringResource(R.string.add_your_webui_address_to_manage), icon = Icons.Rounded.SwapVert, onRetry = onSetup, actionLabel = stringResource(R.string.set_up))
        }
        return
    }
    val vm = viewModel { TorrentsViewModel(container) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        vm.loadMeta()
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { while (true) { vm.refresh(); delay(2000) } }
    }

    var detail by remember { mutableStateOf<String?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var showLimits by remember { mutableStateOf(false) }
    var incoming by remember { mutableStateOf<IncomingTorrent?>(null) }
    var confirmDelete by remember { mutableStateOf<List<String>?>(null) }
    var searching by remember { mutableStateOf(false) }
    val snack = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        container.incomingTorrents.collect { incoming = it; showAdd = true }
    }
    LaunchedEffect(vm.message) { vm.message?.let { snack.showSnackbar(it); vm.message = null } }

    Box(Modifier.fillMaxSize().background(Harbor.Ink)) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 140.dp + com.sridhar.harbor.ui.components.LocalMiniPlayerInset.current)) {
            item(key = "head") {
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 20.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.torrents), style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f))
                    // Landscape phones (side rail): "add" lives up here – a floating button would sit on the stats card.
                    if (com.sridhar.harbor.ui.components.LocalBottomBarInset.current == 0.dp)
                        IconButton({ incoming = null; showAdd = true }) { Icon(Icons.Rounded.Add, stringResource(R.string.add_torrent), tint = Harbor.Sky) }
                    IconButton({ searching = !searching; if (!searching) vm.query = "" }) { Icon(Icons.Rounded.Search, stringResource(R.string.search)) }
                    var sortMenu by remember { mutableStateOf(false) }
                    Box {
                        IconButton({ sortMenu = true }) { Icon(Icons.AutoMirrored.Rounded.Sort, stringResource(R.string.sort)) }
                        DropdownMenu(sortMenu, { sortMenu = false }) {
                            TSort.entries.forEach { s ->
                                DropdownMenuItem({ Text(s.label, fontWeight = if (vm.sort == s) FontWeight.Bold else null) }, { vm.sort = s; sortMenu = false })
                            }
                        }
                    }
                }
            }
            item(key = "switch") { Box(Modifier.padding(horizontal = 20.dp)) { switcher() } }
            item(key = "search") {
                AnimatedVisibility(searching) {
                    OutlinedTextField(vm.query, { vm.query = it }, Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                        placeholder = { Text(stringResource(R.string.filter_by_name)) }, singleLine = true, shape = RoundedCornerShape(16.dp),
                        leadingIcon = { Icon(Icons.Rounded.Search, null) })
                }
            }
            item(key = "dash") { SpeedDashboard(vm, onLimits = { showLimits = true }) }
            item(key = "filters") {
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(TFilter.entries) { f ->
                        val n = vm.count(f)
                        if (f == TFilter.All || n > 0) FilterChip(
                            vm.filter == f, { vm.filter = f }, { Text("${f.label}  $n") },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Harbor.Violet.copy(alpha = .3f)),
                        )
                    }
                }
            }
            if (vm.error != null && vm.torrents.isEmpty()) item { MessageState(stringResource(R.string.can_t_reach_qbittorrent), vm.error, onRetry = onSetup) }
            else if (!vm.loaded) item { Box(Modifier.fillMaxWidth().padding(48.dp), Alignment.Center) { com.sridhar.harbor.ui.components.JellyLoader() } }
            else if (vm.visible.isEmpty()) item { MessageState(stringResource(R.string.nothing_here), stringResource(R.string.no_torrents_match_this_filter), icon = Icons.Rounded.Download) }
            items(vm.visible, key = { it.hash }) { t ->
                val selected = t.hash in vm.selection
                TorrentCard(
                    t, selected, selectionMode = vm.selection.isNotEmpty(),
                    modifier = Modifier.animateItem().combinedClickable(
                        onClick = {
                            if (vm.selection.isNotEmpty()) vm.selection = if (selected) vm.selection - t.hash else vm.selection + t.hash
                            else detail = t.hash
                        },
                        onLongClick = { vm.selection = vm.selection + t.hash },
                    ),
                    onToggle = { vm.toggle(t) },
                )
            }
        }

        // Selection action bar or add FAB
        if (vm.selection.isNotEmpty()) {
            Row(
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = com.sridhar.harbor.ui.components.LocalBottomBarInset.current + 8.dp + com.sridhar.harbor.ui.components.LocalMiniPlayerInset.current, start = 16.dp, end = 16.dp)
                    .fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Harbor.SurfaceHigh).padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton({ vm.selection = emptySet() }) { Icon(Icons.Rounded.Close, stringResource(R.string.clear)) }
                Text(stringResource(R.string.s_1_s_selected, vm.selection.size), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton({ vm.selection = vm.visible.map { it.hash }.toSet() }) { Icon(Icons.Rounded.SelectAll, stringResource(R.string.select_all)) }
                IconButton({ vm.resume(vm.selection.toList()) }) { Icon(Icons.Rounded.PlayArrow, stringResource(R.string.resume), tint = Harbor.Mint) }
                IconButton({ vm.pause(vm.selection.toList()) }) { Icon(Icons.Rounded.Pause, stringResource(R.string.pause), tint = Harbor.Amber) }
                IconButton({ confirmDelete = vm.selection.toList() }) { Icon(Icons.Rounded.Delete, stringResource(R.string.delete), tint = Harbor.Rose) }
            }
        } else if (com.sridhar.harbor.ui.components.LocalBottomBarInset.current > 0.dp) {
            Box(
                Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 20.dp, bottom = com.sridhar.harbor.ui.components.LocalBottomBarInset.current + 12.dp + com.sridhar.harbor.ui.components.LocalMiniPlayerInset.current)
                    .size(60.dp).clip(RoundedCornerShape(20.dp)).background(Harbor.accent).clickable { incoming = null; showAdd = true },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Add, stringResource(R.string.add_torrent), tint = Color.White, modifier = Modifier.size(30.dp)) }
        }
        SnackbarHost(snack, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 160.dp + com.sridhar.harbor.ui.components.LocalMiniPlayerInset.current))
    }

    detail?.let { hash ->
        val t = vm.torrents.firstOrNull { it.hash == hash }
        if (t == null) detail = null
        else TorrentDetailSheet(t, vm, onDismiss = { detail = null }, onDelete = { confirmDelete = listOf(hash) })
    }
    if (showAdd) AddTorrentSheet(vm, incoming, onDismiss = { showAdd = false; incoming = null; @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class) container.incomingTorrents.resetReplayCache() })
    if (showLimits) SpeedLimitSheet(vm, onDismiss = { showLimits = false })
    confirmDelete?.let { hashes ->
        var withFiles by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(if (hashes.size == 1) stringResource(R.string.delete_torrent) else stringResource(R.string.delete_1_s_torrents, hashes.size)) },
            text = {
                Column {
                    Text(vm.torrents.filter { it.hash in hashes }.joinToString("\n") { "• " + it.name }.take(400), color = Harbor.TextDim, style = MaterialTheme.typography.bodySmall)
                    Row(Modifier.fillMaxWidth().clickable { withFiles = !withFiles }.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(withFiles, { withFiles = it })
                        Text(stringResource(R.string.also_delete_downloaded_files_from_disk))
                    }
                }
            },
            confirmButton = { TextButton({ vm.delete(hashes, withFiles); confirmDelete = null; detail = null }) { Text(stringResource(R.string.delete), color = Harbor.Rose) } },
            dismissButton = { TextButton({ confirmDelete = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun SpeedDashboard(vm: TorrentsViewModel, onLimits: () -> Unit) {
    val tr = vm.transfer
    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp))
        .background(Brush.linearGradient(listOf(Color(0xFF1C1F27), Color(0xFF16181F))))
        .border(1.dp, Color.White.copy(alpha = .07f), RoundedCornerShape(24.dp))) {
        Box(Modifier.fillMaxWidth().height(150.dp)) {
            @Suppress("UNUSED_EXPRESSION") vm.historyVersion  // recompose graph on new samples
            Sparkline(vm.dlHistory.toList(), vm.ulHistory.toList(), Modifier.fillMaxSize())
            Row(Modifier.padding(18.dp)) {
                SpeedStat(Icons.Rounded.Download, stringResource(R.string.download), tr.dlSpeed, tr.dlLimit, Harbor.Sky, Modifier.weight(1f))
                SpeedStat(Icons.Rounded.Upload, stringResource(R.string.upload), tr.upSpeed, tr.upLimit, Harbor.Coral, Modifier.weight(1f))
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.session_1_s_2_s, formatBytes(tr.dlData), formatBytes(tr.upData)), style = MaterialTheme.typography.bodySmall, color = Harbor.TextDim)
                Text(
                    when (tr.connectionStatus) { "connected" -> L10n.s(R.string.connected); "firewalled" -> L10n.s(R.string.firewalled); "" -> ""; else -> L10n.s(R.string.disconnected) } +
                        if (tr.dhtNodes > 0) stringResource(R.string.s_1_s_dht_nodes, tr.dhtNodes) else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = when (tr.connectionStatus) { "connected" -> Harbor.Mint; "firewalled" -> Harbor.Amber; else -> Harbor.Rose },
                )
            }
            val turtleBg by animateColorAsState(if (vm.altSpeed) Harbor.Mint.copy(alpha = .2f) else Color.White.copy(alpha = .06f), label = "t")
            Row(Modifier.clip(RoundedCornerShape(50)).background(turtleBg).clickable { vm.toggleAlt() }.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text("🐢", fontSize = 14.sp); Spacer(Modifier.width(4.dp))
                Text(if (vm.altSpeed) stringResource(R.string.alt_on) else stringResource(R.string.alt_off), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (vm.altSpeed) Harbor.Mint else Harbor.TextDim)
            }
            Spacer(Modifier.width(8.dp))
            Row(Modifier.clip(RoundedCornerShape(50)).background(Harbor.accentH).clickable(onClick = onLimits).padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Speed, null, tint = Color.White, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.limits), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

@Composable
private fun SpeedStat(icon: ImageVector, label: String, speed: Long, limit: Long, color: Color, modifier: Modifier) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = color, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp))
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = color)
        }
        val parts = formatSpeed(speed).split(" ")
        Row(verticalAlignment = Alignment.Bottom) {
            Text(parts[0], fontSize = 30.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Default, color = Color.White)
            Spacer(Modifier.width(4.dp))
            Text(parts.getOrElse(1) { "" }, color = Harbor.TextDim, modifier = Modifier.padding(bottom = 5.dp))
        }
        Text(if (limit > 0) stringResource(R.string.limit_1_s, formatSpeed(limit)) else stringResource(R.string.unlimited), style = MaterialTheme.typography.bodySmall, color = Harbor.TextDim)
    }
}

@Composable
internal fun SpeedSparkline(dl: List<Long>, ul: List<Long>, modifier: Modifier) = Sparkline(dl, ul, modifier)

@Composable
private fun Sparkline(dl: List<Long>, ul: List<Long>, modifier: Modifier) {
    Canvas(modifier) {
        val max = ((dl + ul).maxOrNull() ?: 0L).coerceAtLeast(64 * 1024).toFloat() * 1.15f
        fun draw(series: List<Long>, color: Color) {
            if (series.size < 2) return
            val step = size.width / 59f
            val startX = size.width - (series.size - 1) * step
            val path = Path()
            series.forEachIndexed { i, v ->
                val x = startX + i * step; val y = size.height - (v / max) * size.height * 0.7f
                if (i == 0) path.moveTo(x, y) else {
                    val px = startX + (i - 1) * step; val py = size.height - (series[i - 1] / max) * size.height * 0.7f
                    path.cubicTo((px + x) / 2, py, (px + x) / 2, y, x, y)
                }
            }
            val fill = Path().apply { addPath(path); lineTo(size.width, size.height); lineTo(startX, size.height); close() }
            drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = .35f), Color.Transparent)))
            drawPath(path, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
            val lastY = size.height - (series.last() / max) * size.height * 0.7f
            drawCircle(color, 3.5.dp.toPx(), Offset(size.width - 1.dp.toPx(), lastY))
        }
        draw(ul, Harbor.Coral)
        draw(dl, Harbor.Sky)
    }
}

@Composable
private fun TorrentCard(t: Torrent, selected: Boolean, selectionMode: Boolean, modifier: Modifier, onToggle: () -> Unit) {
    val color = t.phase.color()
    val progress by animateFloatAsState(t.progress, label = "p")
    Column(
        Modifier.padding(horizontal = 20.dp, vertical = 6.dp).fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) Harbor.Violet.copy(alpha = .18f) else Harbor.Surface)
            .border(1.dp, if (selected) Harbor.Violet else Color.White.copy(alpha = .05f), RoundedCornerShape(20.dp))
            .then(modifier).padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(t.name, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill(t.stateLabel, color)
                    if (t.category.isNotBlank()) Pill(t.category, Harbor.VioletSoft)
                    if (t.sequential) Pill(stringResource(R.string.seq), Harbor.TextDim)
                }
            }
            Spacer(Modifier.width(8.dp))
            if (selectionMode) Icon(if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.CheckCircle, null,
                tint = if (selected) Harbor.Violet else Color.White.copy(alpha = .15f), modifier = Modifier.size(28.dp))
            else Box(Modifier.size(40.dp).clip(CircleShape).background(color.copy(alpha = .15f)).clickable(onClick = onToggle), contentAlignment = Alignment.Center) {
                Icon(if (t.isStopped) Icons.Rounded.PlayArrow else Icons.Rounded.Pause, if (t.isStopped) stringResource(R.string.resume) else stringResource(R.string.pause), tint = color)
            }
        }
        Spacer(Modifier.height(12.dp))
        GradientProgress(progress, height = 6.dp, brush = if (t.phase == TorrentPhase.Downloading) Harbor.accentH else Brush.horizontalGradient(listOf(color, color)))
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${(t.progress * 100).toInt()}%", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text("  ${formatBytes((t.size * t.progress).toLong())} / ${formatBytes(t.size)}", color = Harbor.TextDim, fontSize = 12.sp, modifier = Modifier.weight(1f))
            if (t.dlspeed > 0) Text("↓ ${formatSpeed(t.dlspeed)}  ", color = Harbor.Sky, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            if (t.upspeed > 0) Text("↑ ${formatSpeed(t.upspeed)}", color = Harbor.Coral, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        if (t.phase == TorrentPhase.Downloading || t.phase == TorrentPhase.Seeding) Text(
            buildString {
                if (t.progress < 1f) append(stringResource(R.string.eta_1_s, formatEta(t.eta)))
                append("Ratio ${"%.2f".format(t.ratio)} · ${t.seeds} seeds · ${t.leechers} peers")
            },
            color = Harbor.TextDim, fontSize = 12.sp,
        )
    }
}

@Composable
private fun TorrentDetailSheet(t: Torrent, vm: TorrentsViewModel, onDismiss: () -> Unit, onDelete: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var tab by remember { mutableIntStateOf(0) }
    var showLimits by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismiss, sheetState = sheet, containerColor = Harbor.Surface) {
        LazyColumn(Modifier.fillMaxWidth().navigationBarsPadding(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp)) {
            item {
                Text(t.name, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Pill(t.stateLabel, t.phase.color()); if (t.forceStart) Pill(stringResource(R.string.forced), Harbor.Amber) }
                Spacer(Modifier.height(14.dp))
                GradientProgress(t.progress, height = 8.dp)
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    SheetAction(if (t.isStopped) Icons.Rounded.PlayArrow else Icons.Rounded.Pause, if (t.isStopped) stringResource(R.string.resume) else stringResource(R.string.pause)) { vm.toggle(t) }
                    SheetAction(Icons.Rounded.Speed, stringResource(R.string.limits)) { showLimits = !showLimits }
                    SheetAction(Icons.Rounded.CheckCircle, stringResource(R.string.recheck)) { vm.recheck(t.hash) }
                    SheetAction(Icons.Rounded.SwapVert, stringResource(R.string.announce)) { vm.reannounce(t.hash) }
                    SheetAction(Icons.Rounded.Delete, stringResource(R.string.delete), Harbor.Rose, onDelete)
                }
                AnimatedVisibility(showLimits) {
                    Column(Modifier.padding(top = 12.dp).glass().padding(12.dp)) {
                        var dl by remember(t.hash) { mutableLongStateOf(t.dlLimit.coerceAtLeast(0)) }
                        var up by remember(t.hash) { mutableLongStateOf(t.upLimit.coerceAtLeast(0)) }
                        LimitPicker(stringResource(R.string.download_limit_this_torrent), dl) { dl = it }
                        LimitPicker(stringResource(R.string.upload_limit_this_torrent), up) { up = it }
                        TextButton({ vm.setTorrentLimits(t.hash, dl, up); showLimits = false }, Modifier.align(Alignment.End)) { Text(stringResource(R.string.apply)) }
                    }
                }
                Spacer(Modifier.height(12.dp))
                ToggleRow(stringResource(R.string.sequential_download), stringResource(R.string.download_pieces_in_order_stream_while), t.sequential) { vm.toggleSequential(t.hash) }
                ToggleRow(stringResource(R.string.force_start), stringResource(R.string.ignore_queue_limits), t.forceStart) { vm.forceStart(t) }
                if (vm.categories.isNotEmpty()) {
                    var catMenu by remember { mutableStateOf(false) }
                    Box {
                        Row(Modifier.fillMaxWidth().clickable { catMenu = true }.padding(vertical = 12.dp)) {
                            Text(stringResource(R.string.category), Modifier.weight(1f)); Text(t.category.ifBlank { stringResource(R.string.none) }, color = Harbor.VioletSoft)
                        }
                        DropdownMenu(catMenu, { catMenu = false }) {
                            DropdownMenuItem({ Text(stringResource(R.string.none)) }, { vm.setCategory(t.hash, ""); catMenu = false })
                            vm.categories.forEach { cat -> DropdownMenuItem({ Text(cat.name) }, { vm.setCategory(t.hash, cat.name); catMenu = false }) }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row { listOf("Info", "Files").forEachIndexed { i, s ->
                    FilterChip(tab == i, { tab = i }, { Text(s) }, Modifier.padding(end = 8.dp))
                } }
            }
            if (tab == 0) item {
                Column(Modifier.padding(vertical = 8.dp)) {
                    InfoLine(stringResource(R.string.size), formatBytes(t.size))
                    InfoLine(stringResource(R.string.downloaded), formatBytes(t.downloaded))
                    InfoLine(stringResource(R.string.uploaded), formatBytes(t.uploaded))
                    InfoLine(stringResource(R.string.ratio), "%.2f".format(t.ratio))
                    InfoLine(stringResource(R.string.speed), "↓ ${formatSpeed(t.dlspeed)}   ↑ ${formatSpeed(t.upspeed)}")
                    InfoLine("ETA", formatEta(t.eta))
                    InfoLine(stringResource(R.string.seeds_peers), "${t.seeds} (${t.seedsTotal}) / ${t.leechers} (${t.leechersTotal})")
                    InfoLine(stringResource(R.string.save_path), t.savePath)
                    InfoLine(stringResource(R.string.added), java.text.DateFormat.getDateTimeInstance().format(java.util.Date(t.addedOn * 1000)))
                    if (t.tracker.isNotBlank()) InfoLine(stringResource(R.string.tracker), t.tracker)
                    Spacer(Modifier.height(24.dp))
                }
            } else {
                item { FilesList(t.hash, vm) }
            }
        }
    }
}

@Composable
private fun FilesList(hash: String, vm: TorrentsViewModel) {
    var version by remember { mutableIntStateOf(0) }
    val files by produceState<List<com.sridhar.harbor.data.qbit.TorrentFile>?>(null, hash, version) { value = vm.files(hash) }
    val scope = rememberCoroutineScope()
    Column(Modifier.padding(vertical = 8.dp)) {
        when (val list = files) {
            null -> Box(Modifier.fillMaxWidth().padding(24.dp), Alignment.Center) { com.sridhar.harbor.ui.components.JellyLoader() }
            else -> list.forEach { f ->
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(f.name.substringAfterLast('/'), maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                        Text("${formatBytes(f.size)} · ${(f.progress * 100).toInt()}%", color = Harbor.TextDim, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(4.dp)); GradientProgress(f.progress, height = 3.dp)
                    }
                    Spacer(Modifier.width(8.dp))
                    Switch(f.priority > 0, { on ->
                        vm.setFilePriority(hash, f.index, if (on) 1 else 0)
                        scope.launch(com.sridhar.harbor.CrashGuard) { delay(600); version++ }
                    })
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable private fun InfoLine(k: String, v: String) = Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
    Text(k, color = Harbor.TextDim, modifier = Modifier.width(120.dp), style = MaterialTheme.typography.bodyMedium)
    Text(v, style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onToggle: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title); Text(subtitle, color = Harbor.TextDim, style = MaterialTheme.typography.bodySmall) }
        Switch(checked, { onToggle() })
    }
}

@Composable
private fun SheetAction(icon: ImageVector, label: String, tint: Color = Color.White, onClick: () -> Unit) {
    Column(Modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = .07f)), contentAlignment = Alignment.Center) {
            Icon(icon, label, tint = tint)
        }
        Spacer(Modifier.height(4.dp)); Text(label, style = MaterialTheme.typography.labelSmall, color = Harbor.TextDim)
    }
}

private val presets = listOf(0L, 256L * 1024, 512L * 1024, 1L shl 20, 2L shl 20, 5L shl 20, 10L shl 20, 25L shl 20, 50L shl 20)

@Composable
fun LimitPicker(label: String, bytes: Long, onChange: (Long) -> Unit) {
    var text by remember(bytes) { mutableStateOf(if (bytes > 0) (bytes / 1024).toString() else "") }
    Column(Modifier.padding(vertical = 6.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(6.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(presets) { p ->
                FilterChip(bytes == p, { onChange(p) }, { Text(if (p == 0L) "∞" else formatSpeed(p).replace(".0", "")) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Harbor.Violet.copy(alpha = .35f)))
            }
        }
        OutlinedTextField(
            text, { v -> text = v.filter(Char::isDigit).take(7); onChange((text.toLongOrNull() ?: 0) * 1024) },
            Modifier.fillMaxWidth().padding(top = 4.dp), singleLine = true, shape = RoundedCornerShape(12.dp),
            label = { Text(stringResource(R.string.custom_kib_s_empty_unlimited)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
    }
}

@Composable
private fun SpeedLimitSheet(vm: TorrentsViewModel, onDismiss: () -> Unit) {
    LaunchedEffect(Unit) { vm.loadMeta() }
    var dl by remember { mutableLongStateOf(vm.transfer.dlLimit) }
    var up by remember { mutableLongStateOf(vm.transfer.upLimit) }
    var altDl by remember(vm.prefs) { mutableLongStateOf(vm.prefs.altDlLimit * 1024) }
    var altUp by remember(vm.prefs) { mutableLongStateOf(vm.prefs.altUpLimit * 1024) }
    ModalBottomSheet(onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Harbor.Surface) {
        LazyColumn(Modifier.navigationBarsPadding(), contentPadding = PaddingValues(20.dp)) {
            item {
                Text(stringResource(R.string.speed_limits), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(R.string.applies_to_all_torrents_qbittorrent_uses), color = Harbor.TextDim, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.global), style = MaterialTheme.typography.labelSmall, color = Harbor.Sky)
                LimitPicker(stringResource(R.string.download), dl) { dl = it }
                LimitPicker(stringResource(R.string.upload), up) { up = it }
                GradientButton(stringResource(R.string.save_global_limits), { vm.setGlobal(dl, up); onDismiss() }, Modifier.fillMaxWidth().padding(top = 8.dp))
                Spacer(Modifier.height(24.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.alternative), style = MaterialTheme.typography.labelSmall, color = Harbor.Mint, modifier = Modifier.weight(1f))
                    Text(if (vm.altSpeed) stringResource(R.string.active) else stringResource(R.string.inactive), color = Harbor.TextDim, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.width(8.dp)); Switch(vm.altSpeed, { vm.toggleAlt() })
                }
                LimitPicker(stringResource(R.string.download), altDl) { altDl = it }
                LimitPicker(stringResource(R.string.upload), altUp) { altUp = it }
                TextButton({ vm.setAlt(altDl, altUp); onDismiss() }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.save_alternative_limits), color = Harbor.Mint) }
            }
        }
    }
}

@Composable
private fun AddTorrentSheet(vm: TorrentsViewModel, incoming: IncomingTorrent?, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var urls by remember { mutableStateOf((incoming as? IncomingTorrent.Magnet)?.uri.orEmpty()) }
    var file by remember { mutableStateOf((incoming as? IncomingTorrent.File)?.let { it.name to it.bytes }) }
    var savePath by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var paused by remember { mutableStateOf(false) }
    var sequential by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.loadMeta() }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch(com.sridhar.harbor.CrashGuard) {
            file = withContext(Dispatchers.IO) {
                runCatching {
                    val name = ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                        ?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: "file.torrent"
                    name to ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
                }.getOrNull()
            }
        }
    }

    ModalBottomSheet(onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Harbor.Surface) {
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.add_torrent), style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(
                urls, { urls = it }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), minLines = 2, maxLines = 5,
                label = { Text(stringResource(R.string.magnet_links_or_urls_one_per)) },
                trailingIcon = {
                    IconButton({ clipboard.getText()?.text?.let { urls = if (urls.isBlank()) it else urls + "\n" + it } }) {
                        Icon(Icons.Rounded.ContentPaste, stringResource(R.string.paste))
                    }
                },
            )
            Row(Modifier.fillMaxWidth().glass(RoundedCornerShape(14.dp)).clickable { picker.launch(arrayOf("application/x-bittorrent", "application/octet-stream")) }
                .padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.FileOpen, null, tint = Harbor.VioletSoft); Spacer(Modifier.width(10.dp))
                Text(file?.first ?: stringResource(R.string.choose_a_torrent_file), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (file != null) IconButton({ file = null }, Modifier.size(24.dp)) { Icon(Icons.Rounded.Close, stringResource(R.string.remove)) }
            }
            OutlinedTextField(savePath, { savePath = it }, Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(14.dp),
                label = { Text(stringResource(R.string.save_path)) }, placeholder = { Text(vm.prefs.savePath.ifBlank { stringResource(R.string.default_label) }) })
            if (vm.categories.isNotEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FilterChip(category.isBlank(), { category = "" }, { Text(stringResource(R.string.no_category)) }) }
                items(vm.categories) { cat -> FilterChip(category == cat.name, { category = cat.name }, { Text(cat.name) }) }
            }
            ToggleRow(stringResource(R.string.start_paused), stringResource(R.string.add_without_starting), paused) { paused = !paused }
            ToggleRow(stringResource(R.string.sequential_download), stringResource(R.string.good_for_watching_while_it_downloads), sequential) { sequential = !sequential }
            GradientButton(
                if (busy) stringResource(R.string.adding) else stringResource(R.string.add_to_qbittorrent),
                onClick = {
                    busy = true
                    vm.add(AddTorrentRequest(urls, file?.first, file?.second, savePath, category, paused, sequential, sequential)) { onDismiss() }
                    scope.launch(com.sridhar.harbor.CrashGuard) { delay(4000); busy = false }
                },
                modifier = Modifier.fillMaxWidth(), icon = Icons.Rounded.Add, enabled = !busy && (urls.isNotBlank() || file != null),
            )
        }
    }
}
