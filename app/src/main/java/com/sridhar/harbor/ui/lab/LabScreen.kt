package com.sridhar.harbor.ui.lab

import com.sridhar.harbor.L10n
import com.sridhar.harbor.R
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sridhar.harbor.data.ssh.ContainerInfo
import com.sridhar.harbor.data.ssh.ProcessInfo
import com.sridhar.harbor.data.ssh.SystemSnapshot
import com.sridhar.harbor.ui.components.GradientProgress
import com.sridhar.harbor.ui.components.LocalContainer
import com.sridhar.harbor.ui.components.MessageState
import com.sridhar.harbor.ui.components.Pill
import com.sridhar.harbor.ui.components.enterRise
import com.sridhar.harbor.ui.components.formatBytes
import com.sridhar.harbor.ui.components.formatSpeed
import com.sridhar.harbor.ui.components.pressable
import com.sridhar.harbor.ui.theme.Harbor
import kotlinx.coroutines.delay

private fun levelColor(f: Float) = when { f > 0.9f -> Harbor.Rose; f > 0.75f -> Harbor.Amber; else -> Harbor.Mint }

@Composable
fun LabScreen(onSetup: () -> Unit, onTerminal: () -> Unit, onProfile: () -> Unit) {
    val container = LocalContainer.current
    val hosts by container.ssh.hosts.collectAsState()
    if (hosts.isEmpty()) {
        Box(Modifier.fillMaxSize()) {
            MessageState(stringResource(R.string.connect_your_homelab), stringResource(R.string.add_an_ssh_login_to_see),
                Modifier.align(Alignment.Center), icon = Icons.Rounded.Dns, onRetry = onSetup, actionLabel = stringResource(R.string.set_up_ssh))
            Box(Modifier.align(Alignment.TopEnd)) { TopActions(onTerminal, onProfile) }
        }
        return
    }
    val vm = viewModel { LabViewModel(container) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) { lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { while (true) { vm.poll(); delay(3000) } } }
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(vm.message) { vm.message?.let { snack.showSnackbar(it); vm.message = null } }
    var sheet by remember { mutableStateOf<ContainerInfo?>(null) }
    var killing by remember { mutableStateOf<ProcessInfo?>(null) }
    var containerQuery by remember { mutableStateOf("") }
    var containerFilter by remember { mutableStateOf("All") }
    var showAllProcs by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(Harbor.Ink)) {
        val s = vm.snapshot
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 130.dp + com.sridhar.harbor.ui.components.LocalMiniPlayerInset.current)) {
            item(key = "head") {
                Column(Modifier.statusBarsPadding().padding(start = 20.dp, end = 20.dp, top = 12.dp)) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.lab), style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f))
                    TopActions(onTerminal, onProfile, inline = true)
                  }
                    Text(
                        if (s == null) stringResource(R.string.connecting_to_1_s, vm.host?.host.orEmpty()) else stringResource(R.string.s_1_s_up_2_s_3, s.hostname, uptime(s.uptimeSec), s.kernel),
                        color = Harbor.TextDim, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (s == null) {
                item { if (vm.error != null) MessageState(stringResource(R.string.can_t_reach_homelab), vm.error, onRetry = onSetup, actionLabel = stringResource(R.string.check_ssh_settings))
                    else Box(Modifier.fillMaxWidth().padding(64.dp), Alignment.Center) { com.sridhar.harbor.ui.components.JellyLoader(color = Harbor.VioletSoft) } }
                return@LazyColumn
            }
            item(key = "status") { StatusBanner(s.warnings(vm.containers), vm.error) }
            item(key = "gauges") {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    RingGauge("CPU", s.cpu, "${(s.cpu * 100).toInt()}%", stringResource(R.string.s_1_s_cores_2_s, s.cores, s.load.firstOrNull() ?: 0f))
                    RingGauge(stringResource(R.string.memory), s.mem, "${(s.mem * 100).toInt()}%", "${formatBytes((s.memTotalKb - s.memAvailKb) * 1024)} / ${formatBytes(s.memTotalKb * 1024)}")
                    val t = s.tempC ?: 0f
                    RingGauge(stringResource(R.string.temp), (t / 85f).coerceIn(0f, 1f), s.tempC?.let { "${it.toInt()}°" } ?: "–", when { t > 75 -> stringResource(R.string.hot); t > 60 -> stringResource(R.string.warm); else -> stringResource(R.string.cool) })
                }
                // Server mood: the dino reacts to the worst of CPU, memory, temperature and the fullest disk.
                val worst = listOf(s.cpu, s.mem, ((s.tempC ?: 0f) / 85f).coerceIn(0f, 1f), s.disks.maxOfOrNull { it.fraction } ?: 0f).max()
                Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Harbor.Surface).padding(14.dp)) {
                    Text(stringResource(R.string.lab_server_mood) + " · " + stringResource(when { worst > 0.85f -> R.string.lab_mood_stressed; worst > 0.6f -> R.string.lab_mood_busy; else -> R.string.lab_mood_happy }),
                        fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    com.sridhar.harbor.ui.components.DinoMeter(worst, height = 40.dp)
                }
            }
            item(key = "live") { LiveCard(vm, s) }
            item(key = "swap") {
                MeterRow(stringResource(R.string.swap), s.swap, stringResource(R.string.s_1_s_of_2_s, formatBytes((s.swapTotalKb - s.swapFreeKb) * 1024), formatBytes(s.swapTotalKb * 1024)))
            }
            item(key = "storage-h") { Section(stringResource(R.string.storage), stringResource(R.string.s_1_s_volumes, s.disks.size)) }
            itemsIndexed(s.disks, key = { _, d -> "disk-" + d.mount }) { i, d ->
                MeterRow(d.mount, d.fraction, stringResource(R.string.s_1_s_free_of_2_s, formatBytes(d.free), formatBytes(d.total)), Modifier.enterRise(i), sub = d.device)
            }
            item(key = "ct-h") {
                val running = vm.containers.count { it.state == "running" }
                Section(stringResource(R.string.containers), stringResource(R.string.s_1_s_running_2_s_stopped, running, vm.containers.size - running))
                Column(Modifier.padding(horizontal = 16.dp)) {
                    OutlinedTextField(containerQuery, { containerQuery = it }, Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(16.dp),
                        placeholder = { Text(stringResource(R.string.filter_containers)) }, leadingIcon = { Icon(Icons.Rounded.Search, null) })
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("All", "Running", "Stopped", "Problems").forEach { f ->
                            FilterChip(containerFilter == f, { containerFilter = f }, { Text(f) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Harbor.Violet.copy(alpha = .3f)))
                        }
                    }
                }
            }
            val shownContainers = vm.containers.filter { c ->
                (containerQuery.isBlank() || c.name.contains(containerQuery, true) || c.image.contains(containerQuery, true)) && when (containerFilter) {
                    "Running" -> c.state == "running"; "Stopped" -> c.state != "running"; "Problems" -> c.problem; else -> true
                }
            }.sortedWith(compareByDescending<ContainerInfo> { it.problem }.thenByDescending { it.cpu ?: 0f })
            itemsIndexed(shownContainers, key = { _, c -> "ct-" + c.name }) { i, c ->
                ContainerRow(c, c.name in vm.busy, Modifier.animateItem().enterRise(i)) { sheet = c }
            }
            item(key = "ps-h") { Section(stringResource(R.string.processes), stringResource(R.string.top_by_cpu)) }
            val procs = if (showAllProcs) s.processes else s.processes.take(12)
            itemsIndexed(procs, key = { _, p -> "p-${p.pid}" }) { _, p -> ProcessRow(p, s.memTotalKb) { killing = p } }
            if (s.processes.size > 12) item { TextButton({ showAllProcs = !showAllProcs }, Modifier.padding(horizontal = 12.dp)) {
                Text(if (showAllProcs) stringResource(R.string.show_fewer) else stringResource(R.string.show_all_1_s, s.processes.size), color = Harbor.VioletSoft) } }
        }
        SnackbarHost(snack, Modifier.align(Alignment.BottomCenter).padding(bottom = 110.dp + com.sridhar.harbor.ui.components.LocalMiniPlayerInset.current))
    }

    sheet?.let { c -> ContainerSheet(vm.containers.firstOrNull { it.name == c.name } ?: c, vm, onDismiss = { sheet = null }) }
    killing?.let { p ->
        AlertDialog(
            onDismissRequest = { killing = null },
            title = { Text(stringResource(R.string.stop_process_1_s, p.pid)) },
            text = { Text(stringResource(R.string.sends_sigterm_to_1_s_2, p.command, p.user)) },
            confirmButton = { TextButton({ vm.kill(p.pid); killing = null }) { Text(stringResource(R.string.kill), color = Harbor.Rose) } },
            dismissButton = { TextButton({ killing = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun TopActions(onTerminal: () -> Unit, onProfile: () -> Unit, inline: Boolean = false) {
    Row(if (inline) Modifier else Modifier.fillMaxWidth().statusBarsPadding().padding(end = 12.dp, top = 12.dp),
        horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.clip(RoundedCornerShape(50)).background(Harbor.accentH).pressable(onClick = onTerminal).padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Terminal, null, tint = Color.White, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.terminal), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
        Spacer(Modifier.width(6.dp))
        IconButton(onProfile) { Icon(Icons.Rounded.AccountCircle, stringResource(R.string.settings), tint = Harbor.TextDim) }
    }
}

private fun uptime(sec: Long): String {
    val d = sec / 86400; val h = (sec % 86400) / 3600; val m = (sec % 3600) / 60
    return if (d > 0) "${d}d ${h}h" else if (h > 0) "${h}h ${m}m" else "${m}m"
}

@Composable
private fun StatusBanner(warnings: List<String>, error: String?) {
    val ok = warnings.isEmpty() && error == null
    AnimatedContent(ok, transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) }, label = "status") { good ->
        val color = if (good) Harbor.Mint else Harbor.Amber
        val pulse = rememberInfiniteTransition(label = "pulse")
        val p by pulse.animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Restart), label = "p")
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(color.copy(alpha = .1f))
            .border(1.dp, color.copy(alpha = .3f), RoundedCornerShape(20.dp)).padding(14.dp), verticalAlignment = Alignment.Top) {
            Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(22.dp).graphicsLayer { scaleX = 0.4f + p * 0.6f; scaleY = scaleX; alpha = 1f - p }.clip(CircleShape).background(color))
                Box(Modifier.size(10.dp).clip(CircleShape).background(color))
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(if (good) stringResource(R.string.all_systems_healthy) else stringResource(R.string.s_1_s_thing_s_need_attention, warnings.size + (if (error != null) 1 else 0)), fontWeight = FontWeight.Bold, color = color)
                if (!good) (listOfNotNull(error) + warnings).take(6).forEach { Text("• $it", fontSize = 12.sp, color = Harbor.Fg.copy(.85f)) }
            }
        }
    }
}

