package com.sridhar.harbor.data.iptv

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class M3uParserTest {
    private val playlist = Playlist(id = "p1", name = "Test", url = "http://x/list.m3u", userAgent = "PlaylistUA")

    private val sample = """
        #EXTM3U x-tvg-url="http://epg.example/guide.xml.gz,http://backup"
        #EXTINF:-1 tvg-id="SunTV.in" tvg-logo="http://logo/sun.png" group-title="Tamil",Sun TV
        http://stream/sun.m3u8
        #EXTINF:-1 tvg-name="Raj News" user-agent="Custom/1.0",Raj News HD
        #EXTVLCOPT:http-referrer=http://ref.example/
        http://stream/raj.m3u8
        #EXTINF:-1,
        #EXTGRP:Music
        http://stream/untitled.m3u8
        # stray comment
        http://orphan-url-without-extinf
    """.trimIndent()

    @Test fun parsesChannelsAndAttributes() {
        val (channels, epg) = M3uParser.parse(sample, playlist)
        assertThat(channels).hasSize(3)
        assertThat(epg).isEqualTo("http://epg.example/guide.xml.gz")

        val sun = channels[0]
        assertThat(sun.name).isEqualTo("Sun TV")
        assertThat(sun.url).isEqualTo("http://stream/sun.m3u8")
        assertThat(sun.logo).isEqualTo("http://logo/sun.png")
        assertThat(sun.group).isEqualTo("Tamil")
        assertThat(sun.tvgId).isEqualTo("suntv.in")
        assertThat(sun.userAgent).isEqualTo("PlaylistUA")
    }

    @Test fun perChannelHeadersOverridePlaylistDefaults() {
        val raj = M3uParser.parse(sample, playlist).first[1]
        assertThat(raj.name).isEqualTo("Raj News HD")
        assertThat(raj.userAgent).isEqualTo("Custom/1.0")
        assertThat(raj.referrer).isEqualTo("http://ref.example/")
        assertThat(raj.group).isEqualTo("Other")
        assertThat(raj.tvgId).isEqualTo("raj news")
    }

    @Test fun extgrpAndNameFallbacks() {
        val c = M3uParser.parse(sample, playlist).first[2]
        assertThat(c.group).isEqualTo("Music")
        assertThat(c.name).isEqualTo("Channel")
    }

    @Test fun idsAreUniqueAndStable() {
        val a = M3uParser.parse(sample, playlist).first.map { it.id }
        assertThat(a).containsNoDuplicates()
        assertThat(M3uParser.parse(sample, playlist).first.map { it.id }).isEqualTo(a)
    }

    @Test fun emptyOrGarbageInputYieldsNothing() {
        assertThat(M3uParser.parse("", playlist).first).isEmpty()
        assertThat(M3uParser.parse("<html>404</html>", playlist).first).isEmpty()
    }

    @Test fun commaInQuotedAttributeDoesNotBreakName() {
        val (c, _) = M3uParser.parse("#EXTM3U\n#EXTINF:-1 group-title=\"News, India\",NDTV 24x7\nhttp://s/ndtv", playlist)
        assertThat(c.single().name).isEqualTo("NDTV 24x7")
        assertThat(c.single().group).isEqualTo("News, India")
    }
}
