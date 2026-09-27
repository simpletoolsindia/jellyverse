package com.sridhar.harbor.tv

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
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.material.icons.rounded.Lock
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
            Column(Modifier.width(340.dp)) {
                com.sridhar.harbor.ui.components.HarborLogo(96.dp)
                Spacer(Modifier.height(24.dp))
                Text(stringResource(R.string.jellyverse_tv), color = Color.White, fontSize = 44.sp, fontWeight = FontWeight.Black)
                Text(stringResource(R.string.your_jellyfin_library_beautifully_on_the), color = Harbor.TextDim, fontSize = 20.sp)
                Spacer(Modifier.height(18.dp))
                com.sridhar.harbor.ui.components.MadeWithLove()
            }
            var usePassword by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
            Column(Modifier.width(460.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                TvField(stringResource(R.string.jellyfin_server), vm.jfUrl, KeyboardType.Uri) { vm.jfUrl = it }
                if (!usePassword) {
                    Text(stringResource(R.string.quick_connect), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
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
        keyboardOptions = KeyboardOptions(keyboardType = type, imeAction = androidx.compose.ui.text.input.ImeAction.Next),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onNext = { fm.moveFocus(androidx.compose.ui.focus.FocusDirection.Down) }),
        visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color.White, unfocusedContainerColor = Harbor.Surface, focusedContainerColor = Harbor.SurfaceHigh))
}

@Composable
fun TvSettings() {
    val container = LocalContainer.current
    val cfg = rememberConfig()
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().padding(64.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text(stringResource(R.string.settings), color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Black)
        Column(Modifier.clip(RoundedCornerShape(20.dp)).background(Harbor.Surface).padding(24.dp)) {
            Text(stringResource(R.string.jellyfin), color = Harbor.TextDim, fontSize = 14.sp)
            Text(cfg.jellyfinUrl, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
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
        com.sridhar.harbor.remote.RemoteServer.localIp()?.let { ip ->
            Text("📱 " + stringResource(R.string.remote_tv_address, ip), color = Harbor.TextDim, fontSize = 15.sp)
        }
        com.sridhar.harbor.ui.components.LanguagePicker()
        Text(stringResource(R.string.jellyverse_tv_1_s, com.sridhar.harbor.BuildConfig.VERSION_NAME), color = Harbor.TextDim, fontSize = 13.sp)
        com.sridhar.harbor.ui.components.MadeWithLove()
    }
}