/** Ring gauge: sweep springs to the new value; read happens inside the Canvas draw. */
@Composable
private fun RingGauge(label: String, fraction: Float, value: String, sub: String, size: Dp = 104.dp) {
    val sweep = animateFloatAsState(fraction.coerceIn(0f, 1f), spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessVeryLow), label = "ring-${label}")
    val color = levelColor(fraction)
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(size + 8.dp)) {
        Box(Modifier.size(size), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 9.dp.toPx()
                val inset = stroke / 2
                val arcSize = androidx.compose.ui.geometry.Size(this.size.width - stroke, this.size.height - stroke)
                drawArc(Harbor.line(.07f), 135f, 270f, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                drawArc(Brush.sweepGradient(listOf(Harbor.Violet, color, Harbor.Violet)), 135f, 270f * sweep.value, false, Offset(inset, inset), arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(value, fontSize = 22.sp, fontWeight = FontWeight.Black)
                Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = Harbor.TextDim)
            }
        }
        Text(sub, fontSize = 11.sp, color = Harbor.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun LiveCard(vm: LabViewModel, s: SystemSnapshot) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp))
        .background(Brush.linearGradient(listOf(Harbor.SurfaceHigh, Harbor.Surface))).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.live), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Legend(Harbor.Violet, "CPU"); Spacer(Modifier.width(10.dp)); Legend(Harbor.Coral, "RAM")
        }
        Spacer(Modifier.height(8.dp))
        val cpu = vm.cpuHistory.toList(); val mem = vm.memHistory.toList()
        Canvas(Modifier.fillMaxWidth().height(90.dp)) {
            listOf(0.25f, 0.5f, 0.75f).forEach { y -> drawLine(Harbor.line(.05f), Offset(0f, size.height * y), Offset(size.width, size.height * y)) }
            fun line(v: List<Float>, c: Color) {
                if (v.size < 2) return
                val step = size.width / 39f; val x0 = size.width - (v.size - 1) * step
                val path = Path(); v.forEachIndexed { i, f -> val x = x0 + i * step; val y = size.height * (1f - f.coerceIn(0f, 1f)); if (i == 0) path.moveTo(x, y) else path.lineTo(x, y) }
                val fill = Path().apply { addPath(path); lineTo(size.width, size.height); lineTo(x0, size.height); close() }
                drawPath(fill, Brush.verticalGradient(listOf(c.copy(.25f), Color.Transparent)))
                drawPath(path, c, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
            }
            line(mem, Harbor.Coral); line(cpu, Harbor.Violet)
        }
        Spacer(Modifier.height(10.dp))
        Row {
            Column(Modifier.weight(1f)) {
                Text("↓ ${formatSpeed(s.rxBps)}", fontWeight = FontWeight.Bold, color = Harbor.Sky)
                Text("↑ ${formatSpeed(s.txBps)}", fontWeight = FontWeight.Bold, color = Harbor.Coral)
                Text(s.netIface ?: "network", fontSize = 11.sp, color = Harbor.TextDim)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("Load ${s.load.joinToString("  ") { "%.2f".format(it) }}", fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                Text(stringResource(R.string.s_1_5_15_min), fontSize = 11.sp, color = Harbor.TextDim)
            }
        }
    }
}

