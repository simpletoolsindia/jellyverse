package com.sridhar.harbor.ui.doctor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sridhar.harbor.L10n
import com.sridhar.harbor.R
import com.sridhar.harbor.data.AppContainer
import com.sridhar.harbor.data.jellyfin.DupCopy
import com.sridhar.harbor.data.jellyfin.DupGroup
import com.sridhar.harbor.ui.components.GradientButton
import com.sridhar.harbor.ui.components.LocalContainer
import com.sridhar.harbor.ui.components.MessageState
import com.sridhar.harbor.ui.components.NetImage
import com.sridhar.harbor.ui.components.friendly
import com.sridhar.harbor.ui.components.rememberConfig
import com.sridhar.harbor.ui.theme.Harbor
import kotlinx.coroutines.launch

class DuplicatesViewModel(private val c: AppContainer) : ViewModel() {
    var groups by mutableStateOf<List<DupGroup>?>(null)
    var loading by mutableStateOf(false)
    var deleting by mutableStateOf(false)
    var message by mutableStateOf<String?>(null)
    /** Copy ids the user marked for deletion. */
    val selected = mutableStateListOf<String>()

    fun scan() = viewModelScope.launch {
        loading = true
        runCatching { c.jellyfin.duplicates() }
            // Nothing is pre-selected: a "duplicate" can be a mis-matched different film, so the user decides.
            .onSuccess { g -> groups = g; selected.retainAll(g.flatMap { grp -> grp.extras.map { it.id } }.toSet()) }
            .onFailure { message = it.friendly() }
        loading = false
    }

    fun toggle(id: String) { if (id in selected) selected -= id else selected += id }
    fun selectExtras(g: DupGroup) { g.extras.forEach { if (it.id !in selected) selected += it.id } }
    fun selectAllExtras() = groups.orEmpty().forEach(::selectExtras)

    val selectedBytes: Long get() = groups.orEmpty().flatMap { it.copies }.filter { it.id in selected }.sumOf { it.size }

    fun deleteSelected() = viewModelScope.launch {
        deleting = true
        var ok = 0; var firstError: String? = null
        for (id in selected.toList()) {
            runCatching { c.jellyfin.deleteItem(id) }.onSuccess { ok++; selected -= id }.onFailure { if (firstError == null) firstError = it.message ?: it.friendly() }
        }
        message = firstError?.let { L10n.s(R.string.dup_deleted_some, ok, it) } ?: L10n.s(R.string.dup_deleted, ok)
        deleting = false
        scan()
    }
}

fun gb(bytes: Long): String = if (bytes <= 0) "—" else if (bytes >= 1_000_000_000) "%.1f GB".format(bytes / 1e9) else "%.0f MB".format(bytes / 1e6)

/** Duplicate films and shows with each copy's quality; pick copies and delete them from the server. */
@Composable
fun DuplicatesScreen(onBack: () -> Unit) {
    val container = LocalContainer.current
    val cfg = rememberConfig()
    val vm = viewModel { DuplicatesViewModel(container) }
    val snack = remember { SnackbarHostState() }
    var confirm by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { if (vm.groups == null && cfg.jellyfinReady) vm.scan() }
    LaunchedEffect(vm.message) { vm.message?.let { snack.showSnackbar(it); vm.message = null } }

    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, containerColor = Harbor.Surface,
        icon = { Icon(Icons.Rounded.Delete, null, tint = Harbor.Rose) },
        title = { Text(stringResource(R.string.dup_confirm_title, vm.selected.size)) },
        text = { Text(stringResource(R.string.dup_confirm_body, gb(vm.selectedBytes))) },
        confirmButton = { TextButton({ confirm = false; vm.deleteSelected() }) { Text(stringResource(R.string.dup_delete), color = Harbor.Rose, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton({ confirm = false }) { Text(stringResource(R.string.dup_cancel)) } })

    Box(Modifier.fillMaxSize().background(Harbor.Ink)) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 140.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Row(Modifier.statusBarsPadding().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) }
                    Column {
                        Text(stringResource(R.string.dup_title), style = MaterialTheme.typography.headlineMedium)
                        Text(stringResource(R.string.dup_sub), color = Harbor.TextDim, fontSize = 12.sp)
                    }
                }
            }
            when {
                !cfg.jellyfinReady -> item { MessageState(stringResource(R.string.jellyfin_isn_t_connected), null) }
                vm.loading && vm.groups == null -> item {
                    Column(Modifier.fillMaxWidth().padding(48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Harbor.Sky); Spacer(Modifier.height(12.dp))
                        Text(stringResource(R.string.dup_scanning), color = Harbor.TextDim)
                    }
                }
                vm.groups?.isEmpty() == true -> item { MessageState(stringResource(R.string.dup_none), stringResource(R.string.dup_none_sub), icon = Icons.Rounded.ContentCopy, onRetry = { vm.scan() }, actionLabel = stringResource(R.string.dup_rescan)) }
                else -> {
                    vm.groups?.let { g ->
                        item {
                            Column(Modifier.padding(horizontal = 16.dp)) {
                                Text(stringResource(R.string.dup_summary, g.size, gb(g.sumOf { it.wastedBytes })), color = Harbor.TextDim, fontSize = 13.sp)
                                Text(stringResource(R.string.dup_check_hint), color = Harbor.Amber, fontSize = 12.sp)
                                Row {
                                    TextButton({ vm.selectAllExtras() }) { Text(stringResource(R.string.dup_select_all)) }
                                    if (vm.selected.isNotEmpty()) TextButton({ vm.selected.clear() }) { Text(stringResource(R.string.dup_clear)) }
                                }
                            }
                        }
                        items(g, key = { it.key }) { grp -> DupCard(grp, vm) }
                    }
                }
            }
        }
        AnimatedVisibility(vm.selected.isNotEmpty() && vm.groups != null, Modifier.align(Alignment.BottomCenter)) {
            Row(Modifier.fillMaxWidth().background(Harbor.Surface).navigationBarsPadding().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.dup_selected, vm.selected.size), fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.dup_frees, gb(vm.selectedBytes)), color = Harbor.TextDim, fontSize = 12.sp)
                }
                if (vm.deleting) CircularProgressIndicator(Modifier.size(28.dp), color = Harbor.Rose, strokeWidth = 3.dp)
                else GradientButton(stringResource(R.string.dup_delete), { confirm = true }, icon = Icons.Rounded.Delete)
            }
        }
        SnackbarHost(snack, Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp))
    }
}

