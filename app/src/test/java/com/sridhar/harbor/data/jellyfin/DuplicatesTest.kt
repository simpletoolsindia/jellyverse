package com.sridhar.harbor.data.jellyfin

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DuplicatesTest {
    private fun movie(id: String, name: String, tmdb: String?, h: Int, size: Long, year: Int = 2026) = BaseItem(
        id = id, name = name, type = "Movie", year = year, providerIds = tmdb?.let { mapOf("Tmdb" to it) } ?: emptyMap(),
        mediaSources = listOf(MediaSource(id = id, path = "/m/$id.mkv", size = size, streams = listOf(MediaStream(type = "Video", height = h, width = h * 16 / 9)))))

    @Test fun groupsByProviderIdAndPicksBestQuality() {
        val g = Duplicates.find(listOf(movie("a", "Leo", "123", 1080, 6_000), movie("b", "Leo (2023)", "123", 720, 3_000), movie("c", "Jailer", "999", 1080, 5_000)))
        assertThat(g).hasSize(1)
        assertThat(g[0].copies.map { it.id }).containsExactly("a", "b")
        assertThat(g[0].best.id).isEqualTo("a")
        assertThat(g[0].copies.first { it.id == "b" }.quality).isEqualTo("720p")
        assertThat(g[0].wastedBytes).isEqualTo(3_000)
    }

    @Test fun unidentifiedTitlesGroupByNameAndYear() {
        val g = Duplicates.find(listOf(movie("a", "Maayabimbum", null, 1080, 6), movie("b", "maayabimbum", null, 1080, 3), movie("c", "Maayabimbum", null, 1080, 3, year = 2019)))
        assertThat(g.single().copies.map { it.id }).containsExactly("a", "b")
    }

    @Test fun oneItemWithTwoVersionsIsADuplicate() {
        val item = BaseItem(id = "x", name = "Vikram", type = "Movie", providerIds = mapOf("Tmdb" to "1"), mediaSources = listOf(
            MediaSource(id = "x", size = 10, streams = listOf(MediaStream(type = "Video", height = 2160))),
            MediaSource(id = "x2", size = 5, streams = listOf(MediaStream(type = "Video", height = 1080)))))
        val g = Duplicates.find(listOf(item)).single()
        assertThat(g.best.quality).isEqualTo("4K")
    }

    @Test fun bestPrefersHigherBitrateWithinTheSameTier() {
        fun m(id: String, h: Int, br: Long) = BaseItem(id = id, name = "X", type = "Movie", providerIds = mapOf("Tmdb" to "5"),
            mediaSources = listOf(MediaSource(id = id, path = "/$id", bitrate = br, size = br, streams = listOf(MediaStream(type = "Video", width = 1920, height = h)))))
        assertThat(Duplicates.find(listOf(m("a", 1080, 2_900_000), m("b", 1040, 7_900_000))).single().best.id).isEqualTo("b")
    }
}
