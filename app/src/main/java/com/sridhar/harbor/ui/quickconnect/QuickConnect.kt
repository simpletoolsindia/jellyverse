package com.sridhar.harbor.ui.quickconnect

import androidx.compose.runtime.mutableIntStateOf
import com.sridhar.harbor.L10n
import com.sridhar.harbor.R
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sridhar.harbor.ui.components.LocalContainer
import com.sridhar.harbor.ui.components.friendly
import com.sridhar.harbor.ui.theme.Harbor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private sealed interface QcState {
    data object Starting : QcState
    data class Waiting(val code: String, val secret: String) : QcState
    data object Approved : QcState
    data class Failed(val message: String) : QcState
}

/**
 * Shows a Jellyfin Quick Connect code and signs in automatically once it is approved
 * (Jellyfin web → Profile → Quick Connect, or Harbor on the phone → You → Authorize a device).
 */
@Composable
fun QuickConnectPanel(serverUrl: String, big: Boolean = false, onSignedIn: () -> Unit = {}) {
    val jf = LocalContainer.current.jellyfin
    var state by remember(serverUrl) { mutableStateOf<QcState>(QcState.Starting) }
    var attempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(serverUrl, attempt) {
        if (serverUrl.isBlank() || !(serverUrl.contains('.') || serverUrl.contains(':'))) { state = QcState.Failed(L10n.s(R.string.qc_enter_server)); return@LaunchedEffect }
        state = QcState.Starting
        delay(700)   // debounce: don't hit the server for every character typed
        val enabled = runCatching { jf.quickConnectEnabled(serverUrl) }.getOrElse { state = QcState.Failed(it.friendly()); return@LaunchedEffect }
        if (!enabled) { state = QcState.Failed(L10n.s(R.string.quick_connect_is_disabled_on_this)); return@LaunchedEffect }
        val start = runCatching { jf.quickConnectStart(serverUrl) }.getOrElse { state = QcState.Failed(it.friendly()); return@LaunchedEffect }
        state = QcState.Waiting(start.code, start.secret)
        com.sridhar.harbor.remote.RemoteServer.quickConnectCode(start.code)   // a phone that scanned the TV's QR approves it
        // Codes live ~10 minutes; poll every 2 s.
        repeat(300) {
            delay(2000)
            val poll = runCatching { jf.quickConnectPoll(serverUrl, start.secret) }.getOrNull()
            if (poll?.authenticated == true) {
                runCatching { jf.quickConnectFinish(serverUrl, start.secret) }
                    .onSuccess { state = QcState.Approved; com.sridhar.harbor.remote.RemoteServer.signedIn(); delay(600); onSignedIn() }
                    .onFailure { state = QcState.Failed(it.friendly()) }
                return@LaunchedEffect
            }
        }
        state = QcState.Failed(L10n.s(R.string.code_expired))
    }
    val digitSize: TextUnit = if (big) 54.sp else 34.sp
    val box: Dp = if (big) 72.dp else 46.dp
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        AnimatedContent(state, contentKey = { it::class }, transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.9f)) togetherWith fadeOut() }, label = "qc") { s ->
            when (s) {
                QcState.Starting -> com.sridhar.harbor.ui.components.JellyLoader(Modifier.size(box), color = Harbor.VioletSoft)
                is QcState.Waiting -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(horizontalArrangement = Arrangement.spacedBy(if (big) 12.dp else 8.dp)) {
                        s.code.forEach { ch ->
                            Box(Modifier.size(box, box * 1.25f).clip(RoundedCornerShape(if (big) 18.dp else 12.dp)).background(Harbor.line(.06f))
                                .border(1.5.dp, Harbor.Violet.copy(.6f), RoundedCornerShape(if (big) 18.dp else 12.dp)), contentAlignment = Alignment.Center) {
                                Text(ch.toString(), fontSize = digitSize, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, color = Harbor.Fg)
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    val t = rememberInfiniteTransition(label = "wait")
                    val a by t.animateFloat(0.3f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "a")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).graphicsLayer { alpha = a }.clip(RoundedCornerShape(50)).background(Harbor.Mint))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.waiting_for_approval), color = Harbor.TextDim, fontSize = if (big) 18.sp else 13.sp)
                    }
                }
                QcState.Approved -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.CheckCircle, null, tint = Harbor.Mint, modifier = Modifier.size(box / 1.5f)); Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.approved_signing_in), color = Harbor.Mint, fontSize = if (big) 22.sp else 15.sp, fontWeight = FontWeight.Bold)
                }
                is QcState.Failed -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(s.message, color = Harbor.Rose, fontSize = if (big) 17.sp else 13.sp)
                    TextButton({ attempt++ }) { Text(stringResource(R.string.new_code), color = Harbor.VioletSoft) }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(stringResource(R.string.open_jellyfin_web_or_jellyverse_on),
            color = Harbor.TextDim, fontSize = if (big) 15.sp else 11.sp, modifier = Modifier.padding(horizontal = 8.dp))
    }
}

/** Phone side: approve a code shown on a TV / another device. */
@Composable
fun AuthorizeDeviceDialog(onDismiss: () -> Unit) {
    val jf = LocalContainer.current.jellyfin
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var ok by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(if (ok) Icons.Rounded.CheckCircle else Icons.Rounded.Devices, null, tint = if (ok) Harbor.Mint else Harbor.VioletSoft) },
        title = { Text(if (ok) stringResource(R.string.device_signed_in) else stringResource(R.string.quick_connect)) },
        text = {
            Column {
                if (!ok) {
                    Text(stringResource(R.string.enter_the_6_digit_code_shown), color = Harbor.TextDim, fontSize = 13.sp)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(code, { code = it.filter(Char::isDigit).take(6) }, singleLine = true, shape = RoundedCornerShape(14.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 28.sp, fontFamily = FontFamily.Monospace, letterSpacing = 8.sp, color = Harbor.Fg),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
                }
                status?.let { Text(it, color = if (ok) Harbor.Mint else Harbor.Rose, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp)) }
            }
        },
        confirmButton = {
            if (ok) TextButton(onDismiss) { Text(stringResource(R.string.done)) }
            else TextButton({
                busy = true
                scope.launch(com.sridhar.harbor.CrashGuard) {
                    runCatching { jf.quickConnectAuthorize(code) }
                        .onSuccess { ok = true; status = L10n.s(R.string.the_device_will_sign_in_within) }
                        .onFailure { status = it.friendly() }
                    busy = false
                }
            }, enabled = code.length == 6 && !busy) { Text(if (busy) stringResource(R.string.authorizing) else stringResource(R.string.authorize)) }
        },
        dismissButton = { if (!ok) TextButton(onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
