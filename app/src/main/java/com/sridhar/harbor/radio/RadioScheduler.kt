package com.sridhar.harbor.radio

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.sridhar.harbor.L10n
import com.sridhar.harbor.MainActivity
import com.sridhar.harbor.R

/**
 * Timed radio recordings and listening reminders, via AlarmManager.
 * Exact alarms when the user allows them (Settings → Alarms & reminders), otherwise the closest the system allows.
 * Alarms are restored after a reboot.
 */
object RadioScheduler {
    fun canExact(ctx: Context) = Build.VERSION.SDK_INT < 31 || ctx.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    fun add(ctx: Context, s: RadioSchedule) { RadioLibrary.putSchedule(s); arm(ctx, s) }

    fun cancel(ctx: Context, id: String) {
        ctx.getSystemService(AlarmManager::class.java).cancel(pi(ctx, id))
        RadioLibrary.removeSchedule(id)
    }

    /** After a run: daily ones move to tomorrow, one-offs are removed. */
    fun afterRun(ctx: Context, id: String) {
        val s = RadioLibrary.schedule(id) ?: return
        if (s.daily) {
            var next = s.startAt
            while (next <= System.currentTimeMillis()) next += 24 * 3_600_000L
            add(ctx, s.copy(startAt = next))
        } else RadioLibrary.removeSchedule(id)
    }

    fun rearmAll(ctx: Context) = RadioLibrary.schedules.value.forEach { s ->
        if (s.startAt > System.currentTimeMillis()) arm(ctx, s) else afterRun(ctx, s.id)
    }

    private fun pi(ctx: Context, id: String) = PendingIntent.getBroadcast(ctx, id.hashCode(),
        Intent(ctx, RadioAlarmReceiver::class.java).putExtra("id", id), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    private fun arm(ctx: Context, s: RadioSchedule) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        if (canExact(ctx)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, s.startAt, pi(ctx, s.id))
        else am.setWindow(AlarmManager.RTC_WAKEUP, s.startAt, 60_000, pi(ctx, s.id))
    }
}

class RadioAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        RadioLibrary.init(ctx)
        val s = RadioLibrary.schedule(intent.getStringExtra("id") ?: return) ?: return
        RecordService.channels(ctx)
        // Done with this slot right away (daily → tomorrow, one-off → removed), so a recording that gets killed
        // can't leave a stale schedule behind.
        RadioScheduler.afterRun(ctx, s.id)
        // Far too late (phone was off, clock changed): don't start a recording or ping at the wrong time.
        if (System.currentTimeMillis() - s.startAt > (if (s.kind == "record") 15 else 30) * 60_000L) return
        if (s.kind == "record") {
            // Exact alarms may start a foreground service from the background; if the system refuses, ask the user.
            val ok = runCatching { RecordService.start(ctx, s.stationName, s.url, s.durationMin, s.id) }.isSuccess
            if (!ok) notify(ctx, s, L10n.s(R.string.rec_tap_to_start, s.stationName), "record/${s.stationId}/${s.durationMin}")
        } else {
            notify(ctx, s, L10n.s(R.string.remind_title, s.stationName), "radio/${s.stationId}")
        }
    }

    private fun notify(ctx: Context, s: RadioSchedule, title: String, deep: String) {
        val open = PendingIntent.getActivity(ctx, s.id.hashCode(),
            Intent(ctx, MainActivity::class.java).setAction(Intent.ACTION_VIEW).setData(android.net.Uri.parse("jellyverse://open/$deep")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        ctx.getSystemService(NotificationManager::class.java).notify(s.id.hashCode(), NotificationCompat.Builder(ctx, RecordService.CHANNEL_DONE)
            .setSmallIcon(R.drawable.ic_stat_rec).setLargeIcon(Avatar.bitmap(ctx))
            .setContentTitle(title).setContentText(L10n.s(R.string.remind_body))
            .setContentIntent(open).setAutoCancel(true)
            .addAction(0, L10n.s(R.string.remind_listen), open).build())
    }
}

/** Alarms don't survive a reboot – put them back. */
class RadioBootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED") return
        RadioLibrary.init(ctx); RadioScheduler.rearmAll(ctx)
    }
}
