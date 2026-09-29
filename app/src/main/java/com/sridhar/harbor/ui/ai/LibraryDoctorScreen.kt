package com.sridhar.harbor.ui.ai

import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import com.sridhar.harbor.L10n
import com.sridhar.harbor.R
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sridhar.harbor.data.AppContainer
import com.sridhar.harbor.data.ai.DoctorIssue
import com.sridhar.harbor.data.ai.FixStatus
import com.sridhar.harbor.data.ai.IssueKind
import com.sridhar.harbor.data.ai.ModelState
import com.sridhar.harbor.data.ai.MovePlan
import com.sridhar.harbor.data.ai.ParsedTitle
import com.sridhar.harbor.ui.components.GradientButton
import com.sridhar.harbor.ui.components.LocalContainer
import com.sridhar.harbor.ui.components.MessageState
import com.sridhar.harbor.ui.components.NetImage
import com.sridhar.harbor.ui.components.Pill
import com.sridhar.harbor.ui.components.enterRise
import com.sridhar.harbor.ui.components.friendly
import com.sridhar.harbor.ui.components.pressable
import com.sridhar.harbor.ui.components.rememberConfig
import com.sridhar.harbor.ui.theme.Harbor
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class DoctorViewModel(private val c: AppContainer) : ViewModel() {
    val issues = mutableStateListOf<DoctorIssue>()
    var phase by mutableStateOf("idle"); private set   // idle | scanning | identifying | done
    var progress by mutableStateOf(0 to 0); private set
    var useAi by mutableStateOf(true)
    /** Titles matching this are shown alone and identified first. */
    var query by mutableStateOf("")
    var message by mutableStateOf<String?>(null)
    var plans by mutableStateOf<List<MovePlan>?>(null)
    var moving by mutableStateOf(false); private set
    private var job: Job? = null

    fun scan() {
        job?.cancel()
        job = viewModelScope.launch(com.sridhar.harbor.CrashGuard) {
            issues.clear(); phase = "scanning"
            val found = runCatching { c.doctor.findIssues() }.getOrElse { message = it.friendly(); phase = "idle"; return@launch }
            issues.addAll(found); phase = "identifying"
            val ai = useAi && c.llm.state.value.let { it is ModelState.Ready || it is ModelState.Loaded }
            val q = query.trim()
            val ordered = if (q.isBlank()) found else found.sortedByDescending { matches(it, q) }
            ordered.forEachIndexed { i, issue ->
                progress = i to found.size
                val idx = issues.indexOfFirst { it.item.id == issue.item.id }
                if (idx >= 0) issues[idx] = runCatching { c.doctor.identify(issue, ai) }.getOrElse { issue.copy(error = it.friendly()) }
            }
            progress = found.size to found.size; phase = "done"
        }
    }

    /** Library Doctor for a single movie or series. */
    fun scanOne(itemId: String) {
        job?.cancel()
        job = viewModelScope.launch(com.sridhar.harbor.CrashGuard) {
            issues.clear(); phase = "scanning"
            val issue = runCatching { c.doctor.issueFor(itemId) }.getOrElse { message = it.friendly(); phase = "idle"; return@launch }
            issues += issue; phase = "identifying"; progress = 0 to 1
            val ai = useAi && c.llm.state.value.let { it is ModelState.Ready || it is ModelState.Loaded }
            issues[0] = runCatching { c.doctor.identify(issue, ai) }.getOrElse { issue.copy(error = it.friendly()) }
            progress = 1 to 1; phase = "done"
        }
    }

    fun stop() { job?.cancel(); phase = "done" }

    fun matches(issue: DoctorIssue, q: String) = q.isBlank() || issue.item.name.contains(q, true) || (issue.item.path?.contains(q, true) == true)

    private fun update(id: String, f: (DoctorIssue) -> DoctorIssue) { val i = issues.indexOfFirst { it.item.id == id }; if (i >= 0) issues[i] = f(issues[i]) }

    fun apply(issue: DoctorIssue) = viewModelScope.launch(com.sridhar.harbor.CrashGuard) {
        val current = issues.firstOrNull { it.item.id == issue.item.id } ?: issue
        if (current.status == FixStatus.Applying) return@launch   // already working – ignore double taps
        val best = current.best
        // Matched a series but filed as a movie (or vice versa): metadata can't fix that – offer to move the file instead.
        if (best != null && best.kind != current.item.type) { planMoves(listOf(current)); return@launch }
        update(current.item.id) { it.copy(status = FixStatus.Applying, error = null) }
        val r = runCatching { c.doctor.apply(current) }.getOrElse { e -> current.copy(status = FixStatus.Failed, error = e.friendly()) }
        update(current.item.id) { r }
    }
    fun applyConfident() = viewModelScope.launch(com.sridhar.harbor.CrashGuard) {
        val list = issues.filter { it.confident && it.status == FixStatus.Pending && it.best?.kind == it.item.type }
        list.forEach { i -> val r = c.doctor.apply(i); update(i.item.id) { r } }
        message = L10n.s(R.string.applied_1_s_matches_jellyfin_is, list.size)
    }
    fun choose(issue: DoctorIssue, idx: Int) = update(issue.item.id) { it.copy(chosen = idx) }
    fun skip(issue: DoctorIssue) = update(issue.item.id) { it.copy(status = FixStatus.Skipped) }

    fun retitle(issue: DoctorIssue, title: String, year: Int?, series: Boolean) = viewModelScope.launch(com.sridhar.harbor.CrashGuard) {
        c.doctor.learn(issue.item.path?.substringAfterLast('/') ?: issue.item.name, title, year, series)
        val manual = issue.copy(parsed = ParsedTitle(title, year, if (series) "series" else "movie"), aiTitle = null)
        update(issue.item.id) { manual.copy(candidates = emptyList()) }
        val r = runCatching { c.doctor.identify(manual, useAi = false) }.getOrElse { manual.copy(error = it.friendly()) }
        update(issue.item.id) { r }
        message = L10n.s(R.string.saved_as_a_qwen_example_for)
    }

    fun planMoves(only: List<DoctorIssue>? = null) = viewModelScope.launch(com.sridhar.harbor.CrashGuard) {
        plans = runCatching { c.doctor.planMoves(only ?: issues.filter { it.status != FixStatus.Skipped && it.best != null }) }.getOrElse { message = it.friendly(); null }
        if (plans?.isEmpty() == true) { message = L10n.s(R.string.everything_is_already_organised); plans = null }
    }

    fun executeMoves(selected: List<MovePlan>) = viewModelScope.launch(com.sridhar.harbor.CrashGuard) {
        moving = true
        val results = runCatching { c.doctor.executeMoves(selected) { r -> plans = plans?.map { if (it.itemId == r.itemId) r else it } } }
            .getOrElse { message = it.friendly(); emptyList() }
        moving = false
        message = "Moved ${results.count { it.done == true }} of ${results.size}; Jellyfin is rescanning"
    }
}

