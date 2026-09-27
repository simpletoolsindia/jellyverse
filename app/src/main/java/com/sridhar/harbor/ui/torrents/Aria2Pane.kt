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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sridhar.harbor.data.AppContainer
import com.sridhar.harbor.data.aria2.Aria2Download
import com.sridhar.harbor.data.aria2.Aria2Stat
import com.sridhar.harbor.ui.components.GradientButton
import com.sridhar.harbor.ui.components.GradientProgress
import com.sridhar.harbor.ui.components.LocalContainer
import com.sridhar.harbor.ui.components.MessageState
import com.sridhar.harbor.ui.components.Pill
import com.sridhar.harbor.ui.components.enterRise
import com.sridhar.harbor.ui.components.formatBytes
import com.sridhar.harbor.ui.components.formatEta
import com.sridhar.harbor.ui.components.formatSpeed
import com.sridhar.harbor.ui.components.friendly
import com.sridhar.harbor.ui.components.glass
import com.sridhar.harbor.ui.components.pressable
import com.sridhar.harbor.ui.theme.Harbor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class Aria2ViewModel(private val c: AppContainer) : ViewModel() {
    var downloads by mutableStateOf<List<Aria2Download>>(emptyList()); private set
    var stat by mutableStateOf(Aria2Stat()); private set
    var dlLimit by mutableLongStateOf(0L); private set
    var ulLimit by mutableLongStateOf(0L); private set
    var error by mutableStateOf<String?>(null); private set
    var loaded by mutableStateOf(false); private set
    var message by mutableStateOf<String?>(null)
    var filter by mutableStateOf("All")
    val history = ArrayDeque<Long>(); val upHistory = ArrayDeque<Long>()
    var tick by mutableIntStateOf(0); private set

    suspend fun refresh() {
        runCatching {
            downloads = c.aria2.downloads(); stat = c.aria2.stat()
            if (tick % 10 == 0) c.aria2.globalOptions().let { o ->
                dlLimit = o["max-overall-download-limit"]?.toString()?.trim('"')?.let(::parseSize) ?: 0
                ulLimit = o["max-overall-upload-limit"]?.toString()?.trim('"')?.let(::parseSize) ?: 0
            }
        }.onSuccess {
            error = null; loaded = true
            history.addLast(stat.downloadSpeed.toLongOrNull() ?: 0); upHistory.addLast(stat.uploadSpeed.toLongOrNull() ?: 0)
            while (history.size > 60) history.removeFirst(); while (upHistory.size > 60) upHistory.removeFirst()
            tick++
        }.onFailure { error = it.friendly(); loaded = true }
    }

    private fun parseSize(s: String): Long {
        val n = s.trimEnd('K', 'M', 'G', 'k', 'm', 'g').toLongOrNull() ?: return 0
        return when (s.lastOrNull()?.uppercaseChar()) { 'K' -> n * 1024; 'M' -> n shl 20; 'G' -> n shl 30; else -> n }
    }

    private fun act(msg: String?, block: suspend () -> Unit) = viewModelScope.launch(com.sridhar.harbor.CrashGuard) {
        runCatching { block() }.onSuccess { msg?.let { message = it }; refresh() }.onFailure { message = it.friendly() }
    }
    fun toggle(d: Aria2Download) = act(null) { if (d.status == "paused") c.aria2.resume(d.gid) else c.aria2.pause(d.gid) }
    fun remove(d: Aria2Download) = act(L10n.s(R.string.removed)) { c.aria2.remove(d) }
    fun purge() = act(L10n.s(R.string.cleared_finished)) { c.aria2.purgeFinished() }
    fun pauseAll() = act(L10n.s(R.string.paused_all)) { c.aria2.pauseAll() }
    fun resumeAll() = act(L10n.s(R.string.resumed_all)) { c.aria2.resumeAll() }
    fun setLimits(dl: Long, ul: Long) = act(L10n.s(R.string.limits_saved)) { c.aria2.setLimits(dl, ul); dlLimit = dl; ulLimit = ul }
    fun addUris(text: String, dir: String) = act(L10n.s(R.string.added_to_aria2)) {
        c.aria2.addUri(text.lines().map { it.trim() }.filter { it.isNotBlank() }, dir)
    }
    fun addTorrent(bytes: ByteArray, dir: String) = act(L10n.s(R.string.torrent_added_to_aria2)) { c.aria2.addTorrent(bytes, dir) }
}

private fun Aria2Download.color() = when (status) {
    "active" -> Harbor.Sky; "waiting" -> Harbor.Amber; "paused" -> Harbor.TextDim; "complete" -> Harbor.Mint
    "error" -> Harbor.Rose; else -> Harbor.TextDim
}

