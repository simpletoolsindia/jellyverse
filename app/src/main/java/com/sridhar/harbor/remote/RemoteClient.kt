package com.sridhar.harbor.remote

import kotlinx.coroutines.flow.first

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.Socket

data class TvDevice(val name: String, val host: String, val port: Int, val id: String? = null)

sealed interface RemoteState {
    data object Idle : RemoteState
    data class Connecting(val tv: TvDevice) : RemoteState
    /** The TV shows a code; the user types it here. */
    data class NeedCode(val tv: TvDevice, val wrong: Boolean = false) : RemoteState
    data class Connected(val tv: TvDevice) : RemoteState
    data class Failed(val tv: TvDevice, val reason: String) : RemoteState
}

/** Phone side of the TV remote: finds JellyVerse TVs on the Wi-Fi, pairs once, then sends keys and text. */
class RemoteClient(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + com.sridhar.harbor.CrashGuard)
    private val prefs = context.getSharedPreferences("harbor_remote_phone", Context.MODE_PRIVATE)

    private val _tvs = MutableStateFlow<List<TvDevice>>(emptyList())
    val tvs: StateFlow<List<TvDevice>> = _tvs.asStateFlow()
    private val _state = MutableStateFlow<RemoteState>(RemoteState.Idle)
    val state: StateFlow<RemoteState> = _state.asStateFlow()
    /** One-scan TV setup progress (null = not setting a TV up). */
    enum class TvSetup { Working, Done, Failed }
    val tvSetup = MutableStateFlow<TvSetup?>(null)
    @Volatile private var qrKey: String? = null
    @Volatile private var tvNeedsSignIn = false

    /** Connect from a scanned QR code (jellyverse://tv?h=…&p=…&k=…&n=…). Returns false if it isn't one. */
    fun connectFromQr(raw: String): Boolean {
        val q = parseQr(raw) ?: return false
        qrKey = q.second; tvSetup.value = null
        connect(q.first)
        return true
    }

    /** The TV focused a text field (label) – the remote opens its keyboard. null = no field focused. */
    val tvField = MutableStateFlow<String?>(null)

    private var socket: Socket? = null
    private var out: PrintWriter? = null
    private val outbox = Channel<JsonObject>(Channel.UNLIMITED)

    // One writer for the whole client, always writing to the current connection.
    init { scope.launch { for (m in outbox) runCatching { out?.println(m.toString()) } } }

    // ---------------- discovery ----------------
    private var nsd: NsdManager? = null
    private var discovery: NsdManager.DiscoveryListener? = null
    private val resolveQueue = ArrayDeque<NsdServiceInfo>()
    private var resolving = false

    fun lastTv(): TvDevice? = prefs.getString("last_host", null)?.let { TvDevice(prefs.getString("last_name", it)!!, it, prefs.getInt("last_port", RemoteProtocol.PORT), prefs.getString("last_id", null)) }

    fun startDiscovery() {
        if (discovery != null) return
        val m = context.getSystemService(NsdManager::class.java) ?: return
        nsd = m
        val l = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(t: String) {}
            override fun onDiscoveryStopped(t: String) {}
            override fun onStartDiscoveryFailed(t: String, e: Int) { discovery = null }
            override fun onStopDiscoveryFailed(t: String, e: Int) {}
            override fun onServiceFound(info: NsdServiceInfo) { synchronized(resolveQueue) { resolveQueue.addLast(info) }; resolveNext() }
            override fun onServiceLost(info: NsdServiceInfo) { _tvs.value = _tvs.value.filterNot { it.name == info.serviceName } }
        }
        discovery = l
        runCatching { m.discoverServices(RemoteProtocol.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, l) }.onFailure { discovery = null }
    }

    /**
     * Fallback when mDNS is blocked (common on home routers): probe this phone's /24 subnet for the remote port.
     * ~254 short TCP connects in parallel batches; finishes in a few seconds and only talks to the local network.
     */
    fun scanSubnet() = scope.launch {
        val me = RemoteServer.localIp() ?: return@launch
        val prefix = me.substringBeforeLast('.')
        kotlinx.coroutines.coroutineScope {
            (1..254).map { "$prefix.$it" }.filter { it != me }.chunked(48).forEach { batch ->
                batch.map { host ->
                    async {
                        val ok = runCatching { Socket().use { it.connect(InetSocketAddress(host, RemoteProtocol.PORT), 350); true } }.getOrDefault(false)
                        if (ok) {
                            val name = runCatching { hello(host) }.getOrNull() ?: "JellyVerse TV · $host"
                            val tv = TvDevice(name, host, RemoteProtocol.PORT)
                            _tvs.value = (_tvs.value.filterNot { it.host == host } + tv).sortedBy { it.name }
                        }
                    }
                }.awaitAll()
            }
        }
    }

    /** Reads the TV's greeting to show its real name. */
    private fun hello(host: String): String? = Socket().use { s ->
        s.connect(InetSocketAddress(host, RemoteProtocol.PORT), 800); s.soTimeout = 800
        val line = BufferedReader(InputStreamReader(s.getInputStream())).readLine() ?: return null
        com.sridhar.harbor.data.HarborJson.parseToJsonElement(line).jsonObject["name"]?.jsonPrimitive?.content
    }

    fun stopDiscovery() { discovery?.let { d -> runCatching { nsd?.stopServiceDiscovery(d) } }; discovery = null }

    @Suppress("DEPRECATION")
    private fun resolveNext() {
        val next = synchronized(resolveQueue) { if (resolving) return; resolveQueue.removeFirstOrNull()?.also { resolving = true } } ?: return
        runCatching {
            nsd?.resolveService(next, object : NsdManager.ResolveListener {
                override fun onResolveFailed(i: NsdServiceInfo, e: Int) { resolving = false; resolveNext() }
                override fun onServiceResolved(i: NsdServiceInfo) {
                    val host = i.host?.hostAddress
                    if (host != null) {
                        val tv = TvDevice(i.serviceName, host, i.port, i.attributes["id"]?.let { String(it) })
                        _tvs.value = (_tvs.value.filterNot { it.name == tv.name } + tv).sortedBy { it.name }
                    }
                    resolving = false; resolveNext()
                }
            })
        }.onFailure { resolving = false }
    }

    // ---------------- connection ----------------
    private fun tokenKey(tv: TvDevice) = "token:" + (tv.id ?: tv.host)

    fun connect(tv: TvDevice) = scope.launch {
        disconnectNow()
        _state.value = RemoteState.Connecting(tv)
        val s = runCatching { Socket().apply { connect(InetSocketAddress(tv.host, tv.port), 4000); tcpNoDelay = true } }.getOrElse {
            _state.value = RemoteState.Failed(tv, it.message ?: "unreachable"); return@launch
        }
        socket = s
        out = PrintWriter(s.getOutputStream().bufferedWriter(), true)
        val input = BufferedReader(InputStreamReader(s.getInputStream()))
        var known = tv
        try {
            while (true) {
                val line = input.readLine() ?: break
                val m = runCatching { com.sridhar.harbor.data.HarborJson.parseToJsonElement(line).jsonObject }.getOrNull() ?: continue
                fun str(k: String) = m[k]?.jsonPrimitive?.content
                when (str("t")) {
                    "hi" -> {
                        tvNeedsSignIn = str("signedIn") == "false"
                        known = tv.copy(id = str("id") ?: tv.id, name = str("name") ?: tv.name)
                        send(buildJsonObject { put("t", "hello"); prefs.getString(tokenKey(known), null)?.let { put("token", it) } })
                    }
                    "ready" -> { remember(known); _state.value = RemoteState.Connected(known); offerSetup() }
                    // Scanned QR: pair with its one-time key – no code to type.
                    "unpaired" -> qrKey?.let { k -> send(buildJsonObject { put("t", "pair"); put("qr", k); put("phone", android.os.Build.MODEL) }) }
                        ?: send(buildJsonObject { put("t", "pairRequest") })
                    "enterCode" -> _state.value = RemoteState.NeedCode(known)
                    "badCode" -> _state.value = RemoteState.NeedCode(known, wrong = true)
                    "paired" -> {
                        prefs.edit().putString(tokenKey(known), str("token")).apply()
                        remember(known); _state.value = RemoteState.Connected(known); qrKey = null; offerSetup()
                    }
                    "qc" -> str("code")?.let { code ->
                        if (com.sridhar.harbor.BuildConfig.DEBUG) android.util.Log.d("RemoteClient", "qc received")
                        // The TV is showing a Quick Connect code: approve it with this phone's Jellyfin account.
                        scope.launch {
                            runCatching { com.sridhar.harbor.HarborApp.instance!!.container.jellyfin.quickConnectAuthorize(code) }
                                .onFailure { if (com.sridhar.harbor.BuildConfig.DEBUG) android.util.Log.d("RemoteClient", "qc authorize failed: ${it.message}"); tvSetup.value = TvSetup.Failed }
                        }
                    }
                    "signedIn" -> tvSetup.value = TvSetup.Done
                    "field" -> tvField.value = if (str("focused") == "true") (str("label") ?: "") else null
                }
            }
        } catch (_: Exception) {}
        // A newer connection may have replaced this one – only report our own drop.
        if (socket === s && _state.value !is RemoteState.Idle) _state.value = RemoteState.Failed(known, "disconnected")
    }

    /** New TV that isn't signed in + this phone has Jellyfin → hand over the server so the TV can sign in. */
    private fun offerSetup() {
        if (!tvNeedsSignIn) return
        val cfg = com.sridhar.harbor.HarborApp.instance?.container?.config?.value ?: return
        if (!cfg.jellyfinReady) return
        tvSetup.value = TvSetup.Working
        send(buildJsonObject { put("t", "setupJellyfin"); put("url", cfg.jellyfinUrl) })
    }

    private fun remember(tv: TvDevice) = prefs.edit().putString("last_host", tv.host).putInt("last_port", tv.port)
        .putString("last_name", tv.name).putString("last_id", tv.id).apply()

    fun submitCode(code: String) = send(buildJsonObject { put("t", "pair"); put("code", code); put("phone", android.os.Build.MODEL) })

    private fun send(m: JsonObject) { outbox.trySend(m) }

    fun key(k: String, long: Boolean = false) = send(buildJsonObject { put("t", "key"); put("k", k); if (long) put("long", true) })
    fun text(s: String) { if (s.isNotEmpty()) send(buildJsonObject { put("t", "text"); put("s", s) }) }
    fun volume(dir: String) = send(buildJsonObject { put("t", "vol"); put("d", dir) })
    fun home() = send(buildJsonObject { put("t", "home") })

    /** A JellyVerse TV this phone has paired with before (for "Play on TV"). */
    val pairedTv: TvDevice? get() = lastTv()?.takeIf { prefs.contains(tokenKey(it)) }

    /**
     * "Play on TV": open [itemId] on the paired JellyVerse TV app at [posMs]. Connects first if needed.
     * Returns the TV's name, or null if it couldn't be reached.
     */
    suspend fun playOnTv(itemId: String, posMs: Long): String? {
        val tv = (state.value as? RemoteState.Connected)?.tv ?: pairedTv ?: return null
        if (state.value !is RemoteState.Connected) {
            connect(tv)
            kotlinx.coroutines.withTimeoutOrNull(6000) { state.first { it is RemoteState.Connected || it is RemoteState.Failed || it is RemoteState.NeedCode } }
            if (state.value !is RemoteState.Connected) return null
        }
        send(buildJsonObject { put("t", "play"); put("id", itemId); put("pos", posMs) })
        return (state.value as? RemoteState.Connected)?.tv?.name ?: tv.name
    }

    /** Mirrors a phone text box into the TV field: deletes what changed, then types the rest. */
    fun mirror(old: String, new: String) {
        val common = old.commonPrefixWith(new).length
        repeat(old.length - common) { key("DEL") }
        text(new.substring(common))
    }

    fun disconnect() = scope.launch { disconnectNow(); _state.value = RemoteState.Idle }

    private suspend fun disconnectNow() = withContext(Dispatchers.IO) {
        runCatching { socket?.close() }; socket = null; out = null; tvField.value = null
    }

    companion object {
        /** jellyverse://tv?h=192.168.1.50&p=47110&k=KEY&n=Name → (TV, key). */
        fun parseQr(raw: String): Pair<TvDevice, String>? = runCatching {
            val u = android.net.Uri.parse(raw.trim())
            if (u.scheme != "jellyverse" || u.host != "tv") return null
            val h = u.getQueryParameter("h") ?: return null
            val k = u.getQueryParameter("k") ?: return null
            TvDevice(u.getQueryParameter("n") ?: "JellyVerse TV", h, u.getQueryParameter("p")?.toIntOrNull() ?: RemoteProtocol.PORT) to k
        }.getOrNull()
    }
}