@Composable
fun LibraryDoctorScreen(itemId: String? = null, onBack: () -> Unit) {
    val container = LocalContainer.current
    val cfg = rememberConfig()
    val vm = viewModel { DoctorViewModel(container) }
    val model by container.llm.state.collectAsState()
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(vm.message) { vm.message?.let { snack.showSnackbar(it); vm.message = null } }
    var alternatives by remember { mutableStateOf<DoctorIssue?>(null) }
    var editing by remember { mutableStateOf<DoctorIssue?>(null) }
    val aiAvailable = model is ModelState.Ready || model is ModelState.Loaded
    val llmModel by container.llm.model.collectAsState()
    var catalog by remember { mutableStateOf(false) }
    if (catalog) ModelCatalog(onDismiss = { catalog = false })
    LaunchedEffect(itemId) { if (itemId != null) vm.scanOne(itemId) }

    Box(Modifier.fillMaxSize().background(Harbor.Ink)) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 120.dp)) {
            item {
                Row(Modifier.statusBarsPadding().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) }
                    Column {
                        Text(stringResource(R.string.library_doctor), style = MaterialTheme.typography.headlineMedium)
                        Text(stringResource(R.string.fix_messy_names_so_jellyfin_finds), color = Harbor.TextDim, fontSize = 12.sp)
                    }
                }
            }
            item { if (!cfg.jellyfinReady) MessageState(stringResource(R.string.jellyfin_isn_t_connected), null) }
            item {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (!aiAvailable) ModelCard()
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Harbor.Surface).clickable(enabled = aiAvailable) { vm.useAi = !vm.useAi }.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        AiOrb(34.dp, busy = vm.phase == "identifying" && vm.useAi && aiAvailable)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.doctor_use_ai, llmModel.displayName), fontWeight = FontWeight.SemiBold)
                            Text(if (aiAvailable) stringResource(R.string.smarter_title_extraction_for_weird_file) else stringResource(R.string.download_the_model_above_to_enable),
                                fontSize = 12.sp, color = Harbor.TextDim)
                        }
                        androidx.compose.material3.TextButton({ catalog = true }) { Text(stringResource(R.string.ai_change_model), fontSize = 12.sp) }
                        Switch(vm.useAi && aiAvailable, { vm.useAi = it }, enabled = aiAvailable)
                    }
                    OutlinedTextField(vm.query, { vm.query = it }, Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(16.dp),
                        placeholder = { Text(stringResource(R.string.find_a_title_identified_first)) },
                        leadingIcon = { Icon(Icons.Rounded.AutoFixHigh, null, tint = Harbor.VioletSoft) })
                    ScanHero(vm)
                }
            }
            if (vm.issues.isNotEmpty()) item {
                val counts = IssueKind.entries.associateWith { k -> vm.issues.count { k in it.kinds } }
                Row(Modifier.padding(16.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    counts.filter { it.value > 0 }.forEach { (k, n) -> Pill("${k.label} $n", when (k) { IssueKind.Misfiled -> Harbor.Coral; IssueKind.NoPoster -> Harbor.Rose; else -> Harbor.Amber }) }
                    Pill("Applied ${vm.issues.count { it.status == FixStatus.Applied }}", Harbor.Mint)
                }
            }
            itemsIndexed(vm.issues.filter { vm.matches(it, vm.query.trim()) }, key = { _, i -> i.item.id }) { i, issue ->
                IssueCard(issue, cfg, Modifier.animateItem().enterRise(i % 8),
                    onApply = { vm.apply(issue) }, onAlternatives = { alternatives = issue }, onEdit = { editing = issue }, onSkip = { vm.skip(issue) })
            }
        }
        if (vm.phase == "done" && vm.issues.isNotEmpty()) Row(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val confident = vm.issues.count { it.confident && it.status == FixStatus.Pending }
            GradientButton(stringResource(R.string.apply_1_s_confident, confident), { vm.applyConfident() }, Modifier.weight(1f), icon = Icons.Rounded.AutoFixHigh, enabled = confident > 0)
            Box(Modifier.height(52.dp).clip(RoundedCornerShape(16.dp)).background(Harbor.SurfaceHigh).pressable { vm.planMoves() }.padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center) {
                Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.AutoMirrored.Rounded.DriveFileMove, null, tint = Harbor.Sky); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.organize), fontWeight = FontWeight.Bold) }
            }
        }
        SnackbarHost(snack, Modifier.align(Alignment.BottomCenter).padding(bottom = 90.dp))
    }

    alternatives?.let { issue ->
        val live = vm.issues.firstOrNull { it.item.id == issue.item.id } ?: issue
        ModalBottomSheet({ alternatives = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Harbor.Surface) {
            LazyColumn(Modifier.navigationBarsPadding(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { Text(stringResource(R.string.choose_the_right_match), style = MaterialTheme.typography.titleLarge); Text(live.item.name, color = Harbor.TextDim, fontSize = 12.sp) }
                itemsIndexed(live.candidates) { idx, c ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (idx == live.chosen) Harbor.Violet.copy(.2f) else Harbor.line(.04f))
                        .clickable { vm.choose(live, idx); alternatives = null }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(56.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(8.dp))) { NetImage(c.result.imageUrl, Modifier.fillMaxSize(), fallback = c.result.name) }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("${c.result.name} ${c.result.year?.let { "($it)" } ?: ""}", fontWeight = FontWeight.SemiBold)
                            Text("${c.kind} · ${c.result.provider ?: ""}", fontSize = 11.sp, color = Harbor.TextDim)
                            c.result.overview?.let { Text(it, fontSize = 11.sp, color = Harbor.TextDim, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                        }
                        ScoreRing(c.score)
                    }
                }
            }
        }
    }
    editing?.let { issue ->
        var title by remember { mutableStateOf(issue.aiTitle?.title ?: issue.parsed.title) }
        var year by remember { mutableStateOf((issue.aiTitle?.year ?: issue.parsed.year)?.toString().orEmpty()) }
        var series by remember { mutableStateOf(issue.item.type == "Series" || issue.parsed.type == "series") }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(stringResource(R.string.correct_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(issue.item.path?.substringAfterLast('/') ?: issue.item.name, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Harbor.TextDim)
                    OutlinedTextField(title, { title = it }, label = { Text(stringResource(R.string.title)) }, singleLine = true)
                    OutlinedTextField(year, { year = it.filter(Char::isDigit).take(4) }, label = { Text(stringResource(R.string.year)) }, singleLine = true)
                    Row(Modifier.clickable { series = !series }, verticalAlignment = Alignment.CenterVertically) { Checkbox(series, { series = it }); Text(stringResource(R.string.this_is_a_tv_series)) }
                    Text(stringResource(R.string.jellyverse_remembers_this_and_shows_it), fontSize = 11.sp, color = Harbor.VioletSoft)
                }
            },
            confirmButton = { TextButton({ vm.retitle(issue, title.trim(), year.toIntOrNull(), series); editing = null }) { Text(stringResource(R.string.search_again)) } },
            dismissButton = { TextButton({ editing = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    vm.plans?.let { plans -> MovesSheet(plans, vm.moving, onDismiss = { vm.plans = null }, onRun = { vm.executeMoves(it) }) }
}

@Composable
private fun ScanHero(vm: DoctorViewModel) {
    val (done, total) = vm.progress
    val frac by animateFloatAsState(if (total > 0) done.toFloat() / total else 0f, spring(), label = "scan")
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Brush.linearGradient(listOf(Harbor.SurfaceHigh, Harbor.Surface))).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                drawArc(Harbor.line(.08f), -90f, 360f, false, style = Stroke(7.dp.toPx(), cap = StrokeCap.Round))
                drawArc(Brush.sweepGradient(listOf(Harbor.Violet, Harbor.Coral, Harbor.Violet)), -90f, 360f * frac, false, style = Stroke(7.dp.toPx(), cap = StrokeCap.Round))
            }
            Text(if (total > 0) "$done/$total" else "–", fontSize = 13.sp, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            AnimatedContent(vm.phase, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "phase") { p ->
                Text(when (p) { "scanning" -> L10n.s(R.string.reading_library); "identifying" -> L10n.s(R.string.identifying_titles); "done" -> L10n.s(R.string.s_1_s_titles_need_attention, vm.issues.size); else -> L10n.s(R.string.scan_when_you_re_ready) },
                    fontWeight = FontWeight.Bold)
            }
            Text(stringResource(R.string.nothing_changes_until_you_tap_apply), fontSize = 12.sp, color = Harbor.TextDim)
        }
        if (vm.phase == "scanning" || vm.phase == "identifying") TextButton({ vm.stop() }) { Text(stringResource(R.string.stop), color = Harbor.Rose) }
        else Box(Modifier.clip(RoundedCornerShape(14.dp)).background(Harbor.accentH).pressable { vm.scan() }.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text(if (vm.phase == "done") stringResource(R.string.rescan) else stringResource(R.string.scan_2), color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ScoreRing(score: Float) {
    val color = when { score >= 0.8f -> Harbor.Mint; score >= 0.55f -> Harbor.Amber; else -> Harbor.Rose }
    Box(Modifier.size(38.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            drawArc(Harbor.line(.08f), -90f, 360f, false, style = Stroke(3.dp.toPx()))
            drawArc(color, -90f, 360f * score, false, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
        }
        Text("${(score * 100).toInt()}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun IssueCard(
    issue: DoctorIssue, cfg: com.sridhar.harbor.data.ServerConfig, modifier: Modifier,
    onApply: () -> Unit, onAlternatives: () -> Unit, onEdit: () -> Unit, onSkip: () -> Unit,
) {
    val best = issue.best
    val border = when (issue.status) { FixStatus.Applied -> Harbor.Mint; FixStatus.Failed -> Harbor.Rose; else -> Harbor.line(.06f) }
    Column(modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Harbor.Surface)
        .border(1.dp, border, RoundedCornerShape(20.dp)).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(64.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(10.dp))) {
                NetImage("${cfg.jellyfinUrl}/Items/${issue.item.id}/Images/Primary?maxWidth=200", Modifier.fillMaxSize(), fallback = issue.item.name)
            }
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = Harbor.TextDim, modifier = Modifier.padding(horizontal = 6.dp).size(18.dp))
            Box(Modifier.width(64.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(10.dp)).background(Harbor.SurfaceHigh)) {
                if (best != null) NetImage(best.result.imageUrl, Modifier.fillMaxSize(), fallback = best.result.name)
                else if (issue.error == null) com.sridhar.harbor.ui.components.JellyLoader(Modifier.align(Alignment.Center).size(20.dp), strokeWidth = 2.dp)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(issue.item.name, fontSize = 11.sp, color = Harbor.TextDim, maxLines = 2, overflow = TextOverflow.Ellipsis, fontFamily = FontFamily.Monospace)
                Text(best?.let { "${it.result.name} ${it.result.year?.let { y -> "($y)" } ?: ""}" } ?: (issue.aiTitle ?: issue.parsed).let { "${it.title} ${it.year?.let { y -> "($y)" } ?: ""}" },
                    fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp)) {
                    issue.kinds.take(2).forEach { k -> Pill(k.label, if (k == IssueKind.Misfiled) Harbor.Coral else Harbor.Amber) }
                    if (issue.aiTitle != null) Pill(stringResource(R.string.qwen), Harbor.VioletSoft)
                }
            }
            best?.let { ScoreRing(it.score) }
        }
        issue.error?.let { Text(it, color = Harbor.Rose, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp)) }
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            when (issue.status) {
                FixStatus.Applied -> { Icon(Icons.Rounded.CheckCircle, null, tint = Harbor.Mint, modifier = Modifier.size(18.dp)); Text(stringResource(R.string.applied_posters_on_the_way), color = Harbor.Mint, fontSize = 12.sp) }
                FixStatus.Skipped -> Text(stringResource(R.string.skipped), color = Harbor.TextDim, fontSize = 12.sp)
                FixStatus.Applying -> {
                    com.sridhar.harbor.ui.components.JellyLoader(Modifier.size(22.dp))
                    Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.doctor_applying), color = Harbor.Sky, fontSize = 12.sp)
                }
                else -> {
                    if (best != null) Text(stringResource(when { best.kind == issue.item.type -> R.string.apply; best.kind == "Series" -> R.string.doctor_move_to_tv; else -> R.string.doctor_move_to_movies }), color = Harbor.Fg, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(Harbor.accentH).pressable(onClick = onApply).padding(horizontal = 16.dp, vertical = 7.dp))
                    if (issue.candidates.size > 1) TextButton(onAlternatives) { Icon(Icons.Rounded.SwapHoriz, null, modifier = Modifier.size(16.dp)); Text(stringResource(R.string.s_1_s_more, issue.candidates.size - 1)) }
                    TextButton(onEdit) { Icon(Icons.Rounded.Edit, null, modifier = Modifier.size(16.dp)); Text(stringResource(R.string.fix_title)) }
                    Spacer(Modifier.weight(1f))
                    TextButton(onSkip) { Text(stringResource(R.string.skip), color = Harbor.TextDim) }
                }
            }
        }
    }
}

