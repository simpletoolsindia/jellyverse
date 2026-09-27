package com.sridhar.harbor

import android.app.Application
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * The app's error boundaries.
 *  1. As a [CoroutineExceptionHandler] on every UI-launched coroutine: a failed request or a parsing bug is logged
 *     (and kept in a small on-device error log) instead of taking the whole app down.
 *  2. [install] records any crash that still escapes, so the next launch can explain what happened.
 */
object CrashGuard : AbstractCoroutineContextElement(CoroutineExceptionHandler), CoroutineExceptionHandler {
    private const val TAG = "JellyVerse"
    private var dir: File? = null

    override fun handleException(context: CoroutineContext, exception: Throwable) {
        Log.e(TAG, "Recovered from background error", exception)
        record("errors.log", exception, append = true)
    }

    fun install(app: Application) {
        dir = File(app.filesDir, "diagnostics").apply { mkdirs() }
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching { record("last_crash.txt", e, append = false, thread = t.name) }
            previous?.uncaughtException(t, e)
        }
    }

    /** The previous run's crash report, consumed once. */
    fun takeLastCrash(): String? = dir?.let { File(it, "last_crash.txt") }?.takeIf { it.exists() }?.let { f -> f.readText().also { f.delete() } }

    private fun record(name: String, e: Throwable, append: Boolean, thread: String? = null) {
        val d = dir ?: return
        val f = File(d, name)
        if (append && f.length() > 64_000) f.writeText("")   // keep the error log tiny
        val header = buildString {
            append(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))
            append(" · JellyVerse ").append(BuildConfig.VERSION_NAME).append(" (").append(BuildConfig.VERSION_CODE).append(")")
            append(" · Android ").append(Build.VERSION.RELEASE).append(" · ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
            thread?.let { append(" · thread ").append(it) }
            append('\n')
        }
        val body = header + Log.getStackTraceString(e).lines().take(40).joinToString("\n") + "\n\n"
        if (append) f.appendText(body) else f.writeText(body)
    }
}
