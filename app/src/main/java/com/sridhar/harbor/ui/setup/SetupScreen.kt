package com.sridhar.harbor.ui.setup

import androidx.compose.material.icons.rounded.Radio as RadioIconDef

import com.sridhar.harbor.L10n
import com.sridhar.harbor.R
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sridhar.harbor.data.AppContainer
import com.sridhar.harbor.ui.components.GradientButton
import com.sridhar.harbor.ui.components.LocalContainer
import com.sridhar.harbor.ui.components.friendly
import com.sridhar.harbor.ui.components.glass
import com.sridhar.harbor.ui.theme.Harbor
import kotlinx.coroutines.launch

sealed interface ConnState {
    data object Idle : ConnState
    data object Working : ConnState
    data class Ok(val detail: String) : ConnState
    data class Failed(val error: String) : ConnState
}

class SetupViewModel(private val c: AppContainer) : ViewModel() {
    var jfUrl by mutableStateOf("")
    var jfUser by mutableStateOf("")
    var jfPass by mutableStateOf("")
    var jfState by mutableStateOf<ConnState>(ConnState.Idle)

    var qbUrl by mutableStateOf("")
    var qbUser by mutableStateOf("")
    var qbPass by mutableStateOf("")
    var qbState by mutableStateOf<ConnState>(ConnState.Idle)

    var jsUrl by mutableStateOf("")
    var jsUseJellyfin by mutableStateOf(true)
    var jsKey by mutableStateOf("")
    var jsState by mutableStateOf<ConnState>(ConnState.Idle)



    var snUrl by mutableStateOf("")
    var snState by mutableStateOf<ConnState>(ConnState.Idle)
    var rdUrl by mutableStateOf("")
    var rdState by mutableStateOf<ConnState>(ConnState.Idle)
    var arrSameLogin by mutableStateOf(true)
    var arrUser by mutableStateOf("")
    var arrPass by mutableStateOf("")

    var a2Url by mutableStateOf("")
    var a2Secret by mutableStateOf("")
    var a2State by mutableStateOf<ConnState>(ConnState.Idle)




    fun connectAria2() = viewModelScope.launch(com.sridhar.harbor.CrashGuard) {
        a2State = ConnState.Working
        a2State = runCatching { c.aria2.test(a2Url, a2Secret.trim()) }.fold({ ConnState.Ok(L10n.s(R.string.aria2_v_1_s, it)) }, { ConnState.Failed(it.friendly()) })
    }

    var sshHost by mutableStateOf("")
    var sshPort by mutableStateOf("22")
    var sshUser by mutableStateOf("")
    var sshPass by mutableStateOf("")
    var sshState by mutableStateOf<ConnState>(ConnState.Idle)

    val anyConnected get() = listOf(jfState, qbState, jsState, snState, rdState, sshState, a2State).any { it is ConnState.Ok }

    private fun arrCreds(): Pair<String, String> =
        if (arrSameLogin) jfUser.trim() to jfPass else arrUser.trim() to arrPass

    fun connectArr(kind: com.sridhar.harbor.data.arr.ArrKind) = viewModelScope.launch(com.sridhar.harbor.CrashGuard) {
        val url = if (kind == com.sridhar.harbor.data.arr.ArrKind.Sonarr) snUrl else rdUrl
        fun set(v: ConnState) { if (kind == com.sridhar.harbor.data.arr.ArrKind.Sonarr) snState = v else rdState = v }
        set(ConnState.Working)
        set(runCatching {
            val (u, p) = arrCreds()
            require(u.isNotBlank() && p.isNotBlank()) { if (arrSameLogin) L10n.s(R.string.enter_your_jellyfin_username_and_password) else "Enter username and password" }
            c.arr(kind).loginForKey(url, u, p)
        }.fold({ ConnState.Ok("${kind.label} v$it") }, { ConnState.Failed(it.friendly()) }))
    }

