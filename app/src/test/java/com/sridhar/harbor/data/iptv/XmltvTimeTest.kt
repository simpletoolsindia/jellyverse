package com.sridhar.harbor.data.iptv

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class XmltvTimeTest {
    @Test fun parsesOffsetTimestamps() {
        // 2026-09-27 18:30 IST == 13:00 UTC
        assertThat(XmltvTime.parse("20260927183000 +0530")).isEqualTo(1790514000000L)
    }

    @Test fun bareTimestampIsUtc() {
        assertThat(XmltvTime.parse("20260927130000")).isEqualTo(1790514000000L)
    }

    @Test fun invalidOrMissingIsZero() {
        assertThat(XmltvTime.parse(null)).isEqualTo(0L)
        assertThat(XmltvTime.parse("garbage")).isEqualTo(0L)
    }
}
