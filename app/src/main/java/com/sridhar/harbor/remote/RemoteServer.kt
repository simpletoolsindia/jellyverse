package com.sridhar.harbor.remote

import android.app.Activity
import android.app.Application
import android.content.Context
import android.media.AudioManager
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import java.util.concurrent.CopyOnWriteArraySet
import kotlin.concurrent.thread

/** Wire format shared by the TV server and the phone client: one JSON object per line over TCP. */
object RemoteProtocol {
    const val SERVICE_TYPE = "_jellyverse._tcp."
    const val PORT = 47110
    val keys = mapOf(
        "UP" to KeyEvent.KEYCODE_DPAD_UP, "DOWN" to KeyEvent.KEYCODE_DPAD_DOWN, "LEFT" to KeyEvent.KEYCODE_DPAD_LEFT,
        "RIGHT" to KeyEvent.KEYCODE_DPAD_RIGHT, "OK" to KeyEvent.KEYCODE_DPAD_CENTER, "BACK" to KeyEvent.KEYCODE_BACK,
        "PLAY_PAUSE" to KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, "REWIND" to KeyEvent.KEYCODE_MEDIA_REWIND,
        "FAST_FORWARD" to KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, "NEXT" to KeyEvent.KEYCODE_MEDIA_NEXT,
        "PREVIOUS" to KeyEvent.KEYCODE_MEDIA_PREVIOUS, "MENU" to KeyEvent.KEYCODE_MENU, "CAPTIONS" to KeyEvent.KEYCODE_CAPTIONS,
        "CH_UP" to KeyEvent.KEYCODE_CHANNEL_UP, "CH_DOWN" to KeyEvent.KEYCODE_CHANNEL_DOWN, "DEL" to KeyEvent.KEYCODE_DEL,
    )
}

/**
 * Runs on the TV: lets a phone on the same Wi-Fi drive the app like a remote and type into text fields.
 * Found via mDNS (NSD); a phone must first pair with the 4-digit code the TV shows. Keys are injected only into
 * JellyVerse's own windows, so it can't control anything else on the TV.
 */
object RemoteServer {
    private const val TAG = "RemoteServer"
    private val main = Handler(Looper.getMainLooper())
    private val clients = CopyOnWriteArraySet<Client>()
    @Volatile private var started = false
    @Volatile private var current: Activity? = null
    private lateinit var app: Application

    /** Code shown on screen while a phone is pairing (null = not pairing). */
    private val _pairingCode = MutableStateFlow<String?>(null)
    val pairingCode: StateFlow<String?> = _pairingCode.asStateFlow()
    /** Remote phones currently connected (for a small "📱 Remote connected" hint). */
    private val _connected = MutableStateFlow(0)
    val connected: StateFlow<Int> = _connected.asStateFlow()
    /** Paired a moment ago – drives the "Paired with …" toast. */
    val justPaired = MutableStateFlow<String?>(null)

