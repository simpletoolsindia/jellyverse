package com.sridhar.harbor.data.jellyfin

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TitleMatcherTest {
    private fun score(q: String, title: String) = TitleMatcher.score(TitleMatcher.normalizeQuery(q), TitleMatcher.normalize(title))

    @Test fun exactAndYearSuffixedTitlesMatch() {
        assertThat(score("Leo", "Leo (2023)")).isEqualTo(1f)
    }

    @Test fun transliterationTyposStillMatch() {
        assertThat(score("aaranmanai", "Aranmanai")).isEqualTo(1f)
        assertThat(score("ponniyin selvan", "Ponniyin Selvan")).isEqualTo(1f)
        assertThat(score("vikram", "Vikramm")).isAtLeast(0.8f)
    }

    @Test fun fillerWordsAreIgnored() {
        assertThat(score("the movie jailer please", "Jailer")).isEqualTo(1f)
    }

    @Test fun unrelatedTitlesFallBelowPlayThreshold() {
        assertThat(score("alli", "Allied")).isLessThan(0.8f)
        assertThat(score("prince", "Harry Potter and the Half-Blood Prince")).isLessThan(0.8f)
    }

    @Test fun prefixMatchScalesWithLength() {
        assertThat(score("aranmanai", "Aranmanai 4")).isGreaterThan(score("aran", "Aranmanai 4"))
    }

    @Test fun emptyCandidateScoresZero() {
        assertThat(TitleMatcher.score("leo", "")).isEqualTo(0f)
    }

    @Test fun leadingArticlesMatchBothWays() {
        assertThat(score("The 6th Day", "The 6th Day")).isEqualTo(1f)
        assertThat(score("6th day", "The 6th Day")).isEqualTo(1f)
        assertThat(score("the awakening", "The Awakening (2026)")).isEqualTo(1f)
    }
}
