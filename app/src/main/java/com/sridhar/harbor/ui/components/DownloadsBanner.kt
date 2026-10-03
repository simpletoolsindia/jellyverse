package com.sridhar.harbor.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sridhar.harbor.R
import com.sridhar.harbor.data.offline.OfflineEntry
import com.sridhar.harbor.data.offline.OfflineProgress
import com.sridhar.harbor.ui.theme.Harbor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Downloads in progress, shown as a small floating button (bottom-right, above the tab bar) instead of a card over
 * the screen: a progress ring with an arrow that keeps dropping. Tap → a compact panel with the title(s), progress,
 * Cancel and "Open downloads". Only running / queued downloads count – a stopped one is never shown as downloading.
 */
@Composable
fun DownloadsBanner() {
    val container = LocalContainer.current
    val entries by container.offline.entries.collectAsState(emptyList())
    var active by remember { mutableStateOf<List<Pair<OfflineEntry, OfflineProgress>>>(emptyList()) }
    var open by remember { mutableStateOf(false) }
    var confirmCancel by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(entries) {
        while (true) {
            active = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                entries.map { it to container.offline.progress(it) }.filter { it.second.active }
            }
            if (active.isEmpty()) open = false
            delay(1000)
        }
    }
    val progress = active.map { it.second.fraction }.average().toFloat().takeIf { !it.isNaN() } ?: 0f
    val title = if (active.size == 1) active.first().first.name else stringResource(R.string.dl_n_items, active.size)

    if (confirmCancel) AlertDialog(onDismissRequest = { confirmCancel = false }, containerColor = Harbor.Surface,
        title = { Text(stringResource(R.string.dl_cancel_title)) },
        text = { Text(stringResource(R.string.dl_cancel_body)) },
        confirmButton = {
            TextButton({ confirmCancel = false; open = false; scope.launch(com.sridhar.harbor.CrashGuard) { container.offline.cancelActive() } }) {
                Text(stringResource(R.string.dl_cancel), color = Harbor.Rose, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton({ confirmCancel = false }) { Text(stringResource(R.string.dl_keep)) } })

    Box(Modifier.fillMaxSize().navigationBarsPadding().padding(end = 16.dp, bottom = 96.dp), contentAlignment = Alignment.BottomEnd) {
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Compact panel, opened from the button.
            AnimatedVisibility(open && active.isNotEmpty(), enter = scaleIn(transformOrigin = TransformOrigin(1f, 1f)) + fadeIn(),
                exit = scaleOut(transformOrigin = TransformOrigin(1f, 1f)) + fadeOut()) {
                Column(Modifier.width(270.dp).shadow(14.dp, RoundedCornerShape(18.dp)).clip(RoundedCornerShape(18.dp)).background(Harbor.SurfaceHigh)
                    .border(1.dp, Harbor.Sky.copy(alpha = .3f), RoundedCornerShape(18.dp)).padding(14.dp)) {
                    Text(stringResource(R.string.dl_panel_title), color = Harbor.TextDim, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    active.take(4).forEach { (e, p) ->
                        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            ProgressRing(p.fraction, size = 34.dp, stroke = 3.dp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(e.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(listOfNotNull(e.subtitle, p.bytesPerSec.takeIf { it > 0 }?.let { "%.1f MB/s".format(it / 1e6) }).joinToString(" · "),
                                    color = Harbor.TextDim, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                        TextButton({ open = false; container.navRequests.tryEmit("downloads") }) { Text(stringResource(R.string.dl_open_all)) }
                        TextButton({ confirmCancel = true }) {
                            Icon(Icons.Rounded.Close, null, Modifier.size(16.dp), tint = Harbor.Rose); Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.dl_cancel), color = Harbor.Rose, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            AnimatedVisibility(active.isNotEmpty(), enter = scaleIn(spring(dampingRatio = .55f)) + fadeIn(), exit = scaleOut() + fadeOut()) {
                DownloadFab(progress, title) { open = !open }
            }
        }
    }
}

/** The floating button: progress ring + an arrow that drops into a tray, over and over. */
@Composable
private fun DownloadFab(progress: Float, description: String, onClick: () -> Unit) {
    val t = rememberInfiniteTransition(label = "dlFab")
    val drop by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart), label = "drop")
    Box(Modifier.size(56.dp).shadow(10.dp, CircleShape).clip(CircleShape).background(Harbor.SurfaceHigh).clickable(onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center) {
        ProgressRing(progress, size = 52.dp, stroke = 3.5.dp, label = false) {
            Box(Modifier.size(26.dp), contentAlignment = Alignment.Center) {
                // The arrow slides down and fades out, then drops in again from the top.
                Icon(Icons.Rounded.ArrowDownward, null, Modifier.size(20.dp).graphicsLayer {
                    translationY = (drop - 0.35f) * 22.dp.toPx(); alpha = (1f - kotlin.math.abs(drop - 0.45f) * 1.6f).coerceIn(0f, 1f)
                }, tint = Harbor.Sky)
                Box(Modifier.align(Alignment.BottomCenter).size(16.dp, 2.5.dp).clip(RoundedCornerShape(2.dp)).background(Harbor.Sky.copy(alpha = .8f)))
            }
        }
        Text("${(progress * 100).toInt()}%", Modifier.align(Alignment.BottomCenter).padding(bottom = 1.dp).clip(RoundedCornerShape(6.dp))
            .background(Color.Black.copy(.55f)).padding(horizontal = 4.dp), color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}
