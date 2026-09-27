package com.sridhar.harbor.ui.ssh

import com.sridhar.harbor.R
import androidx.compose.ui.res.stringResource
import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.util.Base64
import android.view.inputmethod.InputMethodManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.TextDecrease
import androidx.compose.material.icons.rounded.TextIncrease
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sridhar.harbor.data.ssh.SshHost
import com.sridhar.harbor.ui.components.LocalContainer
import com.sridhar.harbor.ui.components.MessageState
import com.sridhar.harbor.ui.theme.Harbor
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.sridhar.harbor.ui.ssh.StickyMod as Mod

private const val ESC = "\u001b"

private data class XKey(val label: String, val seq: String? = null, val repeat: Boolean = false, val mod: String? = null)

// Two rows, Termius/Termux style.
private val row1 = listOf(
    XKey("ESC", ESC), XKey("TAB", "\t"), XKey("CTRL", mod = "ctrl"), XKey("ALT", mod = "alt"),
    XKey("↑", "$ESC[A", repeat = true), XKey("HOME", "$ESC[H"), XKey("END", "$ESC[F"), XKey("PGUP", "$ESC[5~"),
)
private val row2 = listOf(
    XKey("/", "/"), XKey("-", "-"), XKey("|", "|"), XKey("~", "~"),
    XKey("←", "$ESC[D", repeat = true), XKey("↓", "$ESC[B", repeat = true), XKey("→", "$ESC[C", repeat = true), XKey("PGDN", "$ESC[6~"),
)
private val quick = listOf("^C" to "\u0003", "^D" to "\u0004", "^Z" to "\u001a", "^L" to "\u000c", "^R" to "\u0012", "^A" to "\u0001", "^E" to "\u0005", "^W" to "\u0017", ":wq" to ":wq\r", "q" to "q")

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun TerminalScreen(hostId: String, onBack: () -> Unit) {
    val container = LocalContainer.current
    val ctx = LocalContext.current
    val host: SshHost = container.ssh.hosts.value.firstOrNull { it.id == hostId } ?: run {
        MessageState(stringResource(R.string.host_not_found), null, onRetry = onBack, actionLabel = stringResource(R.string.back)); return
    }
    val vm = viewModel(key = "term-$hostId") { TerminalViewModel(container, host, ctx.applicationContext) }
    var web by remember { mutableStateOf<WebView?>(null) }
    var snippets by remember { mutableStateOf(false) }

    fun showKeyboard() {
        web?.let { w ->
            w.evaluateJavascript("focusTerm()", null); w.requestFocus()
            ctx.getSystemService(InputMethodManager::class.java).showSoftInput(w, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    DisposableEffect(Unit) { onDispose { vm.sink = null } }

    Column(Modifier.fillMaxSize().background(Harbor.Ink).statusBarsPadding().navigationBarsPadding().imePadding()) {
        // Top bar
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) }
            val dot by animateColorAsState(when (vm.state) { TermState.Connected -> Harbor.Mint; TermState.Connecting -> Harbor.Amber; else -> Harbor.Rose }, label = "dot")
            Box(Modifier.size(9.dp).clip(CircleShape).background(dot))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(host.name, fontWeight = FontWeight.Bold, maxLines = 1)
                Text("${host.user}@${host.host}", fontSize = 11.sp, color = Harbor.TextDim, maxLines = 1, fontFamily = FontFamily.Monospace)
            }
            IconButton({ vm.fontSize = (vm.fontSize - 1).coerceAtLeast(8); web?.evaluateJavascript("setFontSize(${vm.fontSize})", null) }) { Icon(Icons.Rounded.TextDecrease, stringResource(R.string.smaller)) }
            IconButton({ vm.fontSize = (vm.fontSize + 1).coerceAtMost(24); web?.evaluateJavascript("setFontSize(${vm.fontSize})", null) }) { Icon(Icons.Rounded.TextIncrease, stringResource(R.string.bigger)) }
            IconButton({ snippets = true }) { Icon(Icons.Rounded.Bolt, stringResource(R.string.snippets), tint = Harbor.Amber) }
            if (vm.state is TermState.Closed) IconButton({ vm.reconnect() }) { Icon(Icons.Rounded.Refresh, stringResource(R.string.reconnect), tint = Harbor.VioletSoft) }
        }

        // Terminal
        AndroidView(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            factory = { c ->
                TermWebView(c).apply {
                    setBackgroundColor(android.graphics.Color.parseColor("#0B0B12"))
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = true   // only our bundled assets are loaded
                    isVerticalScrollBarEnabled = false
                    addOnLayoutChangeListener { v, _, top, _, bottom, _, oldTop, _, oldBottom -> if (bottom - top != oldBottom - oldTop) (v as WebView).evaluateJavascript("window.refit && refit()", null) }
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?) = true
                    }
                    val self = this
                    addJavascriptInterface(TerminalBridge(
                        onReadyCb = { cols, rows ->
                            self.post {
                                self.evaluateJavascript("setFontSize(${vm.fontSize}); refit()", null)
                                vm.replay().takeIf { it.isNotEmpty() }?.let { b -> self.evaluateJavascript("writeB64('${Base64.encodeToString(b, Base64.NO_WRAP)}')", null) }
                                vm.sink = { bytes -> self.post { self.evaluateJavascript("writeB64('${Base64.encodeToString(bytes, Base64.NO_WRAP)}')", null) } }
                                vm.onReady(cols, rows)
                            }
                        },
                        onInputCb = { d -> vm.type(d) },
                        onResizeCb = { cols, rows -> vm.resize(cols, rows) },
                        onLinkCb = { url -> if (url.startsWith("http")) c.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) },
                    ), "Android")
                    loadUrl("file:///android_asset/term/index.html")
                    web = this
                }
            },
        )

        // Extra keys
        ExtraKeys(vm, onKeyboard = ::showKeyboard)
    }

    if (snippets) SnippetSheet(vm, onDismiss = { snippets = false }) { cmd -> vm.send(cmd + "\r"); snippets = false }
}