@Composable private fun Legend(c: Color, t: String) = Row(verticalAlignment = Alignment.CenterVertically) {
    Box(Modifier.size(8.dp).clip(CircleShape).background(c)); Spacer(Modifier.width(4.dp)); Text(t, fontSize = 11.sp, color = Harbor.TextDim)
}

@Composable
private fun Section(title: String, sub: String) = Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 8.dp), verticalAlignment = Alignment.Bottom) {
    Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f)); Text(sub, color = Harbor.TextDim, fontSize = 12.sp)
}

@Composable
private fun MeterRow(title: String, fraction: Float, detail: String, modifier: Modifier = Modifier, sub: String? = null) {
    val c = levelColor(fraction)
    Column(modifier.padding(horizontal = 16.dp, vertical = 5.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Harbor.Surface).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                sub?.let { Text(it, fontSize = 11.sp, color = Harbor.TextDim, maxLines = 1) }
            }
            Text("${(fraction * 100).toInt()}%", fontWeight = FontWeight.Black, color = c)
        }
        Spacer(Modifier.height(8.dp))
        com.sridhar.harbor.ui.components.DinoMeter(fraction)
        Text(detail, fontSize = 11.sp, color = Harbor.TextDim, modifier = Modifier.padding(top = 5.dp))
    }
}