    fun connectSsh() = viewModelScope.launch(com.sridhar.harbor.CrashGuard) {
        sshState = ConnState.Working
        val host = (c.ssh.primary ?: com.sridhar.harbor.data.ssh.SshHost(name = "Homelab", host = sshHost.trim(), user = sshUser.trim()))
            .copy(host = sshHost.trim(), port = sshPort.toIntOrNull() ?: 22, user = sshUser.trim(), password = sshPass)
        sshState = runCatching {
            val out = c.ssh.exec(host, "hostname; uname -m").lines().filter { it.isNotBlank() }
            c.ssh.save(host); c.ssh.setPrimary(host)
            out.joinToString(" · ")
        }.fold({ ConnState.Ok(L10n.s(R.string.connected_to_1_s, it)) }, { ConnState.Failed(it.message ?: "SSH failed") })
    }

    fun connectJellyfin() = viewModelScope.launch(com.sridhar.harbor.CrashGuard) {
        jfState = ConnState.Working
        jfState = runCatching { c.jellyfin.login(jfUrl, jfUser.trim(), jfPass) }
            .fold({ ConnState.Ok(L10n.s(R.string.signed_in_as_1_s, it.user.name)) }, { ConnState.Failed(it.friendly()) })
    }

    fun quickConnectDone() = viewModelScope.launch(com.sridhar.harbor.CrashGuard) {
        val cfg = c.settings.current()
        jfUser = cfg.jellyfinUser
        jfState = ConnState.Ok(L10n.s(R.string.signed_in_as_1_s_quick, cfg.jellyfinUser))
    }

    fun connectQbit() = viewModelScope.launch(com.sridhar.harbor.CrashGuard) {
        qbState = ConnState.Working
        qbState = runCatching {
            val v = c.qbit.login(qbUrl, qbUser.trim(), qbPass)
            c.settings.update { it.copy(qbitUrl = qbUrl, qbitUser = qbUser.trim(), qbitPass = qbPass) }
            v
        }.fold({ ConnState.Ok(L10n.s(R.string.qbittorrent_1_s, it)) }, { ConnState.Failed(it.friendly()) })
    }

    fun connectSeerr() = viewModelScope.launch(com.sridhar.harbor.CrashGuard) {
        jsState = ConnState.Working
        jsState = runCatching {
            if (jsUseJellyfin) {
                val user = jfUser.trim().ifBlank { c.settings.current().jellyfinUser }
                require(user.isNotBlank() && jfPass.isNotBlank()) { L10n.s(R.string.enter_your_jellyfin_username_and_password) }
                c.seerr.loginWithJellyfin(jsUrl, user, jfPass)
            } else c.seerr.useApiKey(jsUrl, jsKey)
        }.fold({ ConnState.Ok(L10n.s(R.string.signed_in_as_1_s, it.name)) }, { ConnState.Failed(it.friendly()) })
    }

    // Declared after every property: the coroutine can run synchronously and must not touch uninitialised state.
    init {
        viewModelScope.launch(com.sridhar.harbor.CrashGuard) {
            val cfg = c.settings.current()
            if (cfg.jellyfinUrl.isNotBlank()) jfUrl = cfg.jellyfinUrl
            jfUser = cfg.jellyfinUser
            if (cfg.jellyfinReady) jfState = ConnState.Ok(L10n.s(R.string.signed_in_as_1_s, cfg.jellyfinUser))
            if (cfg.qbitUrl.isNotBlank()) qbUrl = cfg.qbitUrl
            if (cfg.qbitUser.isNotBlank()) qbUser = cfg.qbitUser
            qbPass = cfg.qbitPass
            if (cfg.qbitReady) qbState = ConnState.Ok(L10n.s(R.string.configured))
            if (cfg.seerrUrl.isNotBlank()) jsUrl = cfg.seerrUrl
            jsKey = cfg.seerrApiKey
            jsUseJellyfin = cfg.seerrApiKey.isBlank()
            if (cfg.seerrReady) jsState = ConnState.Ok(L10n.s(R.string.configured))
            if (cfg.aria2Url.isNotBlank()) { a2Url = cfg.aria2Url; a2Secret = cfg.aria2Secret; a2State = ConnState.Ok(L10n.s(R.string.configured)) }
            if (cfg.sonarrUrl.isNotBlank()) snUrl = cfg.sonarrUrl
            if (cfg.sonarrReady) snState = ConnState.Ok(L10n.s(R.string.configured))
            if (cfg.radarrUrl.isNotBlank()) rdUrl = cfg.radarrUrl
            if (cfg.radarrReady) rdState = ConnState.Ok(L10n.s(R.string.configured))
            c.ssh.primary?.let { h -> sshHost = h.host; sshPort = h.port.toString(); sshUser = h.user; sshPass = h.password; sshState = ConnState.Ok("${h.user}@${h.host}") }
        }
    }
}

