package com.sridhar.harbor.tv

import androidx.compose.foundation.layout.widthIn
import com.sridhar.harbor.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.SurroundSound
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sridhar.harbor.data.ServerConfig
import com.sridhar.harbor.ui.components.LocalContainer
import com.sridhar.harbor.ui.components.rememberConfig
import com.sridhar.harbor.ui.setup.ConnState
import com.sridhar.harbor.ui.setup.SetupViewModel
import com.sridhar.harbor.ui.theme.Harbor
import kotlinx.coroutines.launch

@Composable
fun TvSetup() {
    val container = LocalContainer.current
    val vm = viewModel { SetupViewModel(container) }
    Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Harbor.Violet.copy(.10f), Harbor.Ink), radius = 1400f)), contentAlignment = Alignment.Center) {
        Row(horizontalArrangement = Arrangement.spacedBy(56.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(360.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    com.sridhar.harbor.ui.components.HarborLogo(64.dp)
                    Spacer(Modifier.width(16.dp))
                    Text(stringResource(R.string.jellyverse_tv), color = Harbor.Fg, fontSize = 34.sp, fontWeight = FontWeight.Black)
                }
                Text(stringResource(R.string.your_jellyfin_library_beautifully_on_the), color = Harbor.TextDim, fontSize = 16.sp, modifier = Modifier.padding(top = 8.dp))
                Spacer(Modifier.height(22.dp))
                PhoneQrCard(big = false)
            }
            var usePassword by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
            // The phone that scanned the QR sends its Jellyfin server; Quick Connect starts and the phone approves it.
            val offered by com.sridhar.harbor.remote.RemoteServer.setupServer.collectAsState()
            androidx.compose.runtime.LaunchedEffect(offered) { offered?.let { vm.jfUrl = it; usePassword = false } }
            Column(Modifier.width(460.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                TvField(stringResource(R.string.jellyfin_server), vm.jfUrl, KeyboardType.Uri) { vm.jfUrl = it }
                if (!usePassword) {
                    Text(stringResource(R.string.quick_connect), color = Harbor.Fg, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    com.sridhar.harbor.ui.quickconnect.QuickConnectPanel(vm.jfUrl, big = false)
                    TvButton(stringResource(R.string.use_password_instead), Icons.AutoMirrored.Rounded.Login) { usePassword = true }
                } else {
                    TvField(stringResource(R.string.username), vm.jfUser) { vm.jfUser = it }
                    TvField(stringResource(R.string.password), vm.jfPass, KeyboardType.Password, secret = true) { vm.jfPass = it }
                    (vm.jfState as? ConnState.Failed)?.let { Text(it.error, color = Harbor.Rose) }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TvButton(if (vm.jfState is ConnState.Working) stringResource(R.string.signing_in) else stringResource(R.string.sign_in), Icons.AutoMirrored.Rounded.Login, primary = true) { vm.connectJellyfin() }
                        TvButton(stringResource(R.string.quick_connect), null) { usePassword = false }
                    }
                }
            }
        }
    }
}

@Composable
private fun TvField(label: String, value: String, type: KeyboardType = KeyboardType.Text, secret: Boolean = false, onChange: (String) -> Unit) {
    val fm = androidx.compose.ui.platform.LocalFocusManager.current
    val kb = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    OutlinedTextField(value, onChange, Modifier.width(440.dp)
        .onFocusChanged {
            com.sridhar.harbor.remote.RemoteServer.fieldFocused(it.isFocused, label)
            // Typing from a paired phone – keep the TV's own keyboard out of the way.
            if (it.isFocused && com.sridhar.harbor.remote.RemoteServer.connected.value > 0) kb?.hide()
        }.onPreviewKeyEvent { e ->
            // TV remotes: ▲/▼ leave the field instead of moving the caret.
            if (e.type != androidx.compose.ui.input.key.KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (e.key) {
                androidx.compose.ui.input.key.Key.DirectionDown -> fm.moveFocus(androidx.compose.ui.focus.FocusDirection.Down)
                androidx.compose.ui.input.key.Key.DirectionUp -> fm.moveFocus(androidx.compose.ui.focus.FocusDirection.Up)
                else -> false
            }
        }, label = { Text(label) }, singleLine = true, shape = RoundedCornerShape(14.dp),
        keyboardOptions = KeyboardOptions(keyboardType = type, imeAction = androidx.compose.ui.text.input.ImeAction.Next, showKeyboardOnFocus = false),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onNext = { fm.moveFocus(androidx.compose.ui.focus.FocusDirection.Down) }),
        visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Harbor.Fg, unfocusedContainerColor = Harbor.Surface, focusedContainerColor = Harbor.SurfaceHigh))
}

