package com.sridhar.harbor.alerts

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.sridhar.harbor.HarborApp
import com.sridhar.harbor.R
import java.util.concurrent.TimeUnit

/** User-controlled alert preferences. */
class AlertPrefs(context: Context) {
    private val p = context.getSharedPreferences("harbor_alerts", Context.MODE_PRIVATE)
    var downloads: Boolean get() = p.getBoolean("downloads", true); set(v) = p.edit().putBoolean("downloads", v).apply()
    var requests: Boolean get() = p.getBoolean("requests", true); set(v) = p.edit().putBoolean("requests", v).apply()
    var homelab: Boolean get() = p.getBoolean("homelab", true); set(v) = p.edit().putBoolean("homelab", v).apply()
    val any get() = downloads || requests || homelab
    fun seen(key: String): Set<String> = p.getStringSet(key, null) ?: emptySet()
    fun hasSeen(key: String) = p.contains(key)
    fun setSeen(key: String, v: Set<String>) = p.edit().putStringSet(key, v.toList().takeLast(500).toSet()).apply()
}

object Alerts {
    const val CH_DOWNLOADS = "downloads"
    const val CH_REQUESTS = "requests"
    const val CH_HOMELAB = "homelab"

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannels(listOf(
            NotificationChannel(CH_DOWNLOADS, "Finished downloads", NotificationManager.IMPORTANCE_DEFAULT).apply { description = "qBittorrent & aria2 completions" },
            NotificationChannel(CH_REQUESTS, "Requests available", NotificationManager.IMPORTANCE_DEFAULT).apply { description = "Requested movies & shows ready to watch" },
            NotificationChannel(CH_HOMELAB, "Homelab alerts", NotificationManager.IMPORTANCE_HIGH).apply { description = "Disk, temperature and container problems" },
        ))
    }

    fun schedule(context: Context) {
        val wm = WorkManager.getInstance(context)
        if (!AlertPrefs(context).any) { wm.cancelUniqueWork("harbor-alerts"); return }
        wm.enqueueUniquePeriodicWork("harbor-alerts", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<AlertWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build()).build())
    }

    fun notify(context: Context, channel: String, id: Int, title: String, text: String, deepLink: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(deepLink)).setPackage(context.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pi = PendingIntent.getActivity(context, id, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_launcher_monochrome).setColor(0xFF1F80E0.toInt())
            .setContentTitle(title).setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pi).setAutoCancel(true).build()
        val nm = NotificationManagerCompat.from(context)
        // Android 13+: the user may have declined notifications – never post without the permission.
        val allowed = android.os.Build.VERSION.SDK_INT < 33 ||
            androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (allowed && nm.areNotificationsEnabled()) runCatching { nm.notify(id, n) }
    }
}

/**
 * Every 15 min on Wi-Fi: finished downloads, newly available requests, homelab health.
 * First run only records the current state so nothing old is announced.
 */
class AlertWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as HarborApp
        val c = app.container
        val prefs = AlertPrefs(applicationContext)
        val cfg = c.settings.current()

        if (prefs.downloads && cfg.qbitReady) runCatching {
            val done = c.qbit.torrents().filter { it.progress >= 1f }
            val first = !prefs.hasSeen("qbit")
            val seen = prefs.seen("qbit")
            if (!first) done.filter { it.hash !in seen }.take(5).forEach {
                Alerts.notify(applicationContext, Alerts.CH_DOWNLOADS, it.hash.hashCode(), "Download finished", it.name, "jellyverse://open/downloads")
            }
            prefs.setSeen("qbit", seen + done.map { it.hash })
        }
        if (prefs.downloads && cfg.aria2Ready) runCatching {
            val done = c.aria2.downloads().filter { it.status == "complete" }
            val first = !prefs.hasSeen("aria2"); val seen = prefs.seen("aria2")
            if (!first) done.filter { it.gid !in seen }.take(5).forEach {
                Alerts.notify(applicationContext, Alerts.CH_DOWNLOADS, it.gid.hashCode(), "aria2 download finished", it.name, "jellyverse://open/downloads")
            }
            prefs.setSeen("aria2", seen + done.map { it.gid })
        }
        if (prefs.requests && cfg.seerrReady) runCatching {
            val avail = c.seerr.requests("available", 0, 20).results
            val first = !prefs.hasSeen("seerr"); val seen = prefs.seen("seerr")
            if (!first) avail.filter { it.id.toString() !in seen }.take(5).forEach { r ->
                val title = runCatching { c.seerr.details(if (r.type == "tv") "tv" else "movie", r.media.tmdbId).displayTitle }.getOrDefault("Your request")
                Alerts.notify(applicationContext, Alerts.CH_REQUESTS, 10_000 + r.id, "Ready to watch 🍿", "$title is now in your library", "jellyverse://open/watch")
            }
            prefs.setSeen("seerr", seen + avail.map { it.id.toString() })
        }
        if (prefs.homelab) c.ssh.primary?.let { h ->
            runCatching {
                val snap = c.homelab.snapshot(h)
                val warnings = snap.warnings(c.homelab.containers(h, false)).filterNot { it.startsWith("Swap") }.toSet()
                val seen = prefs.seen("lab")
                (warnings - seen).take(3).forEachIndexed { i, w ->
                    Alerts.notify(applicationContext, Alerts.CH_HOMELAB, 20_000 + w.hashCode(), "Homelab needs attention", w, "jellyverse://open/lab")
                }
                prefs.setSeen("lab", warnings)   // resolved warnings drop out, so they alert again if they return
            }
        }
        return Result.success()
    }
}
