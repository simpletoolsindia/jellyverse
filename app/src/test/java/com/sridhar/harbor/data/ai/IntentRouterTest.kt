package com.sridhar.harbor.data.ai

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

class IntentRouterTest {

    @Test fun play_onPhone() {
        assertThat(IntentRouter.route("can you play aaranmanai")).isEqualTo(Intent("play", mapOf("title" to "aaranmanai", "device" to "")))
    }

    @Test fun play_onTv() {
        val i = IntentRouter.route("play Aranmanai in tv")!!
        assertThat(i.tool).isEqualTo("play")
        assertThat(i.args["title"]).isEqualTo("Aranmanai")
        assertThat(i.args["device"]).isEqualTo("tv")
    }

    @Test fun cast_defaultsToTv() {
        assertThat(IntentRouter.route("cast Leo")!!.args["device"]).isEqualTo("tv")
    }

    @Test fun speedLimit_parsesMegabytes() {
        val i = IntentRouter.route("limit download speed to 5 MB")!!
        assertThat(i.tool).isEqualTo("set_speed_limit")
        assertThat(i.args["download_mbps"]).isEqualTo("5")
    }

    @Test fun magnetLinks_areDownloads() {
        assertThat(IntentRouter.route("magnet:?xt=urn:btih:abc")!!.tool).isEqualTo("add_download")
    }

    @Test fun commonIntents() {
        mapOf(
            "pause all" to "pause_downloads",
            "resume everything" to "resume_downloads",
            "how is the server cpu" to "server_status",
            "fix posters" to "fix_library",
            "what's upcoming this week" to "upcoming",
            "recommend a horror movie" to "suggest",
            "tamil movies from 2023" to "smart_search",
            "request Dune Part Two" to "request",
            "turtle mode off" to "slow_mode",
        ).forEach { (msg, tool) -> assertWithMessage(msg).that(IntentRouter.route(msg)?.tool).isEqualTo(tool) }
    }

    @Test fun suggest_extractsGenre() {
        assertThat(IntentRouter.route("suggest a comedy")!!.args["genre"]).isEqualTo("comedy")
    }

    @Test fun smallTalk_goesToTheModel() {
        assertThat(IntentRouter.route("hello there")).isNull()
    }

    @Test fun radioAndMusicRouteBeforeMoviesAndTorrents() {
        assertThat(IntentRouter.route("play Sooriyan FM")?.tool).isEqualTo("play_radio")
        assertThat(IntentRouter.route("play Sooriyan FM")?.args?.get("station")).isEqualTo("Sooriyan FM")
        assertThat(IntentRouter.route("pause the music")?.tool).isEqualTo("music_control")
        assertThat(IntentRouter.route("next song")?.args?.get("action")).isEqualTo("next")
        assertThat(IntentRouter.route("play songs by Anirudh")?.tool).isEqualTo("play_music")
        assertThat(IntentRouter.route("play songs by Anirudh")?.args?.get("query")).isEqualTo("Anirudh")
        assertThat(IntentRouter.route("what's playing")?.tool).isEqualTo("now_playing")
        assertThat(IntentRouter.route("sleep timer 20 min")?.args?.get("minutes")).isEqualTo("20")
        // Unchanged: movies and torrents still go where they did.
        assertThat(IntentRouter.route("play Leo on tv")?.tool).isEqualTo("play")
        assertThat(IntentRouter.route("pause all")?.tool).isEqualTo("pause_downloads")
    }

    @Test fun libraryHelpers() {
        assertThat(IntentRouter.route("mark Leo as watched")?.args?.get("title")).isEqualTo("leo")
        assertThat(IntentRouter.route("remove Hungry from continue watching")?.tool).isEqualTo("remove_from_continue")
        assertThat(IntentRouter.route("scan the library")?.tool).isEqualTo("scan_library")
        assertThat(IntentRouter.route("check for app update")?.tool).isEqualTo("check_update")
        assertThat(IntentRouter.route("how many movies do I have")?.tool).isEqualTo("library_stats")
    }
}
