package com.sridhar.harbor.data.jellyfin

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

/** The forgiving Search matcher: partial names, misspellings, transliteration, spacing, years. */
class TitleSearchTest {
    private fun m(q: String, t: String, y: Int? = null) = TitleMatcher.match(q, t, y)

    @Test fun partialWordsWhileTyping() {
        assertThat(m("ponni", "Ponniyin Selvan: Part I")).isAtLeast(0.85f)
        assertThat(m("inter", "Interstellar")).isAtLeast(0.85f)
        assertThat(m("pushpa rule", "Pushpa 2: The Rule")).isAtLeast(0.8f)
    }

    @Test fun commonMisspellings() {
        assertThat(m("intersteller", "Interstellar")).isAtLeast(0.75f)
        assertThat(m("jialer", "Jailer")).isAtLeast(0.7f)            // swapped letters = one typo
        assertThat(m("avengars endgame", "Avengers: Endgame")).isAtLeast(0.8f)
        assertThat(m("harry poter", "Harry Potter and the Philosopher's Stone")).isAtLeast(0.7f)
        assertThat(m("brekin bad", "Breaking Bad")).isAtLeast(0.7f)
    }

    @Test fun transliterationVariants() {
        assertThat(m("ponniyan selvan", "Ponniyin Selvan")).isAtLeast(0.8f)
        assertThat(m("vada chennai", "Vadachennai")).isAtLeast(0.85f)
        assertThat(m("thalapathy", "Talapathi")).isAtLeast(0.75f)
        assertThat(m("aranmanai", "Aranmanai 4")).isAtLeast(0.8f)
        assertThat(m("kanchana", "Kaanchana")).isAtLeast(0.9f)
    }

    @Test fun spacingAndNumbers() {
        assertThat(m("spiderman", "Spider-Man: No Way Home")).isAtLeast(0.75f)
        assertThat(m("kgf 2", "K.G.F: Chapter 2")).isAtLeast(0.6f)
        assertThat(m("kgf2", "K.G.F: Chapter 2")).isAtLeast(0.6f)
    }

    @Test fun yearHelpsPickTheRightOne() {
        assertThat(m("leo 2023", "Leo", 2023)).isGreaterThan(m("leo 2023", "Leo", 2012))
    }

    @Test fun unrelatedStaysLow() {
        assertThat(m("jailer", "Jumanji")).isLessThan(0.55f)
        assertThat(m("leo", "Titanic")).isLessThan(0.55f)
        assertThat(m("vikram", "Viduthalai")).isLessThan(0.55f)
    }

    @Test fun bestMatchRanksFirst() {
        val titles = listOf("Vikram Vedha", "Vikram", "Vikramarkudu", "Vettaiyan")
        assertThat(titles.maxBy { m("vikram", it) }).isEqualTo("Vikram")
    }

    @Test fun gibberishMatchesNothing() {
        listOf("Mr. X", "Wifelike X (2022)", "X-Deal 2", "Spy x Family").forEach {
            assertWithMessage(it).that(m("qqxzzw", it)).isLessThan(0.55f)
        }
    }
}
