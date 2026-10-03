package com.sridhar.harbor.ui.music

import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sridhar.harbor.R
import com.sridhar.harbor.data.music.RadioStation
import com.sridhar.harbor.ui.components.LocalContainer
import com.sridhar.harbor.ui.components.friendly
import com.sridhar.harbor.ui.theme.Harbor
import kotlinx.coroutines.launch

/** Saved internet / FM stations: tap to play live, long-press to edit / record / schedule / remove, "+" (or a shared link) to add. */
@OptIn(ExperimentalFoundationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun RadioShelf(grid: Boolean = false) {
    val c = LocalContainer.current
    val stations by c.radio.stations.collectAsState()
    // No stations yet (first launch was offline): fetch the preset list now.
    val netOnline by com.sridhar.harbor.net.NetworkMonitor.online.collectAsState()
    androidx.compose.runtime.LaunchedEffect(stations.isEmpty(), netOnline) { if (stations.isEmpty() && netOnline) runCatching { c.radio.sync() } }
    val shared by c.sharedRadioLink.collectAsState()
    val playing by c.musicEngine.state.collectAsState()
    var adding by remember { mutableStateOf<String?>(null) }   // null = closed, else prefilled URL
    var removing by remember { mutableStateOf<RadioStation?>(null) }
    var acting by remember { mutableStateOf<RadioStation?>(null) }
    val showRecs by c.showRecordings.collectAsState()
    val recLive by com.sridhar.harbor.radio.RadioLibrary.live.collectAsState()
    val recCount by com.sridhar.harbor.radio.RadioLibrary.recordings.collectAsState()
    var discovering by remember { mutableStateOf(false) }
    LaunchedEffect(shared) { shared?.let { adding = it; c.sharedRadioLink.value = null } }

    val stationTile: @Composable (RadioStation, androidx.compose.ui.unit.Dp) -> Unit = { st, tileW ->
                val live = playing.current?.id == st.toSong().id
                val (a, b) = remember(st.id) { seedColors(st.name + "radio") }
                Column(Modifier.width(tileW).clip(RoundedCornerShape(14.dp))
                    .combinedClickable(onLongClick = { acting = st }) { c.musicEngine.play(listOf(st.toSong()), source = st.name) }) {
                    Box(Modifier.size(tileW).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(a, b)))
                        .then(if (live) Modifier.border(2.dp, Color.White, RoundedCornerShape(14.dp)) else Modifier), Alignment.Center) {
                        if (live) com.sridhar.harbor.ui.components.LiveBadge(Modifier.align(Alignment.TopStart).padding(6.dp), small = true)
                        if (live && playing.playing) EqualizerBars(true, Modifier.size(34.dp), Color.White)
                        else Icon(Icons.Rounded.Radio, null, tint = Color.White, modifier = Modifier.size(40.dp))
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(st.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text(if (live) stringResource(R.string.radio_live) else stringResource(R.string.radio_station), color = if (live) Harbor.Mint else Harbor.TextDim, fontSize = 12.sp)
                }
            }
    val recordingsTile: @Composable (androidx.compose.ui.unit.Dp) -> Unit = { tileW ->
                Column(Modifier.width(tileW).clip(RoundedCornerShape(14.dp)).combinedClickable { c.showRecordings.value = true }) {
                    Box(Modifier.size(tileW).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(Harbor.Rose.copy(alpha = .4f), Harbor.Violet.copy(alpha = .25f)))), Alignment.Center) {
                        if (recLive != null) RecPulse() else Icon(Icons.Rounded.FiberManualRecord, null, tint = Color.White, modifier = Modifier.size(36.dp))
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(R.string.rec_recordings), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1)
                    Text(if (recLive != null) stringResource(R.string.rec_live_short) else recCount.size.toString(), color = if (recLive != null) Harbor.Rose else Harbor.TextDim, fontSize = 12.sp)
                }
            }
    val discoverTile: @Composable (androidx.compose.ui.unit.Dp) -> Unit = { tileW ->
                Column(Modifier.width(tileW).clip(RoundedCornerShape(14.dp)).combinedClickable { discovering = true }) {
                    Box(Modifier.size(tileW).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(Harbor.Violet.copy(alpha = .35f), Harbor.Sky.copy(alpha = .2f)))), Alignment.Center) {
                        Text("🔎", fontSize = 34.sp)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(R.string.radio_discover), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 2)
                }
            }
    val addTile: @Composable (androidx.compose.ui.unit.Dp) -> Unit = { tileW ->
                Column(Modifier.width(tileW).clip(RoundedCornerShape(14.dp)).combinedClickable { adding = "" }) {
                    Box(Modifier.size(tileW).clip(RoundedCornerShape(14.dp)).border(1.5.dp, Harbor.VioletSoft.copy(alpha = .5f), RoundedCornerShape(14.dp)), Alignment.Center) {
                        Icon(Icons.Rounded.Add, null, tint = Harbor.VioletSoft, modifier = Modifier.size(36.dp))
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(R.string.radio_add), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Harbor.VioletSoft)
                }
            }

    if (grid) {
        // Full page (stand-alone Radio tab): stations as a grid, three across on phones.
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp)) {
            val across = (maxWidth / 132.dp).toInt().coerceAtLeast(3)
            val tileW = (maxWidth - 12.dp * (across - 1)) / across - 1.dp   // -1dp: rounding must never push the last tile to a new row
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                stations.forEach { st -> androidx.compose.runtime.key(st.id) { stationTile(st, tileW) } }
                recordingsTile(tileW); discoverTile(tileW); addTile(tileW)
            }
        }
    } else Column(Modifier.padding(top = 24.dp)) {
        Text(stringResource(R.string.radio_title), fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp))
        Text(stringResource(R.string.radio_hint), color = Harbor.TextDim, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp))
        Spacer(Modifier.height(10.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(stations, key = { it.id }) { st -> stationTile(st, 112.dp) }
            item(key = "recordings") { recordingsTile(112.dp) }
            item(key = "discover") { discoverTile(112.dp) }
            item(key = "add") { addTile(112.dp) }
        }
    }
    adding?.let { pre -> AddStationDialog(pre, onDismiss = { adding = null }) }
    if (discovering) DiscoverSheet(onDismiss = { discovering = false })
    acting?.let { st -> StationSheet(st, onRemove = { removing = st }, onRecordings = { c.showRecordings.value = true }, onDismiss = { acting = null }) }
    if (showRecs) RecordingsSheet(onDismiss = { c.showRecordings.value = false })
    removing?.let { st ->
        AlertDialog(onDismissRequest = { removing = null }, containerColor = Harbor.Surface,
            title = { Text(stringResource(R.string.radio_remove_q, st.name)) },
            confirmButton = { TextButton({ c.radio.remove(st); removing = null }) { Text(stringResource(R.string.remove), color = Harbor.Rose) } },
            dismissButton = { TextButton({ removing = null }) { Text(stringResource(R.string.cancel)) } })
    }
}

