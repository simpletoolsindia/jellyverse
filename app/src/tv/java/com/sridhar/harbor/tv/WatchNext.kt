package com.sridhar.harbor.tv

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import androidx.tvprovider.media.tv.TvContractCompat
import androidx.tvprovider.media.tv.WatchNextProgram
import com.sridhar.harbor.data.AppContainer
import com.sridhar.harbor.data.jellyfin.BaseItem
import com.sridhar.harbor.data.jellyfin.TICKS_PER_MS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Mirrors Continue Watching / Next Up into the Android TV home "Play Next" row. */
// tvprovider 1.0.0 mis-annotates its public WatchNextProgram builder as @RestrictTo; the API is documented and supported.
@android.annotation.SuppressLint("RestrictedApi")
object WatchNext {
    suspend fun sync(context: Context, c: AppContainer) = withContext(Dispatchers.IO) {
        val cfg = c.config.value?.takeIf { it.jellyfinReady } ?: return@withContext
        val resume = runCatching { c.jellyfin.resume().take(8) }.getOrDefault(emptyList())
        val next = runCatching { c.jellyfin.nextUp().take(6) }.getOrDefault(emptyList())
        val prefs = context.getSharedPreferences("harbor_watchnext", Context.MODE_PRIVATE)
        val resolver = context.contentResolver
        // Replace the previous set so finished titles disappear.
        prefs.all.values.filterIsInstance<Long>().forEach { id -> runCatching { resolver.delete(TvContractCompat.buildWatchNextProgramUri(id), null, null) } }
        val edit = prefs.edit().clear()
        (resume.map { it to TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE } +
            next.map { it to TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_NEXT })
            .distinctBy { it.first.id }
            .forEachIndexed { i, (item, type) ->
                val program = build(item, type, c.jellyfin.thumbUrl(cfg, item, 640), System.currentTimeMillis() - i * 1000L)
                runCatching { resolver.insert(TvContractCompat.WatchNextPrograms.CONTENT_URI, program.toContentValues()) }
                    .getOrNull()?.let { edit.putLong(item.id, ContentUris.parseId(it)) }
            }
        edit.apply()
    }

    private fun build(item: BaseItem, type: Int, art: String, engaged: Long): WatchNextProgram {
        val episode = item.type == "Episode"
        return WatchNextProgram.Builder()
            .setType(if (episode) TvContractCompat.WatchNextPrograms.TYPE_TV_EPISODE else TvContractCompat.WatchNextPrograms.TYPE_MOVIE)
            .setWatchNextType(type)
            .setLastEngagementTimeUtcMillis(engaged)
            .setTitle(item.seriesName ?: item.name)
            .apply {
                if (episode) {
                    setEpisodeTitle(item.name)
                    item.seasonNumber?.let { setSeasonNumber(it) }
                    item.indexNumber?.let { setEpisodeNumber(it) }
                }
            }
            .setDescription(item.overview?.take(250).orEmpty())
            .setPosterArtUri(Uri.parse(art))
            .setPosterArtAspectRatio(TvContractCompat.PreviewPrograms.ASPECT_RATIO_16_9)
            .setDurationMillis(((item.runTimeTicks ?: 0) / TICKS_PER_MS).toInt())
            .setLastPlaybackPositionMillis(((item.userData?.positionTicks ?: 0) / TICKS_PER_MS).toInt())
            .setIntentUri(Uri.parse("jellyversetv://play/${item.id}"))
            .setInternalProviderId(item.id)
            .build()
    }
}
