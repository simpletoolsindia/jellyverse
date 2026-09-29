package com.sridhar.harbor.ui.ai

import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sridhar.harbor.R
import com.sridhar.harbor.data.ai.LlmModel
import com.sridhar.harbor.data.ai.ModelState
import com.sridhar.harbor.ui.components.LocalContainer
import com.sridhar.harbor.ui.theme.Harbor
import kotlinx.coroutines.delay

private enum class ModelFilter { All, Fits, Installed }

/**
 * Full-screen model catalogue: every on-device model JellyVerse can run, with search, a "fits this phone" filter
 * and a RAM warning per model. Get downloads and switches; Use switches to a downloaded one.
 */
@Composable
fun ModelCatalog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val llm = LocalContainer.current.llm
        val state by llm.state.collectAsState()
        val current by llm.model.collectAsState()
        val installed by llm.downloaded.collectAsState()
        var query by remember { mutableStateOf("") }
        var filter by remember { mutableStateOf(ModelFilter.All) }
        LaunchedEffect(state) { while (state is ModelState.Downloading) { delay(1000); llm.refreshDownload() } }
        val ram = llm.deviceRamGb
        val shown = LlmModel.entries.filter { m ->
            (query.isBlank() || listOf(m.displayName, m.maker, m.params, m.blurb).any { it.contains(query.trim(), ignoreCase = true) }) &&
                when (filter) { ModelFilter.All -> true; ModelFilter.Fits -> ram >= m.minRamGb; ModelFilter.Installed -> m in installed }
        }
        Column(Modifier.fillMaxSize().background(Harbor.Ink).statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onDismiss) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) }
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.ai_models_title), style = MaterialTheme.typography.headlineMedium)
                    Text(stringResource(R.string.ai_models_sub, ram), color = Harbor.TextDim, fontSize = 13.sp)
                }
            }
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), singleLine = true,
                placeholder = { Text(stringResource(R.string.ai_models_search)) }, leadingIcon = { Icon(Icons.Rounded.Search, null) },
                shape = RoundedCornerShape(20.dp),
                colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = Harbor.Surface, focusedContainerColor = Harbor.Surface,
                    unfocusedBorderColor = Harbor.line(.08f), focusedBorderColor = Harbor.Violet))
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ModelFilter.entries.forEach { f ->
                    FilterChip(filter == f, { filter = f }, { Text(stringResource(when (f) {
                        ModelFilter.All -> R.string.ai_filter_all; ModelFilter.Fits -> R.string.ai_filter_fits; ModelFilter.Installed -> R.string.ai_filter_installed
                    })) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Harbor.Violet, selectedLabelColor = Color.White))
                }
            }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                items(shown, key = { it.id }) { m ->
                    ModelRow(m, active = m == current, installed = m in installed, state = if (m == current) state else null, ram = ram,
                        onGet = { llm.select(m); llm.startDownload() }, onUse = { llm.select(m) }, onDelete = { llm.delete(m) })
                }
                item { Text(stringResource(R.string.ai_models_footer), color = Harbor.TextDim, fontSize = 12.sp, modifier = Modifier.navigationBarsPadding().padding(top = 8.dp)) }
            }
        }
    }
}

@Composable
private fun ModelRow(m: LlmModel, active: Boolean, installed: Boolean, state: ModelState?, ram: Int, onGet: () -> Unit, onUse: () -> Unit, onDelete: () -> Unit) {
    val tile = remember(m.maker) { makerColors(m.maker) }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(Harbor.Surface)
        .border(if (active) 2.dp else 1.dp, if (active) Harbor.Violet else Harbor.line(.08f), RoundedCornerShape(26.dp))
        .padding(18.dp).animateContentSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp).clip(RoundedCornerShape(18.dp)).background(Brush.linearGradient(tile)), Alignment.Center) { Text(m.emoji, fontSize = 28.sp) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(m.displayName, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 2)
                Text(m.maker, color = Harbor.TextDim, fontSize = 14.sp)
            }
            Spacer(Modifier.width(8.dp))
            val downloading = state as? ModelState.Downloading
            when {
                downloading != null -> Pill("${(downloading.fraction * 100).toInt()}%", Icons.Rounded.CloudDownload, Harbor.SurfaceHigh, Harbor.Fg) {}
                active && installed -> Pill(stringResource(R.string.ai_active), Icons.Rounded.Check, Harbor.Violet, Color.White) {}
                installed -> Pill(stringResource(R.string.ai_use), Icons.Rounded.Check, Harbor.Violet.copy(alpha = .18f), Harbor.Fg, onUse)
                else -> Pill(stringResource(R.string.ai_get), Icons.Rounded.Download, Harbor.SurfaceHigh, Harbor.Fg, onGet)
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(m.blurb, color = Harbor.Fg.copy(alpha = .85f), fontSize = 14.sp, lineHeight = 20.sp)
        // RAM check: below the minimum it won't load; right at it, Android may close other apps.
        if (ram < m.minRamGb + 1) {
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Harbor.Amber.copy(alpha = .12f)).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.WarningAmber, null, tint = Harbor.Amber, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(stringResource(if (ram < m.minRamGb) R.string.ai_too_big else R.string.ai_tight_fit), color = Harbor.Amber, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Chip(Icons.Rounded.Tune, m.params)
            Chip(Icons.Rounded.CloudDownload, if (m.sizeMb >= 1000) "%.1f GB".format(m.sizeMb / 1000f) else "${m.sizeMb} MB")
            Chip(Icons.Rounded.Memory, "${m.minRamGb} GB RAM")
            Spacer(Modifier.weight(1f))
            if (installed) IconButton(onDelete, Modifier.size(34.dp)) { Icon(Icons.Rounded.Delete, stringResource(R.string.remove), tint = Harbor.TextDim, modifier = Modifier.size(20.dp)) }
        }
    }
}

@Composable
private fun Pill(text: String, icon: ImageVector, bg: Color, fg: Color, onClick: () -> Unit) {
    Row(Modifier.clip(CircleShape).background(bg).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
        Text(text, color = fg, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

@Composable
private fun Chip(icon: ImageVector, text: String) {
    Row(Modifier.clip(RoundedCornerShape(10.dp)).background(Harbor.SurfaceHigh).padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Harbor.TextDim, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(5.dp))
        Text(text, color = Harbor.Fg.copy(alpha = .85f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** A recognisable tile colour per maker. */
private fun makerColors(maker: String): List<Color> = when (maker) {
    "Alibaba" -> listOf(Color(0xFF7C3AED), Color(0xFF9F67FF))
    "Google" -> listOf(Color(0xFF1A73E8), Color(0xFF4FC3F7))
    "Microsoft" -> listOf(Color(0xFF10B981), Color(0xFF34D399))
    "Hugging Face" -> listOf(Color(0xFFF59E0B), Color(0xFFFBBF24))
    "DeepSeek" -> listOf(Color(0xFF2563EB), Color(0xFF60A5FA))
    "OpenBMB" -> listOf(Color(0xFFEAB308), Color(0xFFF97316))
    "Mistral AI" -> listOf(Color(0xFFEA580C), Color(0xFFF59E0B))
    "Ai2" -> listOf(Color(0xFFDB2777), Color(0xFFF472B6))
    else -> listOf(Color(0xFF0EA5E9), Color(0xFF22D3EE))
}
