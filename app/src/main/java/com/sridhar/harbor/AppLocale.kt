package com.sridhar.harbor

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.annotation.StringRes
import java.util.Locale

/**
 * In-app language (English / தமிழ் / system). Android 13+ uses the platform per-app language API
 * (also exposed in system Settings → Apps → Language); older versions wrap each Activity's context.
 */
object AppLocale {
    data class Option(val tag: String, val nativeName: String)

    /** "" = follow the system language. */
    val options = listOf(Option("", "System default"), Option("en", "English"), Option("ta", "தமிழ்"))

    private const val PREFS = "harbor_locale"
    private const val KEY = "tag"

    fun current(ctx: Context): String =
        if (Build.VERSION.SDK_INT >= 33) ctx.getSystemService(LocaleManager::class.java).applicationLocales.toLanguageTags().substringBefore(',')
        else ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "").orEmpty()

    fun set(activity: Activity, tag: String) {
        activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, tag).apply()
        L10n.reset()
        if (Build.VERSION.SDK_INT >= 33) {
            // The platform recreates activities itself.
            activity.getSystemService(LocaleManager::class.java).applicationLocales =
                if (tag.isBlank()) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
        } else activity.recreate()
    }

    /** For attachBaseContext on Android 12 and below. */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= 33) return base
        val tag = base.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "").orEmpty()
        if (tag.isBlank()) return base
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val cfg = Configuration(base.resources.configuration).apply { setLocale(locale); setLayoutDirection(locale) }
        return base.createConfigurationContext(cfg)
    }
}

/** Localised strings outside Compose (ViewModel messages, enum labels). Holds the application context only. */
@android.annotation.SuppressLint("StaticFieldLeak")
object L10n {
    private var app: Context? = null
    private var localized: Context? = null

    fun init(context: Context) { app = context.applicationContext; localized = null }
    fun reset() { localized = null }

    private fun ctx(): Context? = localized ?: app?.let { AppLocale.wrap(it) }?.also { localized = it }

    /** JVM unit tests plug in a resource-free lookup here. */
    @androidx.annotation.VisibleForTesting
    var testLookup: ((Int, Array<out Any?>) -> String)? = null

    fun s(@StringRes id: Int, vararg args: Any?): String = testLookup?.invoke(id, args) ?: ctx()?.getString(id, *args).orEmpty()
}
