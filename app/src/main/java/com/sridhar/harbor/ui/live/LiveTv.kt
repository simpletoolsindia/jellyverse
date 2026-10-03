package com.sridhar.harbor.ui.live

import androidx.compose.foundation.layout.aspectRatio
import com.sridhar.harbor.ui.components.pressable
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sridhar.harbor.data.AppContainer
import com.sridhar.harbor.data.iptv.Channel
import com.sridhar.harbor.data.iptv.Playlist
import com.sridhar.harbor.ui.components.GradientButton
import com.sridhar.harbor.ui.components.GradientProgress
import com.sridhar.harbor.ui.components.LocalContainer
import com.sridhar.harbor.ui.components.MessageState
import com.sridhar.harbor.ui.components.NetImage
import com.sridhar.harbor.ui.components.friendly
import com.sridhar.harbor.ui.components.dpadFieldNav
import com.sridhar.harbor.ui.player.PlayerActivity
import com.sridhar.harbor.ui.theme.Harbor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

const val FAVORITES = "★ Favourites"
const val ALL = "All channels"

/** Shared by the phone and TV Live TV screens. */
class LiveTvViewModel(private val c: AppContainer) : ViewModel() {
    val playlists = c.iptv.playlists
    val favorites = c.iptv.favorites
    var selected by mutableStateOf<Playlist?>(c.iptv.playlists.value.firstOrNull()); private set
    var channels by mutableStateOf<List<Channel>>(emptyList()); private set
    var loading by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var group by mutableStateOf(ALL)
    var query by mutableStateOf("")
    var epgTick by mutableIntStateOf(0); private set
    var hideOffline by mutableStateOf(false)
    /** True while pulling the latest list from the playlist source. */
    var refreshing by mutableStateOf(false); private set
    var refreshedAt by mutableStateOf<Long?>(null); private set
    /** One-shot message after a manual refresh ("812 channels updated"). */
    var refreshNote by mutableStateOf<String?>(null)
    var checking by mutableStateOf(false); private set
    val checked = c.iptv.checked

    fun checkChannels() = viewModelScope.launch(com.sridhar.harbor.CrashGuard) {
        checking = true; hideOffline = true
        runCatching { c.iptv.checkChannels(if (group == ALL || group == FAVORITES) channels else channels.filter { it.group == group }) }
        checking = false
    }

    /** Groups ordered by size, with the languages most Harbor users want pinned first. */
    val groups: List<String>
        get() {
            val counts = channels.groupingBy { it.group }.eachCount()
            val pinned = listOf("Tamil", "English", "Hindi", "Telugu", "Malayalam", "Kannada", "India").filter { it in counts }
            return listOf(FAVORITES, ALL) + pinned + counts.entries.filter { it.key !in pinned }.sortedByDescending { it.value }.map { it.key }
        }
    val groupLabel get() = selected?.groupLabel ?: "Category"

    val shown: List<Channel>
        get() = channels.filter { ch ->
            (when (group) { ALL -> true; FAVORITES -> ch.id in favorites.value; else -> ch.group == group }) &&
                (query.isBlank() || ch.name.contains(query, true)) && !(hideOffline && c.iptv.isOffline(ch))
        }

    init { selected?.let { load(it) } }

    fun select(p: Playlist) { selected = p; group = ALL; load(p) }

    fun load(p: Playlist, refresh: Boolean = false) = viewModelScope.launch(com.sridhar.harbor.CrashGuard) {
        loading = true; error = null
        if (refresh) refreshing = true
        runCatching { c.iptv.channels(p, refresh) }
            .onSuccess {
                channels = it; if (group != ALL && group != FAVORITES && group !in it.map { c2 -> c2.group }) group = ALL
                if (refresh) refreshNote = com.sridhar.harbor.L10n.s(R.string.iptv_updated_n, it.size)
            }
            .onFailure { error = it.friendly(); if (refresh && channels.isNotEmpty()) refreshNote = com.sridhar.harbor.L10n.s(R.string.iptv_refresh_failed, it.friendly()) }
        loading = false; refreshing = false; refreshedAt = c.iptv.lastRefreshed(p)
        // Stale cache (source lists rotate URLs) → quietly pull the latest in the background.
        if (!refresh && channels.isNotEmpty() && c.iptv.isStale(p)) {
            refreshing = true
            runCatching { c.iptv.channels(p, refresh = true) }.onSuccess { channels = it }
            refreshing = false; refreshedAt = c.iptv.lastRefreshed(p)
        }
        runCatching { c.iptv.loadEpg(p, force = refresh) }; epgTick++
        while (true) { delay(60_000); epgTick++ }   // keep now/next fresh
    }