@Composable
private fun DupCard(g: DupGroup, vm: DuplicatesViewModel) {
    val container = LocalContainer.current
    val cfg = rememberConfig()
    Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Harbor.Surface).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NetImage(container.jellyfin.imageUrl(cfg, g.posterItemId, maxWidth = 160), Modifier.size(44.dp, 64.dp).clip(RoundedCornerShape(8.dp)))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(g.title + (g.year?.let { " ($it)" } ?: ""), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(stringResource(if (g.type == "Series") R.string.dup_copies_show else R.string.dup_copies, g.copies.size) +
                    (if (g.wastedBytes > 0) " · " + stringResource(R.string.dup_wasted, gb(g.wastedBytes)) else ""), color = Harbor.TextDim, fontSize = 12.sp)
            }
            if (g.extras.isNotEmpty()) TextButton({ vm.selectExtras(g) }) { Text(stringResource(R.string.dup_select_extras), fontSize = 12.sp) }
        }
        if (g.type == "Series") Text(stringResource(R.string.dup_series_warn), color = Harbor.Amber, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
        Spacer(Modifier.height(2.dp))
        val shared = g.sharedPath
        g.copies.sortedByDescending { it == g.best }.forEach { copy ->
            CopyRow(copy, copy.id == g.best.id, copy.id in vm.selected, enabled = copy.id !in shared) { vm.toggle(copy.id) }
        }
    }
}

@Composable
private fun CopyRow(c: DupCopy, best: Boolean, checked: Boolean, enabled: Boolean, onToggle: () -> Unit) {
    val qColor = when (c.quality) { "4K" -> Harbor.Amber; "1080p" -> Harbor.Mint; "720p" -> Harbor.Sky; else -> Harbor.TextDim }
    val border by animateColorAsState(if (checked) Harbor.Rose.copy(alpha = 0.7f) else Color.Transparent, label = "dupBorder")
    Row(Modifier.padding(top = 6.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Harbor.SurfaceHigh)
        .border(1.5.dp, border, RoundedCornerShape(12.dp)).clickable(enabled = enabled, onClick = onToggle).padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(c.quality, Modifier.clip(RoundedCornerShape(6.dp)).background(qColor.copy(alpha = 0.18f)).padding(horizontal = 7.dp, vertical = 3.dp),
                color = qColor, fontWeight = FontWeight.Black, fontSize = 12.sp)
            if (best) Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Star, null, Modifier.size(12.dp), tint = Harbor.Amber)
                Text(stringResource(R.string.dup_best), color = Harbor.Amber, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(listOfNotNull(gb(c.size), c.videoCodec?.uppercase(), c.container?.uppercase(), c.bitrate?.let { "%.1f Mbps".format(it / 1e6) }).joinToString(" · "),
                fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text(c.path ?: c.fileName, color = Harbor.TextDim, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (!enabled) Text(stringResource(R.string.dup_same_file), color = Harbor.Amber, fontSize = 11.sp)
        }
        Checkbox(checked, { onToggle() }, enabled = enabled)
    }
}
