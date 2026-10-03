package com.sridhar.harbor.tv

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import androidx.tvprovider.media.tv.PreviewChannel
import androidx.tvprovider.media.tv.PreviewChannelHelper
import androidx.tvprovider.media.tv.PreviewProgram
import androidx.tvprovider.media.tv.TvContractCompat
import com.sridhar.harbor.data.AppContainer
import com.sridhar.harbor.data.jellyfin.BaseItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A "JellyVerse" row on the Android TV / Google TV home screen (Mi TV in Android TV mode, Mi Box…): this week's
 * Top 10 and new arrivals with posters. Selecting one opens its page in the app. Refreshed whenever you leave.
 */
@android.annotation.SuppressLint("RestrictedApi")
object HomeChannel {
    suspend fun sync(context: Context, c: AppContainer) = withContext(Dispatchers.IO) {
        val cfg = c.config.value?.takeIf { it.jellyfinReady } ?: return@withContext
        val helper = PreviewChannelHelper(context)
        val prefs = context.getSharedPreferences("harbor_homechannel", Context.MODE_PRIVATE)
        var channelId = prefs.getLong("channel", -1L)
        if (channelId < 0 || runCatching { helper.getPreviewChannel(channelId) }.getOrNull() == null) {
            val logo = android.graphics.Bitmap.createBitmap(160, 160, android.graphics.Bitmap.Config.ARGB_8888).also {
                com.sridhar.harbor.ui.ai.PetArt.draw(android.graphics.Canvas(it), 160f)
            }
            channelId = helper.publishChannel(PreviewChannel.Builder()
                .setDisplayName("JellyVerse")
                .setDescription(context.getString(com.sridhar.harbor.R.string.tv_channel_desc))
                .setAppLinkIntentUri(Uri.parse("jellyversetv://home"))
                .setLogo(logo)
                .build())
            prefs.edit().putLong("channel", channelId).apply()
            // Ask the launcher to show the row (the default channel is shown without asking on most launchers).
            runCatching { TvContractCompat.requestChannelBrowsable(context, channelId) }
        }
        val parental = c.parental
        val picks: List<BaseItem> = (runCatching { c.jellyfin.top10() }.getOrDefault(emptyList()) +
            runCatching { c.jellyfin.recentlyAdded(12) }.getOrDefault(emptyList()))
            .filterNot { parental.hideFromHome(it) }.distinctBy { it.id }.take(16)
        if (picks.isEmpty()) return@withContext
        // Replace the previous programs.
        prefs.getStringSet("programs", emptySet())!!.forEach { id -> runCatching { helper.deletePreviewProgram(id.toLong()) } }
        val ids = picks.mapIndexedNotNull { i, item ->
            val program = PreviewProgram.Builder()
                .setChannelId(channelId)
                .setType(if (item.type == "Series") TvContractCompat.PreviewPrograms.TYPE_TV_SERIES else TvContractCompat.PreviewPrograms.TYPE_MOVIE)
                .setTitle(item.name)
                .setDescription(item.overview?.take(200).orEmpty())
                .setPosterArtUri(Uri.parse(c.jellyfin.posterUrl(cfg, item, 500)))
                .setPosterArtAspectRatio(TvContractCompat.PreviewPrograms.ASPECT_RATIO_2_3)
                .setIntentUri(Uri.parse("jellyversetv://item/${item.id}"))
                .setInternalProviderId(item.id)
                .setWeight(picks.size - i)
                .apply { item.year?.let { setReleaseDate(it.toString()) } }
                .build()
            runCatching { helper.publishPreviewProgram(program) }.getOrNull()?.toString()
        }
        prefs.edit().putStringSet("programs", ids.toSet()).apply()
    }
}