    /** Manual "Refresh from source": re-download the playlist so rotated stream URLs are picked up. */
    fun refreshFromSource() { selected?.let { if (!refreshing) load(it, refresh = true) } }

    fun add(p: Playlist) { c.iptv.add(p); select(p) }
    fun remove(p: Playlist) { c.iptv.remove(p); selected = c.iptv.playlists.value.firstOrNull(); channels = emptyList(); selected?.let { load(it) } }
    fun nowNext(ch: Channel) = c.iptv.nowNext(ch)
    fun toggleFav(ch: Channel) = c.iptv.toggleFavorite(ch)
}

@Composable
fun LiveTvScreen(onBack: () -> Unit) {
    val container = LocalContainer.current
    val ctx = LocalContext.current
    val vm = viewModel { LiveTvViewModel(container) }
    val playlists by vm.playlists.collectAsState()
    val favs by vm.favorites.collectAsState()
    var adding by remember { mutableStateOf(false) }
    @Suppress("UNUSED_VARIABLE") val tick = vm.epgTick

    Column(Modifier.fillMaxSize().background(Harbor.Ink).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) }
            Text(stringResource(R.string.live_tv), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            val checkedCount by vm.checked.collectAsState()
            androidx.compose.material3.TextButton({ if (vm.hideOffline && !vm.checking) vm.hideOffline = false else vm.checkChannels() }) {
                Text(when { vm.checking -> stringResource(R.string.checking_1_s, checkedCount); vm.hideOffline -> stringResource(R.string.show_all); else -> stringResource(R.string.check_channels) }, color = Harbor.Mint, fontSize = 13.sp)
            }
            IconButton({ adding = true }) { Icon(Icons.Rounded.Add, stringResource(R.string.add_playlist)) }
        }
        if (vm.selected != null) IptvRefreshRow(vm)
        if (playlists.isEmpty()) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    MessageState(stringResource(R.string.add_your_iptv_playlist), stringResource(R.string.paste_an_m3u_m3u8_link_or), icon = Icons.Rounded.LiveTv)
                    GradientButton(stringResource(R.string.add_playlist), { adding = true }, icon = Icons.Rounded.Add)
                }
            }
        } else {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(playlists, key = { it.id }) { p ->
                    FilterChip(vm.selected?.id == p.id, { vm.select(p) }, { Text(p.name) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Harbor.Coral.copy(.3f)))
                }
            }
            OutlinedTextField(vm.query, { vm.query = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), singleLine = true,
                shape = RoundedCornerShape(16.dp), placeholder = { Text(stringResource(R.string.search_1_s_channels, vm.channels.size)) }, leadingIcon = { Icon(Icons.Rounded.Search, null) })
            Text(vm.groupLabel.uppercase(), style = MaterialTheme.typography.labelSmall, color = Harbor.TextDim, modifier = Modifier.padding(start = 20.dp, top = 4.dp))
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(vm.groups) { g -> FilterChip(vm.group == g, { vm.group = g }, { Text(g, maxLines = 1) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Harbor.Violet.copy(.3f))) }
            }
            when {
                vm.loading && vm.channels.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) { com.sridhar.harbor.ui.components.JellyLoader(color = Harbor.VioletSoft) }
                vm.error != null && vm.channels.isEmpty() -> MessageState(stringResource(R.string.couldn_t_load_playlist), vm.error, onRetry = { vm.selected?.let { vm.load(it, true) } })
                else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(vm.shown, key = { it.id }, contentType = { "channel" }) { ch ->
                        ChannelRow(ch, vm.channels.indexOf(ch) + 1, ch.id in favs, vm.nowNext(ch),
                            onFav = { vm.toggleFav(ch) }) { vm.selected?.let { p -> PlayerActivity.startLive(ctx, p.id, ch.id) } }
                    }
                    vm.selected?.let { p -> item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        Text(stringResource(R.string.remove_playlist), color = Harbor.Rose, modifier = Modifier.clickable { vm.remove(p) }.padding(16.dp))
                    } } }
                }
            }
        }
    }
    if (adding) AddPlaylistSheet(onDismiss = { adding = false }) { vm.add(it); adding = false }
}

