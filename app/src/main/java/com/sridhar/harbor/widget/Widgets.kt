package com.sridhar.harbor.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import com.sridhar.harbor.HarborApp
import com.sridhar.harbor.L10n
import com.sridhar.harbor.MainActivity
import com.sridhar.harbor.R
import com.sridhar.harbor.data.music.RadioStation
import com.sridhar.harbor.radio.RadioLibrary
import com.sridhar.harbor.radio.RecordService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.text.DateFormat

/**
 * Home-screen widgets: Radio (play / next / previous / record), Live TV (favourite channels) and Radio
 * recording (next schedule or the live recording, Record now / Schedule). Taps go through [WidgetActions].
 */
object Widgets {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main + com.sridhar.harbor.CrashGuard)

    private fun ids(ctx: Context, cls: Class<*>) = AppWidgetManager.getInstance(ctx).getAppWidgetIds(ComponentName(ctx, cls))
    fun anyRadio(ctx: Context) = ids(ctx, RadioWidget::class.java).isNotEmpty()
    fun anyRecord(ctx: Context) = ids(ctx, RecordWidget::class.java).isNotEmpty()

    fun updateAll(ctx: Context) { updateRadio(ctx); updateRecord(ctx); updateLiveTv(ctx) }

    internal fun action(ctx: Context, action: String, extra: String? = null, code: Int = action.hashCode()) = PendingIntent.getBroadcast(ctx, code + (extra?.hashCode() ?: 0),
        Intent(ctx, WidgetActions::class.java).setAction(action).putExtra("x", extra), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    internal fun open(ctx: Context, link: String, code: Int) = PendingIntent.getActivity(ctx, code,
        Intent(ctx, MainActivity::class.java).setAction(Intent.ACTION_VIEW).setData(Uri.parse(link)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    /** The station shown: what's playing, else the last one played, else the first saved. */
    internal fun station(ctx: Context): RadioStation? {
        val c = HarborApp.instance?.container ?: return null
        val list = c.radio.stations.value
        val cur = if (c.musicEngineCreated) c.musicEngine.state.value.current else null
        val id = cur?.id?.removePrefix("radio:")?.takeIf { cur.id.startsWith("radio:") }
            ?: ctx.getSharedPreferences("harbor_widget", Context.MODE_PRIVATE).getString("last_station", null)
        return list.firstOrNull { it.id == id } ?: list.firstOrNull()
    }

    fun updateRadio(ctx: Context) {
        val ids = ids(ctx, RadioWidget::class.java); if (ids.isEmpty()) return
        val c = HarborApp.instance?.container
        val st = station(ctx)
        val state = if (c?.musicEngineCreated == true) c.musicEngine.state.value else null
        val playingRadio = state?.current?.id == st?.let { "radio:${it.id}" } && state?.playing == true
        val v = RemoteViews(ctx.packageName, R.layout.widget_radio).apply {
            setImageViewBitmap(R.id.w_dog, com.sridhar.harbor.radio.Avatar.bitmap(ctx))
            setTextViewText(R.id.w_title, st?.name ?: L10n.s(R.string.widget_radio_none))
            setTextViewText(R.id.w_sub, if (RadioLibrary.live.value != null) L10n.s(R.string.rec_live_short) else if (playingRadio) L10n.s(R.string.radio_live) else L10n.s(R.string.widget_radio_tap))
            setViewVisibility(R.id.w_live, if (playingRadio) View.VISIBLE else View.GONE)
            setImageViewResource(R.id.w_toggle, if (playingRadio) R.drawable.wic_pause else R.drawable.wic_play)
            setOnClickPendingIntent(R.id.w_toggle, action(ctx, "radio_toggle"))
            setOnClickPendingIntent(R.id.w_next, action(ctx, "radio_next"))
            setOnClickPendingIntent(R.id.w_prev, action(ctx, "radio_prev"))
            setOnClickPendingIntent(R.id.w_rec, action(ctx, "radio_record"))
            setOnClickPendingIntent(R.id.w_root, open(ctx, "jellyverse://open/music", 7101))
        }
        AppWidgetManager.getInstance(ctx).updateAppWidget(ids, v)
    }

    fun updateRecord(ctx: Context) {
        val ids = ids(ctx, RecordWidget::class.java); if (ids.isEmpty()) return
        val live = RadioLibrary.live.value
        val next = RadioLibrary.schedules.value.filter { it.kind == "record" }.minByOrNull { it.startAt }
        val fmt = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
        val v = RemoteViews(ctx.packageName, R.layout.widget_record).apply {
            when {
                live != null -> {
                    setTextViewText(R.id.w_main, "● " + live.station)
                    val el = System.currentTimeMillis() - live.startedAt
                    setTextViewText(R.id.w_sub, L10n.s(R.string.widget_recording_now, (el / 60_000).toInt(), (live.durationMs / 60_000).toInt()))
                    setViewVisibility(R.id.w_progress, View.VISIBLE)
                    setProgressBar(R.id.w_progress, 1000, (el * 1000 / live.durationMs.coerceAtLeast(1)).toInt(), false)
                }
                next != null -> {
                    setTextViewText(R.id.w_main, next.stationName)
                    setTextViewText(R.id.w_sub, L10n.s(R.string.widget_next_recording, fmt.format(next.startAt), next.durationMin))
                    setViewVisibility(R.id.w_progress, View.GONE)
                }
                else -> {
                    setTextViewText(R.id.w_main, L10n.s(R.string.widget_no_schedule))
                    setTextViewText(R.id.w_sub, L10n.s(R.string.widget_record_hint))
                    setViewVisibility(R.id.w_progress, View.GONE)
                }
            }
            setTextViewText(R.id.w_record_now, L10n.s(if (live != null) R.string.rec_stop else R.string.widget_record_now))
            setOnClickPendingIntent(R.id.w_record_now, action(ctx, if (live != null) "rec_stop" else "radio_record"))
            setOnClickPendingIntent(R.id.w_schedule, open(ctx, "jellyverse://open/recordings", 7102))
            setOnClickPendingIntent(R.id.w_root, open(ctx, "jellyverse://open/recordings", 7103))
        }
        AppWidgetManager.getInstance(ctx).updateAppWidget(ids, v)
    }

    /** Favourite channels (or the first ones of your playlist) – one tap to watch. */
    fun updateLiveTv(ctx: Context) {
        val ids = ids(ctx, LiveTvWidget::class.java); if (ids.isEmpty()) return
        val c = HarborApp.instance?.container ?: return
        scope.launch {
            val favs = c.iptv.favorites.value
            val picks = mutableListOf<Pair<String, com.sridhar.harbor.data.iptv.Channel>>()
            for (p in c.iptv.playlists.value) {
                val chs = runCatching { c.iptv.channels(p) }.getOrDefault(emptyList())
                picks += chs.filter { it.id in favs }.map { p.id to it }
            }
            if (picks.isEmpty()) c.iptv.playlists.value.firstOrNull()?.let { p -> picks += runCatching { c.iptv.channels(p) }.getOrDefault(emptyList()).take(4).map { p.id to it } }
            val views = listOf(R.id.w_ch0, R.id.w_ch1, R.id.w_ch2, R.id.w_ch3)
            val v = RemoteViews(ctx.packageName, R.layout.widget_livetv).apply {
                views.forEachIndexed { i, vid ->
                    val pick = picks.getOrNull(i)
                    if (pick == null) { setTextViewText(vid, if (i == 0) L10n.s(R.string.widget_livetv_empty) else ""); setOnClickPendingIntent(vid, open(ctx, "jellyverse://open/live", 7110 + i)) }
                    else { setTextViewText(vid, pick.second.name); setOnClickPendingIntent(vid, action(ctx, "live_play", pick.first + "|" + pick.second.id, 7120 + i)) }
                }
                setOnClickPendingIntent(R.id.w_head, open(ctx, "jellyverse://open/live", 7104))
            }
            AppWidgetManager.getInstance(ctx).updateAppWidget(ids, v)
        }
    }
}

class RadioWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, m: AppWidgetManager, ids: IntArray) = Widgets.updateRadio(ctx)
}
class RecordWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, m: AppWidgetManager, ids: IntArray) = Widgets.updateRecord(ctx)
}
class LiveTvWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, m: AppWidgetManager, ids: IntArray) = Widgets.updateLiveTv(ctx)
}