@Composable
fun SetupScreen(onDone: () -> Unit) {
    val container = LocalContainer.current
    val vm = viewModel { SetupViewModel(container) }

    Box(Modifier.fillMaxSize().background(Harbor.Ink)) {
        // Ambient glow
        Box(Modifier.fillMaxSize().background(
            Brush.radialGradient(listOf(Harbor.Violet.copy(alpha = .10f), Color.Transparent),
                center = androidx.compose.ui.geometry.Offset(200f, 150f), radius = 1100f)))
        Column(
            Modifier.fillMaxSize().statusBarsPadding().imePadding().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(24.dp))
            com.sridhar.harbor.ui.components.HarborLogo(72.dp)
            Text(stringResource(R.string.welcome_to_njellyverse), style = MaterialTheme.typography.headlineLarge)
            com.sridhar.harbor.ui.components.LanguagePicker(Modifier.padding(top = 12.dp), showTitle = false)
            Text(
                stringResource(R.string.connect_the_services_running_on_your),
                color = Harbor.TextDim, style = MaterialTheme.typography.bodyLarge,
            )

            ServiceCard(stringResource(R.string.jellyfin), stringResource(R.string.stream_download_your_library), Icons.Rounded.PlayCircle, Harbor.Violet, vm.jfState) {
                Field(stringResource(R.string.server_url), vm.jfUrl, { vm.jfUrl = it }, KeyboardType.Uri, placeholder = "http://192.168.1.10:8096")
                Field(stringResource(R.string.username), vm.jfUser, { vm.jfUser = it })
                Field(stringResource(R.string.password), vm.jfPass, { vm.jfPass = it }, password = true)
                ConnectButton(vm.jfState, stringResource(R.string.sign_in)) { vm.connectJellyfin() }
                var qc by remember { mutableStateOf(false) }
                androidx.compose.material3.TextButton({ qc = !qc }, Modifier.fillMaxWidth()) {
                    Text(if (qc) stringResource(R.string.hide_quick_connect) else stringResource(R.string.sign_in_with_quick_connect_instead), color = Harbor.VioletSoft)
                }
                if (qc) com.sridhar.harbor.ui.quickconnect.QuickConnectPanel(vm.jfUrl) { vm.quickConnectDone() }
            }

            ServiceCard(stringResource(R.string.qbittorrent), stringResource(R.string.manage_torrents_speed_limits), Icons.Rounded.SwapVert, Harbor.Sky, vm.qbState) {
                Field(stringResource(R.string.webui_url), vm.qbUrl, { vm.qbUrl = it }, KeyboardType.Uri, placeholder = "http://192.168.1.10:8080")
                Field(stringResource(R.string.username), vm.qbUser, { vm.qbUser = it })
                Field(stringResource(R.string.password), vm.qbPass, { vm.qbPass = it }, password = true)
                ConnectButton(vm.qbState, stringResource(R.string.connect)) { vm.connectQbit() }
            }

            ServiceCard(stringResource(R.string.jellyseerr), stringResource(R.string.request_movies_series_manage_users), Icons.Rounded.Inbox, Harbor.Coral, vm.jsState) {
                Field(stringResource(R.string.server_url), vm.jsUrl, { vm.jsUrl = it }, KeyboardType.Uri, placeholder = "http://192.168.1.10:5055")
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(vm.jsUseJellyfin, { vm.jsUseJellyfin = true }, SegmentedButtonDefaults.itemShape(0, 2)) { Text(stringResource(R.string.jellyfin_login)) }
                    SegmentedButton(!vm.jsUseJellyfin, { vm.jsUseJellyfin = false }, SegmentedButtonDefaults.itemShape(1, 2)) { Text(stringResource(R.string.api_key)) }
                }
                if (vm.jsUseJellyfin) Text(stringResource(R.string.uses_the_jellyfin_username_password_entered), color = Harbor.TextDim, style = MaterialTheme.typography.bodySmall)
                else Field(stringResource(R.string.api_key_settings_general), vm.jsKey, { vm.jsKey = it }, password = true)
                ConnectButton(vm.jsState, stringResource(R.string.connect)) { vm.connectSeerr() }
            }

            ServiceCard("aria2", stringResource(R.string.http_ftp_magnet_downloader), Icons.Rounded.SwapVert, Harbor.Sky, vm.a2State) {
                Field(stringResource(R.string.rpc_url), vm.a2Url, { vm.a2Url = it }, KeyboardType.Uri, placeholder = "http://192.168.1.10:6800")
                Field(stringResource(R.string.rpc_secret_rpc_secret), vm.a2Secret, { vm.a2Secret = it }, password = true)
                ConnectButton(vm.a2State, stringResource(R.string.connect)) { vm.connectAria2() }
            }

            ServiceCard(stringResource(R.string.sonarr_radarr), stringResource(R.string.queue_calendar_missing_media_manual_search), Icons.Rounded.Tune, Harbor.Amber,
                if (vm.snState is ConnState.Ok && vm.rdState is ConnState.Ok) ConnState.Ok(stringResource(R.string.both_connected))
                else listOf(vm.snState, vm.rdState).firstOrNull { it !is ConnState.Idle && it !is ConnState.Ok } ?: vm.snState) {
                Field(stringResource(R.string.sonarr_url), vm.snUrl, { vm.snUrl = it }, KeyboardType.Uri, placeholder = "http://192.168.1.10:8989")
                Field(stringResource(R.string.radarr_url), vm.rdUrl, { vm.rdUrl = it }, KeyboardType.Uri, placeholder = "http://192.168.1.10:7878")
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(vm.arrSameLogin, { vm.arrSameLogin = true }, SegmentedButtonDefaults.itemShape(0, 2)) { Text(stringResource(R.string.jellyfin_login)) }
                    SegmentedButton(!vm.arrSameLogin, { vm.arrSameLogin = false }, SegmentedButtonDefaults.itemShape(1, 2)) { Text(stringResource(R.string.other_login)) }
                }
                if (!vm.arrSameLogin) {
                    Field(stringResource(R.string.username), vm.arrUser, { vm.arrUser = it })
                    Field(stringResource(R.string.password), vm.arrPass, { vm.arrPass = it }, password = true)
                }
                Text(stringResource(R.string.jellyverse_signs_in_once_and_stores), color = Harbor.TextDim, style = MaterialTheme.typography.bodySmall)
                StatusLine(stringResource(R.string.sonarr), vm.snState); StatusLine(stringResource(R.string.radarr), vm.rdState)
                ConnectButton(if (vm.snState is ConnState.Working || vm.rdState is ConnState.Working) ConnState.Working else vm.snState, stringResource(R.string.connect_both)) {
                    vm.connectArr(com.sridhar.harbor.data.arr.ArrKind.Sonarr); vm.connectArr(com.sridhar.harbor.data.arr.ArrKind.Radarr)
                }
            }

            ServiceCard(stringResource(R.string.homelab_ssh), stringResource(R.string.terminal_system_health_containers), Icons.Rounded.Terminal, Harbor.Mint, vm.sshState) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) { Field(stringResource(R.string.host_ip), vm.sshHost, { vm.sshHost = it }, KeyboardType.Uri, placeholder = "192.168.1.10") }
                    Box(Modifier.width(96.dp)) { Field(stringResource(R.string.port), vm.sshPort, { vm.sshPort = it.filter(Char::isDigit) }, KeyboardType.Number) }
                }
                Field(stringResource(R.string.username), vm.sshUser, { vm.sshUser = it })
                Field(stringResource(R.string.password), vm.sshPass, { vm.sshPass = it }, password = true)
                Text(stringResource(R.string.stored_encrypted_on_this_phone_the), color = Harbor.TextDim, style = MaterialTheme.typography.bodySmall)
                ConnectButton(vm.sshState, stringResource(R.string.connect)) { vm.connectSsh() }
            }

            GradientButton(stringResource(R.string.enter_jellyverse), onClick = onDone, modifier = Modifier.fillMaxWidth(), enabled = vm.anyConnected)
            // No servers? The radio works on its own (internet / FM stations, recording, reminders).
            if (!vm.anyConnected) {
                val c = com.sridhar.harbor.ui.components.LocalContainer.current
                androidx.compose.material3.OutlinedButton({ c.radioOnly = true; onDone() }, Modifier.fillMaxWidth().height(52.dp)) {
                    androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Rounded.RadioIconDef, null)
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.setup_radio_only))
                }
                Text(stringResource(R.string.setup_radio_only_hint), color = Harbor.TextDim, style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.align(Alignment.CenterHorizontally))
            }
            com.sridhar.harbor.ui.components.MadeWithLove(Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ServiceCard(
    title: String, subtitle: String, icon: ImageVector, tint: Color, state: ConnState,
    content: @Composable () -> Unit,
) {
    var open by remember { mutableStateOf(state !is ConnState.Ok) }
    Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(24.dp)).animateContentSize()) {
        Row(Modifier.fillMaxWidth().clickable { open = !open }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(tint.copy(alpha = .18f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = tint)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    when (state) { is ConnState.Ok -> state.detail; is ConnState.Failed -> state.error; else -> subtitle },
                    style = MaterialTheme.typography.bodySmall,
                    color = when (state) { is ConnState.Ok -> Harbor.Mint; is ConnState.Failed -> Harbor.Rose; else -> Harbor.TextDim },
                    maxLines = 2,
                )
            }
            when (state) {
                is ConnState.Ok -> Icon(Icons.Rounded.CheckCircle, null, tint = Harbor.Mint)
                is ConnState.Failed -> Icon(Icons.Rounded.Error, null, tint = Harbor.Rose)
                is ConnState.Working -> com.sridhar.harbor.ui.components.JellyLoader(Modifier.size(20.dp), strokeWidth = 2.dp)
                else -> Box(Modifier.size(10.dp).clip(CircleShape).background(Harbor.TextDim.copy(alpha = .4f)))
            }
        }
        AnimatedVisibility(open) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
        }
    }
}

