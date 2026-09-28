package com.sridhar.harbor.ui.ai

import com.sridhar.harbor.R
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sridhar.harbor.data.ai.ModelState
import com.sridhar.harbor.data.ai.QwenModel
import com.sridhar.harbor.ui.components.GradientButton
import com.sridhar.harbor.ui.components.GradientProgress
import com.sridhar.harbor.ui.components.LocalContainer
import com.sridhar.harbor.ui.theme.Harbor
import kotlinx.coroutines.delay

/** Animated "AI orb" – a rotating sweep-gradient ring. */
@Composable
fun AiOrb(size: androidx.compose.ui.unit.Dp, busy: Boolean = false) {
    val t = rememberInfiniteTransition(label = "orb")
    val angle by t.animateFloat(0f, 360f, infiniteRepeatable(tween(if (busy) 1200 else 4000)), label = "a")
    val pulse by t.animateFloat(0.9f, 1f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "p")
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            rotate(angle) { drawCircle(Brush.sweepGradient(listOf(Harbor.Violet, Harbor.Coral, Harbor.Sky, Harbor.Violet)), radius = this.size.minDimension / 2 * pulse) }
            drawCircle(Harbor.Ink.copy(alpha = .55f), radius = this.size.minDimension / 2 * 0.72f)
        }
        Icon(Icons.Rounded.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(size * 0.42f))
    }
}

@Composable
fun ModelCard(modifier: Modifier = Modifier) {
    val llm = LocalContainer.current.llm
    val state by llm.state.collectAsState()
    LaunchedEffect(state) { while (state is ModelState.Downloading) { delay(1000); llm.refreshDownload() } }
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Brush.linearGradient(listOf(Harbor.SurfaceHigh, Harbor.Surface)))
        .border(1.dp, Harbor.line(.08f), RoundedCornerShape(22.dp)).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AiOrb(44.dp, busy = state is ModelState.Downloading || state is ModelState.Loading)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(QwenModel.NAME, fontWeight = FontWeight.Bold)
                Text(when (val s = state) {
                    ModelState.Missing -> stringResource(R.string.runs_100_on_this_phone_521)
                    is ModelState.Downloading -> stringResource(R.string.downloading_1_s, (s.fraction * 100).toInt())
                    ModelState.Ready -> stringResource(R.string.downloaded_loads_on_first_use)
                    ModelState.Loading -> stringResource(R.string.loading_into_memory)
                    ModelState.Loaded -> stringResource(R.string.ready_private_offline)
                    is ModelState.Failed -> s.message
                }, fontSize = 12.sp, color = if (state is ModelState.Failed) Harbor.Rose else Harbor.TextDim)
            }
            if (state is ModelState.Ready || state is ModelState.Loaded) TextButton({ llm.delete() }) { Text(stringResource(R.string.remove), color = Harbor.TextDim, fontSize = 12.sp) }
        }
        when (val s = state) {
            ModelState.Missing, is ModelState.Failed -> {
                Spacer(Modifier.height(12.dp))
                GradientButton(stringResource(R.string.download_ai_model), { llm.startDownload() }, Modifier.fillMaxWidth(), icon = Icons.Rounded.AutoAwesome)
            }
            is ModelState.Downloading -> { Spacer(Modifier.height(12.dp)); GradientProgress(s.fraction, height = 6.dp) }
            else -> {}
        }
    }
}