    fun localIp(): String? = runCatching {
        NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }.firstOrNull { it is Inet4Address && it.isSiteLocalAddress }?.hostAddress
    }.getOrNull()

    fun start(application: Application) {
        if (started) return
        started = true; app = application
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(a: Activity) { current = a }
            override fun onActivityPaused(a: Activity) { if (current === a) current = null }
            override fun onActivityCreated(a: Activity, b: Bundle?) {}
            override fun onActivityStarted(a: Activity) {}
            override fun onActivityStopped(a: Activity) {}
            override fun onActivitySaveInstanceState(a: Activity, b: Bundle) {}
            override fun onActivityDestroyed(a: Activity) {}
        })
        thread(name = "jv-remote", isDaemon = true) {
            val server = runCatching { ServerSocket(RemoteProtocol.PORT) }.getOrElse { runCatching { ServerSocket(0) }.getOrNull() } ?: return@thread
            advertise(server.localPort)
            while (true) {
                val s = runCatching { server.accept() }.getOrNull() ?: continue
                // Only phones on the local network.
                if (!s.inetAddress.isSiteLocalAddress && !s.inetAddress.isLoopbackAddress) { s.close(); continue }
                thread(name = "jv-remote-client", isDaemon = true) { Client(s).run() }
            }
        }
    }

    private fun prefs() = app.getSharedPreferences("harbor_remote", Context.MODE_PRIVATE)
    private fun tvId(): String = prefs().getString("id", null) ?: UUID.randomUUID().toString().also { prefs().edit().putString("id", it).apply() }
    private fun tokens(): Set<String> = prefs().getStringSet("tokens", emptySet()).orEmpty()

    private fun advertise(port: Int) {
        val nsd = app.getSystemService(NsdManager::class.java) ?: return
        val info = NsdServiceInfo().apply {
            serviceName = "JellyVerse TV · ${android.os.Build.MODEL}"
            serviceType = RemoteProtocol.SERVICE_TYPE
            this.port = port
            setAttribute("id", tvId())
        }
        runCatching {
            nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(i: NsdServiceInfo) { Log.i(TAG, "advertised ${i.serviceName} on $port") }
                override fun onRegistrationFailed(i: NsdServiceInfo, e: Int) { Log.w(TAG, "nsd register failed $e") }
                override fun onServiceUnregistered(i: NsdServiceInfo) {}
                override fun onUnregistrationFailed(i: NsdServiceInfo, e: Int) {}
            })
        }
    }

    /** Called when a TV text field gains focus – connected phones pop their keyboard. */
    @Volatile private var lastField: Pair<Boolean, String?> = false to null

    fun fieldFocused(focused: Boolean, label: String? = null) {
        if (!started) return
        if (focused) { lastField = true to label; broadcastField() }
        else {
            // Moving field → field fires "lost" and "gained" in either order; only report "no field" if it sticks.
            lastField = false to null
            main.postDelayed({ if (!lastField.first) broadcastField() }, 250)
        }
    }

    private fun broadcastField() {
        val (focused, label) = lastField
        val msg = buildJsonObject { put("t", "field"); put("focused", focused); label?.let { put("label", it) } }
        clients.filter { it.paired }.forEach { it.send(msg) }
    }

    private class Client(private val socket: Socket) {
        @Volatile var paired = false
        private val out = PrintWriter(socket.getOutputStream().bufferedWriter(), true)
        private var code: String? = null
        private var attempts = 0

        fun send(o: JsonObject) = runCatching { synchronized(out) { out.println(o.toString()) } }

        fun run() {
            clients += this
            try {
                val input = BufferedReader(InputStreamReader(socket.getInputStream()))
                send(buildJsonObject { put("t", "hi"); put("id", tvId()); put("name", "JellyVerse TV · ${android.os.Build.MODEL}") })
                while (true) {
                    val line = input.readLine() ?: break
                    val msg = runCatching { com.sridhar.harbor.data.HarborJson.parseToJsonElement(line).jsonObject }.getOrNull() ?: continue
                    handle(msg)
                }
            } catch (_: Exception) {
            } finally {
                clients -= this
                if (paired) _connected.value = clients.count { it.paired }
                if (code != null && _pairingCode.value == code) _pairingCode.value = null
                runCatching { socket.close() }
            }
        }

        /** A field may already be focused when the phone connects – tell it straight away. */
        fun sendField() { val (f, l) = lastField; if (f) send(buildJsonObject { put("t", "field"); put("focused", true); l?.let { put("label", it) } }) }

        private fun str(m: JsonObject, k: String) = m[k]?.jsonPrimitive?.content

        private fun handle(m: JsonObject) {
            when (str(m, "t")) {
                "hello" -> {
                    paired = str(m, "token") in tokens()
                    send(buildJsonObject { put("t", if (paired) "ready" else "unpaired") })
                    if (paired) { _connected.value = clients.count { it.paired }; sendField() }
                }
                "pairRequest" -> {
                    code = (1000..9999).random().toString(); attempts = 0
                    _pairingCode.value = code
                    send(buildJsonObject { put("t", "enterCode") })
                }
                "pair" -> {
                    if (code != null && str(m, "code") == code) {
                        val token = UUID.randomUUID().toString()
                        prefs().edit().putStringSet("tokens", tokens() + token).apply()
                        paired = true; _pairingCode.value = null; code = null
                        _connected.value = clients.count { it.paired }
                        justPaired.value = str(m, "phone") ?: "phone"
                        send(buildJsonObject { put("t", "paired"); put("token", token) })
                        sendField()
                    } else {
                        // A few tries, then a fresh code so it can't be guessed.
                        if (++attempts >= 5) { code = (1000..9999).random().toString(); _pairingCode.value = code; attempts = 0 }
                        send(buildJsonObject { put("t", "badCode") })
                    }
                }
                else -> if (paired) command(m) else send(buildJsonObject { put("t", "unpaired") })
            }
        }

        private fun command(m: JsonObject) {
            when (str(m, "t")) {
                "key" -> RemoteProtocol.keys[str(m, "k")]?.let { injectKey(it, m["long"]?.jsonPrimitive?.content == "true") }
                "text" -> str(m, "s")?.let { injectText(it) }
                "vol" -> {
                    val am = app.getSystemService(AudioManager::class.java)
                    val dir = when (str(m, "d")) { "up" -> AudioManager.ADJUST_RAISE; "down" -> AudioManager.ADJUST_LOWER; else -> AudioManager.ADJUST_TOGGLE_MUTE }
                    am?.adjustStreamVolume(AudioManager.STREAM_MUSIC, dir, AudioManager.FLAG_SHOW_UI)
                }
                "home" -> main.post { com.sridhar.harbor.HarborApp.instance?.container?.navRequests?.tryEmit("home") }
                "ping" -> send(buildJsonObject { put("t", "pong") })
            }
        }
    }

    /** Injects into JellyVerse's own focused window (dialogs included); needs no special permission for our own UI. */
    private fun injectKey(code: Int, long: Boolean) {
        val inst = android.app.Instrumentation()
        val ok = runCatching {
            if (long) {
                val t = android.os.SystemClock.uptimeMillis()
                inst.sendKeySync(KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0))
                inst.sendKeySync(KeyEvent(t, t + 600, KeyEvent.ACTION_DOWN, code, 1, 0, -1, 0, KeyEvent.FLAG_LONG_PRESS))
                inst.sendKeySync(KeyEvent(t, t + 650, KeyEvent.ACTION_UP, code, 0))
            } else inst.sendKeyDownUpSync(code)
        }.isSuccess
        // Fallback (e.g. app in the background): dispatch straight to the resumed activity.
        if (!ok) main.post { current?.let { a -> a.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code)); a.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code)) } }
    }

    private fun injectText(text: String) {
        val ok = runCatching { android.app.Instrumentation().sendStringSync(text) }.isSuccess
        if (!ok) main.post {
            val a = current ?: return@post
            android.view.KeyCharacterMap.load(android.view.KeyCharacterMap.VIRTUAL_KEYBOARD).getEvents(text.toCharArray())?.forEach { a.dispatchKeyEvent(it) }
        }
    }
}