@Composable
private fun StateDot(c: ContainerInfo) {
    val color = when { c.problem -> Harbor.Rose; c.state == "running" -> Harbor.Mint; c.state == "paused" -> Harbor.Amber; else -> Harbor.TextDim }
    if (c.state == "running" && !c.problem) {
        val t = rememberInfiniteTransition(label = "dot")
        val a by t.animateFloat(0.35f, 1f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "a")
        Box(Modifier.size(10.dp).graphicsLayer { alpha = a }.clip(CircleShape).background(color))
    } else Box(Modifier.size(10.dp).clip(CircleShape).background(color))
}

@Composable
private fun ContainerRow(c: ContainerInfo, busy: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Row(modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Harbor.Surface)
        .pressable(0.98f, onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        StateDot(c)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(c.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(c.status, fontSize = 11.sp, color = if (c.problem) Harbor.Rose else Harbor.TextDim, maxLines = 1)
        }
        if (busy) com.sridhar.harbor.ui.components.JellyLoader(Modifier.size(18.dp), strokeWidth = 2.dp)
        else c.cpu?.let { cpu ->
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.width(64.dp)) {
                Text("%.1f%%".format(cpu), fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                Box(Modifier.width(56.dp).height(3.dp).drawBehind {
                    drawRoundRect(Harbor.line(.08f), cornerRadius = CornerRadius(2f))
                    drawRoundRect(levelColor(cpu / 100f), size = size.copy(width = size.width * (cpu / 100f).coerceIn(0.02f, 1f)), cornerRadius = CornerRadius(2f))
                })
            }
        }
    }
}

