package com.sridhar.harbor.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sridhar.harbor.R
import com.sridhar.harbor.ui.theme.Harbor

/**
 * 4-digit PIN entry. [onSubmit] returns null when accepted, or an error message to show (the dots shake and clear).
 * Works with touch, D-pad focus and a TV remote's number keys.
 */
@Composable
fun PinDialog(title: String, subtitle: String? = null, onDismiss: () -> Unit, onSubmit: (String) -> String?) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val shake = remember { Animatable(0f) }
    var attempt by remember { mutableStateOf(0) }
    LaunchedEffect(attempt) {
        if (attempt > 0) { for (x in listOf(18f, -14f, 10f, -6f, 0f)) shake.animateTo(x, spring(stiffness = 3000f)) }
    }
    fun press(d: Char) {
        if (pin.length >= 4) return
        pin += d; error = null
        if (pin.length == 4) {
            val err = onSubmit(pin)
            if (err != null) { error = err; pin = ""; attempt++ }
        }
    }
    val first = remember { FocusRequester() }

    // TVs and landscape phones are short: put the title beside the keypad so nothing is cut off.
    val compact = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp < 600
    val header: @Composable () -> Unit = {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(52.dp).clip(CircleShape).background(Harbor.Violet.copy(alpha = .18f)), Alignment.Center) {
                Icon(Icons.Rounded.Lock, null, tint = Harbor.VioletSoft)
            }
            Spacer(Modifier.height(12.dp))
            Text(title, fontWeight = FontWeight.Bold, fontSize = 20.sp, textAlign = TextAlign.Center)
            subtitle?.let { Text(it, color = Harbor.TextDim, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp).width(260.dp)) }
            Spacer(Modifier.height(18.dp))
            Row(Modifier.graphicsLayer { translationX = shake.value }.semantics { contentDescription = "${pin.length} of 4" },
                horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                repeat(4) { i ->
                    val filled = i < pin.length
                    val sc by animateFloatAsState(if (filled) 1f else 0.7f, spring(dampingRatio = 0.4f), label = "dot")
                    Box(Modifier.size(16.dp).graphicsLayer { scaleX = sc; scaleY = sc }.clip(CircleShape)
                        .background(if (filled) (if (error != null) Harbor.Rose else Harbor.Sky) else Harbor.line(.15f)))
                }
            }
            Text(error.orEmpty(), color = Harbor.Rose, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp).height(18.dp))
            TextButton(onDismiss, Modifier.padding(top = 4.dp)) { Text(stringResource(R.string.cancel), color = Harbor.TextDim) }
        }
    }
    val keypad: @Composable () -> Unit = {
        // A focus group that keeps the D-pad inside the pad (focus never wanders off to nothing).
        Column(Modifier.focusGroup(), horizontalAlignment = Alignment.CenterHorizontally) {
            listOf("123", "456", "789", " 0⌫").forEach { keys ->
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(vertical = if (compact) 5.dp else 6.dp)) {
                    keys.forEach { k ->
                        if (k == ' ') Spacer(Modifier.size(if (compact) 56.dp else 64.dp))
                        else PinKey(k, if (compact) 56.dp else 64.dp, if (k == '1') Modifier.focusRequester(first) else Modifier) {
                            if (k == '⌫') { pin = pin.dropLast(1); error = null } else press(k)
                        }
                    }
                }
            }
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        // The dialog is its own window: ask for focus once it has laid out, so the D-pad starts on "1".
        LaunchedEffect(Unit) { kotlinx.coroutines.delay(120); runCatching { first.requestFocus() } }
        val frame = Modifier.padding(16.dp).clip(RoundedCornerShape(28.dp)).background(Harbor.Surface).padding(horizontal = 28.dp, vertical = 20.dp)
            .onPreviewKeyEvent { e ->
                // Remote / keyboard digits type directly.
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val code = e.nativeKeyEvent.keyCode
                when (code) {
                    in android.view.KeyEvent.KEYCODE_0..android.view.KeyEvent.KEYCODE_9 -> { press('0' + (code - android.view.KeyEvent.KEYCODE_0)); true }
                    in android.view.KeyEvent.KEYCODE_NUMPAD_0..android.view.KeyEvent.KEYCODE_NUMPAD_9 -> { press('0' + (code - android.view.KeyEvent.KEYCODE_NUMPAD_0)); true }
                    android.view.KeyEvent.KEYCODE_DEL -> { pin = pin.dropLast(1); true }
                    else -> false
                }
            }
        if (compact) Row(frame, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(36.dp)) { header(); keypad() }
        else Column(frame, horizontalAlignment = Alignment.CenterHorizontally) { header(); Spacer(Modifier.height(4.dp)); keypad() }
    }
}

@Composable
private fun PinKey(k: Char, size: androidx.compose.ui.unit.Dp, modifier: Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier.size(size).onFocusChanged { focused = it.isFocused }.clip(CircleShape)
            .background(if (focused) Harbor.Violet else Harbor.line(.07f))
            .border(if (focused) 2.dp else 0.dp, Color.White.copy(alpha = if (focused) 0.9f else 0f), CircleShape)
            .clickable(onClick = onClick),
        Alignment.Center,
    ) {
        if (k == '⌫') Icon(Icons.AutoMirrored.Rounded.Backspace, stringResource(R.string.pin_delete), tint = Harbor.Fg)
        else Text(k.toString(), fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = Harbor.Fg)
    }
}

/**
 * Create-a-PIN flow: enter, then confirm. Calls [onCreated] with the new PIN.
 */
@Composable
fun CreatePinDialog(onDismiss: () -> Unit, onCreated: (String) -> Unit) {
    var first by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf<String?>(null) }
    val mismatch = stringResource(R.string.pin_mismatch)
    val hint = stringResource(R.string.pin_create_hint)
    // Keyed so each step starts with empty dots.
    androidx.compose.runtime.key(first == null, note) {
        if (first == null) PinDialog(stringResource(R.string.pin_create), note ?: hint, onDismiss) { first = it; null }
        else PinDialog(stringResource(R.string.pin_confirm), null, onDismiss) { p -> if (p == first) { onCreated(p); null } else { first = null; note = mismatch; null } }
    }
}
