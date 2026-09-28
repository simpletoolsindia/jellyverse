package com.sridhar.harbor.ui.remote

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sridhar.harbor.R
import com.sridhar.harbor.remote.RemoteClient
import com.sridhar.harbor.remote.RemoteState
import com.sridhar.harbor.remote.TvDevice
import com.sridhar.harbor.ui.components.JellyLoader
import com.sridhar.harbor.ui.components.LocalContainer
import com.sridhar.harbor.ui.components.PinDialog
import com.sridhar.harbor.ui.theme.Harbor
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Phone → TV remote over Wi-Fi: pick a JellyVerse TV, pair once with its code, then drive it. */
@Composable
fun RemoteScreen(onBack: () -> Unit) {
    val client = LocalContainer.current.remote
    val state by client.state.collectAsState()
    val tvs by client.tvs.collectAsState()
    DisposableEffect(Unit) { client.startDiscovery(); onDispose { client.stopDiscovery() } }
    // mDNS often doesn't cross routers / phone Wi-Fi isolation – after 3 s with nothing found, scan the subnet too.
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(3000); if (client.tvs.value.isEmpty()) client.scanSubnet() }
    // Reconnect to the last TV automatically.
    LaunchedEffect(Unit) { if (state is RemoteState.Idle) client.lastTv()?.let { client.connect(it) } }

    Column(Modifier.fillMaxSize().background(Harbor.Ink).statusBarsPadding().navigationBarsPadding().imePadding()) {
        val setup by client.tvSetup.collectAsState()
        setup?.let { st ->
            val (text, color) = when (st) {
                RemoteClient.TvSetup.Working -> stringResource(R.string.qr_setting_up) to Harbor.Sky
                RemoteClient.TvSetup.Done -> "✓ " + stringResource(R.string.qr_tv_ready) to Harbor.Mint
                RemoteClient.TvSetup.Failed -> stringResource(R.string.remote_failed, "TV") to Harbor.Rose
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clip(RoundedCornerShape(14.dp)).background(color.copy(alpha = .15f)).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically) {
                if (st == RemoteClient.TvSetup.Working) { JellyLoader(Modifier.size(28.dp)); Spacer(Modifier.width(10.dp)) }
                Text(text, color = color, fontWeight = FontWeight.SemiBold)
            }
        }
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) }
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.remote_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                val sub = when (val s = state) {
                    is RemoteState.Connected -> "● " + s.tv.name
                    is RemoteState.Connecting -> stringResource(R.string.remote_connecting, s.tv.name)
                    else -> stringResource(R.string.remote_same_wifi)
                }
                Text(sub, color = if (state is RemoteState.Connected) Harbor.Mint else Harbor.TextDim, fontSize = 13.sp, maxLines = 1)
            }
            if (state is RemoteState.Connected) TextButton({ client.disconnect() }) { Text(stringResource(R.string.remote_change_tv), color = Harbor.VioletSoft) }
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 480.dp).fillMaxSize()) {
                when (val s = state) {
                    is RemoteState.Connected -> RemotePad(client)
                    is RemoteState.Connecting -> Box(Modifier.fillMaxSize(), Alignment.Center) { JellyLoader(Modifier.size(72.dp)) }
                    else -> TvPicker(tvs, (s as? RemoteState.Failed)?.let { stringResource(R.string.remote_failed, it.tv.name) }) { client.connect(it) }
                }
            }
        }
    }
    (state as? RemoteState.NeedCode)?.let { s ->
        val wrong = stringResource(R.string.pin_wrong)
        androidx.compose.runtime.key(s.wrong) {
            PinDialog(stringResource(R.string.remote_enter_code), stringResource(R.string.remote_enter_code_hint, s.tv.name), onDismiss = { client.disconnect() }) {
                client.submitCode(it); null
            }
        }
        if (s.wrong) LaunchedEffect(s) { android.widget.Toast.makeText(com.sridhar.harbor.HarborApp.instance, wrong, android.widget.Toast.LENGTH_SHORT).show() }
    }
}

