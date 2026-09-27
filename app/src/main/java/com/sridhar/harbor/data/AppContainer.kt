package com.sridhar.harbor.data

import android.content.Context
import com.sridhar.harbor.data.arr.ArrKind
import com.sridhar.harbor.data.arr.ArrRepository
import com.sridhar.harbor.data.jellyfin.JellyfinRepository
import com.sridhar.harbor.data.offline.OfflineRepository
import com.sridhar.harbor.data.qbit.QbitRepository
import com.sridhar.harbor.data.seerr.SeerrRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

val HarborJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    explicitNulls = false
    isLenient = true
    encodeDefaults = true
}

/** Hand-rolled DI: one instance per process, owned by [com.sridhar.harbor.HarborApp]. */
class AppContainer(context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val settings = SettingsStore(context)

    /** Live snapshot of server settings for synchronous URL building in UI code. */
    val config: StateFlow<ServerConfig?> = settings.config.stateIn(scope, SharingStarted.Eagerly, null)

    /** One shared connection pool / dispatcher for every client derived from this one. */
    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .connectionPool(okhttp3.ConnectionPool(8, 5, TimeUnit.MINUTES))
        .retryOnConnectionFailure(true)
        .build()

    /** Low-RAM devices (e.g. 1.5 GB Android TVs) get lighter images and caches. */
    val lowRam: Boolean = context.getSystemService(android.app.ActivityManager::class.java).let { it.isLowRamDevice || it.memoryClass <= 192 }

    /** Image client: Jellyseerr avatars sit behind the same auth as its API. */
    val imageHttp: OkHttpClient = http.newBuilder().addInterceptor { chain ->
        val req = chain.request()
        val cfg = config.value
        val seerrHost = cfg?.seerrUrl?.takeIf { it.isNotBlank() }?.let { runCatching { java.net.URI(it).host }.getOrNull() }
        if (cfg != null && seerrHost != null && req.url.host == seerrHost) {
            val b = req.newBuilder()
            if (cfg.seerrApiKey.isNotBlank()) b.header("X-Api-Key", cfg.seerrApiKey)
            else if (cfg.seerrCookie.isNotBlank()) b.header("Cookie", cfg.seerrCookie)
            chain.proceed(b.build())
        } else chain.proceed(req)
    }.build()

    val jellyfin = JellyfinRepository(settings, http)
    val qbit = QbitRepository(settings, http)
    val music by lazy { com.sridhar.harbor.data.music.NavidromeRepository(settings, http) }
    val musicEngine by lazy { com.sridhar.harbor.music.MusicEngine(context, music, settings, http) }
    val parental by lazy { com.sridhar.harbor.data.parental.ParentalControls(context) }
    val radio by lazy { com.sridhar.harbor.data.music.RadioStations(context, http) }
    /** A stream link shared into the app ("Share → JellyVerse"); Music home offers to save it as a station. */
    val sharedRadioLink = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val admin by lazy { com.sridhar.harbor.data.jellyfin.admin.JellyfinAdminRepository(jellyfin) }
    val seerr = SeerrRepository(settings, http)
    val offline = OfflineRepository(context, settings, jellyfin)
    val sonarr = ArrRepository(ArrKind.Sonarr, settings, http)
    val radarr = ArrRepository(ArrKind.Radarr, settings, http)
    val aria2 = com.sridhar.harbor.data.aria2.Aria2Repository(settings, http)
    val iptv by lazy { com.sridhar.harbor.data.iptv.IptvRepository(context, http) }
    val llm by lazy { com.sridhar.harbor.data.ai.LocalLlm(context) }
    val cast = com.sridhar.harbor.cast.CastController(context)
    // Lazy: the encrypted store hits the Android Keystore (slow) and TV never needs SSH.
    val ssh by lazy { com.sridhar.harbor.data.ssh.SshRepository(context) }
    val homelab by lazy { com.sridhar.harbor.data.ssh.HomelabRepository(ssh) }
    val doctor by lazy { com.sridhar.harbor.data.ai.LibraryDoctor(jellyfin, llm, ssh, context.getSharedPreferences("harbor_doctor", android.content.Context.MODE_PRIVATE)) }
    val assistant by lazy { com.sridhar.harbor.data.ai.Assistant(this) }
    fun arr(kind: ArrKind) = if (kind == ArrKind.Sonarr) sonarr else radarr

    /** Magnet links / .torrent files handed to the app by other apps. */
    val incomingTorrents = MutableSharedFlow<IncomingTorrent>(replay = 1)

    /** Deep-link destinations from shortcuts / notifications ("search", "ai", "downloads", "lab", "watch"). */
    val navRequests = MutableSharedFlow<String>(replay = 1, extraBufferCapacity = 4)
}

sealed interface IncomingTorrent {
    data class Magnet(val uri: String) : IncomingTorrent
    data class File(val name: String, val bytes: ByteArray) : IncomingTorrent
}