@Composable
private fun ProcessRow(p: ProcessInfo, memTotalKb: Long, onClick: () -> Unit) {
    Row(Modifier.padding(horizontal = 16.dp, vertical = 2.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick)
        .padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(p.command, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace, fontSize = 13.sp, maxLines = 1)
            Text("${p.pid} · ${p.user} · ${p.elapsed}", fontSize = 11.sp, color = Harbor.TextDim, maxLines = 1)
        }
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.width(92.dp)) {
            Text("%.1f%% CPU".format(p.cpu), fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = levelColor(p.cpu / 100f))
            Text(formatBytes(p.rssKb * 1024), fontSize = 11.sp, color = Harbor.TextDim, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun ContainerSheet(c: ContainerInfo, vm: LabViewModel, onDismiss: () -> Unit) {
    var showLogs by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<String?>(null) }
    ModalBottomSheet(onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Harbor.Surface) {
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { StateDot(c); Spacer(Modifier.width(10.dp)); Text(c.name, style = MaterialTheme.typography.headlineSmall) }
            Text(c.image, color = Harbor.TextDim, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 10.dp)) {
                Pill(c.status, if (c.problem) Harbor.Rose else if (c.state == "running") Harbor.Mint else Harbor.TextDim)
                c.cpu?.let { Pill("CPU %.1f%%".format(it), Harbor.Sky) }
                c.memUsage?.let { Pill(it, Harbor.VioletSoft) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val running = c.state == "running"
                ActionTile(Icons.Rounded.RestartAlt, stringResource(R.string.restart), Harbor.Sky, Modifier.weight(1f)) { confirm = "restart" }
                if (running) ActionTile(Icons.Rounded.Stop, stringResource(R.string.stop), Harbor.Rose, Modifier.weight(1f)) { confirm = "stop" }
                else ActionTile(Icons.Rounded.PlayArrow, stringResource(R.string.start), Harbor.Mint, Modifier.weight(1f)) { vm.containerAction(c.name, "start"); onDismiss() }
                ActionTile(Icons.Rounded.Memory, if (showLogs) stringResource(R.string.hide_logs) else stringResource(R.string.logs), Harbor.VioletSoft, Modifier.weight(1f)) { showLogs = !showLogs }
            }
            AnimatedVisibility(showLogs, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                val logs by produceState<String?>(null, c.name) { value = vm.logs(c.name) }
                Box(Modifier.padding(top = 12.dp).fillMaxWidth().heightIn(max = 420.dp).clip(RoundedCornerShape(14.dp)).background(Color.Black).padding(10.dp)) {
                    if (logs == null) com.sridhar.harbor.ui.components.JellyLoader(Modifier.align(Alignment.Center).size(24.dp))
                    else SelectionContainer {
                        val scroll = rememberScrollState()
                        LaunchedEffect(logs) { scroll.scrollTo(scroll.maxValue) }
                        Text(logs!!.ifBlank { stringResource(R.string.no_output) }, fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = Color(0xFFD4D4E0),
                            modifier = Modifier.verticalScroll(scroll).horizontalScroll(rememberScrollState()))
                    }
                }
            }
        }
    }
    confirm?.let { action ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(stringResource(if (action == "stop") R.string.stop_container_1_s else R.string.restart_container_1_s, c.name)) },
            text = { Text(if (c.name in setOf("npm", "gluetun")) stringResource(R.string.other_services_depend_on_1_s, c.name) else stringResource(R.string.the_service_will_be_briefly_unavailable)) },
            confirmButton = { TextButton({ vm.containerAction(c.name, action); confirm = null; onDismiss() }) { Text(stringResource(if (action == "stop") R.string.stop else R.string.restart)) } },
            dismissButton = { TextButton({ confirm = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun ActionTile(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tint: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(tint.copy(alpha = .12f)).pressable(onClick = onClick).padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, label, tint = tint); Spacer(Modifier.height(4.dp)); Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = tint)
    }
}
