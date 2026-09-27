package com.sridhar.harbor.data.ai

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TitleCleanerTest {

    @Test fun parse_stripsSitePrefixQualityAndLanguageTags() {
        val p = TitleCleaner.parse("www.1TamilMV.rocks - The Awakening (2026) HQ HDRip - 1080p - x264 - [Tam + Tel] - 2.4GB.mkv")
        assertThat(p.title).isEqualTo("The Awakening")
        assertThat(p.year).isEqualTo(2026)
        assertThat(p.type).isEqualTo("movie")
    }

    @Test fun parse_handlesUnderscoreSitePrefix() {
        val p = TitleCleaner.parse("www.1tamilmv.com_aranmanai_2026.mp4")
        assertThat(p.title).isEqualTo("Aranmanai")
        assertThat(p.year).isEqualTo(2026)
    }

    @Test fun parse_underscoreOnlyNameKeepsSequelNumber() {
        val p = TitleCleaner.parse("aranmanai_4_2026.mkv")
        assertThat(p.title).isEqualTo("Aranmanai 4")
        assertThat(p.year).isEqualTo(2026)
    }

    @Test fun parse_detectsSeriesEpisode() {
        val p = TitleCleaner.parse("Breaking.Bad.S03E07.720p.BluRay.x264-DEMAND.mkv")
        assertThat(p.title).isEqualTo("Breaking Bad")
        assertThat(p.type).isEqualTo("series")
        assertThat(p.season).isEqualTo(3)
        assertThat(p.episode).isEqualTo(7)
    }

    @Test fun parse_dottedSceneNameWithUploaderSuffix() {
        val p = TitleCleaner.parse("Jailer.2023.1080p.AMZN.WEB-DL.DDP5.1.H.264-TamilBlasters.mkv")
        assertThat(p.title).isEqualTo("Jailer")
        assertThat(p.year).isEqualTo(2023)
    }

    @Test fun parse_splitsCamelCase() {
        assertThat(TitleCleaner.parse("AlienEarth (2025)").title).isEqualTo("Alien Earth")
    }

    @Test fun parse_titleCasesLowercaseNames() {
        assertThat(TitleCleaner.parse("the dark knight 2008").title).isEqualTo("The Dark Knight")
    }

    @Test fun parse_numericTitleIsNotMistakenForYear() {
        val p = TitleCleaner.parse("1917 (2019).mkv")
        assertThat(p.year).isEqualTo(2019)
        assertThat(p.title).isEqualTo("1917")
    }

    @Test fun parse_blankInputFallsBackToRaw() {
        assertThat(TitleCleaner.parse("1080p").title).isNotEmpty()
    }

    @Test fun looksMessy_flagsSceneNamesButNotCleanTitles() {
        assertThat(TitleCleaner.looksMessy("www.1TamilMV.rocks - Leo (2023) 1080p HDRip")).isTrue()
        assertThat(TitleCleaner.looksMessy("Movie.Name.2023.1080p.x264")).isTrue()
        assertThat(TitleCleaner.looksMessy("The Awakening")).isFalse()
        assertThat(TitleCleaner.looksMessy("Leo")).isFalse()
    }

    @Test fun similarity_isSymmetricAndBounded() {
        val a = TitleCleaner.similarity("The Dark Knight", "Dark Knight")
        val b = TitleCleaner.similarity("Dark Knight", "The Dark Knight")
        assertThat(a).isEqualTo(b)
        assertThat(a).isAtMost(1f)
        assertThat(a).isAtLeast(0.9f)
    }

    @Test fun similarity_ignoresAccentsAndPunctuation() {
        assertThat(TitleCleaner.similarity("Amélie", "Amelie")).isEqualTo(1f)
        assertThat(TitleCleaner.similarity("Alien: Earth", "Alien Earth")).isEqualTo(1f)
    }

    @Test fun similarity_unrelatedTitlesScoreLow() {
        assertThat(TitleCleaner.similarity("Cobra", "1972: Munich's Black September")).isLessThan(0.2f)
    }

    @Test fun similarity_emptyIsZero() {
        assertThat(TitleCleaner.similarity("", "Leo")).isEqualTo(0f)
        assertThat(TitleCleaner.similarity("the", "a")).isEqualTo(0f)
    }
}
