package com.sridhar.harbor.ui.ssh

import com.sridhar.harbor.L10n
import com.sridhar.harbor.R
import android.content.Context
import android.webkit.JavascriptInterface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jcraft.jsch.ChannelShell
import com.jcraft.jsch.Session
import com.sridhar.harbor.data.AppContainer
import com.sridhar.harbor.data.ssh.SshHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.OutputStream

sealed interface TermState {
    data object Connecting : TermState
    data object Connected : TermState
    data class Closed(val reason: String?) : TermState
}

enum class StickyMod { None, Once, Locked }

/** Owns the SSH session so the shell survives rotation; the WebView only renders bytes. */
class TerminalViewModel(private val c: AppContainer, val host: SshHost, private val appContext: Context) : ViewModel() {
    var state by mutableStateOf<TermState>(TermState.Connecting); private set
    var ctrl by mutableStateOf(StickyMod.None)
    var alt by mutableStateOf(StickyMod.None)
    var fontSize by mutableIntStateOf(13)

    private var session: Session? = null
    private var shell: ChannelShell? = null
    private var out: OutputStream? = null
    private val writes = Channel<ByteArray>(Channel.UNLIMITED)
    private var cols = 80; private var rows = 24

    /** Scrollback replay so a recreated WebView (rotation) shows previous output. */
    private val history = ByteArrayOutputStream()
    private val maxHistory = 512 * 1024
    @Volatile var sink: ((ByteArray) -> Unit)? = null

    val snippets = mutableListOf(
        "htop", "docker ps --format 'table {{.Names}}\\t{{.Status}}'", "docker stats --no-stream", "df -h", "free -h",
        "sudo systemctl status docker", "journalctl -xe --no-pager | tail -50", "vcgencmd measure_temp", "sudo apt update && sudo apt list --upgradable",
    )
    private val prefs = appContext.getSharedPreferences("harbor_snippets", Context.MODE_PRIVATE)
    init { prefs.getStringSet("custom", emptySet())?.let { snippets.addAll(0, it.sorted()) } }

    fun addSnippet(s: String) {
        if (s.isBlank()) return
        snippets.add(0, s)
        prefs.edit().putStringSet("custom", (prefs.getStringSet("custom", emptySet()).orEmpty() + s)).apply()
    }

    /** DECCKM: full-screen apps (htop, vim, less) switch arrows to ESC O x. */
    @Volatile private var appCursor = false

    private fun trackModes(b: ByteArray) {
        val t = String(b, Charsets.ISO_8859_1)
        val on = t.lastIndexOf("\u001b[?1h"); val off = t.lastIndexOf("\u001b[?1l")
        if (on >= 0 || off >= 0) appCursor = on > off
    }

    private fun emit(b: ByteArray) {
        trackModes(b)
        synchronized(history) {
            history.write(b)
            if (history.size() > maxHistory) {
                val all = history.toByteArray(); history.reset(); history.write(all, all.size - maxHistory / 2, maxHistory / 2)
            }
        }
        sink?.invoke(b)
    }

    fun replay(): ByteArray = synchronized(history) { history.toByteArray() }

    fun onReady(c0: Int, r0: Int) {
        cols = c0; rows = r0
        if (session == null && state is TermState.Connecting) connect()
        else resize(c0, r0)
    }

    fun reconnect() {
        disconnect()
        state = TermState.Connecting
        emit("\r\n\u001b[35m── reconnecting ──\u001b[0m\r\n".toByteArray())
        connect()
    }

