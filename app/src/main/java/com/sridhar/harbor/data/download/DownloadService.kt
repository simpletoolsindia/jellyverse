package com.sridhar.harbor.data.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.sridhar.harbor.HarborApp
import com.sridhar.harbor.L10n
import com.sridhar.harbor.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Keeps [SegmentedDownloader] running with the screen off and shows each download in the shade:
 * "Leo · 42% · 18.4 MB/s · 3 min left", with a Cancel action. Stops itself when nothing is left.
 */
class DownloadService : Service() {
    companion object {
        private const val CHANNEL = "jv_downloads_live"
        private const val SUMMARY_ID = 7_300
        private const val ACTION_CANCEL = "jv.download.cancel"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val nm by lazy { getSystemService(NotificationManager::class.java) }
    private val shown = HashSet<String>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        nm.createNotificationChannel(NotificationChannel(CHANNEL, L10n.s(R.string.dl_channel), NotificationManager.IMPORTANCE_LOW))
        ServiceCompat.startForeground(this, SUMMARY_ID, summary(L10n.s(R.string.dl_starting), ""),
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0)
        val dl = HarborApp.instance!!.container.downloader
        scope.launch {
            dl.state.collect { all ->
                val live = all.values.filter { it.status == DlStatus.Running || it.status == DlStatus.Queued }
                all.values.forEach { s -> post(s) }
                (shown - all.keys).forEach { nm.cancel(it.hashCode()); shown.remove(it) }
                if (live.isEmpty()) { ServiceCompat.stopForeground(this@DownloadService, ServiceCompat.STOP_FOREGROUND_REMOVE); stopSelf() }
                else {
                    val speed = live.sumOf { it.bytesPerSec }
                    nm.notify(SUMMARY_ID, summary(L10n.s(R.string.dl_summary, live.size), speed(speed)))
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) intent.getStringExtra("key")?.let { HarborApp.instance?.container?.downloader?.cancel(it, discard = false) }
        return START_NOT_STICKY
    }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }

    private fun summary(title: String, text: String): Notification = NotificationCompat.Builder(this, CHANNEL)
        .setSmallIcon(android.R.drawable.stat_sys_download).setContentTitle(title).setContentText(text)
        .setOngoing(true).setOnlyAlertOnce(true).setGroup("jv_dl").setGroupSummary(true).build()

    private fun post(s: DlState) {
        val id = s.key.hashCode()
        if (s.status == DlStatus.Done || s.status == DlStatus.Failed || s.status == DlStatus.Cancelled) {
            if (s.key in shown) {
                shown.remove(s.key)
                if (s.status == DlStatus.Cancelled) { nm.cancel(id); return }
                nm.notify(id, NotificationCompat.Builder(this, CHANNEL)
                    .setSmallIcon(if (s.status == DlStatus.Done) android.R.drawable.stat_sys_download_done else android.R.drawable.stat_notify_error)
                    .setContentTitle(s.title).setContentText(L10n.s(if (s.status == DlStatus.Done) R.string.dl_done else R.string.dl_failed))
                    .setAutoCancel(true).setGroup("jv_dl").build())
            }
            return
        }
        shown += s.key
        val cancel = PendingIntent.getService(this, id, Intent(this, DownloadService::class.java).setAction(ACTION_CANCEL).putExtra("key", s.key),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val pct = (s.fraction * 100).toInt()
        val eta = s.etaSec.takeIf { it >= 0 }?.let { if (it >= 3600) "${it / 3600} h ${it % 3600 / 60} min" else if (it >= 60) "${it / 60} min" else "$it s" }
        val text = listOfNotNull("$pct%", speed(s.bytesPerSec).takeIf { s.bytesPerSec > 0 }, eta?.let { L10n.s(R.string.dl_left, it) },
            if (s.parts > 1) L10n.s(R.string.dl_parts, s.parts) else null).joinToString(" · ")
        nm.notify(id, NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download).setContentTitle(s.title).setContentText(text)
            .setProgress(1000, (s.fraction * 1000).toInt(), s.total <= 0)
            .setOngoing(true).setOnlyAlertOnce(true).setSilent(true).setGroup("jv_dl")
            .addAction(0, L10n.s(R.string.cancel), cancel).build())
    }

    private fun speed(bps: Long) = when {
        bps >= 1_000_000 -> "%.1f MB/s".format(bps / 1_000_000.0)
        bps >= 1_000 -> "${bps / 1_000} KB/s"
        else -> "$bps B/s"
    }
}
