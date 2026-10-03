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
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.setValue
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
import com.sridhar.harbor.data.ai.LlmModel
import com.sridhar.harbor.ui.components.GradientButton
import com.sridhar.harbor.ui.components.GradientProgress
import com.sridhar.harbor.ui.components.LocalContainer
import com.sridhar.harbor.ui.theme.Harbor
import kotlinx.coroutines.delay

/** The assistant's face everywhere in the app: Jelly, the animated mascot (see [JellyBuddy]). */
@Composable
fun AiOrb(size: androidx.compose.ui.unit.Dp, busy: Boolean = false) = JellyBuddy(size, busy)

@Composable
fun ModelCard(modifier: Modifier = Modifier) {
    val llm = LocalContainer.current.llm
    val state by llm.state.collectAsState()
    val model by llm.model.collectAsState()
    var picking by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    LaunchedEffect(state) { while (state is ModelState.Downloading) { delay(1000); llm.refreshDownload() } }
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Brush.linearGradient(listOf(Harbor.SurfaceHigh, Harbor.Surface)))
        .border(1.dp, Harbor.line(.08f), RoundedCornerShape(22.dp)).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AiOrb(44.dp, busy = state is ModelState.Downloading || state is ModelState.Loading)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(model.displayName, fontWeight = FontWeight.Bold)
                Text(when (val s = state) {
                    ModelState.Missing -> stringResource(R.string.ai_model_missing, mb(model.sizeBytes))
                    is ModelState.Downloading -> stringResource(R.string.downloading_1_s, (s.fraction * 100).toInt())
                    ModelState.Ready -> stringResource(R.string.downloaded_loads_on_first_use)
                    ModelState.Loading -> stringResource(R.string.loading_into_memory)
                    ModelState.Loaded -> stringResource(R.string.ready_private_offline)
                    is ModelState.Failed -> s.message
                }, fontSize = 12.sp, color = if (state is ModelState.Failed) Harbor.Rose else Harbor.TextDim)
            }
            TextButton({ picking = true }) { Text(stringResource(R.string.ai_change_model), color = Harbor.VioletSoft, fontSize = 12.sp) }
        }
        when (val s = state) {
            ModelState.Missing, is ModelState.Failed -> {
                Spacer(Modifier.height(12.dp))
                GradientButton(stringResource(R.string.download_ai_model), { llm.startDownload() }, Modifier.fillMaxWidth(), icon = Icons.Rounded.AutoAwesome)
            }
            is ModelState.Downloading -> { Spacer(Modifier.height(12.dp)); GradientProgress(s.fraction, height = 6.dp) }
            else -> {}
        }
        if (picking) ModelCatalog(onDismiss = { picking = false })
    }
}

private fun mb(bytes: Long) = if (bytes >= 1_000_000_000) "%.1f GB".format(bytes / 1e9) else "${bytes / 1_000_000} MB"