    private fun connect() = viewModelScope.launch(Dispatchers.IO + com.sridhar.harbor.CrashGuard) {
        emit("\u001b[38;5;141mJellyVerse\u001b[0m ▸ connecting to \u001b[1m${host.user}@${host.host}:${host.port}\u001b[0m …\r\n".toByteArray())
        try {
            val s = c.ssh.connect(host)
            val ch = c.ssh.openShell(s, cols, rows)
            val input = ch.inputStream
            out = ch.outputStream
            ch.connect(8_000)
            session = s; shell = ch
            withContext(Dispatchers.Main) { state = TermState.Connected }
            c.ssh.pinnedFingerprint(host)?.let { emit("\u001b[2mhost key $it\u001b[0m\r\n".toByteArray()) }
            launch { for (b in writes) runCatching { out?.write(b); out?.flush() } }
            val buf = ByteArray(32 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                emit(buf.copyOf(n))
            }
            withContext(Dispatchers.Main) { state = TermState.Closed(L10n.s(R.string.session_ended)) }
            emit("\r\n\u001b[33m── session closed ──\u001b[0m\r\n".toByteArray())
        } catch (e: Exception) {
            val msg = e.message ?: e.javaClass.simpleName
            emit("\r\n\u001b[31m✖ $msg\u001b[0m\r\n".toByteArray())
            withContext(Dispatchers.Main) { state = TermState.Closed(msg) }
        }
    }

    fun resize(c0: Int, r0: Int) {
        cols = c0; rows = r0
        runCatching { shell?.setPtySize(c0, r0, 0, 0) }
    }

    /** Text from the soft keyboard (via xterm.js) – applies sticky CTRL / ALT. */
    fun type(data: String) {
        var d = data
        if (ctrl != StickyMod.None && d.length == 1) {
            val ch = d[0]
            d = when {
                ch in 'a'..'z' -> (ch.code - 96).toChar().toString()
                ch in 'A'..'Z' -> (ch.code - 64).toChar().toString()
                ch in "@[\\]^_" -> (ch.code - 64).toChar().toString()
                ch == ' ' -> "\u0000"
                ch == '?' -> "\u007f"
                else -> d
            }
            if (ctrl == StickyMod.Once) ctrl = StickyMod.None
        }
        if (alt != StickyMod.None) { d = "\u001b" + d; if (alt == StickyMod.Once) alt = StickyMod.None }
        send(d)
    }

    /** Special key from the extra-keys bar (already an escape sequence). ALT still applies. */
    fun key(seq: String) {
        var d = seq
        // CTRL + arrows/home/end → xterm modifier form (word jumps in bash/zsh/vim).
        val isCursor = d.length == 3 && d.startsWith("\u001b[") && d[2] in "ABCDHF"
        if (ctrl != StickyMod.None && isCursor) d = "\u001b[1;5" + d[2]
        else if (appCursor && isCursor) d = "\u001bO" + d[2]
        if (alt != StickyMod.None) { d = "\u001b" + d; if (alt == StickyMod.Once) alt = StickyMod.None }
        if (ctrl == StickyMod.Once) ctrl = StickyMod.None
        send(d)
    }

    fun send(s: String) { if (state == TermState.Connected) writes.trySend(s.toByteArray(Charsets.UTF_8)) }

    fun cycle(m: StickyMod) = when (m) { StickyMod.None -> StickyMod.Once; StickyMod.Once -> StickyMod.Locked; StickyMod.Locked -> StickyMod.None }

    fun disconnect() {
        runCatching { shell?.disconnect() }; runCatching { session?.disconnect() }
        shell = null; session = null; out = null
    }

    override fun onCleared() = disconnect()
}

/** JS → Kotlin bridge for xterm.js. Methods are called on a WebView binder thread. */
class TerminalBridge(
    private val onReadyCb: (Int, Int) -> Unit,
    private val onInputCb: (String) -> Unit,
    private val onResizeCb: (Int, Int) -> Unit,
    private val onLinkCb: (String) -> Unit,
) {
    @JavascriptInterface fun onReady(cols: Int, rows: Int) = onReadyCb(cols, rows)
    @JavascriptInterface fun onInput(data: String) = onInputCb(data)
    @JavascriptInterface fun onResize(cols: Int, rows: Int) = onResizeCb(cols, rows)
    @JavascriptInterface fun openLink(url: String) = onLinkCb(url)
}
