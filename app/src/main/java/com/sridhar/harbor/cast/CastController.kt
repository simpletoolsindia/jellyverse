package com.sridhar.harbor.cast

import android.content.Context
import android.net.Uri
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.framework.SessionProvider
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.google.android.gms.common.images.WebImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.Executors

/** Uses Google's Default Media Receiver – no custom receiver app needed. */
class CastOptionsProvider : OptionsProvider {
    override fun getCastOptions(context: Context): CastOptions = CastOptions.Builder()
        .setReceiverApplicationId(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID)
        .setStopReceiverApplicationWhenEndingSession(true)
        // Keeps playing on the TV when you leave the app: controls in the notification and on the lock screen.
        .setCastMediaOptions(com.google.android.gms.cast.framework.media.CastMediaOptions.Builder()
            .setMediaSessionEnabled(true)
            .setNotificationOptions(com.google.android.gms.cast.framework.media.NotificationOptions.Builder()
                .setActions(listOf(
                    com.google.android.gms.cast.framework.media.MediaIntentReceiver.ACTION_REWIND,
                    com.google.android.gms.cast.framework.media.MediaIntentReceiver.ACTION_TOGGLE_PLAYBACK,
                    com.google.android.gms.cast.framework.media.MediaIntentReceiver.ACTION_FORWARD,
                    com.google.android.gms.cast.framework.media.MediaIntentReceiver.ACTION_STOP_CASTING,
                ), intArrayOf(1, 3))
                .setSkipStepMs(30_000)
                .setTargetActivityClassName(com.sridhar.harbor.MainActivity::class.java.name)
                .build())
            .build())
        .build()
    override fun getAdditionalSessionProviders(context: Context): List<SessionProvider>? = null
}

data class CastDevice(val id: String, val name: String, val description: String?)
data class CastStatus(val device: String, val title: String?, val positionMs: Long, val durationMs: Long, val playing: Boolean, val itemId: String?)

/** Chromecast control shared by the player, the phone UI and Harbor AI ("play X on TV"). */
class CastController(private val context: Context) {
    private var castContext: CastContext? = null
    private val router by lazy { MediaRouter.getInstance(context) }
    private val selector by lazy {
        MediaRouteSelector.Builder().addControlCategory(CastMediaControlIntent.categoryForCast(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID)).build()
    }
    private val _devices = MutableStateFlow<List<CastDevice>>(emptyList())
    val devices: StateFlow<List<CastDevice>> = _devices
    private val _status = MutableStateFlow<CastStatus?>(null)
    val status: StateFlow<CastStatus?> = _status
    private var currentItemId: String? = null

    val available get() = castContext != null

    private val routerCallback = object : MediaRouter.Callback() {
        override fun onRouteAdded(r: MediaRouter, route: MediaRouter.RouteInfo) = refresh()
        override fun onRouteRemoved(r: MediaRouter, route: MediaRouter.RouteInfo) = refresh()
        override fun onRouteChanged(r: MediaRouter, route: MediaRouter.RouteInfo) = refresh()
    }

    private fun refresh() {
        _devices.value = router.routes.filter { !it.isDefault && it.matchesSelector(selector) && it.isEnabled }
            .map { CastDevice(it.id, it.name, it.description) }
    }