@Composable
fun TvSettings() {
    val container = LocalContainer.current
    val cfg = rememberConfig()
    val scope = rememberCoroutineScope()
    // Scrolls with D-pad focus so every option below the fold stays reachable.
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 120.dp, top = 48.dp, end = 64.dp, bottom = 64.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.settings), color = Harbor.Fg, fontSize = 40.sp, fontWeight = FontWeight.Black)
        Column(Modifier.clip(RoundedCornerShape(20.dp)).background(Harbor.Surface).padding(24.dp)) {
            Text(stringResource(R.string.jellyfin), color = Harbor.TextDim, fontSize = 14.sp)
            Text(cfg.jellyfinUrl, color = Harbor.Fg, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.signed_in_as_1_s, cfg.jellyfinUser), color = Harbor.Mint, fontSize = 15.sp)
        }
        Text(stringResource(R.string.player_options_subtitles_audio_quality_speed),
            color = Harbor.TextDim, fontSize = 15.sp, modifier = Modifier.width(760.dp))
        TvButton(stringResource(R.string.sign_out_2), Icons.AutoMirrored.Rounded.Logout) {
            scope.launch(com.sridhar.harbor.CrashGuard) { container.settings.update { ServerConfig(deviceId = it.deviceId) } }
        }
        var parental by remember { mutableStateOf(false) }
        TvButton(stringResource(R.string.parental_title) + " · " + com.sridhar.harbor.ui.parental.parentalSummary(), Icons.Rounded.Lock) { parental = true }
        if (parental) com.sridhar.harbor.ui.parental.ParentalSettingsDialog { parental = false }
        com.sridhar.harbor.ui.components.AppearancePicker(Modifier.padding(vertical = 8.dp).widthIn(max = 720.dp), tv = true)
        // Playback: previews compete with the film for the decoder on budget boxes; passthrough's clock stutters on many.
        val previewMode by container.previewMode.collectAsState()
        TvButton(stringResource(R.string.previews_title) + " · " + when (previewMode) {
            "on" -> stringResource(R.string.on_label); "off" -> stringResource(R.string.off_label)
            else -> stringResource(if (container.previewsOn("auto")) R.string.previews_auto_on else R.string.previews_auto_off)
        }, Icons.Rounded.Movie) { container.setPreviewMode(when (previewMode) { "auto" -> "on"; "on" -> "off"; else -> "auto" }) }
        val ctxP = androidx.compose.ui.platform.LocalContext.current
        val playerPrefs = androidx.compose.runtime.remember { com.sridhar.harbor.ui.player.PlayerPrefs(ctxP) }
        var passthrough by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(playerPrefs.passthrough) }
        TvButton(stringResource(R.string.passthrough_title) + " · " + stringResource(if (passthrough) R.string.passthrough_on else R.string.passthrough_off),
            Icons.Rounded.SurroundSound) { passthrough = !passthrough; playerPrefs.passthrough = passthrough }
        if (container.updater.enabled) {
            val autoUpd by container.updater.autoCheckFlow.collectAsState()
            val ctxU = androidx.compose.ui.platform.LocalContext.current
            TvButton(stringResource(R.string.update_auto) + " · " + stringResource(if (autoUpd) R.string.on_label else R.string.off_label), Icons.Rounded.SystemUpdate) {
                container.updater.autoCheck = !autoUpd; com.sridhar.harbor.update.UpdateWorker.schedule(ctxU)
            }
        }
        com.sridhar.harbor.update.updateStatus()?.let { status ->
            TvButton(stringResource(R.string.update_check) + " · " + status, Icons.Rounded.SystemUpdate) {
                scope.launch(com.sridhar.harbor.CrashGuard) { container.updater.check(userInitiated = true) }
            }
        }
        var pairing by remember { mutableStateOf(false) }
        val phones by com.sridhar.harbor.remote.RemoteServer.connected.collectAsState()
        TvButton("📱  " + stringResource(R.string.remote_pair_phone) + if (phones > 0) " · " + stringResource(R.string.remote_phones_connected, phones) else "", null) { pairing = true }
        if (pairing) PairPhoneDialog { pairing = false }
        com.sridhar.harbor.ui.components.LanguagePicker()
        Text(stringResource(R.string.jellyverse_tv_1_s, com.sridhar.harbor.BuildConfig.VERSION_NAME), color = Harbor.TextDim, fontSize = 13.sp)
        com.sridhar.harbor.ui.components.MadeWithLove()
    }
}

