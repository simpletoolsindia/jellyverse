package com.sridhar.harbor.data.music

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RadioStationsTest {
    @Test fun plsPlaylistResolvesToFirstStream() {
        val pls = "[playlist]\nNumberOfEntries=2\nFile1=https://stream.example.com/live.mp3\nTitle1=Live\nFile2=http://backup.example.com/aac\n"
        assertThat(RadioStations.parsePlaylist(pls)).isEqualTo("https://stream.example.com/live.mp3")
    }

    @Test fun m3uPlaylistSkipsComments() {
        val m3u = "#EXTM3U\n#EXTINF:-1,Radio\nhttp://ice.example.org:8000/radio\n"
        assertThat(RadioStations.parsePlaylist(m3u)).isEqualTo("http://ice.example.org:8000/radio")
    }

    @Test fun sharedTextYieldsTheLink() {
        assertThat(RadioStations.linkIn("Listen live: https://radio.example.in/stream.aac, enjoy!")).isEqualTo("https://radio.example.in/stream.aac")
        assertThat(RadioStations.linkIn("no link here")).isNull()
    }

    @Test fun stationNameFallsBackToHost() {
        assertThat(RadioStations.guessName("https://www.radiomirchi.com/live")).isEqualTo("radiomirchi.com")
    }

    @Test fun stationPlaysAsARadioSong() {
        val song = RadioStation(id = "x", name = "Mirchi", url = "https://s/x.mp3").toSong()
        assertThat(song.streamUrl).isEqualTo("https://s/x.mp3")
        assertThat(song.id).isEqualTo("radio:x")
    }
}
