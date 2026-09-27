package com.sridhar.harbor.data.parental

import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

class RatingsTest {
    @Test fun adultRatingsAreProtected() {
        listOf("R", "NC-17", "TV-MA", "IN-A", "A", "18+", "DE:18", "FSK 18", "X", "r", " IN-A ").forEach {
            assertWithMessage(it).that(Ratings.isAdult(it)).isTrue()
        }
    }

    @Test fun familyAndTeenRatingsStayVisible() {
        listOf("G", "PG", "PG-13", "TV-14", "IN-U", "IN-UA", "IN-U/A 13+", "IN-U/A 16+", "IN-U/A 7+", "NR", "6", "", null).forEach {
            assertWithMessage(it.toString()).that(Ratings.isAdult(it)).isFalse()
        }
    }
}
