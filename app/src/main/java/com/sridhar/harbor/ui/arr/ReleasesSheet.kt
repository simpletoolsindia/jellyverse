package com.sridhar.harbor.ui.arr

import com.sridhar.harbor.R
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sridhar.harbor.data.arr.ArrRelease
import com.sridhar.harbor.ui.components.MessageState
import com.sridhar.harbor.ui.components.Pill
import com.sridhar.harbor.ui.components.enterRise
import com.sridhar.harbor.ui.components.formatBytes
import com.sridhar.harbor.ui.components.friendly
import com.sridhar.harbor.ui.theme.Harbor

/** Interactive search: every indexer result, best first; tap to grab. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReleasesSheet(title: String, load: suspend () -> List<ArrRelease>, onGrab: (ArrRelease) -> Unit, onDismiss: () -> Unit) {
    val result by produceState<Load<List<ArrRelease>>>(Load.Loading, title) {
        value = runCatching { load() }.fold(
            { list -> Load.Ready(list.sortedWith(compareByDescending<ArrRelease> { !it.rejected }.thenByDescending { it.customFormatScore }.thenByDescending { it.seeders ?: 0 })) },
            { Load.Failed(it.friendly()) },
        )
    }
    var confirm by remember { mutableStateOf<ArrRelease?>(null) }
    ModalBottomSheet(onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Harbor.Surface) {
        Column(Modifier.navigationBarsPadding()) {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text(stringResource(R.string.manual_search), style = MaterialTheme.typography.headlineSmall)
                Text(title, color = Harbor.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            AnimatedContent(result, contentKey = { it.branch }, transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(150)) }, label = "releases") { state ->
                when (state) {
                    Load.Loading -> SearchingIndicator()
                    is Load.Failed -> MessageState(stringResource(R.string.search_failed), state.message)
                    is Load.Ready -> if (state.value.isEmpty()) MessageState(stringResource(R.string.no_releases_found), stringResource(R.string.indexers_returned_nothing_for_this_title), icon = Icons.Rounded.TravelExplore)
                    else LazyColumn(Modifier.heightIn(max = 620.dp), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        item { Text("${state.value.size} releases · ${state.value.count { !it.rejected }} acceptable", color = Harbor.TextDim, style = MaterialTheme.typography.bodySmall) }
                        itemsIndexed(state.value, key = { _, r -> r.guid }) { i, r -> ReleaseRow(r, Modifier.enterRise(i)) { confirm = r } }
                    }
                }
            }
        }
    }
    confirm?.let { r ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(if (r.rejected) stringResource(R.string.grab_a_rejected_release) else stringResource(R.string.download_this_release)) },
            text = {
                Column {
                    Text(r.title, style = MaterialTheme.typography.bodySmall)
                    if (r.rejected) { Spacer(Modifier.height(8.dp)); Text(r.rejections.joinToString("\n") { "• $it" }, color = Harbor.Amber, style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = { TextButton({ onGrab(r); confirm = null; onDismiss() }) { Text(stringResource(R.string.download)) } },
            dismissButton = { TextButton({ confirm = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun SearchingIndicator() {
    val t = rememberInfiniteTransition(label = "radar")
    val pulse by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1400), RepeatMode.Restart), label = "pulse")
    Column(Modifier.fillMaxWidth().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(110.dp), contentAlignment = Alignment.Center) {
            listOf(0f, 0.5f).forEach { offset ->
                Box(Modifier.size(110.dp).graphicsLayer {
                    val p = (pulse + offset) % 1f
                    scaleX = 0.3f + p * 0.7f; scaleY = scaleX; alpha = 1f - p
                }.clip(CircleShape).background(Harbor.Violet.copy(alpha = .35f)))
            }
            Box(Modifier.size(52.dp).clip(CircleShape).background(Harbor.accent), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.TravelExplore, null, tint = Color.White)
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.asking_every_indexer), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.this_can_take_up_to_a), color = Harbor.TextDim, style = MaterialTheme.typography.bodySmall)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReleaseRow(r: ArrRelease, modifier: Modifier, onClick: () -> Unit) {
    var showWhy by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Harbor.SurfaceHigh.copy(alpha = if (r.rejected) .5f else 1f))
        .clickable { if (r.rejected) showWhy = !showWhy else onClick() }.padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(r.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 2,
                overflow = TextOverflow.Ellipsis, color = if (r.rejected) Harbor.TextDim else Color.White)
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(38.dp).clip(CircleShape).background(if (r.rejected) Color.White.copy(.06f) else Harbor.Violet.copy(.25f)).clickable(onClick = onClick),
                contentAlignment = Alignment.Center) {
                Icon(if (r.rejected) Icons.Rounded.Block else Icons.Rounded.Download, stringResource(R.string.grab), tint = if (r.rejected) Harbor.Rose else Harbor.VioletSoft, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            r.quality?.quality?.name?.let { Pill(it, Harbor.Sky) }
            Pill(formatBytes(r.size), Harbor.TextDim)
            if (r.protocol == "torrent") Pill("▲ ${r.seeders ?: 0}  ▼ ${r.leechers ?: 0}", if ((r.seeders ?: 0) > 10) Harbor.Mint else Harbor.Amber)
            r.indexer?.let { Pill(it, Harbor.VioletSoft) }
            r.ageHours?.let { h -> Pill(if (h < 48) "${h.toInt()}h" else "${(h / 24).toInt()}d", Harbor.TextDim) }
            if (r.customFormatScore != 0) Pill(stringResource(R.string.cf_1_s, r.customFormatScore), Harbor.Coral)
            r.languages.firstOrNull()?.name?.takeIf { it != "English" }?.let { Pill(it, Harbor.TextDim) }
        }
        AnimatedVisibility(showWhy && r.rejected) {
            Text(r.rejections.joinToString("\n") { "• $it" }, color = Harbor.Amber, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
        }
    }
}
