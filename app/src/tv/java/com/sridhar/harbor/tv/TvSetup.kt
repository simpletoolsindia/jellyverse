package com.sridhar.harbor.tv
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.clickable
import androidx.compose.material3.Icon
import androidx.compose.animation.togetherWith
import androidx.compose.ui.graphics.graphicsLayer

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

private enum class SettingsCat(val label: Int, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Account(R.string.tvs_account, Icons.Rounded.AccountCircle),
    Playback(R.string.tvs_playback, Icons.Rounded.PlayCircle),
    Look(R.string.tvs_appearance, Icons.Rounded.Palette),
    Parental(R.string.parental_title, Icons.Rounded.Lock),
    Phone(R.string.tvs_phone_remote, Icons.Rounded.PhoneAndroid),
    Updates(R.string.tvs_updates, Icons.Rounded.SystemUpdate),
    About(R.string.tvs_about, Icons.Rounded.Info),
}

/**
 * TV settings, two-pane like Google TV / Prime Video: categories on the left (moving over one shows its options),
 * big option rows on the right with the current value and a one-line explanation. Everything is D-pad first.
 */
@Composable
fun TvSettings() {
    var cat by rememberSaveable { mutableStateOf(SettingsCat.Account) }
    // ▶ from a category jumps into its options, ◀ from the options goes back to that category.
    val pane = remember { androidx.compose.ui.focus.FocusRequester() }
    val fm = androidx.compose.ui.platform.LocalFocusManager.current
    val catFocus = remember { SettingsCat.entries.associateWith { androidx.compose.ui.focus.FocusRequester() } }
    fun isKey(e: androidx.compose.ui.input.key.KeyEvent, k: androidx.compose.ui.input.key.Key) =
        e.type == androidx.compose.ui.input.key.KeyEventType.KeyDown && e.key == k
    Row(Modifier.fillMaxSize().padding(start = 108.dp, top = 40.dp, end = 48.dp, bottom = 24.dp)) {
        // ---- left: categories
        Column(Modifier.width(300.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.settings), color = Harbor.Fg, fontSize = 36.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(14.dp))
            SettingsCat.entries.forEach { c ->
                var focused by remember { mutableStateOf(false) }
                val selected = c == cat
                Row(Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(16.dp))
                    .background(when { focused -> Color.White; selected -> Harbor.line(.10f); else -> Color.Transparent })
                    .focusRequester(catFocus.getValue(c))
                    .onPreviewKeyEvent { if (isKey(it, androidx.compose.ui.input.key.Key.DirectionRight)) {
                        runCatching { pane.requestFocus() }.onFailure { fm.moveFocus(androidx.compose.ui.focus.FocusDirection.Right) }; true } else false }
                    .onFocusChanged { focused = it.isFocused; if (it.isFocused) cat = c }
                    .clickable { cat = c }.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(c.icon, null, tint = if (focused) Color.Black else if (selected) Harbor.Sky else Harbor.TextDim, modifier = Modifier.size(26.dp))
                    Spacer(Modifier.width(14.dp))
                    Text(stringResource(c.label), color = if (focused) Color.Black else Harbor.Fg, fontSize = 18.sp,
                        fontWeight = if (selected || focused) FontWeight.Bold else FontWeight.Medium)
                    if (selected && !focused) { Spacer(Modifier.weight(1f)); Box(Modifier.size(width = 4.dp, height = 22.dp).clip(RoundedCornerShape(2.dp)).background(Harbor.Sky)) }
                }
            }
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                com.sridhar.harbor.ui.ai.JellyBuddy(40.dp)
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.jellyverse_tv_1_s, com.sridhar.harbor.BuildConfig.VERSION_NAME), color = Harbor.TextDim, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.width(32.dp))
        // ---- right: options of the category
        androidx.compose.animation.AnimatedContent(cat, Modifier.weight(1f).fillMaxHeight(), label = "tvs",
            transitionSpec = { androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(180)) togetherWith androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(90)) }) { c ->
            Column(Modifier.fillMaxSize()
                .focusGroup()
                // ◀ when nothing further left in the pane: back to the category list.
                .onKeyEvent { if (isKey(it, androidx.compose.ui.input.key.Key.DirectionLeft)) { runCatching { catFocus.getValue(c).requestFocus() }; true } else false }
                .verticalScroll(rememberScrollState()).padding(top = 64.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(c.label), color = Harbor.TextDim, fontSize = 15.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                SettingsPane(c, pane)
            }
        }
    }
}