@Composable
fun Aria2Pane(switcher: @Composable () -> Unit) {
    val container = LocalContainer.current
    val vm = viewModel { Aria2ViewModel(container) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) { lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { while (true) { vm.refresh(); delay(2000) } } }
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(vm.message) { vm.message?.let { snack.showSnackbar(it); vm.message = null } }
    var adding by remember { mutableStateOf(false) }
    var limits by remember { mutableStateOf(false) }

    val shown = vm.downloads.filter {
        when (vm.filter) { "Active" -> it.status == "active"; "Waiting" -> it.status == "waiting" || it.status == "paused"
            "Done" -> it.status == "complete"; "Errors" -> it.status == "error"; else -> true }
    }
    Box(Modifier.fillMaxSize().background(Harbor.Ink)) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 150.dp)) {
            item {
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 20.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.downloads), style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f))
                    IconButton({ vm.purge() }) { Icon(Icons.Rounded.CleaningServices, stringResource(R.string.clear_finished)) }
                }
                Box(Modifier.padding(horizontal = 20.dp)) { switcher() }
            }
            item {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 10.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF1C1F27), Color(0xFF16181F)))).border(1.dp, Color.White.copy(.07f), RoundedCornerShape(24.dp))) {
                    Box(Modifier.fillMaxWidth().height(130.dp)) {
                        @Suppress("UNUSED_EXPRESSION") vm.tick
                        SpeedSparkline(vm.history.toList(), vm.upHistory.toList(), Modifier.fillMaxSize())
                        Row(Modifier.padding(18.dp)) {
                            SpeedBlock(Icons.Rounded.Download, stringResource(R.string.aria2), vm.stat.downloadSpeed.toLongOrNull() ?: 0, vm.dlLimit, Harbor.Sky, Modifier.weight(1f))
                            SpeedBlock(Icons.Rounded.Upload, stringResource(R.string.aria2_2), vm.stat.uploadSpeed.toLongOrNull() ?: 0, vm.ulLimit, Harbor.Coral, Modifier.weight(1f))
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.s_1_s_active_2_s_waiting, vm.stat.numActive, vm.stat.numWaiting, vm.stat.numStopped),
                            color = Harbor.TextDim, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        IconButton({ vm.pauseAll() }) { Icon(Icons.Rounded.Pause, stringResource(R.string.pause_all), tint = Harbor.Amber) }
                        IconButton({ vm.resumeAll() }) { Icon(Icons.Rounded.PlayArrow, stringResource(R.string.resume_all), tint = Harbor.Mint) }
                        Row(Modifier.clip(RoundedCornerShape(50)).background(Harbor.accentH).pressable { limits = true }.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Speed, null, tint = Color.White, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.limits), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listOf("All", "Active", "Waiting", "Done", "Errors")) { f ->
                        FilterChip(vm.filter == f, { vm.filter = f }, { Text(f) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Harbor.Sky.copy(.3f)))
                    }
                }
            }
            if (vm.error != null && vm.downloads.isEmpty()) item { MessageState(stringResource(R.string.can_t_reach_aria2), vm.error) }
            else if (vm.loaded && shown.isEmpty()) item { MessageState(stringResource(R.string.nothing_here), stringResource(R.string.add_a_link_magnet_or_torrent), icon = Icons.Rounded.Download) }
            itemsIndexed(shown, key = { _, d -> d.gid }) { i, d ->
                Column(Modifier.animateItem().enterRise(i).padding(horizontal = 20.dp, vertical = 6.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp))
                    .background(Harbor.Surface).padding(16.dp)) {
                    Row(verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) {
                            Text(d.name, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                                Pill(d.status.replaceFirstChar { it.uppercase() }, d.color())
                                Pill(if (d.isTorrent) stringResource(R.string.bittorrent) else "HTTP", Harbor.VioletSoft)
                                if (d.connections != "0") Pill(stringResource(R.string.s_1_s_conn, d.connections), Harbor.TextDim)
                            }
                        }
                        if (d.status in setOf("active", "waiting", "paused")) Box(Modifier.size(40.dp).clip(CircleShape).background(d.color().copy(.15f)).clickable { vm.toggle(d) },
                            contentAlignment = Alignment.Center) { Icon(if (d.status == "paused") Icons.Rounded.PlayArrow else Icons.Rounded.Pause, null, tint = d.color()) }
                        IconButton({ vm.remove(d) }) { Icon(Icons.Rounded.Delete, stringResource(R.string.remove), tint = Harbor.TextDim) }
                    }
                    Spacer(Modifier.height(10.dp))
                    GradientProgress(d.progress, height = 6.dp, brush = if (d.status == "active") Harbor.accentH else SolidColor(d.color()))
                    Row(Modifier.padding(top = 6.dp)) {
                        Text("${(d.progress * 100).toInt()}%  ${formatBytes(d.done)} / ${formatBytes(d.total)}", fontSize = 12.sp, color = Harbor.TextDim, modifier = Modifier.weight(1f))
                        if (d.dl > 0) Text("↓ ${formatSpeed(d.dl)}  ", fontSize = 12.sp, color = Harbor.Sky)
                        if (d.dl > 0) Text(formatEta(d.eta), fontSize = 12.sp, color = Harbor.TextDim)
                    }
                    d.errorMessage?.takeIf { d.status == "error" }?.let { Text(it, color = Harbor.Rose, fontSize = 12.sp) }
                }
            }
        }
        Box(Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 20.dp, bottom = 92.dp + com.sridhar.harbor.ui.components.LocalMiniPlayerInset.current).size(60.dp)
            .clip(RoundedCornerShape(20.dp)).background(Brush.linearGradient(listOf(Harbor.Sky, Harbor.Violet))).pressable { adding = true },
            contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Add, stringResource(R.string.add_download), tint = Color.White, modifier = Modifier.size(30.dp)) }
        SnackbarHost(snack, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 160.dp + com.sridhar.harbor.ui.components.LocalMiniPlayerInset.current))
    }
    if (adding) Aria2AddSheet(vm) { adding = false }
    if (limits) ModalBottomSheet({ limits = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Harbor.Surface) {
        var dl by remember { mutableLongStateOf(vm.dlLimit) }; var ul by remember { mutableLongStateOf(vm.ulLimit) }
        Column(Modifier.padding(20.dp).navigationBarsPadding()) {
            Text(stringResource(R.string.aria2_speed_limits), style = MaterialTheme.typography.headlineSmall)
            LimitPicker(stringResource(R.string.download), dl) { dl = it }
            LimitPicker(stringResource(R.string.upload), ul) { ul = it }
            GradientButton(stringResource(R.string.save), { vm.setLimits(dl, ul); limits = false }, Modifier.fillMaxWidth().padding(top = 8.dp))
        }
    }
}

