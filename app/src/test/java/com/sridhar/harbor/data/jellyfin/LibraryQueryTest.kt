package com.sridhar.harbor.data.jellyfin

import com.google.common.truth.Truth.assertThat
import com.sridhar.harbor.ui.player.langKey
import org.junit.Test
import java.util.Locale

class LibraryQueryTest {
    private fun movie(id: String, name: String, year: Int?, vararg langs: String?, genres: List<String> = emptyList(), played: Boolean = false, fav: Boolean = false, added: String = "2026-01-0${id.last()}") =
        IndexItem(id, "Movie", name, year = year, genres = genres, dateCreated = added, streams = langs.map { IndexStream("Audio", it) } + IndexStream("Video"), userData = IndexUserData(played, fav))

    private val index = LibraryIndex.build(listOf(
        movie("m1", "Leo", 2023, "tam", "tel", genres = listOf("Action")),
        movie("m2", "Jawan", 2023, "hin", "tam", genres = listOf("Action", "Thriller"), played = true),
        movie("m3", "Anbe Sivam", 2003, "tam", genres = listOf("Drama"), fav = true),
        movie("m4", "Inception", 2010, "eng", genres = listOf("Science Fiction")),
        movie("m5", "Home video", null, "und", null),
    ))

    @Test fun languagesCountedAndUndeterminedDropped() {
        assertThat(index.languages.first()).isEqualTo("tam" to 3)
        assertThat(index.languages.map { it.first }).containsExactly("tam", "tel", "hin", "eng")
        assertThat(index.entries.first { it.id == "m5" }.languages).isEmpty()
    }

    @Test fun seriesTakeTheirEpisodesLanguages() {
        val series = IndexItem("s1", "Series", "Dark")
        val eps = listOf(IndexItem("e1", "Episode", seriesId = "s1", streams = listOf(IndexStream("Audio", "ger"))), IndexItem("e2", "Episode", seriesId = "s1", streams = listOf(IndexStream("Audio", "eng"))))
        assertThat(LibraryIndex.build(listOf(series), eps).entries.single().languages).containsExactly("ger", "eng")
    }

    @Test fun decadesNewestFirst() {
        assertThat(index.decades).containsExactly(2020, 2010, 2000).inOrder()
        assertThat(LibraryQuery.yearsParam(2020)).isEqualTo("2020,2021,2022,2023,2024,2025,2026,2027,2028,2029")
        assertThat(LibraryQuery.yearsParam(null)).isNull()
    }

    @Test fun languagePlusGenrePlusDecade() {
        val r = LibraryQuery.apply(index.entries, LibraryQuery.Filter(language = "tam", genre = "action", decade = 2020), "SortName", false)
        assertThat(r.map { it.id }).containsExactly("m2", "m1").inOrder()   // jawan < leo
    }

    @Test fun watchedAndFavouriteFlags() {
        assertThat(LibraryQuery.apply(index.entries, LibraryQuery.Filter(language = "tam", unwatchedOnly = true), "SortName", false).map { it.id }).containsExactly("m3", "m1").inOrder()
        assertThat(LibraryQuery.apply(index.entries, LibraryQuery.Filter(favoritesOnly = true), "SortName", false).map { it.id }).containsExactly("m3")
    }

    @Test fun sortingByDateAndRatingDirection() {
        val byAdded = LibraryQuery.apply(index.entries, LibraryQuery.Filter(language = "tam"), "DateCreated", true)
        assertThat(byAdded.map { it.id }).containsExactly("m3", "m2", "m1").inOrder()
    }

    @Test fun languageCodesNormalise() {
        assertThat(LibraryIndex.key("ta")).isEqualTo("tam")
        assertThat(LibraryIndex.key("TAM")).isEqualTo("tam")
        assertThat(LibraryIndex.key("und")).isNull()
        assertThat(langKey("ta-IN")).isEqualTo("tam")
        assertThat(langKey("en")).isEqualTo("eng")
        assertThat(langKey(null)).isNull()
        assertThat(LibraryIndex.displayName("tam", Locale.ENGLISH)).isEqualTo("Tamil")
    }
}
