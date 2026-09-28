package com.sridhar.harbor.tv

import com.sridhar.harbor.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sridhar.harbor.data.iptv.Channel
import com.sridhar.harbor.ui.components.GradientProgress
import com.sridhar.harbor.ui.components.LocalContainer
import com.sridhar.harbor.ui.components.NetImage
import com.sridhar.harbor.ui.live.AddPlaylistForm
import com.sridhar.harbor.ui.live.LiveTvViewModel
import com.sridhar.harbor.ui.player.PlayerActivity
import com.sridhar.harbor.ui.theme.Harbor
import java.text.DateFormat
import java.util.Date

@Composable
fun TvLive() {
    val container = LocalContainer.current
    val ctx = LocalContext.current
    val vm = viewModel { LiveTvViewModel(container) }
    val playlists by vm.playlists.collectAsState()
    val favs by vm.favorites.collectAsState()
    var adding by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf<Channel?>(null) }
    @Suppress("UNUSED_VARIABLE") val tick = vm.epgTick

    if (playlists.isEmpty() || adding) {
        Row(Modifier.fillMaxSize().padding(56.dp), horizontalArrangement = Arrangement.spacedBy(56.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(320.dp)) {
                Text(stringResource(R.string.live_tv), color = Harbor.Fg, fontSize = 44.sp, fontWeight = FontWeight.Black)
                Text(stringResource(R.string.add_an_m3u_playlist_or_xtream), color = Harbor.TextDim, fontSize = 16.sp)
                if (adding) TvButton(stringResource(R.string.cancel), null, modifier = Modifier.padding(top = 20.dp)) { adding = false }
            }
            Box(Modifier.width(480.dp)) { AddPlaylistForm { vm.add(it); adding = false } }
        }
        return
    }

    Row(Modifier.fillMaxSize()) {
        // Groups
        LazyColumn(Modifier.width(230.dp).fillMaxHeight().background(Color.Black.copy(.25f)), contentPadding = PaddingValues(vertical = 32.dp, horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            item { Text(stringResource(R.string.live_tv), color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(start = 8.dp, bottom = 12.dp)) }
            items(playlists, key = { "p" + it.id }) { p -> Chip("📡 " + p.name, vm.selected?.id == p.id) { vm.select(p) } }
            item {
                Chip(if (vm.refreshing) "⏳ " + stringResource(R.string.iptv_refreshing) else "🔄 " + stringResource(R.string.iptv_refresh_source), false) { vm.refreshFromSource() }
                val note = vm.refreshNote
                androidx.compose.runtime.LaunchedEffect(note) { if (note != null) { kotlinx.coroutines.delay(3500); vm.refreshNote = null } }
                val ago = vm.refreshedAt?.let { if (System.currentTimeMillis() - it < 60_000) com.sridhar.harbor.L10n.s(R.string.iptv_just_now) else android.text.format.DateUtils.getRelativeTimeSpanString(it, System.currentTimeMillis(), android.text.format.DateUtils.MINUTE_IN_MILLIS).toString() }
                Text(note ?: ago?.let { stringResource(R.string.iptv_updated_ago, it) } ?: "", color = if (note != null) Harbor.Mint else Harbor.TextDim, fontSize = 12.sp,
                    modifier = Modifier.padding(start = 12.dp, bottom = 6.dp))
            }
            item { Text(vm.groupLabel.uppercase(), color = Harbor.TextDim, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp, top = 14.dp, bottom = 4.dp)) }
            items(vm.groups, key = { "g$it" }) { g -> Chip(g, vm.group == g) { vm.group = g } }
            item {
                Spacer(Modifier.height(10.dp))
                val n by vm.checked.collectAsState()
                Chip(when { vm.checking -> stringResource(R.string.checking_1_s, n); vm.hideOffline -> stringResource(R.string.working_only); else -> stringResource(R.string.check_channels) }, vm.hideOffline) {
                    if (vm.hideOffline && !vm.checking) vm.hideOffline = false else vm.checkChannels()
                }
            }
            item { Spacer(Modifier.height(10.dp)); TvButton(stringResource(R.string.add_playlist), Icons.Rounded.Add) { adding = true } }
        }
        Column(Modifier.weight(1f).fillMaxHeight()) {
            // Header follows focus: what's on now
            Row(Modifier.fillMaxWidth().height(150.dp).background(Brush.horizontalGradient(listOf(Harbor.Violet.copy(.25f), Color.Transparent))).padding(24.dp),
                verticalAlignment = Alignment.CenterVertically) {
                focused?.let { ch ->
                    Box(Modifier.size(96.dp).clip(RoundedCornerShape(18.dp)).background(Harbor.line(.08f))) {
                        NetImage(ch.logo, Modifier.fillMaxSize().padding(8.dp), contentScale = ContentScale.Fit, fallback = ch.name.take(3))
                    }
                    Spacer(Modifier.width(20.dp))
                    Column(Modifier.weight(1f)) {
                        Text(ch.name, color = Harbor.Fg, fontSize = 26.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val nn = vm.nowNext(ch)
                        nn.now?.let { Text(stringResource(R.string.now_1_s, it.title), color = Harbor.Fg.copy(.85f), fontSize = 16.sp, maxLines = 1); GradientProgress(it.progress, height = 4.dp, modifier = Modifier.width(360.dp).padding(vertical = 6.dp)) }
                            ?: Text(ch.group, color = Harbor.TextDim, fontSize = 16.sp)
                        nn.next?.let { Text(stringResource(R.string.next_1_s_2_s_2, DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it.start)), it.title), color = Harbor.TextDim, fontSize = 14.sp, maxLines = 1) }
                    }
                }
            }
            if (vm.loading && vm.channels.isEmpty()) Box(Modifier.fillMaxSize(), Alignment.Center) { com.sridhar.harbor.ui.components.JellyLoader(color = Harbor.VioletSoft) }
            else if (vm.error != null && vm.channels.isEmpty()) Text(vm.error.orEmpty(), color = Harbor.Rose, modifier = Modifier.padding(24.dp))
            else LazyVerticalGrid(GridCells.Adaptive(150.dp), Modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                items(vm.shown, key = { it.id }, contentType = { "ch" }) { ch ->
                    Column(Modifier.width(200.dp)) {
                        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).tvFocusable(onFocus = { focused = ch }) {
                            vm.selected?.let { p -> PlayerActivity.startLive(ctx, p.id, ch.id) }
                        }.clip(RoundedCornerShape(14.dp)).background(Harbor.Surface), contentAlignment = Alignment.Center) {
                            NetImage(ch.logo, Modifier.fillMaxSize().padding(18.dp), contentScale = ContentScale.Fit, fallback = ch.name)
                            if (ch.id in favs) Text("★", color = Harbor.Amber, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp))
                        }
                        Text(ch.name, color = Color.White, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }
        }
    }
}
