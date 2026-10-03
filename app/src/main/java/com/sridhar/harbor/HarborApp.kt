package com.sridhar.harbor

import coil3.request.allowRgb565
import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import coil3.disk.directory
import com.sridhar.harbor.data.AppContainer
import kotlinx.coroutines.launch

class HarborApp : Application(), SingletonImageLoader.Factory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        CrashGuard.install(this)
        L10n.init(this)
        com.sridhar.harbor.ui.theme.Looks.init(this, BuildConfig.FLAVOR == "tv")
        com.sridhar.harbor.net.NetworkMonitor.init(this)
        com.sridhar.harbor.radio.RadioLibrary.init(this)
        container = AppContainer(this)
        watchForeground()
        container.cast.init()
        // Off the main thread: channels + WorkManager aren't needed for the first frame.
        container.scope.launch {
            com.sridhar.harbor.alerts.Alerts.createChannels(this@HarborApp)
            com.sridhar.harbor.update.UpdateWorker.schedule(this@HarborApp)
            com.sridhar.harbor.radio.RecordService.channels(this@HarborApp)
            com.sridhar.harbor.radio.RadioScheduler.rearmAll(this@HarborApp)
            // Home-screen widgets follow playback and recordings (only collected when a widget is placed).
            val app = this@HarborApp
            launch(kotlinx.coroutines.Dispatchers.Main) {
                kotlinx.coroutines.flow.combine(com.sridhar.harbor.radio.RadioLibrary.live, com.sridhar.harbor.radio.RadioLibrary.schedules) { l, s -> (l?.station to (l?.bytes ?: 0) / 3_000_000) to s.size }
                    .collect { if (com.sridhar.harbor.widget.Widgets.anyRecord(app) || com.sridhar.harbor.widget.Widgets.anyRadio(app)) { com.sridhar.harbor.widget.Widgets.updateRecord(app); com.sridhar.harbor.widget.Widgets.updateRadio(app) } }
            }
            launch(kotlinx.coroutines.Dispatchers.Main) {
                if (com.sridhar.harbor.widget.Widgets.anyRadio(app)) container.musicEngine.state
                    .collect { s -> com.sridhar.harbor.widget.Widgets.updateRadio(app) }
            }
            if (!packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK)) {
                com.sridhar.harbor.alerts.Alerts.schedule(this@HarborApp)
                com.sridhar.harbor.alerts.Suggestions.schedule(this@HarborApp)
            }
        }
    }

    companion object {
        /** For process-wide helpers (e.g. the phone-remote server) that have no Context of their own. */
        @Volatile var instance: HarborApp? = null
            private set
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { container.imageHttp })) }
            // Low-end: half-size bitmaps (RGB_565 – no visible difference for posters) and no per-image crossfade.
            .crossfade(if (container.lowEnd) 0 else 200)
            .allowRgb565(container.lowEnd)
            .memoryCache { coil3.memory.MemoryCache.Builder().maxSizePercent(context, if (container.lowRam) 0.15 else 0.25).build() }
            .diskCache { coil3.disk.DiskCache.Builder().directory(context.cacheDir.resolve("images")).maxSizeBytes(if (container.lowRam) 128L shl 20 else 384L shl 20).build() }
            .build()

    /** Counts visible screens; when none are left the user has gone (home, recents, closed) – free the AI model. */
    private fun watchForeground() = registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
        private var started = 0
        private fun llm() = if (container.llmCreated) container.llm else null
        override fun onActivityStarted(a: android.app.Activity) { if (started++ == 0) llm()?.onAppForeground() }
        override fun onActivityStopped(a: android.app.Activity) { if (--started == 0 && !a.isChangingConfigurations) llm()?.onAppBackground() }
        override fun onActivityCreated(a: android.app.Activity, b: android.os.Bundle?) {}
        override fun onActivityResumed(a: android.app.Activity) {}
        override fun onActivityPaused(a: android.app.Activity) {}
        override fun onActivitySaveInstanceState(a: android.app.Activity, b: android.os.Bundle) {}
        override fun onActivityDestroyed(a: android.app.Activity) {}
    })

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // Low memory (in the app or cached in the background): the model is the biggest thing we hold.
        @Suppress("DEPRECATION")
        val pressure = level == android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW || level == android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL ||
            level >= android.content.ComponentCallbacks2.TRIM_MEMORY_BACKGROUND
        if (pressure && ::container.isInitialized && container.llmCreated) container.llm.onLowMemory()
    }
}