@Composable
private fun Field(
    label: String, value: String, onChange: (String) -> Unit, keyboard: KeyboardType = KeyboardType.Text,
    password: Boolean = false, placeholder: String? = null,
) {
    var reveal by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true,
        placeholder = placeholder?.let { { Text(it, color = Harbor.TextDim.copy(alpha = .6f)) } },
        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
        keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboard),
        visualTransformation = if (password && !reveal) PasswordVisualTransformation() else VisualTransformation.None,
        leadingIcon = if (keyboard == KeyboardType.Uri) ({ Icon(Icons.Rounded.Link, null) }) else null,
        trailingIcon = if (password) ({
            IconButton({ reveal = !reveal }) { Icon(if (reveal) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, null) }
        }) else null,
        colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Harbor.line(.12f)),
    )
}

@Composable
private fun StatusLine(name: String, state: ConnState) {
    if (state is ConnState.Idle) return
    Text("$name: " + when (state) { is ConnState.Ok -> state.detail; is ConnState.Failed -> state.error; else -> "connecting…" },
        color = when (state) { is ConnState.Ok -> Harbor.Mint; is ConnState.Failed -> Harbor.Rose; else -> Harbor.TextDim },
        style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun ConnectButton(state: ConnState, label: String, onClick: () -> Unit) {
    FilledTonalButton(onClick, enabled = state !is ConnState.Working, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(14.dp),
        colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(containerColor = Harbor.Violet.copy(alpha = .22f), contentColor = Harbor.VioletSoft)) {
        if (state is ConnState.Working) com.sridhar.harbor.ui.components.JellyLoader(Modifier.size(18.dp), strokeWidth = 2.dp)
        else Text(if (state is ConnState.Ok) stringResource(R.string.reconnect) else label, fontWeight = FontWeight.Bold)
    }
}
