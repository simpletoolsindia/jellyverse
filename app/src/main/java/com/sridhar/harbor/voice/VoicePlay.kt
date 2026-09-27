package com.sridhar.harbor.voice

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.provider.MediaStore
import android.widget.Toast
import com.sridhar.harbor.L10n
import com.sridhar.harbor.R
import com.sridhar.harbor.data.AppContainer
import com.sridhar.harbor.ui.player.PlayerActivity

/**
 * Google Assistant / system voice search: "Play Leo on JellyVerse", "Play Anirudh songs".
 * Videos come from Jellyfin (strict title match so the wrong film never starts), music from Navidrome.
 */
object VoicePlay {
    fun isVoiceSearch(intent: Intent?) = intent?.action == MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH

    suspend fun handle(ctx: Context, c: AppContainer, intent: Intent): Boolean {
        val query = intent.getStringExtra(SearchManager.QUERY)?.trim().orEmpty()
        val focus = intent.getStringExtra(MediaStore.EXTRA_MEDIA_FOCUS).orEmpty()
        val cfg = c.settings.current()
        val wantsMusic = focus.contains("audio") || focus.contains("artist") || focus.contains("album") ||
            Regex("""\b(song|songs|music|album|playlist|track)\b""", RegexOption.IGNORE_CASE).containsMatchIn(query)

        // "Play something" with no title: resume whatever was playing.
        if (query.isBlank()) {
            if (c.musicEngine.state.value.current != null) { c.musicEngine.toggle(); return true }
            return false
        }
        val clean = query.replace(Regex("""\b(songs?|music|by|the album|album|playlist)\b""", RegexOption.IGNORE_CASE), " ").replace(Regex("\\s+"), " ").trim()

        if (!wantsMusic && cfg.jellyfinReady && playVideo(ctx, c, clean)) return true
        if (cfg.navidromeReady && playMusic(c, clean)) return true
        if (wantsMusic && cfg.jellyfinReady && playVideo(ctx, c, clean)) return true

        Toast.makeText(ctx, L10n.s(R.string.voice_not_found, query), Toast.LENGTH_LONG).show()
        return false
    }

    private suspend fun playVideo(ctx: Context, c: AppContainer, q: String): Boolean {
        val hit = runCatching { c.jellyfin.fuzzyFind(q, minScore = 0.8f) }.getOrDefault(emptyList()).firstOrNull() ?: return false
        val target = if (hit.type == "Series") runCatching { c.jellyfin.nextUpFor(hit.id) }.getOrNull() ?: return false else hit
        PlayerActivity.start(ctx, target.id)
        return true
    }

    private suspend fun playMusic(c: AppContainer, q: String): Boolean {
        val r = runCatching { c.music.search(q) }.getOrNull() ?: return false
        val artist = r.artist.firstOrNull { com.sridhar.harbor.data.music.MusicText.artistKey(it.name) == com.sridhar.harbor.data.music.MusicText.artistKey(q) }
        val songs = when {
            artist != null -> c.music.topSongs(artist.name, 30).ifEmpty { r.song }
            r.song.isNotEmpty() -> r.song
            r.album.isNotEmpty() -> c.music.album(r.album.first().id).songs
            else -> emptyList()
        }
        if (songs.isEmpty()) return false
        c.musicEngine.play(songs, source = L10n.s(R.string.voice_source, q))
        return true
    }
}
