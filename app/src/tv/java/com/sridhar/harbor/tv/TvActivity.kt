package com.sridhar.harbor.tv

import androidx.lifecycle.lifecycleScope
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.sridhar.harbor.HarborApp
import com.sridhar.harbor.ui.components.ProvideContainer
import com.sridhar.harbor.ui.theme.HarborTheme
import kotlinx.coroutines.launch

class TvActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: android.content.Context) = super.attachBaseContext(com.sridhar.harbor.AppLocale.wrap(newBase))

    private val container get() = (application as HarborApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleDeepLink(intent)
        setContent {
            HarborTheme {
                ProvideContainer(container) {
                    val cfg by container.config.collectAsState()
                    when {
                        cfg == null -> {}
                        !cfg!!.jellyfinReady -> TvSetup()
                        else -> TvApp()
                    }
                    com.sridhar.harbor.ui.components.OfflineBanner()
                    com.sridhar.harbor.ui.components.CrashNotice()
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) { super.onNewIntent(intent); handleDeepLink(intent) }

    private fun handleDeepLink(intent: android.content.Intent?) {
        if (intent != null && com.sridhar.harbor.voice.VoicePlay.isVoiceSearch(intent)) {
            val app = application as com.sridhar.harbor.HarborApp
            lifecycleScope.launch(com.sridhar.harbor.CrashGuard) { com.sridhar.harbor.voice.VoicePlay.handle(this@TvActivity, app.container, intent) }
            return
        }
        val data = intent?.data ?: return
        if (data.scheme == "jellyversetv" && data.host == "play") data.lastPathSegment?.let { com.sridhar.harbor.ui.player.PlayerActivity.start(this, it) }
    }

    /** Refresh the home-screen Play Next row whenever the user leaves Harbor TV. */
    override fun onStop() {
        super.onStop()
        container.scope.launch(com.sridhar.harbor.CrashGuard) { runCatching { WatchNext.sync(applicationContext, container) } }
    }
}
