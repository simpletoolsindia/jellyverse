package com.sridhar.harbor.ui.components

import android.annotation.SuppressLint
import android.net.ConnectivityManager
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.annotation.OptIn
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.sridhar.harbor.data.jellyfin.BaseItem
import kotlinx.coroutines.delay

/**
 * Hotstar-style preview: after the item has been focused/visible for [delayMs], its trailer fades in over
 * the backdrop – muted and looping. YouTube trailers (from metadata) play in the official embedded player;
 * titles without one get a muted preview from the film itself. Skipped on low-RAM devices and mobile data.
 */
@Composable
fun TrailerPreview(item: BaseItem?, modifier: Modifier = Modifier, delayMs: Long = 1500, onPlaying: (Boolean) -> Unit = {}) {
    val c = LocalContainer.current
    val ctx = LocalContext.current
    // Only true low-memory devices skip previews (budget TVs with a 192 MB heap still play them fine).
    val lowRamDevice = remember { ctx.getSystemService(android.app.ActivityManager::class.java).isLowRamDevice }
    // Never run under the player (or any other screen): leaving composition releases the decoder / WebView.
    val resumed = rememberResumed()
    val mode by c.previewMode.collectAsState()
    if (item == null || lowRamDevice || !resumed || !c.previewsOn(mode)) return
    var start by remember(item.id) { mutableStateOf(false) }
    var playing by remember(item.id) { mutableStateOf(false) }
    LaunchedEffect(item.id) {
        delay(delayMs)
        // Never burn mobile data on previews.
        val metered = ctx.getSystemService(ConnectivityManager::class.java)?.isActiveNetworkMetered == true
        if (!metered) start = true
    }
    if (!start) return
    val alpha by animateFloatAsState(if (playing) 1f else 0f, tween(700), label = "trailer")
    val latest by androidx.compose.runtime.rememberUpdatedState(onPlaying)
    LaunchedEffect(playing) { latest(playing) }
    DisposableEffect(item.id) { onDispose { latest(false) } }
    val yt = item.remoteTrailers.firstNotNullOfOrNull { it.youtubeId }
    Box(modifier.graphicsLayer { this.alpha = alpha }) {
        if (yt != null) YouTubePreview(yt, onPlaying = { playing = it })
        else if (item.type == "Movie") FilmPreview(item, onPlaying = { playing = it })
    }
}

private class YtBridge(val onState: (Boolean) -> Unit) {
    @JavascriptInterface fun state(playing: Boolean) = onState(playing)
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun YouTubePreview(videoId: String, onPlaying: (Boolean) -> Unit) {
    val main = remember { android.os.Handler(android.os.Looper.getMainLooper()) }
    // Official IFrame player, muted, no controls; the 16:9 frame is scaled to *cover* the area (cropped like a backdrop).
    val html = """
        <!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
        <style>html,body{margin:0;height:100%;background:#000;overflow:hidden}
        #w{position:absolute;top:50%;left:50%;width:max(100vw,177.78vh);height:max(56.25vw,100vh);transform:translate(-50%,-50%);pointer-events:none}
        #p{width:100%;height:100%}</style></head><body><div id="w"><div id="p"></div></div>
        <script src="https://www.youtube.com/iframe_api"></script>
        <script>
        function onYouTubeIframeAPIReady(){ new YT.Player('p',{videoId:'$videoId',host:'https://www.youtube-nocookie.com',
          playerVars:{autoplay:1,mute:1,controls:0,disablekb:1,fs:0,iv_load_policy:3,loop:1,playlist:'$videoId',modestbranding:1,playsinline:1,rel:0,start:8},
          events:{onReady:function(e){e.target.mute();e.target.playVideo();},
                  onStateChange:function(e){Android.state(e.data==1);},
                  onError:function(){Android.state(false);}}});}
        </script></body></html>
    """.trimIndent()
    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                setBackgroundColor(android.graphics.Color.BLACK)
                isFocusable = false; isFocusableInTouchMode = false   // never steal D-pad focus from the UI
                settings.javaScriptEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.domStorageEnabled = true
                webChromeClient = WebChromeClient()
                addJavascriptInterface(YtBridge { p -> main.post { onPlaying(p) } }, "Android")
                loadDataWithBaseURL("https://www.youtube-nocookie.com", html, "text/html", "utf-8", null)
            }
        },
        onRelease = { it.stopLoading(); it.loadUrl("about:blank"); it.destroy() },
        modifier = Modifier.fillMaxSize(),
    )
}

@OptIn(UnstableApi::class)
@Composable
private fun FilmPreview(item: BaseItem, onPlaying: (Boolean) -> Unit) {
    val c = LocalContainer.current
    val ctx = LocalContext.current
    val cfg = rememberConfig()
    val player = remember(item.id) {
        ExoPlayer.Builder(ctx).build().apply {
            volume = 0f; repeatMode = Player.REPEAT_MODE_ONE
            val msId = item.mediaSources.firstOrNull()?.id ?: item.id
            setMediaItem(MediaItem.Builder().setUri(c.jellyfin.directStreamUrl(cfg, item.id, msId))
                // Start ~20% in (past logos/intros) and loop a minute.
                .setClippingConfiguration(MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(((item.runTimeTicks ?: 0) / 10_000 * 0.2).toLong())
                    .setEndPositionMs(((item.runTimeTicks ?: 0) / 10_000 * 0.2).toLong() + 60_000).build()).build())
            addListener(object : Player.Listener { override fun onRenderedFirstFrame() = onPlaying(true) })
            prepare(); play()
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    AndroidView(
        factory = { PlayerView(it).apply {
            useController = false; resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            isFocusable = false; this.player = player
        } },
        onRelease = { it.player = null },
        modifier = Modifier.fillMaxSize(),
    )
}
