package com.sridhar.harbor.ui.music

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Radio
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

/** Saved internet / FM stations: tap to play live, long-press to remove, "+" (or a shared link) to add. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RadioShelf() {
    val c = LocalContainer.current
    val stations by c.radio.stations.collectAsState()
    val shared by c.sharedRadioLink.collectAsState()
    val playing by c.musicEngine.state.collectAsState()
    var adding by remember { mutableStateOf<String?>(null) }   // null = closed, else prefilled URL
    var removing by remember { mutableStateOf<RadioStation?>(null) }
    LaunchedEffect(shared) { shared?.let { adding = it; c.sharedRadioLink.value = null } }

    Column(Modifier.padding(top = 24.dp)) {
        Text(stringResource(R.string.radio_title), fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp))
        Text(stringResource(R.string.radio_hint), color = Harbor.TextDim, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp))
        Spacer(Modifier.height(10.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(stations, key = { it.id }) { st ->
                val live = playing.current?.id == st.toSong().id
                val (a, b) = remember(st.id) { seedColors(st.name + "radio") }
                Column(Modifier.width(112.dp).clip(RoundedCornerShape(14.dp))
                    .combinedClickable(onLongClick = { removing = st }) { c.musicEngine.play(listOf(st.toSong()), source = st.name) }) {
                    Box(Modifier.size(112.dp).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(a, b)))
                        .then(if (live) Modifier.border(2.dp, Color.White, RoundedCornerShape(14.dp)) else Modifier), Alignment.Center) {
                        if (live && playing.playing) EqualizerBars(true, Modifier.size(34.dp), Color.White)
                        else Icon(Icons.Rounded.Radio, null, tint = Color.White, modifier = Modifier.size(40.dp))
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(st.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text(if (live) stringResource(R.string.radio_live) else stringResource(R.string.radio_station), color = if (live) Harbor.Mint else Harbor.TextDim, fontSize = 12.sp)
                }
            }
            item(key = "add") {
                Column(Modifier.width(112.dp).clip(RoundedCornerShape(14.dp)).combinedClickable { adding = "" }) {
                    Box(Modifier.size(112.dp).clip(RoundedCornerShape(14.dp)).border(1.5.dp, Harbor.VioletSoft.copy(alpha = .5f), RoundedCornerShape(14.dp)), Alignment.Center) {
                        Icon(Icons.Rounded.Add, null, tint = Harbor.VioletSoft, modifier = Modifier.size(36.dp))
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(R.string.radio_add), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Harbor.VioletSoft)
                }
            }
        }
    }
    adding?.let { pre -> AddStationDialog(pre, onDismiss = { adding = null }) }
    removing?.let { st ->
        AlertDialog(onDismissRequest = { removing = null }, containerColor = Harbor.Surface,
            title = { Text(stringResource(R.string.radio_remove_q, st.name)) },
            confirmButton = { TextButton({ c.radio.remove(st); removing = null }) { Text(stringResource(R.string.remove), color = Harbor.Rose) } },
            dismissButton = { TextButton({ removing = null }) { Text(stringResource(R.string.cancel)) } })
    }
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
