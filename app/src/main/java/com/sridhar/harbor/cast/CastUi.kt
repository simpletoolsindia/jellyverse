package com.sridhar.harbor.cast

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.CastConnected
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Forward30
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sridhar.harbor.ui.components.GradientProgress
import com.sridhar.harbor.ui.components.LocalContainer
import com.sridhar.harbor.ui.components.formatClock
import com.sridhar.harbor.ui.theme.Harbor
import kotlinx.coroutines.launch

/** Device picker; [onPicked] receives the chosen device name hint. */
@Composable
fun CastPicker(onDismiss: () -> Unit, onPicked: (String) -> Unit) {
    val cast = LocalContainer.current.cast
    val devices by cast.devices.collectAsState()
    DisposableEffect(Unit) { cast.startDiscovery(); onDispose { } }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Cast, null, tint = Harbor.VioletSoft) },
        title = { Text("Cast to") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (!cast.available) Text("Chromecast needs Google Play services on this device.", color = Harbor.TextDim)
                else if (devices.isEmpty()) Row(verticalAlignment = Alignment.CenterVertically) {
                    com.sridhar.harbor.ui.components.JellyLoader(Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.width(10.dp))
                    Text("Looking for Chromecasts on your Wi-Fi…", color = Harbor.TextDim)
                }
                devices.forEach { d ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Harbor.line(.05f)).clickable { onPicked(d.name) }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Tv, null, tint = Harbor.Sky); Spacer(Modifier.width(12.dp))
                        Column { Text(d.name, fontWeight = FontWeight.SemiBold); d.description?.let { Text(it, fontSize = 11.sp, color = Harbor.TextDim) } }
                    }
                }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text("Close") } },
    )
}

/** Floating "now casting" controller shown across the phone app. */
@Composable
fun CastMiniBar(modifier: Modifier = Modifier) {
    val cast = LocalContainer.current.cast
    val status by cast.status.collectAsState()
    AnimatedVisibility(status != null, modifier, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
        val s = status ?: return@AnimatedVisibility
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(20.dp)).background(Harbor.SurfaceHigh)
            .border(1.dp, Harbor.Sky.copy(.35f), RoundedCornerShape(20.dp))) {
            Row(Modifier.padding(start = 14.dp, end = 4.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.CastConnected, null, tint = Harbor.Sky, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(s.title ?: "Connected", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${s.device} · ${formatClock(s.positionMs)} / ${formatClock(s.durationMs)}", fontSize = 11.sp, color = Harbor.TextDim, maxLines = 1)
                }
                IconButton({ cast.seekBy(-10_000) }) { Icon(Icons.Rounded.Replay10, "Back 10s") }
                IconButton({ cast.togglePlay() }) { Icon(if (s.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Play/pause", tint = Harbor.Fg) }
                IconButton({ cast.seekBy(30_000) }) { Icon(Icons.Rounded.Forward30, "Forward 30s") }
                IconButton({ cast.disconnect() }) { Icon(Icons.Rounded.Close, "Stop casting", tint = Harbor.TextDim) }
            }
            GradientProgress(if (s.durationMs > 0) s.positionMs.toFloat() / s.durationMs else 0f, height = 3.dp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp))
        }
    }
}
