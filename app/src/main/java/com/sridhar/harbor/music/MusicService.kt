package com.sridhar.harbor.music

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import android.os.Bundle
import androidx.media3.session.CommandButton
import androidx.media3.session.LibraryResult
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.sridhar.harbor.HarborApp
import com.sridhar.harbor.L10n
import com.sridhar.harbor.R
import com.sridhar.harbor.data.music.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.guava.future

/**
 * Background music: notification + lock screen + Bluetooth controls, and a browsable library
 * for Android Auto / car head units and other media controllers.
 */
@OptIn(UnstableApi::class)
class MusicService : MediaLibraryService() {
    private companion object { const val CMD_LIKE = "jv.like"; const val CMD_SHUFFLE = "jv.shuffle" }
    private var session: MediaLibrarySession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main + com.sridhar.harbor.CrashGuard)
    private val container get() = (application as HarborApp).container

    override fun onCreate() {
        super.onCreate()
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(Intent.ACTION_VIEW, android.net.Uri.parse("jellyverse://open/nowplaying")).setPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaLibrarySession.Builder(this, container.musicEngine.player, LibraryCallback())
            .setSessionActivity(open).build()
        // Like + shuffle buttons in the notification / lock screen, kept in sync with the app.
        scope.launch {
            container.musicEngine.state.map { st -> Triple(st.current?.let { it.streamUrl == null }, st.current?.id in st.likedIds, st.shuffle) }
                .distinctUntilChanged().collect { (likeable, liked, shuffle) -> session?.setCustomLayout(buttons(likeable == true, liked, shuffle)) }
        }
    }

    private fun buttons(likeable: Boolean, liked: Boolean, shuffle: Boolean): ImmutableList<CommandButton> {
        val out = ImmutableList.builder<CommandButton>()
        if (likeable) out.add(CommandButton.Builder().setDisplayName(L10n.s(if (liked) R.string.mu_unlike else R.string.mu_like))
            .setIconResId(if (liked) androidx.media3.session.R.drawable.media3_icon_heart_filled else androidx.media3.session.R.drawable.media3_icon_heart_unfilled)
            .setSessionCommand(SessionCommand(CMD_LIKE, Bundle.EMPTY)).build())
        out.add(CommandButton.Builder().setDisplayName(L10n.s(R.string.mu_shuffle))
            .setIconResId(if (shuffle) androidx.media3.session.R.drawable.media3_icon_shuffle_on else androidx.media3.session.R.drawable.media3_icon_shuffle_off)
            .setSessionCommand(SessionCommand(CMD_SHUFFLE, Bundle.EMPTY)).build())
        return out.build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = session

    /** Stop when the user swipes the app away while paused (keep going if music is playing). */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        session?.run { release() }; session = null
        scope.cancel()
        super.onDestroy()
    }

    private fun folder(id: String, title: String) = MediaItem.Builder().setMediaId(id).setMediaMetadata(
        MediaMetadata.Builder().setTitle(title).setIsBrowsable(true).setIsPlayable(false).setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED).build(),
    ).build()

    private suspend fun songItem(s: Song, parent: String): MediaItem {
        val cfg = container.settings.current()
        return MediaItem.Builder().setMediaId("$parent|${s.id}").setMediaMetadata(
            MediaMetadata.Builder().setTitle(s.displayTitle).setArtist(s.displayArtist).setAlbumTitle(s.displayAlbum)
                .setArtworkUri(container.music.coverUrl(cfg, s.coverArt, 300)?.let(android.net.Uri::parse))
                .setIsBrowsable(false).setIsPlayable(true).setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC).build(),
        ).build()
    }

    private suspend fun songsFor(parent: String): List<Song> {
        val repo = container.music
        return when {
            parent == "liked" -> repo.starred().song
            parent == "mix" -> repo.randomSongs(50)
            parent.startsWith("pl:") -> repo.playlist(parent.removePrefix("pl:")).songs
            parent.startsWith("al:") -> repo.album(parent.removePrefix("al:")).songs
            else -> emptyList()
        }
    }

    private inner class LibraryCallback : MediaLibrarySession.Callback {
        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
            val cmds = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
                .add(SessionCommand(CMD_LIKE, Bundle.EMPTY)).add(SessionCommand(CMD_SHUFFLE, Bundle.EMPTY)).build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session).setAvailableSessionCommands(cmds).build()
        }

        override fun onCustomCommand(session: MediaSession, controller: MediaSession.ControllerInfo, customCommand: SessionCommand, args: Bundle): ListenableFuture<SessionResult> {
            val e = container.musicEngine
            when (customCommand.customAction) {
                CMD_LIKE -> e.state.value.current?.let(e::toggleLike)
                CMD_SHUFFLE -> e.setShuffle(!e.state.value.shuffle)
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        override fun onGetLibraryRoot(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, params: LibraryParams?): ListenableFuture<LibraryResult<MediaItem>> =
            Futures.immediateFuture(LibraryResult.ofItem(folder("root", "JellyVerse Music"), params))

        override fun onGetChildren(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, parentId: String, page: Int, pageSize: Int, params: LibraryParams?) =
            scope.future {
                val repo = container.music
                val items: List<MediaItem> = runCatching {
                    when (parentId) {
                        "root" -> listOf(folder("recent", L10n.s(R.string.mu_recently_played)), folder("playlists", L10n.s(R.string.mu_playlists)), folder("liked", L10n.s(R.string.mu_liked_songs)), folder("mix", L10n.s(R.string.mu_shuffle_mix)))
                        "recent" -> repo.albums("recent", 30).map { folder("al:${it.id}", it.displayName) }
                        "playlists" -> repo.playlists().map { folder("pl:${it.id}", it.name) }
                        else -> songsFor(parentId).map { songItem(it, parentId) }
                    }
                }.getOrDefault(emptyList())
                LibraryResult.ofItemList(ImmutableList.copyOf(items), params)
            }

        /** A controller picked a song from the browse tree: queue its whole folder, starting there. */
        override fun onSetMediaItems(mediaSession: MediaSession, controller: MediaSession.ControllerInfo, mediaItems: MutableList<MediaItem>, startIndex: Int, startPositionMs: Long) =
            scope.future {
                val picked = mediaItems.firstOrNull()?.mediaId.orEmpty()
                val parent = picked.substringBefore('|'); val songId = picked.substringAfter('|')
                val songs = runCatching { songsFor(parent) }.getOrDefault(emptyList())
                container.musicEngine.play(songs, songs.indexOfFirst { it.id == songId }.coerceAtLeast(0))
                // The engine already set the playlist; hand back the current items so Media3 doesn't replace them.
                MediaSession.MediaItemsWithStartPosition(emptyList(), 0, 0)
            }
    }
}