@Composable
private fun TvPicker(tvs: List<TvDevice>, error: String?, onPick: (TvDevice) -> Unit) {
    var manual by remember { mutableStateOf(false) }
    var ip by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        error?.let { Text(it, color = Harbor.Rose) }
        ScanQrButton()
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.remote_pick_tv), fontWeight = FontWeight.Bold, fontSize = 18.sp)
        if (tvs.isEmpty()) Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Harbor.Surface).padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            JellyLoader(Modifier.size(44.dp)); Spacer(Modifier.width(14.dp))
            Text(stringResource(R.string.remote_searching), color = Harbor.TextDim)
        }
        tvs.forEach { tv ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Harbor.Surface).clickable { onPick(tv) }.padding(18.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(Harbor.Violet.copy(alpha = .2f)), Alignment.Center) { Icon(Icons.Rounded.Tv, null, tint = Harbor.Sky) }
                Spacer(Modifier.width(14.dp))
                Column { Text(tv.name, fontWeight = FontWeight.SemiBold); Text(tv.host, color = Harbor.TextDim, fontSize = 12.sp) }
            }
        }
        TextButton({ manual = !manual }) { Text(stringResource(R.string.remote_manual), color = Harbor.VioletSoft) }
        if (manual) Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(ip, { ip = it.trim() }, Modifier.weight(1f), singleLine = true, label = { Text(stringResource(R.string.remote_tv_ip)) },
                placeholder = { Text("192.168.1.50") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
            TextButton({ val (h, p) = ip.split(':').let { it[0] to (it.getOrNull(1)?.toIntOrNull() ?: com.sridhar.harbor.remote.RemoteProtocol.PORT) }; onPick(TvDevice(h, h, p)) },
                enabled = ip.isNotBlank()) { Text(stringResource(R.string.remote_connect)) }
        }
        Text(stringResource(R.string.remote_help), color = Harbor.TextDim, fontSize = 13.sp)
    }
}

