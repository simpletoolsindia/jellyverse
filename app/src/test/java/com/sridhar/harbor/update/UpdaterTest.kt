package com.sridhar.harbor.update

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class UpdaterTest {
    @Test fun newerVersionsAreDetected() {
        assertThat(Updater.isNewer("2.6.0", "2.5.0")).isTrue()
        assertThat(Updater.isNewer("2.10.0", "2.9.3")).isTrue()
        assertThat(Updater.isNewer("3.0", "2.99.99")).isTrue()
        assertThat(Updater.isNewer("2.5.1", "2.5.0-tv")).isTrue()
    }

    @Test fun sameOrOlderIsNotAnUpdate() {
        assertThat(Updater.isNewer("2.5.0", "2.5.0")).isFalse()
        assertThat(Updater.isNewer("2.5.0", "2.5.0-tv")).isFalse()
        assertThat(Updater.isNewer("2.4.9", "2.5.0")).isFalse()
        assertThat(Updater.isNewer("2.5", "2.5.0")).isFalse()
    }
}
