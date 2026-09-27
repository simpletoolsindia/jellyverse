package com.sridhar.harbor.data.music

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Samples are real tags from a Navidrome library. */
class MusicTextTest {
    @Test fun stripsSiteTagsFromTitles() {
        assertThat(MusicText.cleanTitle("Chanakya Chanakya - MassTamilan.com (MassTamilan.com)")).isEqualTo("Chanakya Chanakya")
        assertThat(MusicText.cleanTitle("Lajjavathiyea Ii - TnHits.Co (TnHits.Co)")).isEqualTo("Lajjavathiyea Ii")
    }

    @Test fun youtubeStyleTitles() {
        val raw = "Paiya - Thuli Thuli Tamil Lyric ｜ Yuvanshankar Raja ｜ Karthi, Tamannaah_rHweW6dRRYY"
        assertThat(MusicText.cleanTitle(raw)).isEqualTo("Paiya - Thuli Thuli")
        assertThat(MusicText.cleanTitle("Vedalam - Aaluma Doluma Video ｜ Ajith ｜ Anirudh Ravichander_2ogKpj5QuSY")).isEqualTo("Vedalam - Aaluma Doluma")
        assertThat(MusicText.cleanTitle("Uruguthey Maruguthey with Tamil Lyrics_V-XJpelnIDY")).isEqualTo("Uruguthey Maruguthey")
    }

    @Test fun keepsRealTitlesIntact() {
        assertThat(MusicText.cleanTitle("Aga Naga (From “Ponniyin Selvan Part-2”) - A.R. Rahman")).isEqualTo("Aga Naga (From “Ponniyin Selvan Part-2”) - A.R. Rahman")
        assertThat(MusicText.cleanTitle("Senjitaley")).isEqualTo("Senjitaley")
        assertThat(MusicText.cleanTitle("Uyire Uyire - Bombay")).isEqualTo("Uyire Uyire - Bombay")
    }

    @Test fun missingArtistFallsBackToTitleCredits() {
        val s = Song("x", title = "Paiya - Thuli Thuli Tamil Lyric ｜ Yuvanshankar Raja ｜ Karthi, Tamannaah_rHweW6dRRYY", artist = "[Unknown Artist]", album = "[Unknown Album]")
        assertThat(s.displayArtist).isEqualTo("Yuvanshankar Raja, Karthi, Tamannaah")
        assertThat(s.displayAlbum).isEqualTo("Singles")
    }

    @Test fun artists() {
        assertThat(MusicText.cleanArtist("Unnikrishnan &  S.Janaki - MassTamilan.com")).isEqualTo("Unnikrishnan & S.Janaki")
        assertThat(MusicText.cleanArtist("Friendstamilmp3.com")).isEqualTo("Unknown artist")
        assertThat(MusicText.cleanArtist("Anirudh Ravichander (feat. Elfe Choir)")).isEqualTo("Anirudh Ravichander")
    }

    @Test fun albums() {
        assertThat(MusicText.cleanAlbum("Dum - MassTamilan.com")).isEqualTo("Dum")
        assertThat(MusicText.cleanAlbum("3-Moonu-320kbps-MassTamilan")).isEqualTo("Moonu")
        assertThat(MusicText.cleanAlbum("[Unknown Album]")).isEqualTo("Singles")
    }

    @Test fun duplicateArtistSpellingsMerge() {
        val groups = MusicText.groupArtists(listOf(
            Artist("1", "A.R. Rahman", albumCount = 5), Artist("2", "A.R.Rahman", albumCount = 2),
            Artist("3", "A.R.Rahman - MassTamilan.com", albumCount = 7), Artist("4", "Anirudh Ravichander"),
            Artist("5", "[Unknown Artist]"),
        ))
        assertThat(groups.map { it.name }).containsExactly("A.R.Rahman", "Anirudh Ravichander").inOrder()
        assertThat(groups.first().ids).containsExactly("1", "2", "3")
        assertThat(groups.first().albumCount).isEqualTo(14)
    }

    @Test fun genresDropSiteTagsAndMergeCase() {
        val g = MusicText.realGenres(listOf(Genre("MassTamilan.com", 297), Genre("Tamil", 47), Genre("tamil", 3), Genre("TnHits.Co", 5), Genre("Melody", 10)))
        assertThat(g.map { it.value to it.songCount }).containsExactly("Tamil" to 50, "Melody" to 10).inOrder()
    }

    @Test fun subsonicTokenMatchesSpecExample() {
        // From the Subsonic API docs: password "sesame", salt "c19b2d" → 26719a1196d2a940705a59634eb18eab
        assertThat(SubsonicAuth.token("sesame", "c19b2d")).isEqualTo("26719a1196d2a940705a59634eb18eab")
        assertThat(SubsonicAuth.newSalt()).hasLength(12)
    }

    @Test fun activeLyricLine() {
        val lines = listOf(LyricLine(0, "a"), LyricLine(5_000, "b"), LyricLine(9_000, "c"))
        assertThat(Lyrics.activeIndex(lines, 0)).isEqualTo(0)
        assertThat(Lyrics.activeIndex(lines, 5_500)).isEqualTo(1)
        assertThat(Lyrics.activeIndex(lines, 60_000)).isEqualTo(2)
        assertThat(Lyrics.activeIndex(lines, 4_000, offsetMs = 1_500)).isEqualTo(1)
        assertThat(Lyrics.activeIndex(listOf(LyricLine(1_000, "x")), 500)).isEqualTo(-1)
    }

    @Test fun durations() {
        assertThat(MusicText.duration(185)).isEqualTo("3:05")
        assertThat(MusicText.totalDuration(20685)).isEqualTo("5 hr 44 min")
        assertThat(MusicText.totalDuration(600)).isEqualTo("10 min")
    }

    @Test fun lyricAdvertsAreDropped() {
        val lines = listOf(LyricLine(0, "MassTamilan.dev - Download 320kbps, 128kbps latest songs for free"), LyricLine(1000, "Vandha edam nalla edam"), LyricLine(2000, "Visit www.site.com"))
        assertThat(Lyrics.clean(lines).map { it.value }).containsExactly("Vandha edam nalla edam")
    }
}