@Composable
private fun ChannelRow(ch: Channel, number: Int, fav: Boolean, nn: com.sridhar.harbor.data.iptv.NowNext, onFav: () -> Unit, onPlay: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Harbor.Surface).clickable(onClick = onPlay).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).background(Harbor.line(.06f))) {
            NetImage(ch.logo, Modifier.fillMaxSize().padding(5.dp), contentScale = ContentScale.Fit, fallback = ch.name.take(3))
            com.sridhar.harbor.ui.components.LiveBadge(Modifier.align(Alignment.BottomCenter).padding(bottom = 2.dp), small = true)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("$number", color = Harbor.Coral, fontSize = 12.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.width(6.dp))
                Text(ch.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val now = nn.now
            if (now != null) {
                Text(now.title, color = Harbor.TextDim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                GradientProgress(now.progress, height = 3.dp, modifier = Modifier.padding(top = 4.dp))
            } else Text(ch.group, color = Harbor.TextDim, fontSize = 12.sp, maxLines = 1)
        }
        IconButton(onFav) { Icon(if (fav) Icons.Rounded.Star else Icons.Rounded.StarBorder, stringResource(R.string.favourite), tint = if (fav) Harbor.Amber else Harbor.TextDim) }
    }
}

/** M3U URL or Xtream Codes login. */
@Composable
fun AddPlaylistForm(onSave: (Playlist) -> Unit) {
    val iptv = LocalContainer.current.iptv
    var xtream by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var epg by remember { mutableStateOf("") }
    var server by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var advanced by remember { mutableStateOf(false) }
    var m3uUser by remember { mutableStateOf("") }
    var m3uPass by remember { mutableStateOf("") }
    var ua by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(!xtream, { xtream = false }, SegmentedButtonDefaults.itemShape(0, 2)) { Text(stringResource(R.string.m3u_link)) }
            SegmentedButton(xtream, { xtream = true }, SegmentedButtonDefaults.itemShape(1, 2)) { Text(stringResource(R.string.xtream_codes)) }
        }
        OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth().dpadFieldNav(), label = { Text(stringResource(R.string.name)) }, singleLine = true, shape = RoundedCornerShape(14.dp))
        if (!xtream) {
            OutlinedTextField(url, { url = it.trim() }, Modifier.fillMaxWidth().dpadFieldNav(), label = { Text(stringResource(R.string.playlist_url_m3u_m3u8)) }, singleLine = true,
                shape = RoundedCornerShape(14.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
            OutlinedTextField(epg, { epg = it.trim() }, Modifier.fillMaxWidth().dpadFieldNav(), label = { Text(stringResource(R.string.epg_xmltv_url_optional)) }, singleLine = true,
                shape = RoundedCornerShape(14.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
            Text(if (advanced) stringResource(R.string.provider_login_user_agent) else stringResource(R.string.provider_login_user_agent_optional), color = Harbor.VioletSoft, fontSize = 13.sp,
                modifier = Modifier.clickable { advanced = !advanced }.padding(vertical = 4.dp))
            if (advanced) {
                OutlinedTextField(m3uUser, { m3uUser = it.trim() }, Modifier.fillMaxWidth().dpadFieldNav(), label = { Text(stringResource(R.string.username)) }, singleLine = true, shape = RoundedCornerShape(14.dp))
                OutlinedTextField(m3uPass, { m3uPass = it }, Modifier.fillMaxWidth().dpadFieldNav(), label = { Text(stringResource(R.string.password)) }, singleLine = true, shape = RoundedCornerShape(14.dp),
                    visualTransformation = PasswordVisualTransformation())
                OutlinedTextField(ua, { ua = it }, Modifier.fillMaxWidth().dpadFieldNav(), label = { Text(stringResource(R.string.user_agent_e_g_vlc_3)) }, singleLine = true, shape = RoundedCornerShape(14.dp))
            }
        } else {
            OutlinedTextField(server, { server = it.trim() }, Modifier.fillMaxWidth().dpadFieldNav(), label = { Text(stringResource(R.string.server_http_host_port)) }, singleLine = true,
                shape = RoundedCornerShape(14.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
            OutlinedTextField(user, { user = it.trim() }, Modifier.fillMaxWidth().dpadFieldNav(), label = { Text(stringResource(R.string.username)) }, singleLine = true, shape = RoundedCornerShape(14.dp))
            OutlinedTextField(pass, { pass = it }, Modifier.fillMaxWidth().dpadFieldNav(), label = { Text(stringResource(R.string.password)) }, singleLine = true, shape = RoundedCornerShape(14.dp),
                visualTransformation = PasswordVisualTransformation())
        }
        GradientButton(stringResource(R.string.save_playlist), {
            onSave(if (xtream) iptv.xtream(name, server, user, pass)
                   else Playlist(name = name.ifBlank { url.substringAfter("://").substringBefore('/') }, url = url, epgUrl = epg,
                       username = m3uUser, password = m3uPass, userAgent = ua.trim()))
        }, Modifier.fillMaxWidth(), icon = Icons.Rounded.LiveTv,
            enabled = if (xtream) server.isNotBlank() && user.isNotBlank() && pass.isNotBlank() else url.startsWith("http"))
        Text(stringResource(R.string.jellyverse_plays_streams_you_already_have), color = Harbor.TextDim, fontSize = 11.sp)
    }
}

@Composable
private fun AddPlaylistSheet(onDismiss: () -> Unit, onSave: (Playlist) -> Unit) {
    ModalBottomSheet(onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Harbor.Surface) {
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(stringResource(R.string.add_iptv_playlist), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 12.dp))
            AddPlaylistForm(onSave)
        }
    }
}

/** "Channels updated 3 h ago · Refresh from source" – lets users fix dead streams themselves when the source rotates URLs. */
@Composable
fun IptvRefreshRow(vm: LiveTvViewModel, modifier: Modifier = Modifier) {
    val note = vm.refreshNote
    LaunchedEffect(note) { if (note != null) { delay(3500); vm.refreshNote = null } }
    Row(modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        val ago = vm.refreshedAt?.let { if (System.currentTimeMillis() - it < 60_000) com.sridhar.harbor.L10n.s(R.string.iptv_just_now) else android.text.format.DateUtils.getRelativeTimeSpanString(it, System.currentTimeMillis(), android.text.format.DateUtils.MINUTE_IN_MILLIS).toString() }
        Text(note ?: (ago?.let { stringResource(R.string.iptv_updated_ago, it) } ?: stringResource(R.string.iptv_never_updated)),
            color = if (note != null) Harbor.Mint else Harbor.TextDim, fontSize = 12.sp, maxLines = 1, modifier = Modifier.weight(1f))
        androidx.compose.material3.TextButton({ vm.refreshFromSource() }, enabled = !vm.refreshing) {
            if (vm.refreshing) { com.sridhar.harbor.ui.components.JellyLoader(Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.iptv_refreshing), fontSize = 13.sp) }
            else { Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.iptv_refresh_source), fontSize = 13.sp) }
        }
    }
}


