package com.sridhar.harbor

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.OpenableColumns
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.sridhar.harbor.data.IncomingTorrent
import com.sridhar.harbor.ui.components.ProvideContainer
import com.sridhar.harbor.ui.lock.LockGate
import com.sridhar.harbor.ui.lock.LockPrefs
import com.sridhar.harbor.ui.nav.HarborNavHost
import com.sridhar.harbor.ui.theme.HarborTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : FragmentActivity() {
    override fun attachBaseContext(newBase: android.content.Context) = super.attachBaseContext(com.sridhar.harbor.AppLocale.wrap(newBase))

    private val container get() = (application as HarborApp).container
    private val locked = mutableStateOf(false)
    private var backgroundedAt = 0L
    private val lockPrefs by lazy { LockPrefs(this) }

    private val notifPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        splash.setKeepOnScreenCondition { container.config.value == null }
        enableEdgeToEdge()
        locked.value = lockPrefs.enabled
        handleIntent(intent)
        setContent {
            HarborTheme {
                ProvideContainer(container) {
                    com.sridhar.harbor.ui.components.LaunchIntro { LockGate(locked.value, onUnlock = ::authenticate) { HarborNavHost() } }
                    com.sridhar.harbor.ui.components.OfflineBanner()
                    com.sridhar.harbor.ui.components.DownloadsBanner()
                    com.sridhar.harbor.update.UpdatePrompt()
                    com.sridhar.harbor.ui.components.CrashNotice()
                }
            }
        }
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        if (locked.value) authenticate()
    }

    override fun onStop() { super.onStop(); backgroundedAt = SystemClock.elapsedRealtime() }

    override fun onStart() {
        super.onStart()
        if (lockPrefs.enabled && backgroundedAt > 0 && SystemClock.elapsedRealtime() - backgroundedAt > 60_000) {
            locked.value = true; authenticate()
        }
    }

    fun authenticate() {
        if (!locked.value) return
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { locked.value = false }
        })
        runCatching {
            prompt.authenticate(BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock JellyVerse").setSubtitle("Your homelab controls are protected")
                .setAllowedAuthenticators(BIOMETRIC_WEAK or DEVICE_CREDENTIAL).build())
        }.onFailure { locked.value = false }   // no lock screen configured on the device
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        val data: Uri? = intent.data
        when {
            com.sridhar.harbor.voice.VoicePlay.isVoiceSearch(intent) -> lifecycleScope.launch(com.sridhar.harbor.CrashGuard) {
                if (com.sridhar.harbor.voice.VoicePlay.handle(this@MainActivity, container, intent) && container.musicEngine.state.value.current != null)
                    container.navRequests.tryEmit("nowplaying")
            }
            // TV QR scanned with the phone's own camera app.
            data?.scheme == "jellyverse" && data.host == "tv" -> if (container.remote.connectFromQr(data.toString())) container.navRequests.tryEmit("remote")
            data?.scheme == "jellyverse" -> container.navRequests.tryEmit(data.lastPathSegment ?: data.host?.takeIf { it != "open" } ?: "watch")
            data?.scheme == "magnet" -> container.incomingTorrents.tryEmit(IncomingTorrent.Magnet(data.toString()))
            intent.action == Intent.ACTION_SEND -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
                val magnet = Regex("magnet:\\?[^\\s]+").find(text)
                if (magnet != null) container.incomingTorrents.tryEmit(IncomingTorrent.Magnet(magnet.value))
                // Any other shared link → offer to save it as a radio station in Music.
                else com.sridhar.harbor.data.music.RadioStations.linkIn(text)?.let { link ->
                    container.sharedRadioLink.value = link
                    container.navRequests.tryEmit("music")
                }
            }
            data != null && intent.action == Intent.ACTION_VIEW -> lifecycleScope.launch(com.sridhar.harbor.CrashGuard) {
                val torrent = withContext(Dispatchers.IO) {
                    runCatching {
                        val name = contentResolver.query(data, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: "file.torrent"
                        IncomingTorrent.File(name, contentResolver.openInputStream(data)!!.use { it.readBytes() })
                    }.getOrNull()
                }
                torrent?.let { container.incomingTorrents.tryEmit(it) }
            }
        }
    }
}
