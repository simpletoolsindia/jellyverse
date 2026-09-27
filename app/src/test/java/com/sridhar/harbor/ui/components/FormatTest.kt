package com.sridhar.harbor.ui.components

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FormatTest {
    @org.junit.Before fun english() = com.sridhar.harbor.testutil.EnglishStrings.install()

    @Test fun bytes() {
        assertThat(formatBytes(512)).isEqualTo("512 B")
        assertThat(formatBytes(1536)).isEqualTo("1.5 KB")
        assertThat(formatBytes(150L * 1024 * 1024)).isEqualTo("150 MB")
        assertThat(formatBytes(3L * 1024 * 1024 * 1024 * 1024 * 1024)).isEqualTo("3072 TB")
    }

    @Test fun speed() {
        assertThat(formatSpeed(0)).isEqualTo("0 KB/s")
        assertThat(formatSpeed(-5)).isEqualTo("0 KB/s")
        assertThat(formatSpeed(2048)).isEqualTo("2.0 KB/s")
    }

    @Test fun eta() {
        assertThat(formatEta(0)).isEqualTo("∞")
        assertThat(formatEta(8_640_000)).isEqualTo("∞")
        assertThat(formatEta(45)).isEqualTo("45s")
        assertThat(formatEta(125)).isEqualTo("2m 5s")
        assertThat(formatEta(3_720)).isEqualTo("1h 2m")
        assertThat(formatEta(90_000)).isEqualTo("1d 1h")
    }

    @Test fun clock() {
        assertThat(formatClock(65_000)).isEqualTo("1:05")
        assertThat(formatClock(3_725_000)).isEqualTo("1:02:05")
        assertThat(formatClock(-10_000)).isEqualTo("-0:10")
    }

    @Test fun runtime() {
        assertThat(formatRuntime(null)).isNull()
        assertThat(formatRuntime(0)).isNull()
        assertThat(formatRuntime(45)).isEqualTo("45m")
        assertThat(formatRuntime(135)).isEqualTo("2h 15m")
    }

    @Test fun relative() {
        assertThat(relativeTime(null)).isEmpty()
        assertThat(relativeTime("not-a-date")).isEmpty()
        assertThat(relativeTime(java.time.Instant.now().toString())).isEqualTo("just now")
        assertThat(relativeTime(java.time.Instant.now().minusSeconds(7200).toString())).isEqualTo("2h ago")
    }
}
