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
    var newInLibrary: Boolean get() = p.getBoolean("new_in_library", true); set(v) = p.edit().putBoolean("new_in_library", v).apply()
    val any get() = downloads || requests || homelab || newInLibrary
    fun seen(key: String): Set<String> = p.getStringSet(key, null) ?: emptySet()
    fun hasSeen(key: String) = p.contains(key)
    fun setSeen(key: String, v: Set<String>) = p.edit().putStringSet(key, v.toList().takeLast(500).toSet()).apply()
}

object Alerts {
    const val CH_DOWNLOADS = "downloads"
    const val CH_REQUESTS = "requests"
    const val CH_HOMELAB = "homelab"
    const val CH_NEW = "new_in_library"

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannels(listOf(
            NotificationChannel(CH_DOWNLOADS, "Finished downloads", NotificationManager.IMPORTANCE_DEFAULT).apply { description = "qBittorrent & aria2 completions" },
            NotificationChannel(CH_REQUESTS, "Requests available", NotificationManager.IMPORTANCE_DEFAULT).apply { description = "Requested movies & shows ready to watch" },
            NotificationChannel(CH_HOMELAB, "Homelab alerts", NotificationManager.IMPORTANCE_HIGH).apply { description = "Disk, temperature and container problems" },
            NotificationChannel(CH_NEW, context.getString(R.string.new_lib_channel), NotificationManager.IMPORTANCE_DEFAULT).apply { description = context.getString(R.string.new_lib_channel_desc) },
        ))
    }

    fun schedule(context: Context) {
        val wm = WorkManager.getInstance(context)
        if (!AlertPrefs(context).any) { wm.cancelUniqueWork("harbor-alerts"); return }
        wm.enqueueUniquePeriodicWork("harbor-alerts", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<AlertWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build()).build())
    }

    private fun link(context: Context, id: Int, deepLink: String) = PendingIntent.getActivity(context, id,
        Intent(Intent.ACTION_VIEW, Uri.parse(deepLink)).setPackage(context.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    /** [actions]: button label → deep link. [picture]: poster shown large; otherwise Jelly, the mascot. */
    fun notify(context: Context, channel: String, id: Int, title: String, text: String, deepLink: String,
               actions: List<Pair<String, String>> = emptyList(), picture: android.graphics.Bitmap? = null) {
        val b = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_jv).setColor(0xFF1F80E0.toInt())
            .setLargeIcon(picture ?: com.sridhar.harbor.radio.Avatar.bitmap(context))
            .setContentTitle(title).setContentText(text)
            .setStyle(if (picture != null) NotificationCompat.BigPictureStyle().bigPicture(picture).bigLargeIcon(null as android.graphics.Bitmap?).setSummaryText(text)
                else NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(link(context, id, deepLink)).setAutoCancel(true)
        actions.forEachIndexed { i, (label, url) -> b.addAction(0, label, link(context, id * 31 + i + 1, url)) }
        val n = b.build()
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
        var finished = 0

        if (prefs.downloads && cfg.qbitReady) runCatching {
            val done = c.qbit.torrents().filter { it.progress >= 1f }
            val first = !prefs.hasSeen("qbit")
            val seen = prefs.seen("qbit")
            val fresh = done.filter { it.hash !in seen }
            if (!first) fresh.take(5).forEach {
                Alerts.notify(applicationContext, Alerts.CH_DOWNLOADS, it.hash.hashCode(), applicationContext.getString(R.string.dl_done_title), it.name + "\n" + applicationContext.getString(R.string.dl_done_scan), "jellyverse://open/downloads")
            }
            if (!first) finished += fresh.size
            prefs.setSeen("qbit", seen + done.map { it.hash })
        }
        if (prefs.downloads && cfg.aria2Ready) runCatching {
            val done = c.aria2.downloads().filter { it.status == "complete" }
            val first = !prefs.hasSeen("aria2"); val seen = prefs.seen("aria2")
            val fresh = done.filter { it.gid !in seen }
            if (!first) fresh.take(5).forEach {
                Alerts.notify(applicationContext, Alerts.CH_DOWNLOADS, it.gid.hashCode(), applicationContext.getString(R.string.dl_done_title), it.name + "\n" + applicationContext.getString(R.string.dl_done_scan), "jellyverse://open/downloads")
            }
            if (!first) finished += fresh.size
            prefs.setSeen("aria2", seen + done.map { it.gid })
        }
        if (finished > 0 && cfg.jellyfinReady) runCatching { c.jellyfin.refreshLibrary() }   // index it now so the name & poster are right
        if (prefs.newInLibrary && cfg.jellyfinReady) runCatching { newInLibrary(prefs) }
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

    /**
     * Films and shows that just landed in Jellyfin. Titles that still look like a file name (no poster or synopsis)
     * get a "Fix name & poster" button that opens Library Doctor for that title.
     */
    private suspend fun newInLibrary(prefs: AlertPrefs) {
        val c = (applicationContext as HarborApp).container
        val items = c.jellyfin.recentlyAdded()
        val first = !prefs.hasSeen("newlib"); val seen = prefs.seen("newlib")
        if (!first) items.filter { it.id !in seen }.take(4).forEach { item ->
            val unmatched = item.imageTags["Primary"] == null || item.overview.isNullOrBlank() ||
                Regex("""(?i)\b(1080p|720p|2160p|x26[45]|hevc|webrip|web-dl|bluray|hdrip)\b|[._]\d{4}[._]""").containsMatchIn(item.name)
            val poster = if (unmatched) null else runCatching {
                val url = c.jellyfin.imageUrl(c.settings.current(), item.id, maxWidth = 600, tag = item.imageTags["Primary"])
                c.http.newCall(okhttp3.Request.Builder().url(url).build()).execute().use { r -> r.body?.bytes()?.let { android.graphics.BitmapFactory.decodeByteArray(it, 0, it.size) } }
            }.getOrNull()
            val ctx = applicationContext
            val title = item.name + (item.year?.let { " ($it)" } ?: "")
            Alerts.notify(ctx, Alerts.CH_NEW, 30_000 + item.id.hashCode() % 10_000,
                ctx.getString(if (unmatched) R.string.new_lib_unmatched_title else R.string.new_lib_title),
                if (unmatched) ctx.getString(R.string.new_lib_unmatched_body, title) else ctx.getString(R.string.new_lib_body, title),
                "jellyverse://open/item/${item.id}",
                actions = listOf(ctx.getString(R.string.new_lib_open) to "jellyverse://open/item/${item.id}",
                    ctx.getString(R.string.new_lib_fix) to "jellyverse://open/doctor/${item.id}"),
                picture = poster)
        }
        prefs.setSeen("newlib", seen + items.map { it.id })
    }
}
