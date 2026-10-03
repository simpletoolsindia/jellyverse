package com.sridhar.harbor.ui.setup
import androidx.compose.material.icons.automirrored.rounded.ArrowBack

import androidx.compose.material.icons.rounded.Radio as RadioIconDef

import com.sridhar.harbor.L10n
import com.sridhar.harbor.R
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.animation.togetherWith
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.material.icons.rounded.MusicNote
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

    var ndUrl by mutableStateOf("")
    var ndUser by mutableStateOf("")
    var ndPass by mutableStateOf("")
    var ndState by mutableStateOf<ConnState>(ConnState.Idle)

    fun connectNavidrome() = viewModelScope.launch(com.sridhar.harbor.CrashGuard) {
        ndState = ConnState.Working
        ndState = runCatching { c.music.signIn(ndUrl, ndUser.trim(), ndPass) }
            .fold({ ConnState.Ok(L10n.s(R.string.setup_nd_ok, it)) }, { ConnState.Failed(it.friendly()) })
    }

    var sshHost by mutableStateOf("")
    var sshPort by mutableStateOf("22")
    var sshUser by mutableStateOf("")
    var sshPass by mutableStateOf("")
    var sshState by mutableStateOf<ConnState>(ConnState.Idle)

    /** Everything the user can edit for one service – to know whether "Done" must reconnect. */
    internal fun fieldsKey(s: Svc): String = when (s) {
        Svc.Movies -> "$jfUrl|$jfUser|${jfPass.length}"
        Svc.Music -> "$ndUrl|$ndUser|${ndPass.length}"
        Svc.Requests -> "$jsUrl|$jsUseJellyfin|$jsKey|$jfUser|${jfPass.length}"
        Svc.Downloads -> "$qbUrl|$qbUser|${qbPass.length}|$a2Url|${a2Secret.length}"
        Svc.Library -> "$snUrl|$rdUrl|$arrSameLogin|$arrUser|${arrPass.length}"
        Svc.Homelab -> "$sshHost|$sshPort|$sshUser|${sshPass.length}"
    }

    /** Connect / sign in whatever this service step has filled in. */
    internal fun connect(s: Svc) {
        when (s) {
            Svc.Movies -> connectJellyfin()
            Svc.Music -> connectNavidrome()
            Svc.Requests -> connectSeerr()
            Svc.Downloads -> { if (qbUrl.isNotBlank()) connectQbit(); if (a2Url.isNotBlank()) connectAria2() }
            Svc.Library -> { connectArr(com.sridhar.harbor.data.arr.ArrKind.Sonarr); connectArr(com.sridhar.harbor.data.arr.ArrKind.Radarr) }
            Svc.Homelab -> connectSsh()
        }
    }

    val anyConnected get() = listOf(jfState, qbState, jsState, snState, rdState, sshState, a2State, ndState).any { it is ConnState.Ok }

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
            if (cfg.navidromeUrl.isNotBlank()) ndUrl = cfg.navidromeUrl
            if (cfg.navidromeUser.isNotBlank()) ndUser = cfg.navidromeUser
            if (cfg.navidromeReady) ndState = ConnState.Ok(L10n.s(R.string.configured))
            c.ssh.primary?.let { h -> sshHost = h.host; sshPort = h.port.toString(); sshUser = h.user; sshPass = h.password; sshState = ConnState.Ok("${h.user}@${h.host}") }
        }
    }
}

/** What the user wants JellyVerse for – each choice adds one setup step. */
internal enum class Svc(val icon: ImageVector, val title: Int, val sub: Int, val tint: () -> Color) {
    Movies(Icons.Rounded.PlayCircle, R.string.setup_svc_movies, R.string.setup_svc_movies_sub, { Harbor.Violet }),
    Music(Icons.Rounded.MusicNote, R.string.setup_svc_music, R.string.setup_svc_music_sub, { Harbor.Coral }),
    Requests(Icons.Rounded.Inbox, R.string.setup_svc_requests, R.string.setup_svc_requests_sub, { Harbor.Amber }),
    Downloads(Icons.Rounded.SwapVert, R.string.setup_svc_downloads, R.string.setup_svc_downloads_sub, { Harbor.Sky }),
    Library(Icons.Rounded.Tune, R.string.setup_svc_library, R.string.setup_svc_library_sub, { Harbor.Mint }),
    Homelab(Icons.Rounded.Terminal, R.string.setup_svc_homelab, R.string.setup_svc_homelab_sub, { Harbor.Mint }),
}