/** Home-screen channel tile: logo on a soft card, LIVE badge, the channel name and what's on now. */
@Composable
fun ChannelCard(ch: Channel, nn: com.sridhar.harbor.data.iptv.NowNext, plays: Int = 0, onPlay: () -> Unit) {
    Column(Modifier.width(150.dp).pressable(onClick = onPlay)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 11f).clip(RoundedCornerShape(16.dp))
            .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Harbor.SurfaceHigh, Harbor.Surface)))) {
            NetImage(ch.logo, Modifier.fillMaxSize().padding(18.dp), contentScale = ContentScale.Fit, fallback = ch.name.take(3))
            com.sridhar.harbor.ui.components.LiveBadge(Modifier.align(Alignment.TopStart).padding(6.dp), small = true)
            if (plays > 1) Text("×$plays", Modifier.align(Alignment.TopEnd).padding(6.dp).clip(RoundedCornerShape(6.dp))
                .background(androidx.compose.ui.graphics.Color.Black.copy(.45f)).padding(horizontal = 5.dp, vertical = 1.dp),
                color = androidx.compose.ui.graphics.Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            nn.now?.let { GradientProgress(it.progress, Modifier.align(Alignment.BottomCenter).padding(horizontal = 10.dp, vertical = 6.dp), height = 3.dp) }
        }
        Text(ch.name, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
        Text(nn.now?.title ?: ch.group, color = Harbor.TextDim, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
