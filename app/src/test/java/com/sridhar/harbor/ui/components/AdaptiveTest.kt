package com.sridhar.harbor.ui.components

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AdaptiveTest {
    @Test fun phonesAreCompact() {
        assertThat(widthClassOf(360)).isEqualTo(WidthClass.Compact)
        assertThat(widthClassOf(599)).isEqualTo(WidthClass.Compact)
    }

    @Test fun foldablesAndLandscapePhonesAreMedium() {
        assertThat(widthClassOf(600)).isEqualTo(WidthClass.Medium)
        assertThat(widthClassOf(839)).isEqualTo(WidthClass.Medium)
    }

    @Test fun tabletsAreExpanded() {
        assertThat(widthClassOf(840)).isEqualTo(WidthClass.Expanded)
        assertThat(widthClassOf(1280)).isEqualTo(WidthClass.Expanded)
    }
}