@Composable
private fun SpeedBlock(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, speed: Long, limit: Long, color: Color, modifier: Modifier) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = color, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp))
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = color)
        }
        Text(formatSpeed(speed), fontSize = 24.sp, fontWeight = FontWeight.Black)
        Text(if (limit > 0) stringResource(R.string.limit_1_s, formatSpeed(limit)) else stringResource(R.string.unlimited), fontSize = 11.sp, color = Harbor.TextDim)
    }
}

@Composable
private fun Aria2AddSheet(vm: Aria2ViewModel, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var urls by remember { mutableStateOf("") }
    var dir by remember { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch(com.sridhar.harbor.CrashGuard) {
            val bytes = withContext(Dispatchers.IO) { runCatching { ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes() } }.getOrNull() }
            if (bytes != null) { vm.addTorrent(bytes, dir); onDismiss() }
        }
    }
    ModalBottomSheet(onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Harbor.Surface) {
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.add_to_aria2), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.http_s_ftp_magnet_links_one), color = Harbor.TextDim, fontSize = 12.sp)
            OutlinedTextField(urls, { urls = it }, Modifier.fillMaxWidth(), minLines = 3, shape = RoundedCornerShape(14.dp), label = { Text(stringResource(R.string.links)) },
                trailingIcon = { IconButton({ clipboard.getText()?.text?.let { urls = if (urls.isBlank()) it else urls + "\n" + it } }) { Icon(Icons.Rounded.ContentPaste, stringResource(R.string.paste)) } })
            Row(Modifier.fillMaxWidth().glass(RoundedCornerShape(14.dp)).clickable { picker.launch(arrayOf("application/x-bittorrent", "application/octet-stream")) }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.FileOpen, null, tint = Harbor.VioletSoft); Spacer(Modifier.width(10.dp)); Text(stringResource(R.string.or_pick_a_torrent_file))
            }
            OutlinedTextField(dir, { dir = it }, Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(14.dp),
                label = { Text(stringResource(R.string.save_directory_optional)) }, placeholder = { Text("/downloads") })
            GradientButton(stringResource(R.string.start_download), { vm.addUris(urls, dir); onDismiss() }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Add, enabled = urls.isNotBlank())
        }
    }
}
