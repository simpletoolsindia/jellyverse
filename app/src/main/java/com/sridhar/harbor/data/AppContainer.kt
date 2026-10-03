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

    /** 2 GB-class hardware (most budget TV boxes): trailer previews default off so playback gets the whole device. */
    val lowEnd: Boolean = lowRam || context.getSystemService(android.app.ActivityManager::class.java).let { am ->
        android.app.ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }.totalMem < 2_600L * 1024 * 1024
    } || Runtime.getRuntime().availableProcessors() <= 2

    private val uiPrefs = context.getSharedPreferences("harbor_ui", Context.MODE_PRIVATE)
    /** Animations: "auto" (reduced on low-end devices or when Android's animations are off), "full" or "reduced". */
    val motionMode = kotlinx.coroutines.flow.MutableStateFlow(uiPrefs.getString("motion", "auto") ?: "auto")
    fun setMotionMode(mode: String) { uiPrefs.edit().putString("motion", mode).apply(); motionMode.value = mode }
    private val systemAnimsOff = runCatching {
        android.provider.Settings.Global.getFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }.getOrDefault(false)
    fun reducedMotion(mode: String = motionMode.value): Boolean = when (mode) { "full" -> false; "reduced" -> true; else -> lowEnd || systemAnimsOff }
    /** Trailer previews: "auto" (on unless [lowEnd]), "on" or "off". */
    val previewMode = kotlinx.coroutines.flow.MutableStateFlow(uiPrefs.getString("previews", "auto") ?: "auto")
    fun setPreviewMode(mode: String) { uiPrefs.edit().putString("previews", mode).apply(); previewMode.value = mode }
    fun previewsOn(mode: String = previewMode.value): Boolean = when (mode) { "on" -> true; "off" -> false; else -> !lowEnd }

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
    val offlineMusic by lazy { com.sridhar.harbor.data.music.OfflineMusic(context, settings, music, downloader, http) }
    val musicEngine by lazy { com.sridhar.harbor.music.MusicEngine(context, music, settings, http) { offlineMusic } }
    val updater by lazy { com.sridhar.harbor.update.Updater(context, http) }
    val remote by lazy { com.sridhar.harbor.remote.RemoteClient(context) }
    val parental by lazy { com.sridhar.harbor.data.parental.ParentalControls(context) }
    /** On-device "Recommended for you" from library embeddings + Jellyfin watch history. */
    val reco by lazy { com.sridhar.harbor.data.reco.RecoRepository(context, jellyfin, parental) { llm } }
    val radio by lazy { com.sridhar.harbor.data.music.RadioStations(context, http) }
    /** A stream link shared into the app ("Share → JellyVerse"); Music home offers to save it as a station. */
    val sharedRadioLink = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    /** Opens the radio recordings sheet in Music (from the recording notifications). */
    val showRecordings = kotlinx.coroutines.flow.MutableStateFlow(false)
    val admin by lazy { com.sridhar.harbor.data.jellyfin.admin.JellyfinAdminRepository(jellyfin) }
    val seerr = SeerrRepository(settings, http)
    /** Multi-part parallel downloader shared by offline films and AI models. */
    val downloader = com.sridhar.harbor.data.download.SegmentedDownloader(context, http)
    val offline = OfflineRepository(context, settings, jellyfin, downloader)
    val sonarr = ArrRepository(ArrKind.Sonarr, settings, http)
    val radarr = ArrRepository(ArrKind.Radarr, settings, http)
    val aria2 = com.sridhar.harbor.data.aria2.Aria2Repository(settings, http)
    val iptv by lazy { com.sridhar.harbor.data.iptv.IptvRepository(context, http) }
    private val llmLazy = lazy { com.sridhar.harbor.data.ai.LocalLlm(context, downloader) }
    val llm by llmLazy
    /** False until something uses the AI – lifecycle hooks then don't create it just to unload it. */
    val llmCreated get() = llmLazy.isInitialized()
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
