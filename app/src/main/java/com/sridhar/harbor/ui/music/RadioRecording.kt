package com.sridhar.harbor.ui.music

import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sridhar.harbor.R
import com.sridhar.harbor.data.music.RadioStation
import com.sridhar.harbor.radio.RadioLibrary
import com.sridhar.harbor.radio.RadioSchedule
import com.sridhar.harbor.radio.RadioScheduler
import com.sridhar.harbor.radio.RecordService
import com.sridhar.harbor.ui.components.LocalContainer
import com.sridhar.harbor.ui.components.friendly
import com.sridhar.harbor.ui.theme.Harbor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Calendar

private val DURATIONS = listOf(15, 30, 60, 120, 180)

private fun durLabel(min: Int) = if (min < 60) "${min}m" else "${min / 60}h"

internal fun clock(ms: Long): String {
    val s = ms / 1000
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

private enum class Mode { Menu, Edit, Record, Schedule, Remind }

/** Long-press on a station: edit it, record now, schedule a recording, set a listening reminder, or remove it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StationSheet(st: RadioStation, onRemove: () -> Unit, onRecordings: () -> Unit, onDismiss: () -> Unit) {
    var mode by remember { mutableStateOf(Mode.Menu) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Harbor.Surface) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 20.dp).navigationBarsPadding()) {
            Text(st.name, fontWeight = FontWeight.Bold, fontSize = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(st.url, color = Harbor.TextDim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(12.dp))
            when (mode) {
                Mode.Menu -> {
                    SheetAction(Icons.Rounded.FiberManualRecord, stringResource(R.string.rec_record_now), Harbor.Rose) { mode = Mode.Record }
                    SheetAction(Icons.Rounded.Schedule, stringResource(R.string.rec_schedule)) { mode = Mode.Schedule }
                    SheetAction(Icons.Rounded.Alarm, stringResource(R.string.rec_remind)) { mode = Mode.Remind }
                    SheetAction(Icons.Rounded.LibraryMusic, stringResource(R.string.rec_recordings)) { onDismiss(); onRecordings() }
                    SheetAction(Icons.Rounded.Edit, stringResource(R.string.radio_edit)) { mode = Mode.Edit }
                    SheetAction(Icons.Rounded.Delete, stringResource(R.string.remove), Harbor.Rose) { onDismiss(); onRemove() }
                }
                Mode.Edit -> EditStation(st, onDismiss)
                Mode.Record -> RecordNow(st) { onDismiss(); onRecordings() }
                Mode.Schedule -> ScheduleForm(st, record = true, onDone = onDismiss)
                Mode.Remind -> ScheduleForm(st, record = false, onDone = onDismiss)
            }
        }
    }
}

@Composable
private fun SheetAction(icon: ImageVector, label: String, tint: Color = Harbor.Fg, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 14.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = tint)
        Spacer(Modifier.width(16.dp))
        Text(label, color = tint, fontSize = 16.sp)
    }
}

@Composable
private fun EditStation(st: RadioStation, onDone: () -> Unit) {
    val c = LocalContainer.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(st.name) }
    var url by remember { mutableStateOf(st.url) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val valid = url.trim().startsWith("http://") || url.trim().startsWith("https://")
    OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.radio_name)) }, singleLine = true)
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(url, { url = it; error = null }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.radio_link)) },
        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
    error?.let { Text(it, color = Harbor.Rose, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp)) }
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
        TextButton(onDone) { Text(stringResource(R.string.cancel)) }
        TextButton({
            busy = true
            scope.launch(com.sridhar.harbor.CrashGuard) {
                runCatching { c.radio.update(st.id, name, url) }.onSuccess { onDone() }.onFailure { error = it.friendly() }
                busy = false
            }
        }, enabled = valid && !busy && name.isNotBlank()) { Text(stringResource(R.string.save)) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DurationChips(selected: Int, onPick: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DURATIONS.forEach { m -> FilterChip(selected == m, { onPick(m) }, label = { Text(durLabel(m)) }) }
    }
}

@Composable
private fun RecordNow(st: RadioStation, onStarted: () -> Unit) {
    val ctx = LocalContext.current
    var min by remember { mutableIntStateOf(60) }
    Text(stringResource(R.string.rec_how_long), fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(8.dp))
    DurationChips(min) { min = it }
    Text(stringResource(R.string.rec_cool_hint), color = Harbor.TextDim, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
        TextButton({ RecordService.start(ctx, st.name, st.url, min); onStarted() }) {
            Icon(Icons.Rounded.FiberManualRecord, null, tint = Harbor.Rose, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.rec_start))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleForm(st: RadioStation, record: Boolean, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val now = remember { Calendar.getInstance().apply { add(Calendar.MINUTE, 5) } }
    val time = rememberTimePickerState(now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), android.text.format.DateFormat.is24HourFormat(ctx))
    var min by remember { mutableIntStateOf(60) }
    var daily by remember { mutableStateOf(false) }
    Text(stringResource(if (record) R.string.rec_schedule else R.string.rec_remind), fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(8.dp))
    Box(Modifier.fillMaxWidth(), Alignment.Center) { TimeInput(time) }
    if (record) {
        Text(stringResource(R.string.rec_how_long), fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        DurationChips(min) { min = it }
    }
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.rec_daily), Modifier.weight(1f))
        Switch(daily, { daily = it })
    }
    if (!RadioScheduler.canExact(ctx) && Build.VERSION.SDK_INT >= 31) {
        Text(stringResource(R.string.rec_exact_hint), color = Harbor.Amber, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        TextButton({
            runCatching { ctx.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, android.net.Uri.parse("package:" + ctx.packageName))) }
        }) { Text(stringResource(R.string.rec_exact_allow)) }
    }
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
        TextButton(onDone) { Text(stringResource(R.string.cancel)) }
        TextButton({
            val at = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, time.hour); set(Calendar.MINUTE, time.minute); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
            }.timeInMillis
            RadioScheduler.add(ctx, RadioSchedule(stationId = st.id, stationName = st.name, url = st.url, startAt = at, durationMin = min,
                kind = if (record) "record" else "remind", daily = daily))
            Toast.makeText(ctx, ctx.getString(R.string.rec_scheduled_at, DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(at)), Toast.LENGTH_SHORT).show()
            onDone()
        }) { Text(stringResource(R.string.save)) }
    }
}

/** Your radio recordings (play, save to Downloads, delete), what's recording now, and upcoming schedules. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingsSheet(onDismiss: () -> Unit) {
    val c = LocalContainer.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val recs by RadioLibrary.recordings.collectAsState()
    val scheds by RadioLibrary.schedules.collectAsState()
    val live by RadioLibrary.live.collectAsState()
    val fmt = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Harbor.Surface) {
        LazyColumn(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            item {
                Text(stringResource(R.string.rec_recordings), fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.padding(horizontal = 20.dp))
                Text(stringResource(R.string.rec_recordings_hint), color = Harbor.TextDim, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
            }
            live?.let { l ->
                item(key = "live") {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp))
                        .background(Harbor.Rose.copy(alpha = .12f)).padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.FiberManualRecord, null, tint = Harbor.Rose, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.rec_now, l.station), Modifier.weight(1f), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            IconButton({ RecordService.stop(ctx) }) { Icon(Icons.Rounded.Stop, stringResource(R.string.rec_stop), tint = Harbor.Rose) }
                        }
                        val el = System.currentTimeMillis() - l.startedAt
                        LinearProgressIndicator({ (el.toFloat() / l.durationMs).coerceIn(0f, 1f) }, Modifier.fillMaxWidth(), color = Harbor.Rose)
                        Text("${clock(el)} / ${clock(l.durationMs)} · ${"%.1f".format(l.bytes / 1e6)} MB", color = Harbor.TextDim, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
            if (recs.isEmpty() && live == null) item { Text(stringResource(R.string.rec_none), color = Harbor.TextDim, modifier = Modifier.padding(20.dp)) }
            items(recs, key = { it.id }) { r ->
                Row(Modifier.fillMaxWidth().clickable { c.musicEngine.play(listOf(r.toSong()), source = r.station) }.padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.PlayArrow, null, tint = Harbor.Sky)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(r.station, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${fmt.format(r.startedAt)} · ${clock(r.durationMs)} · ${"%.1f".format(r.bytes / 1e6)} MB", color = Harbor.TextDim, fontSize = 12.sp)
                    }
                    IconButton({
                        scope.launch {
                            val msg = withContext(Dispatchers.IO) { runCatching { RadioLibrary.exportToDownloads(r) } }
                                .fold({ ctx.getString(R.string.rec_saved_to, it) }, { it.friendly() })
                            Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
                        }
                    }) { Icon(Icons.Rounded.Download, stringResource(R.string.rec_save_downloads)) }
                    IconButton({ RadioLibrary.deleteRecording(r) }) { Icon(Icons.Rounded.DeleteOutline, stringResource(R.string.remove), tint = Harbor.TextDim) }
                }
            }
            if (scheds.isNotEmpty()) {
                item { Text(stringResource(R.string.rec_upcoming), fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp)) }
                items(scheds.sortedBy { it.startAt }, key = { "s" + it.id }) { s ->
                    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (s.kind == "record") Icons.Rounded.Schedule else Icons.Rounded.Alarm, null, tint = if (s.kind == "record") Harbor.Rose else Harbor.Sky)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(s.stationName, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val what = if (s.kind == "record") stringResource(R.string.rec_kind_record, durLabel(s.durationMin)) else stringResource(R.string.rec_kind_remind)
                            Text(fmt.format(s.startAt) + " · " + what + if (s.daily) " · " + stringResource(R.string.rec_daily_short) else "",
                                color = Harbor.TextDim, fontSize = 12.sp)
                        }
                        IconButton({ RadioScheduler.cancel(ctx, s.id) }) { Icon(Icons.Rounded.DeleteOutline, stringResource(R.string.cancel), tint = Harbor.TextDim) }
                    }
                }
            }
        }
    }
}