/** Pulsing red dot while a recording runs. */
@Composable
private fun RecPulse() {
    val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "rec")
    val a by t.animateFloat(1f, .35f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(700), androidx.compose.animation.core.RepeatMode.Reverse), label = "a")
    Icon(Icons.Rounded.FiberManualRecord, null, tint = Harbor.Rose.copy(alpha = a), modifier = Modifier.size(40.dp))
}

@Composable
private fun AddStationDialog(prefill: String, onDismiss: () -> Unit) {
    val c = LocalContainer.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf(prefill) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val valid = url.trim().startsWith("http://") || url.trim().startsWith("https://")
    AlertDialog(onDismissRequest = onDismiss, containerColor = Harbor.Surface,
        icon = { Icon(Icons.Rounded.Radio, null, tint = Harbor.Sky) },
        title = { Text(stringResource(R.string.radio_add_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.radio_name)) }, singleLine = true,
                    placeholder = { Text("Radio Mirchi 98.3") })
                OutlinedTextField(url, { url = it; error = null }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.radio_link)) },
                    placeholder = { Text("https://…/stream.mp3") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
                Text(stringResource(R.string.radio_link_help), color = Harbor.TextDim, fontSize = 12.sp)
                error?.let { Text(it, color = Harbor.Rose, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            TextButton({
                busy = true
                scope.launch(com.sridhar.harbor.CrashGuard) {
                    runCatching { c.radio.add(name, url) }
                        .onSuccess { st -> c.musicEngine.play(listOf(st.toSong()), source = st.name); onDismiss() }
                        .onFailure { error = it.friendly() }
                    busy = false
                }
            }, enabled = valid && !busy) { Text(stringResource(R.string.radio_save_play)) }
        },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.cancel)) } })
}

