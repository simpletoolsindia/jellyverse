package com.sridhar.harbor.update

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.sridhar.harbor.HarborApp
import com.sridhar.harbor.L10n
import com.sridhar.harbor.R
import java.util.concurrent.TimeUnit

/** Daily background check for a new GitHub release; posts one notification per new version. */
class UpdateWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val updater = (applicationContext as HarborApp).container.updater
        if (!updater.enabled || !updater.autoCheck) return Result.success()
        val info = runCatching { updater.checkQuietly() }.getOrNull() ?: return Result.success()
        if (!updater.markNotified(info.version)) return Result.success()   // already told the user about this one
        notify(applicationContext, info)
        return Result.success()
    }

    companion object {
        private const val WORK = "jellyverse-update-check"
        private const val CHANNEL = "updates"

        /** (Re)schedules or cancels the daily check according to the user's setting. */
        fun schedule(context: Context) {
            val wm = WorkManager.getInstance(context)
            val updater = (context.applicationContext as HarborApp).container.updater
            if (!updater.enabled || !updater.autoCheck) { wm.cancelUniqueWork(WORK); return }
            wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<UpdateWorker>(24, TimeUnit.HOURS, 6, TimeUnit.HOURS)
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).setRequiresBatteryNotLow(true).build())
                    .build())
        }

        private fun notify(context: Context, info: UpdateInfo) {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, L10n.s(R.string.update_channel), NotificationManager.IMPORTANCE_DEFAULT))
            // Opens the app, whose update popup takes it from there.
            val open = context.packageManager.getLaunchIntentForPackage(context.packageName)
                ?: Intent(Intent.ACTION_VIEW, Uri.parse("jellyverse://open/watch"))
            val pi = PendingIntent.getActivity(context, 7, open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val n = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_launcher_monochrome).setColor(0xFF1F80E0.toInt())
                .setContentTitle(L10n.s(R.string.update_notif_title, info.version))
                .setContentText(L10n.s(R.string.update_notif_body))
                .setAutoCancel(true).setContentIntent(pi).build()
            runCatching { NotificationManagerCompat.from(context).notify(7001, n) }
        }
    }
}