private fun SetupViewModel.stateOf(s: Svc): ConnState = when (s) {
    Svc.Movies -> jfState
    Svc.Music -> ndState
    Svc.Requests -> jsState
    Svc.Downloads -> listOf(qbState, a2State).firstOrNull { it is ConnState.Ok } ?: listOf(qbState, a2State).firstOrNull { it !is ConnState.Idle } ?: ConnState.Idle
    Svc.Library -> listOf(snState, rdState).firstOrNull { it is ConnState.Ok } ?: listOf(snState, rdState).firstOrNull { it !is ConnState.Idle } ?: ConnState.Idle
    Svc.Homelab -> sshState
}

/**
 * First-run setup as a short, friendly wizard: welcome → pick what you use → one step per service (each can be
 * skipped) → done. Nothing is mandatory: with nothing connected the app starts as a stand-alone radio.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun SetupScreen(onDone: () -> Unit, only: String? = null) {
    val container = LocalContainer.current
    val vm = viewModel { SetupViewModel(container) }
    // Opened from a service row in Settings: just that one step, then back.
    val single = only?.let { n -> Svc.entries.firstOrNull { it.name == n } }
    if (single != null) { SingleServiceSetup(single, vm, onDone); return }
    // Chosen services (already-connected ones start ticked; Movies is the usual first pick).
    var chosen by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(listOf(Svc.Movies.name)) }
    // Re-running setup from Settings: services that are already connected get ticked too (never un-ticks a choice).
    val connected = Svc.entries.filter { vm.stateOf(it) is ConnState.Ok }.map { it.name }
    androidx.compose.runtime.LaunchedEffect(connected) { val add = connected - chosen.toSet(); if (add.isNotEmpty()) chosen = chosen + add }
    val services = Svc.entries.filter { it.name in chosen }
    // 0 = welcome, 1..n = services, n+1 = done
    var step by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(0) }
    val last = services.size + 1
    var forward by remember { mutableStateOf(true) }
    fun go(to: Int) { forward = to > step; step = to.coerceIn(0, last) }
    fun finish() { if (!vm.anyConnected) container.radioOnly = true; onDone() }
    androidx.activity.compose.BackHandler(step > 0) { go(step - 1) }

    Box(Modifier.fillMaxSize().background(Harbor.Ink)) {
        val glowTint = when (step) { 0 -> Harbor.Violet; last -> Harbor.Mint; else -> services.getOrNull(step - 1)?.tint?.invoke() ?: Harbor.Violet }
        val glow by androidx.compose.animation.animateColorAsState(glowTint.copy(alpha = .16f), androidx.compose.animation.core.tween(600), label = "glow")
        Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(glow, Color.Transparent),
            center = androidx.compose.ui.geometry.Offset(300f, 200f), radius = 1300f)))
        Column(Modifier.fillMaxSize().statusBarsPadding().imePadding().navigationBarsPadding()) {
            // Progress
            if (step > 0) Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                val p by androidx.compose.animation.core.animateFloatAsState(step.toFloat() / last, androidx.compose.animation.core.spring(stiffness = 120f), label = "p")
                com.sridhar.harbor.ui.components.ProgressRing(p, size = 40.dp, label = false) {
                    Text("${step.coerceAtMost(last)}/$last", fontSize = androidx.compose.ui.unit.TextUnit(11f, androidx.compose.ui.unit.TextUnitType.Sp), fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.setup_step_of, step.coerceAtMost(last), last), Modifier.weight(1f), color = Harbor.TextDim, style = MaterialTheme.typography.labelMedium)
            }
            androidx.compose.animation.AnimatedContent(step, Modifier.weight(1f), label = "step",
                transitionSpec = {
                    val dir = if (forward) 1 else -1
                    (androidx.compose.animation.slideInHorizontally(androidx.compose.animation.core.tween(380)) { it / 3 * dir } + androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(300, 80))) togetherWith
                        (androidx.compose.animation.slideOutHorizontally(androidx.compose.animation.core.tween(300)) { -it / 4 * dir } + androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(160)))
                }) { st ->
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    when {
                        st == 0 -> WelcomeStep(chosen, onToggle = { n -> chosen = if (n in chosen) chosen - n else chosen + n })
                        st == last -> DoneStep(vm, services)
                        else -> services.getOrNull(st - 1)?.let { ServiceStep(it, vm) }
                    }
                }
            }
            // Bottom actions
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                when (step) {
                    0 -> {
                        GradientButton(stringResource(if (services.isEmpty()) R.string.setup_continue_radio else R.string.setup_lets_go), onClick = { go(1) }, modifier = Modifier.fillMaxWidth())
                        if (!vm.anyConnected) androidx.compose.material3.TextButton({ container.radioOnly = true; onDone() }, Modifier.fillMaxWidth()) {
                            Icon(Icons.Rounded.RadioIconDef, null, tint = Harbor.TextDim); Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.setup_radio_only), color = Harbor.TextDim)
                        }
                    }
                    last -> GradientButton(stringResource(R.string.enter_jellyverse), onClick = { finish() }, modifier = Modifier.fillMaxWidth())
                    else -> {
                        val svc = services[step - 1]
                        val ok = vm.stateOf(svc) is ConnState.Ok
                        // Connected → move on by itself after a beat.
                        // Moves on by itself only when you just connected here – an already-connected service stays open to edit.
                        val okOnEntry = remember(step) { ok }
                        androidx.compose.runtime.LaunchedEffect(ok, step) { if (ok && !okOnEntry && only == null) { kotlinx.coroutines.delay(1100); if (vm.stateOf(svc) is ConnState.Ok && step == services.indexOf(svc) + 1) go(step + 1) } }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.material3.TextButton({ go(step - 1) }) { Text(stringResource(R.string.setup_back), color = Harbor.TextDim) }
                            Spacer(Modifier.weight(1f))
                            if (ok) GradientButton(stringResource(R.string.setup_next), onClick = { go(step + 1) }, modifier = Modifier.width(160.dp))
                            else androidx.compose.material3.OutlinedButton({ go(step + 1) }, Modifier.height(48.dp)) { Text(stringResource(R.string.setup_skip)) }
                        }
                    }
                }
            }
        }
    }
}

/** Settings → tap a service: edit just that service (address, sign-in) and come back. */
@Composable
private fun SingleServiceSetup(s: Svc, vm: SetupViewModel, onDone: () -> Unit) {
    androidx.activity.compose.BackHandler(onBack = onDone)
    val st = vm.stateOf(s)
    // The bottom button is what people press (the inline Connect is often under the keyboard): it saves & connects
    // whenever something changed or nothing is connected yet, and closes once connected.
    val key = vm.fieldsKey(s)
    var savedKey by remember { mutableStateOf(key) }
    var finishing by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(st) {
        if (st is ConnState.Ok) { savedKey = key; if (finishing) onDone() }
        if (st is ConnState.Failed) finishing = false
    }
    val upToDate = st is ConnState.Ok && key == savedKey
    Box(Modifier.fillMaxSize().background(Harbor.Ink)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().imePadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onDone) { Icon(androidx.compose.material.icons.Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.setup_back)) }
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                ServiceStep(s, vm)
            }
            GradientButton(
                stringResource(when { st is ConnState.Working || finishing -> R.string.setup_connecting; upToDate -> R.string.setup_done_btn; else -> R.string.setup_save_connect }),
                onClick = { if (upToDate) onDone() else if (st !is ConnState.Working) { finishing = true; vm.connect(s) } },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp))
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun androidx.compose.foundation.layout.ColumnScope.WelcomeStep(chosen: List<String>, onToggle: (String) -> Unit) {
    // Compact header so the service cards are visible without scrolling, even with large text / display size.
    Spacer(Modifier.height(8.dp))
    Box(Modifier.fillMaxWidth(), Alignment.Center) { com.sridhar.harbor.ui.ai.JellyBuddy(76.dp) }
    Text(stringResource(R.string.setup_hi), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black,
        modifier = Modifier.align(Alignment.CenterHorizontally), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    Text(stringResource(R.string.setup_hi_sub), color = Harbor.TextDim, style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.align(Alignment.CenterHorizontally), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    Box(Modifier.fillMaxWidth(), Alignment.Center) { com.sridhar.harbor.ui.components.LanguagePicker(Modifier, showTitle = false) }
    Text(stringResource(R.string.setup_what_use), style = MaterialTheme.typography.titleMedium)
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
        val w = (maxWidth - 10.dp) / 2 - 1.dp
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Svc.entries.forEachIndexed { i, s ->
                val on = s.name in chosen
                val tint = s.tint()
                val scale by androidx.compose.animation.core.animateFloatAsState(if (on) 1f else 0.97f, androidx.compose.animation.core.spring(dampingRatio = .5f), label = "s")
                Column(Modifier.width(w).graphicsLayer { scaleX = scale; scaleY = scale }
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (on) tint.copy(alpha = .16f) else Harbor.line(.04f))
                    .border(1.5.dp, if (on) tint else Harbor.line(.08f), RoundedCornerShape(20.dp))
                    .clickable { onToggle(s.name) }.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(tint.copy(alpha = .2f)), Alignment.Center) { Icon(s.icon, null, tint = tint) }
                        Spacer(Modifier.weight(1f))
                        androidx.compose.animation.AnimatedVisibility(on, enter = androidx.compose.animation.scaleIn(), exit = androidx.compose.animation.scaleOut()) {
                            Icon(Icons.Rounded.CheckCircle, null, tint = tint)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(stringResource(s.title), fontWeight = FontWeight.Bold)
                    Text(stringResource(s.sub), color = Harbor.TextDim, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                }
            }
        }
    }
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Harbor.line(.04f)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.RadioIconDef, null, tint = Harbor.Rose)
        Spacer(Modifier.width(10.dp))
        Text(stringResource(R.string.setup_radio_always), color = Harbor.TextDim, style = MaterialTheme.typography.bodySmall)
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun StepHeader(s: Svc, state: ConnState) {
    Spacer(Modifier.height(12.dp))
    val tint = s.tint()
    val pop = remember { androidx.compose.animation.core.Animatable(0.6f) }
    androidx.compose.runtime.LaunchedEffect(Unit) { pop.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = .45f, stiffness = 300f)) }
    Box(Modifier.size(84.dp).graphicsLayer { scaleX = pop.value; scaleY = pop.value }.clip(RoundedCornerShape(26.dp)).background(tint.copy(alpha = .18f)), Alignment.Center) {
        androidx.compose.animation.Crossfade(state is ConnState.Ok, label = "ok") { ok ->
            Icon(if (ok) Icons.Rounded.CheckCircle else s.icon, null, tint = if (ok) Harbor.Mint else tint, modifier = Modifier.size(44.dp))
        }
    }
    Text(stringResource(s.title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
    Text(stringResource(s.sub), color = Harbor.TextDim, style = MaterialTheme.typography.bodyLarge)
    if (state is ConnState.Ok) Text("✓ " + state.detail, color = Harbor.Mint, fontWeight = FontWeight.SemiBold)
    if (state is ConnState.Failed) Text(state.error, color = Harbor.Rose, style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun ServiceStep(s: Svc, vm: SetupViewModel) {
    StepHeader(s, vm.stateOf(s))
    when (s) {
        Svc.Movies -> {
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
        Svc.Music -> {
            Field(stringResource(R.string.server_url), vm.ndUrl, { vm.ndUrl = it }, KeyboardType.Uri, placeholder = "http://192.168.1.10:4533")
            Field(stringResource(R.string.username), vm.ndUser, { vm.ndUser = it })
            Field(stringResource(R.string.password), vm.ndPass, { vm.ndPass = it }, password = true)
            ConnectButton(vm.ndState, stringResource(R.string.sign_in)) { vm.connectNavidrome() }
        }
        Svc.Requests -> {
            Field(stringResource(R.string.server_url), vm.jsUrl, { vm.jsUrl = it }, KeyboardType.Uri, placeholder = "http://192.168.1.10:5055")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(vm.jsUseJellyfin, { vm.jsUseJellyfin = true }, SegmentedButtonDefaults.itemShape(0, 2)) { Text(stringResource(R.string.jellyfin_login)) }
                SegmentedButton(!vm.jsUseJellyfin, { vm.jsUseJellyfin = false }, SegmentedButtonDefaults.itemShape(1, 2)) { Text(stringResource(R.string.api_key)) }
            }
            if (vm.jsUseJellyfin) {
                // Your Jellyfin account (the app doesn't keep the password, so ask for it here).
                Field(stringResource(R.string.username), vm.jfUser, { vm.jfUser = it })
                Field(stringResource(R.string.password), vm.jfPass, { vm.jfPass = it }, password = true)
                Text(stringResource(R.string.setup_seerr_jf_hint), color = Harbor.TextDim, style = MaterialTheme.typography.bodySmall)
            }
            else Field(stringResource(R.string.api_key_settings_general), vm.jsKey, { vm.jsKey = it }, password = true)
            ConnectButton(vm.jsState, stringResource(R.string.connect)) { vm.connectSeerr() }
        }
        Svc.Downloads -> {
            Text(stringResource(R.string.setup_dl_either), color = Harbor.TextDim, style = MaterialTheme.typography.bodySmall)
            SubCard(stringResource(R.string.qbittorrent), vm.qbState) {
                Field(stringResource(R.string.webui_url), vm.qbUrl, { vm.qbUrl = it }, KeyboardType.Uri, placeholder = "http://192.168.1.10:8080")
                Field(stringResource(R.string.username), vm.qbUser, { vm.qbUser = it })
                Field(stringResource(R.string.password), vm.qbPass, { vm.qbPass = it }, password = true)
                ConnectButton(vm.qbState, stringResource(R.string.connect)) { vm.connectQbit() }
            }
            SubCard("aria2", vm.a2State) {
                Field(stringResource(R.string.rpc_url), vm.a2Url, { vm.a2Url = it }, KeyboardType.Uri, placeholder = "http://192.168.1.10:6800")
                Field(stringResource(R.string.rpc_secret_rpc_secret), vm.a2Secret, { vm.a2Secret = it }, password = true)
                ConnectButton(vm.a2State, stringResource(R.string.connect)) { vm.connectAria2() }
            }
        }
        Svc.Library -> {
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
        Svc.Homelab -> {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) { Field(stringResource(R.string.host_ip), vm.sshHost, { vm.sshHost = it }, KeyboardType.Uri, placeholder = "192.168.1.10") }
                Box(Modifier.width(96.dp)) { Field(stringResource(R.string.port), vm.sshPort, { vm.sshPort = it.filter(Char::isDigit) }, KeyboardType.Number) }
            }
            Field(stringResource(R.string.username), vm.sshUser, { vm.sshUser = it })
            Field(stringResource(R.string.password), vm.sshPass, { vm.sshPass = it }, password = true)
            Text(stringResource(R.string.stored_encrypted_on_this_phone_the), color = Harbor.TextDim, style = MaterialTheme.typography.bodySmall)
            ConnectButton(vm.sshState, stringResource(R.string.connect)) { vm.connectSsh() }
        }
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun SubCard(title: String, state: ConnState, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(20.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (state is ConnState.Ok) Icon(Icons.Rounded.CheckCircle, null, tint = Harbor.Mint)
        }
        if (state is ConnState.Failed) Text(state.error, color = Harbor.Rose, style = MaterialTheme.typography.bodySmall)
        content()
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.DoneStep(vm: SetupViewModel, services: List<Svc>) {
    Spacer(Modifier.height(28.dp))
    Box(Modifier.fillMaxWidth(), Alignment.Center) { com.sridhar.harbor.ui.ai.JellyBuddy(130.dp, busy = true) }
    Text(stringResource(if (vm.anyConnected) R.string.setup_done else R.string.setup_done_radio), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black,
        modifier = Modifier.align(Alignment.CenterHorizontally), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    Text(stringResource(if (vm.anyConnected) R.string.setup_done_sub else R.string.setup_done_radio_sub), color = Harbor.TextDim, style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.align(Alignment.CenterHorizontally), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    Spacer(Modifier.height(4.dp))
    services.forEachIndexed { i, s ->
        val ok = vm.stateOf(s) is ConnState.Ok
        Row(Modifier.fillMaxWidth().then(Modifier).clip(RoundedCornerShape(16.dp)).background(Harbor.line(.04f)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(s.icon, null, tint = s.tint())
            Spacer(Modifier.width(14.dp))
            Text(stringResource(s.title), Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            if (ok) Icon(Icons.Rounded.CheckCircle, null, tint = Harbor.Mint)
            else Text(stringResource(R.string.setup_skipped), color = Harbor.TextDim, style = MaterialTheme.typography.bodySmall)
        }
    }
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Harbor.line(.04f)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.RadioIconDef, null, tint = Harbor.Rose)
        Spacer(Modifier.width(14.dp))
        Text(stringResource(R.string.tab_radio), Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
        Icon(Icons.Rounded.CheckCircle, null, tint = Harbor.Mint)
    }
    Text(stringResource(R.string.setup_change_later), color = Harbor.TextDim, style = MaterialTheme.typography.bodySmall)
    com.sridhar.harbor.ui.components.MadeWithLove(Modifier.align(Alignment.CenterHorizontally))
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
