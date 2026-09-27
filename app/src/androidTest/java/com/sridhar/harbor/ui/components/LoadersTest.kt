package com.sridhar.harbor.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import androidx.test.platform.app.InstrumentationRegistry
import com.sridhar.harbor.testutil.setThemed
import com.sridhar.harbor.ui.theme.Harbor
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@MediumTest
@RunWith(AndroidJUnit4::class)
class LoadersTest {
    @get:Rule val rule = createComposeRule()

    @Test fun jellyLoader_isAnIndeterminateProgressBar() {
        rule.setThemed { JellyLoader(Modifier.testTag("jl")) }
        rule.onNodeWithTag("jl").assert(SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo.Indeterminate))
    }

    @Test fun tideBar_reportsDeterminateProgress() {
        rule.setThemed { TideBar(Modifier.testTag("tb"), progress = { 0.4f }) }
        rule.onNodeWithTag("tb").assert(SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo(0.4f, 0f..1f)))
    }

    @Test fun tideBar_withoutProgressIsIndeterminate() {
        rule.setThemed { TideBar(Modifier.testTag("tb")) }
        rule.onNodeWithTag("tb").assert(SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo.Indeterminate))
    }

    @Test fun introFrames() {
        if (InstrumentationRegistry.getArguments().getString("loaderFrames") != "true") return
        rule.mainClock.autoAdvance = false
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as com.sridhar.harbor.HarborApp
        rule.setThemed { ProvideContainer(app.container) { LaunchIntro { androidx.compose.material3.Text("APP CONTENT") } } }
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "loader-frames").apply { mkdirs() }
        for (i in 0 until 10) {
            rule.mainClock.advanceTimeBy(160)
            val bmp = rule.onRoot().captureToImage().asAndroidBitmap()
            File(dir, "intro$i.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    /** Design review aid: `-e loaderFrames true` saves animation frames to …/files/loader-frames. */
    @Test fun saveFrames() {
        if (InstrumentationRegistry.getArguments().getString("loaderFrames") != "true") return
        rule.mainClock.autoAdvance = false
        rule.setThemed {
            Column(Modifier.background(Harbor.Ink).padding(start = 24.dp, end = 24.dp, bottom = 24.dp, top = 120.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                    JellyLoader(Modifier.size(96.dp)); JellyLoader(); JellyLoader(Modifier.size(20.dp)); JellyLoader(color = Harbor.VioletSoft)
                }
                TideBar(Modifier.width(320.dp))
                TideBar(Modifier.width(320.dp), progress = { 0.62f })
                TideBar(Modifier.width(320.dp), rider = false)
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) { AdriftJelly(); GlitchJelly() }
            }
        }
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "loader-frames").apply { mkdirs() }
        for (i in 0 until 8) {
            rule.mainClock.advanceTimeBy(170)
            val bmp = rule.onRoot().captureToImage().asAndroidBitmap()
            File(dir, "frame$i.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
