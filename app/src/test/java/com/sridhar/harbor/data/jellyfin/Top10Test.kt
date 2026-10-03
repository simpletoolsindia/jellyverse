package com.sridhar.harbor.data.jellyfin

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Instant

class Top10Test {
    private val pool = (1..40).map { BaseItem(id = "m$it", name = "M$it", type = "Movie", communityRating = 5.0 + (it % 40) / 10.0) }

    @Test fun picksTenAndChangesWeekToWeek() {
        val w1 = Top10.pick(pool, Instant.parse("2026-10-05T00:00:00Z")).map { it.id }
        val w2 = Top10.pick(pool, Instant.parse("2026-10-12T00:00:00Z")).map { it.id }
        assertThat(w1).hasSize(10)
        assertThat(w1).isNotEqualTo(w2)
        assertThat(Top10.pick(pool, Instant.parse("2026-10-06T00:00:00Z")).map { it.id }).isEqualTo(w1)   // same week → same list
    }

    @Test fun recentlyAddedGetsABoost() {
        val now = Instant.parse("2026-10-05T00:00:00Z")
        val fresh = BaseItem(id = "new", name = "New", type = "Movie", communityRating = 8.6, dateCreated = "2026-10-01T00:00:00Z")
        val hits = (0..20).count { wk -> Top10.pick(pool + fresh, now.plusSeconds(wk * 7L * 86_400)).any { it.id == "new" } }
        assertThat(hits).isAtLeast(15)
    }
}
