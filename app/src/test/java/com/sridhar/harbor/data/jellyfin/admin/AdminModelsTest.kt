package com.sridhar.harbor.data.jellyfin.admin

import com.google.common.truth.Truth.assertThat
import com.sridhar.harbor.data.HarborJson
import org.junit.Test

class AdminModelsTest {
    private fun session(json: String) = HarborJson.decodeFromString(AdminSession.serializer(), json)

    @Test fun progressAndDirectPlay() {
        val s = session("""{"Id":"s","NowPlayingItem":{"Name":"Leo","ProductionYear":2023,"RunTimeTicks":1000},"PlayState":{"PositionTicks":250,"PlayMethod":"DirectPlay"}}""")
        assertThat(s.progress).isWithin(0.001f).of(0.25f)
        assertThat(s.playMethodLabel).isEqualTo("Direct play")
        assertThat(s.nowPlaying!!.title).isEqualTo("Leo · 2023")
    }

    @Test fun transcodingLabelShowsCodec() {
        val s = session("""{"Id":"s","NowPlayingItem":{"Name":"Ep","SeriesName":"Dark","ParentIndexNumber":1,"IndexNumber":3},"TranscodingInfo":{"VideoCodec":"h264","IsVideoDirect":false,"IsAudioDirect":true}}""")
        assertThat(s.playMethodLabel).isEqualTo("Transcoding · H264")
        assertThat(s.nowPlaying!!.title).isEqualTo("Dark · S1E3 · Ep")
    }

    @Test fun idleSessionHasNoProgress() {
        assertThat(session("""{"Id":"s","PlayState":{}}""").progress).isEqualTo(0f)
    }

    @Test fun pluginEnabledStates() {
        assertThat(PluginInfo("p", status = "Active").enabled).isTrue()
        assertThat(PluginInfo("p", status = "Restart").enabled).isTrue()
        assertThat(PluginInfo("p", status = "Disabled").enabled).isFalse()
    }
}
