package com.sridhar.harbor.data.parental

import com.google.common.truth.Truth.assertThat
import com.sridhar.harbor.data.jellyfin.BaseItem
import org.junit.Test

class AdultContentTest {
    private fun item(rating: String? = null, genres: List<String> = emptyList(), tags: List<String> = emptyList()) =
        BaseItem(id = "x", name = "x", type = "Movie", officialRating = rating, genres = genres, tags = tags)

    @Test fun adultRatingsAreCaught() {
        listOf("A", "R", "NC-17", "TV-MA", "IN-A", "18", "DE:18").forEach { assertThat(Ratings.isAdultItem(item(it))).isTrue() }
        listOf("U", "UA", "PG-13", "TV-14", null).forEach { assertThat(Ratings.isAdultItem(item(it))).isFalse() }
    }

    @Test fun unratedAdultGenresOrTagsAreCaught() {
        assertThat(Ratings.isAdultItem(item(genres = listOf("Drama", "Erotic")))).isTrue()
        assertThat(Ratings.isAdultItem(item(tags = listOf("softcore")))).isTrue()
        assertThat(Ratings.isAdultItem(item(genres = listOf("Drama", "Romance")))).isFalse()
        assertThat(Ratings.isAdultGenre("Adult")).isTrue()
        assertThat(Ratings.isAdultGenre("Adventure")).isFalse()
    }
}
