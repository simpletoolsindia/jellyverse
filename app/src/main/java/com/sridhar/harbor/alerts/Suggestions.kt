package com.sridhar.harbor.alerts

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
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
import com.sridhar.harbor.data.jellyfin.BaseItem
import java.util.Calendar
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * "Jelly's pick for tonight": now and then, in the evening, one unwatched film or show from your library.
 * Uses your personal recommendations when you've allowed them, otherwise a well-rated random pick.
 * Off / Sometimes (every 2–3 evenings, at a random time) / Daily – in Settings. Never between 22:00 and 18:00.
 */
object Suggestions {
    const val CHANNEL = "suggestions"
    private const val WORK = "harbor-suggestions"
    private const val NOTIF_ID = 41_000
    const val OFF = "off"; const val SOMETIMES = "sometimes"; const val DAILY = "daily"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("harbor_suggest", Context.MODE_PRIVATE)
    fun frequency(ctx: Context): String = prefs(ctx).getString("freq", SOMETIMES)!!
    fun setFrequency(ctx: Context, f: String) { prefs(ctx).edit().putString("freq", f).apply(); schedule(ctx) }

    fun schedule(ctx: Context) {
        val wm = WorkManager.getInstance(ctx)
        if (frequency(ctx) == OFF) { wm.cancelUniqueWork(WORK); return }
        // Cheap wake-ups (a clock check) every 3 h; the real work happens only once on a chosen evening.
        wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<SuggestWorker>(3, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).setRequiresBatteryNotLow(true).build()).build())
    }

    fun channel(ctx: Context) = ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(
        NotificationChannel(CHANNEL, ctx.getString(R.string.suggest_channel), NotificationManager.IMPORTANCE_DEFAULT)
            .apply { description = ctx.getString(R.string.suggest_channel_desc) })

    internal fun due(ctx: Context, now: Long = System.currentTimeMillis()): Boolean {
        val f = frequency(ctx)
        if (f == OFF) return false
        val hour = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY)
        return hour in 18..21 && now >= prefs(ctx).getLong("next", 0)
    }

    private fun planNext(ctx: Context, now: Long) {
        val gapH = if (frequency(ctx) == DAILY) 20 else Random.nextInt(44, 68)   // "sometimes" lands on a random evening
        prefs(ctx).edit().putLong("next", now + gapH * 3_600_000L).apply()
    }

    private fun recent(ctx: Context): Set<String> = prefs(ctx).getStringSet("recent", emptySet())!!
    private fun remember(ctx: Context, id: String) = prefs(ctx).edit().putStringSet("recent", (recent(ctx).toList().takeLast(59) + id).toSet()).apply()
    fun skip(ctx: Context, id: String) { remember(ctx, id); prefs(ctx).edit().putStringSet("skip", (skipped(ctx).toList().takeLast(199) + id).toSet()).apply() }
    private fun skipped(ctx: Context): Set<String> = prefs(ctx).getStringSet("skip", emptySet())!!

    /** Picks a title and posts the notification. Returns false when nothing suitable was found. */
    suspend fun suggest(ctx: Context): Boolean {
        val c = (ctx.applicationContext as HarborApp).container
        val cfg = c.settings.current()
        if (!cfg.jellyfinReady) return false
        val avoid = recent(ctx) + skipped(ctx)
        fun ok(i: BaseItem) = i.id !in avoid && i.imageTags["Primary"] != null && !c.parental.hideFromHome(i) && i.userData?.played != true

        // 1) Personal pick (only with the user's consent), varied among the top matches.
        var pick: BaseItem? = null
        var reason: String? = null
        if (c.reco.consent.value == true) {
            runCatching { c.reco.refresh() }
            c.reco.recs.value.forYou.filter { ok(it.item) }.take(8).randomOrNull()?.let { pick = it.item; reason = it.reason.takeIf { r -> r.isNotBlank() } }
        }
        // 2) Otherwise something good they haven't watched.
        if (pick == null) pick = runCatching { c.jellyfin.randomUnwatched() }.getOrDefault(emptyList())
            .filter { ok(it) && (it.communityRating ?: 6.5) >= 6.0 }.take(10).maxByOrNull { (it.communityRating ?: 6.5) + Random.nextDouble(1.5) }
        val item = pick ?: return false

        val art = runCatching {
            c.http.newCall(okhttp3.Request.Builder().url(c.jellyfin.backdropUrl(cfg, item, 960)).build()).execute()
                .use { r -> if (r.isSuccessful) r.body?.bytes()?.let { android.graphics.BitmapFactory.decodeByteArray(it, 0, it.size) } else null }
        }.getOrNull()

        val titles = listOf(R.string.suggest_t1, R.string.suggest_t2, R.string.suggest_t3, R.string.suggest_t4)
        val name = item.name + (item.year?.let { " ($it)" } ?: "")
        val facts = listOfNotNull(item.genres.take(2).joinToString(" · ").takeIf { it.isNotBlank() },
            item.communityRating?.let { "★ %.1f".format(it) },
            item.runtimeMinutes?.takeIf { it > 0 }?.let { if (it >= 60) "${it / 60}h ${it % 60}m" else "${it}m" }).joinToString(" · ")
        val why = reason?.let { ctx.getString(R.string.suggest_because, it) } ?: item.overview?.take(140)?.let { if (item.overview.length > 140) "$it…" else it }
        val body = listOfNotNull(name, facts.takeIf { it.isNotBlank() }, why).joinToString("\n")

        fun open(id: Int, link: String) = PendingIntent.getActivity(ctx, id,
            Intent(Intent.ACTION_VIEW, Uri.parse(link)).setPackage(ctx.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notInterested = PendingIntent.getBroadcast(ctx, NOTIF_ID + 1,
            Intent(ctx, SuggestActionReceiver::class.java).putExtra("id", item.id), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val b = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_jv).setColor(0xFF1F80E0.toInt())
            .setLargeIcon(com.sridhar.harbor.radio.Avatar.bitmap(ctx))
            .setContentTitle(ctx.getString(titles.random()))
            .setContentText(name)
            .setStyle(if (art != null) NotificationCompat.BigPictureStyle().bigPicture(art).setSummaryText(listOf(name, facts).filter { it.isNotBlank() }.joinToString("\n"))
                else NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(open(NOTIF_ID, "jellyverse://open/item/${item.id}"))
            .addAction(0, ctx.getString(R.string.suggest_watch), open(NOTIF_ID + 2, "jellyverse://open/item/${item.id}"))
            .addAction(0, ctx.getString(R.string.suggest_not_interested), notInterested)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setAutoCancel(true).setOnlyAlertOnce(true)
        val nm = NotificationManagerCompat.from(ctx)
        val allowed = android.os.Build.VERSION.SDK_INT < 33 ||
            androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!allowed || !nm.areNotificationsEnabled()) return false
        channel(ctx)
        runCatching { nm.notify(NOTIF_ID, b.build()) }
        remember(ctx, item.id)
        return true
    }

    internal suspend fun runIfDue(ctx: Context) {
        val now = System.currentTimeMillis()
        if (!due(ctx, now)) return
        // Posted or not (e.g. nothing new to suggest), try again on a later evening rather than every 3 h.
        runCatching { suggest(ctx) }
        planNext(ctx, now)
    }
}

class SuggestWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result { Suggestions.runIfDue(applicationContext); return Result.success() }
}

/** "Not interested": never suggest this title again. */
class SuggestActionReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        intent.getStringExtra("id")?.let { Suggestions.skip(ctx, it) }
        NotificationManagerCompat.from(ctx).cancel(41_000)
    }
}