/** Widget button presses. */
class WidgetActions : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val c = HarborApp.instance?.container ?: return
        val stations = c.radio.stations.value
        fun play(st: RadioStation) {
            ctx.getSharedPreferences("harbor_widget", Context.MODE_PRIVATE).edit().putString("last_station", st.id).apply()
            c.musicEngine.play(listOf(st.toSong()), source = st.name)
        }
        when (intent.action) {
            "radio_toggle" -> {
                val st = Widgets.station(ctx) ?: return
                val s = c.musicEngine.state.value
                if (s.current?.id == "radio:${st.id}") c.musicEngine.toggle() else play(st)
            }
            "radio_next", "radio_prev" -> {
                if (stations.isEmpty()) return
                val cur = Widgets.station(ctx)
                val i = stations.indexOfFirst { it.id == cur?.id }.coerceAtLeast(0)
                play(stations[(i + if (intent.action == "radio_next") 1 else -1).mod(stations.size)])
            }
            "radio_record" -> Widgets.station(ctx)?.let { st -> runCatching { RecordService.start(ctx, st.name, st.url, 60) } }
            "rec_stop" -> RecordService.stop(ctx)
            "live_play" -> intent.getStringExtra("x")?.split('|')?.takeIf { it.size == 2 }?.let { (pl, ch) ->
                com.sridhar.harbor.ui.player.PlayerActivity.startLive(ctx, pl, ch)
            }
        }
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ Widgets.updateAll(ctx) }, 600)
    }
}
