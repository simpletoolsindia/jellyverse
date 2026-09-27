package com.sridhar.harbor.ui.watch

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sridhar.harbor.R
import com.sridhar.harbor.data.offline.OfflineEntry
import com.sridhar.harbor.ui.components.AdriftJelly
import com.sridhar.harbor.ui.components.LocalContainer
import com.sridhar.harbor.ui.components.NetImage
import com.sridhar.harbor.ui.components.pressable
import com.sridhar.harbor.ui.player.PlayerActivity
import com.sridhar.harbor.ui.theme.Harbor

/** Finished downloads, playable straight from storage – the point of downloading is watching without a network. */
@Composable
fun rememberFinishedDownloads(): List<OfflineEntry> {
    val container = LocalContainer.current
    val entries by container.offline.entries.collectAsState(emptyList())
    return remember(entries) { entries.filter { container.offline.progress(it).done } }
}

@Composable
fun DownloadsShelf(entries: List<OfflineEntry>, offline: Boolean, modifier: Modifier = Modifier) {
    if (entries.isEmpty()) return
    val ctx = LocalContext.current
    Column(modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp)) {
        if (offline) Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(20.dp)).background(Harbor.Surface).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            AdriftJelly(Modifier.size(64.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.net_offline), fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.offline_downloads_ready, entries.size), color = Harbor.TextDim, style = MaterialTheme.typography.bodySmall)
            }
        }
        Text(stringResource(R.string.on_this_device), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(entries, key = { it.itemId }) { e ->
                Column(Modifier.width(120.dp).pressable { PlayerActivity.start(ctx, e.itemId, offlinePath = e.filePath, title = e.name) }) {
                    Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(14.dp))) {
                        NetImage(e.posterPath, Modifier.fillMaxSize(), fallback = e.name)
                        Box(Modifier.align(Alignment.BottomEnd).padding(8.dp).size(34.dp).clip(CircleShape).background(Harbor.Violet), Alignment.Center) {
                            Icon(Icons.Rounded.PlayArrow, stringResource(R.string.play), tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(e.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                    e.subtitle?.let { Text(it, color = Harbor.TextDim, maxLines = 1, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
}
