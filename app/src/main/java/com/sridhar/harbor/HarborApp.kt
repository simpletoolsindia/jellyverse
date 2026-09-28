package com.sridhar.harbor

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
        container = AppContainer(this)
        container.cast.init()
        // Off the main thread: channels + WorkManager aren't needed for the first frame.
        container.scope.launch {
            com.sridhar.harbor.alerts.Alerts.createChannels(this@HarborApp)
            com.sridhar.harbor.update.UpdateWorker.schedule(this@HarborApp)
            if (!packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK)) com.sridhar.harbor.alerts.Alerts.schedule(this@HarborApp)
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
            .crossfade(if (container.lowRam) 120 else 200)
            .memoryCache { coil3.memory.MemoryCache.Builder().maxSizePercent(context, if (container.lowRam) 0.15 else 0.25).build() }
            .diskCache { coil3.disk.DiskCache.Builder().directory(context.cacheDir.resolve("images")).maxSizeBytes(if (container.lowRam) 128L shl 20 else 384L shl 20).build() }
            .build()
}