@Composable
private fun MovesSheet(plans: List<MovePlan>, moving: Boolean, onDismiss: () -> Unit, onRun: (List<MovePlan>) -> Unit) {
    val selected = remember(plans.size) { mutableStateListOf<String>().apply { addAll(plans.filter { it.done == null }.map { it.itemId }) } }
    ModalBottomSheet(onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Harbor.Surface) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 16.dp)) {
            Text(stringResource(R.string.organize_files), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.preview_moves_happen_on_your_server), fontSize = 12.sp, color = Harbor.TextDim)
            LazyColumn(Modifier.heightIn(max = 480.dp).padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(plans, key = { it.itemId }) { p ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(.25f)).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (p.done == null) Checkbox(p.itemId in selected, { on -> if (on) selected += p.itemId else selected -= p.itemId })
                        else Icon(if (p.done) Icons.Rounded.CheckCircle else Icons.Rounded.Error, null, tint = if (p.done) Harbor.Mint else Harbor.Rose, modifier = Modifier.padding(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.label, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text(p.fromHost.substringAfterLast("/movies/").substringAfterLast("/tv/"), fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = Harbor.Rose.copy(.8f), maxLines = 2)
                            Text("→ " + p.toHost.substringAfterLast("/movies/").substringAfterLast("/tv/"), fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = Harbor.Mint, maxLines = 2)
                            p.error?.let { Text(it, fontSize = 10.sp, color = Harbor.Rose) }
                        }
                    }
                }
            }
            GradientButton(if (moving) stringResource(R.string.moving) else stringResource(R.string.move_1_s_item_s, selected.size), { onRun(plans.filter { it.itemId in selected }) },
                Modifier.fillMaxWidth().padding(bottom = 16.dp), icon = Icons.AutoMirrored.Rounded.DriveFileMove, enabled = !moving && selected.isNotEmpty())
        }
    }
}
