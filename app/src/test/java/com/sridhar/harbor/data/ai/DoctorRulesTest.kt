package com.sridhar.harbor.data.ai

import com.google.common.truth.Truth.assertThat
import com.sridhar.harbor.data.jellyfin.LibraryItem
import com.sridhar.harbor.data.jellyfin.RemoteSearchResult
import org.junit.Test

class DoctorRulesTest {

    private fun item(name: String, path: String?, year: Int? = null, tmdb: String? = "1", poster: Boolean = true, type: String = "Movie") =
        LibraryItem(id = "id", name = name, type = type, year = year, path = path,
            providerIds = if (tmdb != null) mapOf("Tmdb" to tmdb) else emptyMap(),
            imageTags = if (poster) mapOf("Primary" to "tag") else emptyMap())

    private fun kinds(i: LibraryItem) = DoctorRules.issueKinds(i, TitleCleaner.parse(DoctorRules.sourceName(i)))

    @Test fun healthyItem_hasNoIssues() {
        assertThat(kinds(item("Leo", "/media/movies/Leo (2023)/Leo (2023).mkv", 2023))).isEmpty()
    }

    @Test fun unmatchedItemWithoutPoster_flagsBoth() {
        val k = kinds(item("www.1TamilMV.rocks - The Awakening", "/m/The Awakening.mkv", tmdb = null, poster = false))
        assertThat(k).containsExactly(IssueKind.Unidentified, IssueKind.NoPoster)
    }

    @Test fun wrongMatch_requiresTitleAndYearDisagreement() {
        val wrong = item("1972: Munich's Black September", "/m/Cobra (2022)/Cobra (2022).mkv", year = 1972)
        assertThat(kinds(wrong)).contains(IssueKind.WrongMatch)
        // Foreign / alternate title but same year → leave it alone.
        val alt = item("Ponniyin Selvan: Part I", "/m/PS1 (2022)/PS1 (2022).mkv", year = 2022)
        assertThat(kinds(alt)).doesNotContain(IssueKind.WrongMatch)
    }

    @Test fun seriesFileInMovieLibrary_isMisfiled() {
        assertThat(kinds(item("Breaking Bad", "/m/Breaking.Bad.S01E01.mkv"))).contains(IssueKind.Misfiled)
    }

    @Test fun sourceName_prefersFileButFallsBackForGenericNames() {
        assertThat(DoctorRules.sourceName(item("x", "/m/Leo (2023)/Leo.2023.1080p.mkv"))).isEqualTo("Leo.2023.1080p.mkv")
        assertThat(DoctorRules.sourceName(item("x", "/m/Leo (2023)/movie.mkv"))).isEqualTo("Leo (2023)")
        assertThat(DoctorRules.sourceName(item("Name Only", null))).isEqualTo("Name Only")
    }

    @Test fun score_rewardsExactTitleAndYear() {
        val target = ParsedTitle("The Awakening", 2026, "movie")
        val exact = DoctorRules.score(target, RemoteSearchResult(name = "The Awakening", year = 2026, imageUrl = "x"), "Movie", "Movie")
        val offYear = DoctorRules.score(target, RemoteSearchResult(name = "The Awakening", year = 2011), "Movie", "Movie")
        val otherTitle = DoctorRules.score(target, RemoteSearchResult(name = "Allied", year = 2026), "Movie", "Movie")
        assertThat(exact).isEqualTo(1f)
        assertThat(exact).isGreaterThan(offYear)
        assertThat(exact).isGreaterThan(otherTitle)
        assertThat(otherTitle).isAtLeast(0f)
    }

    @Test fun score_penalisesKindMismatch() {
        val t = ParsedTitle("Dark", 2017, "series")
        val same = DoctorRules.score(t, RemoteSearchResult(name = "Dark", year = 2017), "Series", "Series")
        val diff = DoctorRules.score(t, RemoteSearchResult(name = "Dark", year = 2017), "Movie", "Series")
        assertThat(same).isGreaterThan(diff)
    }
}
