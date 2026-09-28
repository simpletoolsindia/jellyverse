package com.sridhar.harbor

import android.content.Context
import android.content.res.Configuration
import kotlin.math.max
import kotlin.math.min

/**
 * The TV UI is designed on a 960×540 dp canvas (Android TV's reference: 1080p at xhdpi).
 * TV boxes report all sorts of densities – a 720p box at mdpi would get 1280×720 dp and everything
 * would render smaller with a different layout. Pinning the density makes every TV, 720p to 4K,
 * show exactly the same screen, just sharper.
 */
object TvScale {
    private const val W = 960f
    private const val H = 540f

    fun wrap(base: Context): Context {
        if (BuildConfig.FLAVOR != "tv") return base
        val dm = base.resources.displayMetrics
        val long = max(dm.widthPixels, dm.heightPixels).toFloat()
        val short = min(dm.widthPixels, dm.heightPixels).toFloat()
        if (long <= 0f || short <= 0f) return base
        val dpi = (min(long / W, short / H) * 160f).toInt().coerceAtLeast(120)
        if (dpi == dm.densityDpi) return base
        val cfg = Configuration(base.resources.configuration).apply {
            densityDpi = dpi
            screenWidthDp = (long * 160f / dpi).toInt(); screenHeightDp = (short * 160f / dpi).toInt()
            smallestScreenWidthDp = screenHeightDp
        }
        return base.createConfigurationContext(cfg)
    }
}
