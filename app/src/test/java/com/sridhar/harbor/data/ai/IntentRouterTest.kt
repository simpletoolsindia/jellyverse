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
}
