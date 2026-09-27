package com.sridhar.harbor.ui.offline

import com.sridhar.harbor.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DownloadForOffline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sridhar.harbor.ui.components.GradientProgress
import com.sridhar.harbor.ui.components.LocalContainer
import com.sridhar.harbor.ui.components.MessageState
import com.sridhar.harbor.ui.components.NetImage
import com.sridhar.harbor.ui.components.formatBytes
import com.sridhar.harbor.ui.player.PlayerActivity
import com.sridhar.harbor.ui.theme.Harbor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun OfflineScreen(onBack: () -> Unit) {
    val container = LocalContainer.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val entries by container.offline.entries.collectAsState(emptyList())
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(1000); tick++ } }

    Scaffold(containerColor = Harbor.Ink, topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.offline)) },
            navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Harbor.Ink),
        )
    }) { pad ->
        if (entries.isEmpty()) {
            MessageState(stringResource(R.string.no_downloads_yet), stringResource(R.string.tap_download_on_any_movie_or), Modifier.padding(pad), icon = Icons.Rounded.DownloadForOffline)
            return@Scaffold
        }
        val total = remember(entries, tick) { entries.sumOf { File(it.filePath).length() } }
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text(stringResource(R.string.s_1_s_items_2_s_on, entries.size, formatBytes(total)), color = Harbor.TextDim) }
            items(entries, key = { it.itemId }) { e ->
                val p = remember(e, tick) { container.offline.progress(e) }
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Harbor.Surface)
                    .clickable(enabled = p.done) { PlayerActivity.start(ctx, e.itemId, offlinePath = e.filePath, title = e.name) }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(70.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(10.dp))) {
                        NetImage(e.posterPath, Modifier.fillMaxSize(), fallback = e.name)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(e.name, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        e.subtitle?.let { Text(it, color = Harbor.TextDim, style = MaterialTheme.typography.bodySmall, maxLines = 1) }
                        Spacer(Modifier.height(8.dp))
                        when {
                            p.done -> Text(formatBytes(p.total), color = Harbor.Mint, style = MaterialTheme.typography.bodySmall)
                            p.failed -> Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.ErrorOutline, null, tint = Harbor.Rose, modifier = Modifier.size(16.dp))
                                Text(stringResource(R.string.download_failed), color = Harbor.Rose, style = MaterialTheme.typography.bodySmall)
                            }
                            else -> {
                                GradientProgress(p.fraction, height = 5.dp)
                                Text("${formatBytes(p.downloaded)} of ${if (p.total > 0) formatBytes(p.total) else "…"}", color = Harbor.TextDim, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    if (p.done) Box(Modifier.size(42.dp).clip(CircleShape).background(Harbor.accent), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.PlayArrow, stringResource(R.string.play), tint = Color.White)
                    }
                    IconButton({ scope.launch(com.sridhar.harbor.CrashGuard) { container.offline.remove(e) } }) { Icon(Icons.Rounded.Delete, stringResource(R.string.remove), tint = Harbor.TextDim) }
                }
            }
        }
    }
}