@Composable
private fun SettingsPane(c: SettingsCat, first: androidx.compose.ui.focus.FocusRequester) {
    val container = LocalContainer.current
    val cfg = rememberConfig()
    val scope = rememberCoroutineScope()
    val on = stringResource(R.string.on_label); val off = stringResource(R.string.off_label)
    when (c) {
        SettingsCat.Account -> {
            SettingInfo(Icons.Rounded.Dns, stringResource(R.string.jellyfin), cfg.jellyfinUrl.ifBlank { stringResource(R.string.not_configured) },
                if (cfg.jellyfinReady) stringResource(R.string.signed_in_as_1_s, cfg.jellyfinUser) else null, first)
            SettingRow(Icons.AutoMirrored.Rounded.Logout, stringResource(R.string.sign_out_2), null, stringResource(R.string.tvs_sign_out_hint)) {
                scope.launch(com.sridhar.harbor.CrashGuard) { container.settings.update { ServerConfig(deviceId = it.deviceId) } }
            }
        }
        SettingsCat.Playback -> {
            val previewMode by container.previewMode.collectAsState()
            SettingRow(Icons.Rounded.Movie, stringResource(R.string.previews_title), focus = first, value = when (previewMode) {
                "on" -> on; "off" -> off
                else -> stringResource(if (container.previewsOn("auto")) R.string.previews_auto_on else R.string.previews_auto_off)
            }, hint = stringResource(R.string.tvs_previews_hint)) { container.setPreviewMode(when (previewMode) { "auto" -> "on"; "on" -> "off"; else -> "auto" }) }
            val ctx = androidx.compose.ui.platform.LocalContext.current
            val playerPrefs = remember { com.sridhar.harbor.ui.player.PlayerPrefs(ctx) }
            var passthrough by remember { mutableStateOf(playerPrefs.passthrough) }
            SettingRow(Icons.Rounded.SurroundSound, stringResource(R.string.passthrough_title), if (passthrough) on else off,
                stringResource(R.string.tvs_passthrough_hint)) { passthrough = !passthrough; playerPrefs.passthrough = passthrough }
            val recoOn by container.reco.consent.collectAsState()
            SettingRow(Icons.Rounded.AutoAwesome, stringResource(R.string.reco_toggle), if (recoOn == true) on else off,
                stringResource(R.string.tvs_reco_hint)) { container.reco.setConsent(recoOn != true) }
            Text(stringResource(R.string.player_options_subtitles_audio_quality_speed), color = Harbor.TextDim, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
        }
        SettingsCat.Look -> {
            com.sridhar.harbor.ui.components.AppearancePicker(Modifier.widthIn(max = 760.dp), tv = true)
            com.sridhar.harbor.ui.components.LanguagePicker()
        }
        SettingsCat.Parental -> {
            var open by remember { mutableStateOf(false) }
            SettingRow(Icons.Rounded.Lock, stringResource(R.string.parental_title), com.sridhar.harbor.ui.parental.parentalSummary(),
                stringResource(R.string.tvs_parental_hint), first) { open = true }
            if (open) com.sridhar.harbor.ui.parental.ParentalSettingsDialog { open = false }
        }
        SettingsCat.Phone -> {
            var pairing by remember { mutableStateOf(false) }
            val phones by com.sridhar.harbor.remote.RemoteServer.connected.collectAsState()
            SettingRow(Icons.Rounded.QrCode2, stringResource(R.string.remote_pair_phone),
                if (phones > 0) stringResource(R.string.remote_phones_connected, phones) else null, stringResource(R.string.tvs_phone_hint), first) { pairing = true }
            if (pairing) PairPhoneDialog { pairing = false }
        }
        SettingsCat.Updates -> {
            if (container.updater.enabled) {
                val autoUpd by container.updater.autoCheckFlow.collectAsState()
                val ctxU = androidx.compose.ui.platform.LocalContext.current
                SettingRow(Icons.Rounded.Autorenew, stringResource(R.string.update_auto), if (autoUpd) on else off, stringResource(R.string.tvs_update_auto_hint), first) {
                    container.updater.autoCheck = !autoUpd; com.sridhar.harbor.update.UpdateWorker.schedule(ctxU)
                }
            }
            com.sridhar.harbor.update.updateStatus()?.let { status ->
                SettingRow(Icons.Rounded.SystemUpdate, stringResource(R.string.update_check), status, null) {
                    scope.launch(com.sridhar.harbor.CrashGuard) { container.updater.check(userInitiated = true) }
                }
            }
        }
        SettingsCat.About -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                com.sridhar.harbor.ui.ai.JellyBuddy(96.dp)
                Spacer(Modifier.width(20.dp))
                Column {
                    Text("JellyVerse TV", color = Harbor.Fg, fontSize = 28.sp, fontWeight = FontWeight.Black)
                    Text(stringResource(R.string.jellyverse_tv_1_s, com.sridhar.harbor.BuildConfig.VERSION_NAME), color = Harbor.TextDim, fontSize = 15.sp)
                }
            }
            SettingInfo(Icons.Rounded.Info, stringResource(R.string.tvs_remote_tip_title), stringResource(R.string.tvs_remote_tip), null, first)
            com.sridhar.harbor.ui.components.MadeWithLove()
        }
    }
}

