package com.sridhar.harbor.ui.components

import android.app.DownloadManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sridhar.harbor.ui.theme.Harbor
import kotlinx.coroutines.delay

@Composable
fun DownloadsBanner() {
    val container = LocalContainer.current
    val entries by container.offline.entries.collectAsState(emptyList())
    var activeProgress by remember { mutableStateOf(0f) }
    var activeName by remember { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }

    LaunchedEffect(entries) {
        while (true) {
            val active = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                entries.map { it to container.offline.progress(it) }
            }.filter { !it.second.done && !it.second.failed }
            
            if (active.isNotEmpty()) {
                show = true
                val totalProgress = active.map { it.second.fraction }.average().toFloat()
                activeProgress = if (totalProgress.isNaN()) 0f else totalProgress
                activeName = if (active.size == 1) active.first().first.name else "${active.size} items downloading"
            } else {
                show = false
            }
            delay(1000)
        }
    }

    Box(Modifier.fillMaxSize().statusBarsPadding().padding(top = 70.dp), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(
            visible = show,
            enter = slideInVertically(spring(dampingRatio = 0.7f)) { -it * 2 } + fadeIn(),
            exit = slideOutVertically { -it * 2 } + fadeOut()
        ) {
            Column(
                Modifier
                    .padding(horizontal = 16.dp)
                    .width(280.dp)
                    .shadow(12.dp, RoundedCornerShape(12.dp))
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF1A1D26))
                    .border(1.dp, Harbor.Violet.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                Text(
                    text = "Downloading...",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = activeName,
                    color = Harbor.TextDim,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(10.dp))
                GradientProgress(progress = activeProgress, height = 4.dp)
            }
        }
    }
}