/** Live Tamil stations from Radio Browser: tap to listen, ＋ to keep it in your Radio row. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun DiscoverSheet(onDismiss: () -> Unit) {
    val c = LocalContainer.current
    val saved by c.radio.stations.collectAsState()
    val list by androidx.compose.runtime.produceState<List<RadioStation>?>(null) { value = runCatching { c.radio.discoverTamil() }.getOrDefault(emptyList()) }
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Harbor.Surface) {
        Text(stringResource(R.string.radio_discover_title), fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.padding(horizontal = 20.dp))
        Text(stringResource(R.string.radio_discover_hint), color = Harbor.TextDim, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
        val l = list
        when {
            l == null -> Box(Modifier.fillMaxWidth().padding(40.dp), Alignment.Center) { com.sridhar.harbor.ui.components.JellyLoader() }
            l.isEmpty() -> Text(stringResource(R.string.radio_discover_none), color = Harbor.TextDim, modifier = Modifier.padding(20.dp))
            else -> androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                items(l, key = { it.id }) { st ->
                    val kept = saved.any { it.url == st.url }
                    androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().combinedClickable { c.musicEngine.play(listOf(st.toSong()), source = st.name) }
                        .padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Radio, null, tint = Harbor.Sky)
                        Spacer(Modifier.width(14.dp))
                        Text(st.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        androidx.compose.material3.IconButton({ if (!kept) c.radio.save(st) }, enabled = !kept) {
                            Icon(if (kept) androidx.compose.material.icons.Icons.Rounded.Check else Icons.Rounded.Add, stringResource(R.string.radio_add),
                                tint = if (kept) Harbor.Mint else Harbor.Fg)
                        }
                    }
                }
            }
        }
    }
}

/**
 * The stand-alone Radio tab (no Navidrome needed – also the whole app in "Just use Radio" mode): your stations,
 * discover / add, and the latest recordings to play right here.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RadioHomeScreen() {
    val c = LocalContainer.current
    val recs by com.sridhar.harbor.radio.RadioLibrary.recordings.collectAsState()
    val scheds by com.sridhar.harbor.radio.RadioLibrary.schedules.collectAsState()
    val fmt = remember { java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT) }
    androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = 120.dp + com.sridhar.harbor.ui.components.LocalMiniPlayerInset.current)) {
        item {
            androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 20.dp, end = 20.dp, top = 20.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.tab_radio), fontSize = 30.sp, fontWeight = FontWeight.Black)
                    Text(stringResource(R.string.radio_home_hint), color = Harbor.TextDim, fontSize = 13.sp)
                }
                com.sridhar.harbor.ui.ai.AiOrb(44.dp)
            }
        }
        item { RadioShelf(grid = true) }
        if (recs.isNotEmpty()) {
            item {
                androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 28.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.rec_recordings), fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton({ c.showRecordings.value = true }) { Text(stringResource(R.string.see_all), color = Harbor.VioletSoft) }
                }
            }
            items(recs.take(5), key = { "r" + it.id }) { r ->
                androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().combinedClickable { c.musicEngine.play(listOf(r.toSong()), source = r.station) }
                    .padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    val (a, b) = remember(r.station) { seedColors(r.station + "radio") }
                    Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(Brush.linearGradient(listOf(a, b))), Alignment.Center) {
                        Icon(Icons.Rounded.FiberManualRecord, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(r.station, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${fmt.format(r.startedAt)} · ${clock(r.durationMs)}", color = Harbor.TextDim, fontSize = 12.sp)
                    }
                }
            }
        }
        if (scheds.isNotEmpty()) item {
            Text(stringResource(R.string.rec_upcoming_count, scheds.size), color = Harbor.TextDim, fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp).clip(RoundedCornerShape(8.dp)).combinedClickable { c.showRecordings.value = true })
        }
    }
}