/** One big D-pad-friendly option: icon, title, current value pill, one-line explanation. Focused → white card. */
@Composable
private fun SettingRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, value: String?, hint: String?,
                       focus: androidx.compose.ui.focus.FocusRequester? = null, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by androidx.compose.animation.core.animateFloatAsState(if (focused) 1.02f else 1f, label = "s")
    Row(Modifier.fillMaxWidth().widthIn(max = 820.dp).graphicsLayer { scaleX = scale; scaleY = scale }.clip(RoundedCornerShape(18.dp))
        .background(if (focused) Color.White else Harbor.Surface).then(if (focus != null) Modifier.focusRequester(focus) else Modifier)
        .onFocusChanged { focused = it.isFocused }.clickable(onClick = onClick)
        .padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = if (focused) Color.Black else Harbor.Sky, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = if (focused) Color.Black else Harbor.Fg, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            hint?.let { Text(it, color = if (focused) Color.Black.copy(alpha = .6f) else Harbor.TextDim, fontSize = 14.sp) }
        }
        value?.let {
            Spacer(Modifier.width(12.dp))
            Box(Modifier.clip(RoundedCornerShape(50)).background(if (focused) Color.Black.copy(alpha = .08f) else Harbor.line(.08f)).padding(horizontal = 14.dp, vertical = 6.dp)) {
                Text(it, color = if (focused) Color.Black else Harbor.Fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        }
    }
}

/** Read-only info card (focusable so the remote can reach and read it). */
@Composable
private fun SettingInfo(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, value: String, sub: String?,
                        focus: androidx.compose.ui.focus.FocusRequester? = null) {
    var focused by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().widthIn(max = 820.dp).clip(RoundedCornerShape(18.dp)).background(Harbor.Surface)
        .border(if (focused) 2.dp else 0.dp, if (focused) Harbor.Sky else Color.Transparent, RoundedCornerShape(18.dp))
        .then(if (focus != null) Modifier.focusRequester(focus) else Modifier)
        .onFocusChanged { focused = it.isFocused }.focusable().padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Harbor.Sky, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(18.dp))
        Column {
            Text(title, color = Harbor.TextDim, fontSize = 14.sp)
            Text(value, color = Harbor.Fg, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            sub?.let { Text(it, color = Harbor.Mint, fontSize = 14.sp) }
        }
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