@Composable
private fun ExtraKeys(vm: TerminalViewModel, onKeyboard: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    Column(Modifier.fillMaxWidth().background(Harbor.Surface).padding(horizontal = 4.dp, vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 2.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.clip(RoundedCornerShape(10.dp)).background(Harbor.accentH).clickable { onKeyboard() }.padding(horizontal = 10.dp, vertical = 6.dp)) {
                Icon(Icons.Rounded.Keyboard, stringResource(R.string.keyboard), tint = Color.White, modifier = Modifier.size(18.dp))
            }
            quick.forEach { (label, seq) ->
                Text(label, fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Harbor.Coral,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Harbor.Coral.copy(alpha = .12f))
                        .clickable { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); vm.send(seq) }.padding(horizontal = 10.dp, vertical = 6.dp))
            }
        }
        listOf(row1, row2).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { k -> KeyCap(k, vm, Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun KeyCap(k: XKey, vm: TerminalViewModel, modifier: Modifier) {
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val modState = when (k.mod) { "ctrl" -> vm.ctrl; "alt" -> vm.alt; else -> Mod.None }
    val bg by animateColorAsState(when (modState) { Mod.Once -> Harbor.Violet.copy(alpha = .55f); Mod.Locked -> Harbor.Violet; else -> Color.White.copy(alpha = .06f) }, label = "key")
    val isArrow = k.label in setOf("↑", "↓", "←", "→")
    Box(
        modifier.height(40.dp).clip(RoundedCornerShape(10.dp)).background(bg)
            .border(1.dp, if (modState == Mod.Locked) Harbor.VioletSoft else Color.Transparent, RoundedCornerShape(10.dp))
            .pointerInput(k, modState) {
                awaitEachGesture {
                    awaitFirstDown()
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    var job: Job? = null
                    when {
                        k.mod == "ctrl" -> vm.ctrl = vm.cycle(vm.ctrl)
                        k.mod == "alt" -> vm.alt = vm.cycle(vm.alt)
                        else -> {
                            vm.key(k.seq!!)
                            // Hold to auto-repeat (arrows): 400 ms delay, then ~16 keys/s.
                            if (k.repeat) job = scope.launch(com.sridhar.harbor.CrashGuard) { delay(400); while (true) { vm.key(k.seq); delay(60) } }
                        }
                    }
                    waitForUpOrCancellation()
                    job?.cancel()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            k.label + if (modState == Mod.Locked) "•" else "",
            fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
            fontSize = if (isArrow) 18.sp else if (k.label.length > 3) 10.sp else 12.sp,
            color = if (modState != Mod.None) Color.White else Color(0xFFD4D4E0),
        )
    }
}

@Composable
private fun SnippetSheet(vm: TerminalViewModel, onDismiss: () -> Unit, onRun: (String) -> Unit) {
    var custom by remember { mutableStateOf("") }
    var list by remember { mutableStateOf(vm.snippets.toList()) }
    ModalBottomSheet(onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Harbor.Surface) {
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding()) {
            Text(stringResource(R.string.snippets), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.tap_to_run_in_the_terminal), color = Harbor.TextDim, fontSize = 12.sp)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 10.dp)) {
                OutlinedTextField(custom, { custom = it }, Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(14.dp),
                    placeholder = { Text(stringResource(R.string.save_a_new_command), fontFamily = FontFamily.Monospace) })
                IconButton({ vm.addSnippet(custom.trim()); list = vm.snippets.toList(); custom = "" }) { Icon(Icons.Rounded.Add, stringResource(R.string.save), tint = Harbor.VioletSoft) }
            }
            LazyColumn(contentPadding = PaddingValues(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(list) { s ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = .35f)).clickable { onRun(s) }.padding(12.dp)) {
                        Text("$ ", color = Harbor.Mint, fontFamily = FontFamily.Monospace)
                        Text(s, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

/**
 * Tells the soft keyboard this is a raw terminal: no suggestions, no composing, no autocorrect. Every key is
 * committed at once, which stops Samsung Keyboard / Gboard from duplicating or rewriting what you type.
 */
private class TermWebView(c: android.content.Context) : WebView(c) {
    override fun onCreateInputConnection(outAttrs: android.view.inputmethod.EditorInfo): android.view.inputmethod.InputConnection? {
        val ic = super.onCreateInputConnection(outAttrs)
        outAttrs.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD or
            android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        outAttrs.imeOptions = outAttrs.imeOptions or android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI or
            android.view.inputmethod.EditorInfo.IME_FLAG_NO_FULLSCREEN or android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
        return ic
    }
}
