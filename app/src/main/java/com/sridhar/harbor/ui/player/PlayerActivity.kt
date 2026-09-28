package com.sridhar.harbor.ui.player

import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.mutableStateOf
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.sridhar.harbor.ui.theme.HarborTheme

class PlayerActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: android.content.Context) = super.attachBaseContext(com.sridhar.harbor.TvScale.wrap(com.sridhar.harbor.AppLocale.wrap(newBase)))

    private val vm: PlayerViewModel by viewModels()
    private val inPip = mutableStateOf(false)
    private var session: androidx.media3.session.MediaSession? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        val livePlaylist = intent.getStringExtra(EXTRA_LIVE_PLAYLIST)
        if (livePlaylist != null) vm.startLive(livePlaylist, intent.getStringExtra(EXTRA_ID)!!)
        else vm.start(
            itemId = intent.getStringExtra(EXTRA_ID)!!,
            offlinePath = intent.getStringExtra(EXTRA_OFFLINE),
            offlineTitle = intent.getStringExtra(EXTRA_TITLE),
            fromStart = intent.getBooleanExtra(EXTRA_FROM_START, false),
        )
        // Headset / Bluetooth / system media controls.
        session = androidx.media3.session.MediaSession.Builder(this, vm.player).setId("harbor-${System.nanoTime()}").build()
        // Hardware volume keys adjust media volume while the player is open (phones and TV remotes).
        volumeControlStream = android.media.AudioManager.STREAM_MUSIC
        setContent {
            HarborTheme {
                com.sridhar.harbor.ui.components.ProvideContainer((application as com.sridhar.harbor.HarborApp).container) {
                    PlayerScreen(vm, inPip.value, onBack = { finish() }, onPip = ::enterPip)
                }
            }
        }
    }

    /**
     * Set by PlayerScreen. Gets every key Compose didn't consume, so the remote keeps working even when focus
     * was lost (e.g. the video surface being rebuilt on a channel change).
     */
    var remoteKeys: ((android.view.KeyEvent) -> Boolean)? = null

    @android.annotation.SuppressLint("RestrictedApi")   // Activity.dispatchKeyEvent is public API; the lint hit is ComponentActivity's annotation
    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean =
        super.dispatchKeyEvent(event) || remoteKeys?.invoke(event) == true

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getStringExtra(EXTRA_LIVE_PLAYLIST) != null) { finish(); startActivity(intent); return }
        vm.replace(
            itemId = intent.getStringExtra(EXTRA_ID)!!,
            offlinePath = intent.getStringExtra(EXTRA_OFFLINE),
            offlineTitle = intent.getStringExtra(EXTRA_TITLE),
            fromStart = intent.getBooleanExtra(EXTRA_FROM_START, false),
        )
    }

    private fun pipParams(): PictureInPictureParams {
        val fmt = vm.player.videoSize
        val ratio = if (fmt.width > 0 && fmt.height > 0) Rational(fmt.width, fmt.height).let {
            // Android rejects ratios outside ~2.39:1 .. 1:2.39
            if (it.toFloat() > 2.39f) Rational(239, 100) else if (it.toFloat() < 0.42f) Rational(100, 239) else it
        } else Rational(16, 9)
        return PictureInPictureParams.Builder().setAspectRatio(ratio).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) setSeamlessResizeEnabled(true)
        }.build()
    }

    fun enterPip() {
        runCatching { enterPictureInPictureMode(pipParams()) }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Picture-in-picture is a phone pattern: on a TV it leaves a stray window over the launcher/app, so just stop.
        val tv = packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK)
        if (vm.player.isPlaying && !vm.ui.backgroundPlay && !tv) enterPip()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip.value = isInPictureInPictureMode
    }

    override fun onStop() {
        super.onStop()
        // Closing the PiP window (or backgrounding without PiP) should pause playback.
        if (!isInPictureInPictureMode && !vm.ui.backgroundPlay) vm.player.pause()
    }

    override fun onDestroy() {
        session?.release(); session = null
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_ID = "id"
        private const val EXTRA_OFFLINE = "offline"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_FROM_START = "from_start"
        private const val EXTRA_LIVE_PLAYLIST = "live_playlist"

        fun startLive(ctx: Context, playlistId: String, channelId: String) {
            ctx.startActivity(Intent(ctx, PlayerActivity::class.java).putExtra(EXTRA_ID, channelId).putExtra(EXTRA_LIVE_PLAYLIST, playlistId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK.takeIf { ctx !is android.app.Activity } ?: 0))
        }

        fun start(ctx: Context, itemId: String, fromStart: Boolean = false, offlinePath: String? = null, title: String? = null) {
            ctx.startActivity(Intent(ctx, PlayerActivity::class.java)
                .putExtra(EXTRA_ID, itemId).putExtra(EXTRA_FROM_START, fromStart)
                .putExtra(EXTRA_OFFLINE, offlinePath).putExtra(EXTRA_TITLE, title)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK.takeIf { ctx !is android.app.Activity } ?: 0))
        }
    }
}