@Composable
private fun RemotePad(client: RemoteClient) {
    val haptics = LocalHapticFeedback.current
    val tap: (String) -> Unit = { k -> haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); client.key(k) }
    val field by client.tvField.collectAsState()
    var keyboard by remember { mutableStateOf(false) }
    // TV focused a text field → keyboard pops up here.
    LaunchedEffect(field) { if (field != null) keyboard = true }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        if (keyboard) KeyboardBar(client, field, onClose = { keyboard = false })
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            RoundKey(Icons.AutoMirrored.Rounded.Undo, stringResource(R.string.back)) { tap("BACK") }
            RoundKey(Icons.Rounded.Home, stringResource(R.string.remote_home)) { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); client.home() }
            RoundKey(Icons.Rounded.Menu, stringResource(R.string.remote_menu)) { tap("MENU") }
            RoundKey(Icons.Rounded.Keyboard, stringResource(R.string.remote_keyboard), active = keyboard) { keyboard = !keyboard }
        }
        DPad(client)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            RoundKey(Icons.Rounded.FastRewind, stringResource(R.string.remote_rewind)) { tap("REWIND") }
            RoundKey(Icons.Rounded.PlayArrow, stringResource(R.string.play), big = true) { tap("PLAY_PAUSE") }
            RoundKey(Icons.Rounded.FastForward, stringResource(R.string.remote_ff)) { tap("FAST_FORWARD") }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            Rocker(stringResource(R.string.remote_vol), onUp = { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); client.volume("up") },
                onDown = { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); client.volume("down") })
            RoundKey(Icons.AutoMirrored.Rounded.VolumeOff, stringResource(R.string.remote_mute)) { client.volume("mute") }
            Rocker(stringResource(R.string.remote_ch), onUp = { tap("CH_UP") }, onDown = { tap("CH_DOWN") })
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** Round pad: tap an edge for arrows (hold to repeat), tap the centre for OK, or swipe anywhere like a touchpad. */
@Composable
private fun DPad(client: RemoteClient) {
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var lit by remember { mutableStateOf<String?>(null) }
    Box(
        Modifier.fillMaxWidth(0.82f).aspectRatio(1f).clip(CircleShape)
            .background(Brush.radialGradient(listOf(Harbor.SurfaceHigh, Harbor.Surface)))
            .border(1.dp, Color.White.copy(alpha = .08f), CircleShape)
            .semantics { contentDescription = "D-pad" }
            .pointerInput(Unit) {
                var repeatJob: Job? = null
                detectTapGestures(
                    onPress = { o ->
                        val c = size.width / 2f
                        val dx = o.x - c; val dy = o.y - c
                        val dir = if (kotlin.math.hypot(dx, dy) < c * 0.34f) "OK" else if (abs(dx) > abs(dy)) (if (dx > 0) "RIGHT" else "LEFT") else (if (dy > 0) "DOWN" else "UP")
                        lit = dir
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        client.key(dir)
                        // Hold an arrow to keep scrolling, like a real remote.
                        if (dir != "OK") repeatJob = scope.launch { delay(450); while (true) { client.key(dir); delay(110) } }
                        tryAwaitRelease()
                        repeatJob?.cancel(); lit = null
                    },
                )
            }
            .pointerInput(Unit) {
                // Swipe = touchpad: one arrow per ~48 px of travel.
                var accX = 0f; var accY = 0f
                detectDragGestures(onDragStart = { accX = 0f; accY = 0f }) { ch, d ->
                    ch.consume(); accX += d.x; accY += d.y
                    val step = 48.dp.toPx()
                    while (abs(accX) >= step) { client.key(if (accX > 0) "RIGHT" else "LEFT"); accX -= if (accX > 0) step else -step; haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
                    while (abs(accY) >= step) { client.key(if (accY > 0) "DOWN" else "UP"); accY -= if (accY > 0) step else -step; haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        @Composable fun Arrow(icon: ImageVector, dir: String, align: Alignment) = Box(Modifier.fillMaxSize().padding(14.dp), align) {
            Icon(icon, dir, tint = if (lit == dir) Harbor.Sky else Color.White.copy(alpha = .75f), modifier = Modifier.size(40.dp))
        }
        Arrow(Icons.Rounded.KeyboardArrowUp, "UP", Alignment.TopCenter)
        Arrow(Icons.Rounded.KeyboardArrowDown, "DOWN", Alignment.BottomCenter)
        Arrow(Icons.Rounded.KeyboardArrowLeft, "LEFT", Alignment.CenterStart)
        Arrow(Icons.Rounded.KeyboardArrowRight, "RIGHT", Alignment.CenterEnd)
        val press by animateFloatAsState(if (lit == "OK") 0.92f else 1f, spring(dampingRatio = .5f), label = "ok")
        Box(Modifier.fillMaxSize(0.36f).graphicsLayer { scaleX = press; scaleY = press }.clip(CircleShape)
            .background(Brush.linearGradient(listOf(Harbor.Violet, Harbor.Coral))), Alignment.Center) {
            Text("OK", color = Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp)
        }
    }
}

@Composable
private fun RoundKey(icon: ImageVector, desc: String, big: Boolean = false, active: Boolean = false, onClick: () -> Unit) {
    Box(Modifier.size(if (big) 72.dp else 56.dp).clip(CircleShape).background(if (big || active) Harbor.Violet else Harbor.Surface)
        .clickable(onClick = onClick), Alignment.Center) {
        Icon(icon, desc, tint = Color.White, modifier = Modifier.size(if (big) 34.dp else 26.dp))
    }
}

@Composable
private fun Rocker(label: String, onUp: () -> Unit, onDown: () -> Unit) {
    Column(Modifier.width(64.dp).clip(RoundedCornerShape(32.dp)).background(Harbor.Surface), horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onUp) { Icon(Icons.Rounded.Add, "$label +", tint = Color.White) }
        Text(label, color = Harbor.TextDim, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        IconButton(onDown) { Icon(Icons.Rounded.Remove, "$label −", tint = Color.White) }
    }
}

/** Type on the phone, and the TV field fills in live; Enter moves on like the TV keyboard's "Next". */
@Composable
private fun KeyboardBar(client: RemoteClient, field: String?, onClose: () -> Unit) {
    var text by remember(field) { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(field) { runCatching { focus.requestFocus() } }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Harbor.Surface).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (field.isNullOrBlank()) stringResource(R.string.remote_type_on_tv) else stringResource(R.string.remote_typing_into, field),
                color = Harbor.Sky, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.weight(1f))
            TextButton(onClose) { Text(stringResource(R.string.close), color = Harbor.TextDim) }
        }
        OutlinedTextField(text, { new -> client.mirror(text, new); text = new }, Modifier.fillMaxWidth().focusRequester(focus), singleLine = true,
            placeholder = { Text(stringResource(R.string.remote_type_hint)) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next, autoCorrectEnabled = false),
            keyboardActions = KeyboardActions(onNext = { client.key("DOWN"); text = "" }, onDone = { client.key("OK") }))
    }
}

/** Opens Google's code scanner (no camera permission needed) and connects to the scanned TV. */
@Composable
fun ScanQrButton(modifier: Modifier = Modifier) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val client = LocalContainer.current.remote
    val bad = stringResource(R.string.qr_bad)
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Brush.linearGradient(listOf(Harbor.Violet, Harbor.Coral)))
        .clickable {
            val opts = com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions.Builder()
                .setBarcodeFormats(com.google.mlkit.vision.barcode.common.Barcode.FORMAT_QR_CODE).build()
            com.google.mlkit.vision.codescanner.GmsBarcodeScanning.getClient(ctx, opts).startScan()
                .addOnSuccessListener { code -> if (!client.connectFromQr(code.rawValue.orEmpty())) android.widget.Toast.makeText(ctx, bad, android.widget.Toast.LENGTH_SHORT).show() }
        }.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.QrCodeScanner, null, tint = Color.White, modifier = Modifier.size(30.dp))
        Spacer(Modifier.width(14.dp))
        Column {
            Text(stringResource(R.string.qr_scan), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text(stringResource(R.string.qr_hint), color = Color.White.copy(alpha = .85f), fontSize = 12.sp)
        }
    }
}
