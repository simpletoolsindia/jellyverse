package com.sridhar.harbor.data.reco

import com.google.common.truth.Truth.assertThat
import com.sridhar.harbor.data.jellyfin.BaseItem
import com.sridhar.harbor.data.jellyfin.Person
import com.sridhar.harbor.data.jellyfin.UserData
import org.junit.Test
import java.time.Instant

class RecommenderTest {
    private val now = Instant.parse("2026-09-29T00:00:00Z")
    private fun item(id: String, genres: List<String>, actors: List<String> = emptyList(), watchedDaysAgo: Long? = null,
                     fav: Boolean = false, rating: Double = 7.0, year: Int = 2020) = BaseItem(
        id = id, name = id, type = "Movie", genres = genres, year = year, communityRating = rating,
        people = actors.mapIndexed { i, a -> Person(id = "$id-$i", name = a, type = "Actor") },
        userData = if (watchedDaysAgo != null) UserData(played = true, playCount = 1, lastPlayedDate = now.minusSeconds(watchedDaysAgo * 86_400).toString(), isFavorite = fav)
            else UserData(isFavorite = fav),
    )

    private val library = listOf(
        item("Leo", listOf("Action", "Thriller"), listOf("Vijay", "Trisha"), watchedDaysAgo = 3),
        item("Master", listOf("Action", "Thriller"), listOf("Vijay", "Vijay Sethupathi"), watchedDaysAgo = 20),
        item("Vikram", listOf("Action", "Thriller"), listOf("Kamal Haasan", "Vijay Sethupathi")),
        item("Beast", listOf("Action", "Comedy"), listOf("Vijay", "Pooja Hegde")),
        item("Toy Story", listOf("Animation", "Family"), listOf("Tom Hanks")),
        item("Frozen", listOf("Animation", "Family", "Musical"), listOf("Idina Menzel")),
        item("Notebook", listOf("Romance", "Drama"), listOf("Ryan Gosling"), year = 2004),
    ) + (1..14).map { item("Action $it", listOf("Action", if (it % 2 == 0) "Thriller" else "Crime"), listOf("Actor $it")) }

    @Test fun ranksSimilarTitlesFirst() {
        val r = Recommender.compute(library, now = now)
        val ids = r.forYou.map { it.item.id }
        assertThat(ids).containsNoneOf("Leo", "Master")            // never recommend what you watched
        assertThat(ids.take(2)).containsExactly("Vikram", "Beast")  // shared cast + genres win
        assertThat(ids.indexOf("Vikram")).isLessThan(ids.indexOf("Toy Story"))
    }

    @Test fun reasonNamesSharedFeatures() {
        val vikram = Recommender.compute(library, now = now).forYou.first { it.item.id == "Vikram" }
        assertThat(vikram.reason).isNotEmpty()
        assertThat(vikram.reason).containsMatch("Thriller|Action|Vijay Sethupathi")
    }

    @Test fun becauseYouWatchedUsesMostRecentFirst() {
        val r = Recommender.compute(library, now = now)
        assertThat(r.because.firstOrNull()?.seed?.id).isEqualTo("Leo")
    }

    @Test fun noHistoryMeansNoRecommendations() {
        val fresh = library.map { it.copy(userData = UserData()) }
        assertThat(Recommender.compute(fresh, now = now).forYou).isEmpty()
    }

    @Test fun respectsParentalFilter() {
        val r = Recommender.compute(library, keep = { it.id != "Vikram" }, now = now)
        assertThat(r.forYou.map { it.item.id }).doesNotContain("Vikram")
    }
}