/** Step-by-step card for pairing a phone as remote & keyboard; shows this TV's address for manual connect. */
@Composable
private fun PairPhoneDialog(onDismiss: () -> Unit) {
    val ip = remember { com.sridhar.harbor.remote.RemoteServer.localIp() }
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    androidx.compose.runtime.LaunchedEffect(Unit) { runCatching { kotlinx.coroutines.delay(100); focus.requestFocus() } }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.clip(RoundedCornerShape(28.dp)).background(Harbor.Surface).padding(36.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("📱 " + stringResource(R.string.remote_pair_phone), color = Harbor.Fg, fontSize = 28.sp, fontWeight = FontWeight.Black)
            PhoneQrCard(big = true)
            Text(stringResource(R.string.qr_or_manual), color = Harbor.TextDim, fontSize = 15.sp)
            listOf(R.string.remote_step1, R.string.remote_step2, R.string.remote_step3).forEachIndexed { i, r ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(34.dp).clip(RoundedCornerShape(50)).background(Harbor.Violet), Alignment.Center) { Text("${i + 1}", color = Color.White, fontWeight = FontWeight.Bold) }
                    Spacer(Modifier.width(14.dp))
                    Text(stringResource(r), color = Harbor.Fg.copy(alpha = .9f), fontSize = 18.sp)
                }
            }
            if (ip != null) Column(Modifier.clip(RoundedCornerShape(16.dp)).background(Harbor.Ink).padding(18.dp)) {
                Text(stringResource(R.string.remote_not_listed), color = Harbor.TextDim, fontSize = 14.sp)
                Text(ip, color = Harbor.Sky, fontSize = 34.sp, fontWeight = FontWeight.Black)
            }
            TvButton(stringResource(R.string.done), null, primary = true, modifier = Modifier.focusRequester(focus)) { onDismiss() }
        }
    }
}

/** "Scan with your phone" card: QR of this TV's address + a one-time key; refreshes every 9 minutes. */
@Composable
fun PhoneQrCard(big: Boolean) {
    var payload by remember { mutableStateOf(com.sridhar.harbor.remote.RemoteServer.qrPayload()) }
    androidx.compose.runtime.LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(9 * 60_000L); payload = com.sridhar.harbor.remote.RemoteServer.qrPayload() } }
    val paired by com.sridhar.harbor.remote.RemoteServer.connected.collectAsState()
    val p = payload ?: return
    // Takes the first focus on the setup screen so the QR is what you see – not a text field and its keyboard.
    val first = remember { androidx.compose.ui.focus.FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) { runCatching { kotlinx.coroutines.delay(150); first.requestFocus() } }
    Row(Modifier.focusRequester(first).onFocusChanged { focused = it.isFocused }.focusable()
        .clip(RoundedCornerShape(20.dp)).background(Harbor.Surface)
        .border(if (focused) 2.dp else 0.dp, if (focused) Harbor.Sky else Color.Transparent, RoundedCornerShape(20.dp)).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(if (big) 200.dp else 150.dp).clip(RoundedCornerShape(12.dp))) { com.sridhar.harbor.ui.components.QrCode(p, Modifier.fillMaxSize()) }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.width(if (big) 220.dp else 170.dp)) {
            Text("📱 " + stringResource(R.string.qr_title), color = Harbor.Fg, fontWeight = FontWeight.Bold, fontSize = if (big) 22.sp else 17.sp)
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.qr_hint), color = Harbor.TextDim, fontSize = if (big) 16.sp else 13.sp)
            if (paired > 0) Text("✓ " + stringResource(R.string.qr_connected), color = Harbor.Mint, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

/** Sidebar "Connect phone": big QR + the manual steps, always one press away. */
@Composable
fun TvConnectPhone() {
    val ip = remember { com.sridhar.harbor.remote.RemoteServer.localIp() }
    Row(Modifier.fillMaxSize().padding(start = 132.dp, end = 48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(40.dp)) {
        PhoneQrCard(big = true)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(stringResource(R.string.qr_or_manual), color = Harbor.Fg, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            listOf(R.string.remote_step1, R.string.remote_step2, R.string.remote_step3).forEachIndexed { i, r ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(30.dp).clip(RoundedCornerShape(50)).background(Harbor.Violet), Alignment.Center) { Text("${i + 1}", color = Color.White, fontWeight = FontWeight.Bold) }
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(r), color = Harbor.Fg.copy(alpha = .9f), fontSize = 15.sp, modifier = Modifier.weight(1f))
                }
            }
            if (ip != null) Text(stringResource(R.string.remote_not_listed) + "  " + ip, color = Harbor.Sky, fontSize = 16.sp)
        }
    }
}