    private val sessionListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarted(s: CastSession, id: String) = attach(s)
        override fun onSessionResumed(s: CastSession, wasSuspended: Boolean) = attach(s)
        override fun onSessionEnded(s: CastSession, error: Int) { _status.value = null; currentItemId = null }
        override fun onSessionStarting(s: CastSession) {}
        override fun onSessionStartFailed(s: CastSession, error: Int) { _status.value = null }
        override fun onSessionEnding(s: CastSession) {}
        override fun onSessionResuming(s: CastSession, id: String) {}
        override fun onSessionResumeFailed(s: CastSession, error: Int) {}
        override fun onSessionSuspended(s: CastSession, reason: Int) {}
    }

    private fun attach(s: CastSession) {
        val device = s.castDevice?.friendlyName ?: "Chromecast"
        _status.value = CastStatus(device, null, 0, 0, false, currentItemId)
        s.remoteMediaClient?.addProgressListener({ pos, dur ->
            val c = client()
            _status.value = CastStatus(device, c?.mediaInfo?.metadata?.getString(MediaMetadata.KEY_TITLE), pos, dur, c?.isPlaying == true, currentItemId)
        }, 1000)
    }

    /** Call once from the main thread (Application.onCreate). Safe on devices without Play services. */
    fun init() {
        runCatching {
            CastContext.getSharedInstance(context, Executors.newSingleThreadExecutor()).addOnSuccessListener { cc ->
                castContext = cc
                cc.sessionManager.addSessionManagerListener(sessionListener, CastSession::class.java)
                cc.sessionManager.currentCastSession?.let(::attach)
            }
        }
    }

    fun startDiscovery() = runCatching { router.addCallback(selector, routerCallback, MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY); refresh() }
    fun stopDiscovery() = runCatching { router.removeCallback(routerCallback) }

    private fun client(): RemoteMediaClient? = castContext?.sessionManager?.currentCastSession?.remoteMediaClient

    /** Connect to the device whose name best matches [hint] ("tv", "living room") – or the only/first one. */
    suspend fun connect(hint: String?): String = withContext(Dispatchers.Main) {
        if (castContext == null) throw IllegalStateException("Chromecast needs Google Play services")
        castContext?.sessionManager?.currentCastSession?.takeIf { it.isConnected }?.let { return@withContext it.castDevice?.friendlyName ?: "Chromecast" }
        startDiscovery()
        val route = withTimeoutOrNull(8_000) {
            while (true) {
                val routes = router.routes.filter { !it.isDefault && it.matchesSelector(selector) && it.isEnabled }
                val h = hint?.lowercase()?.replace(Regex("\\b(the|my|on|in|to)\\b"), "")?.trim().orEmpty()
                val pick = routes.firstOrNull { h.isNotBlank() && it.name.lowercase().contains(h) } ?: routes.firstOrNull { "tv" in it.name.lowercase() } ?: routes.firstOrNull()
                if (pick != null) return@withTimeoutOrNull pick
                delay(400)
            }
            @Suppress("UNREACHABLE_CODE") null
        } ?: throw IllegalStateException("No Chromecast found on this Wi-Fi")
        router.selectRoute(route)
        withTimeoutOrNull(15_000) { while (client() == null) delay(300) } ?: throw IllegalStateException("Couldn't connect to ${route.name}")
        route.name
    }

    suspend fun load(itemId: String, url: String, title: String, subtitle: String?, posterUrl: String?, startMs: Long) = withContext(Dispatchers.Main) {
        val md = MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE).apply {
            putString(MediaMetadata.KEY_TITLE, title)
            subtitle?.let { putString(MediaMetadata.KEY_SUBTITLE, it) }
            posterUrl?.let { addImage(WebImage(Uri.parse(it))) }
        }
        val info = MediaInfo.Builder(url).setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
            .setContentType("application/x-mpegURL").setMetadata(md).build()
        currentItemId = itemId
        client()?.load(MediaLoadRequestData.Builder().setMediaInfo(info).setAutoplay(true).setCurrentTime(startMs).build())
            ?: throw IllegalStateException("Not connected")
    }

    fun togglePlay() { client()?.let { if (it.isPlaying) it.pause() else it.play() } }
    fun seekBy(ms: Long) { client()?.let { seekTo((it.approximateStreamPosition + ms).coerceAtLeast(0)) } }
    fun seekTo(ms: Long) { client()?.seek(com.google.android.gms.cast.MediaSeekOptions.Builder().setPosition(ms).build()) }
    fun position(): Long = client()?.approximateStreamPosition ?: 0
    fun disconnect() { runCatching { castContext?.sessionManager?.endCurrentSession(true) }; _status.value = null; currentItemId = null }
}
