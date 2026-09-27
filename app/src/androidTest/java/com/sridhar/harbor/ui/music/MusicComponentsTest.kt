package com.sridhar.harbor.ui.music

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.google.common.truth.Truth.assertThat
import com.sridhar.harbor.data.music.LyricLine
import com.sridhar.harbor.data.music.StructuredLyrics
import com.sridhar.harbor.testutil.setThemed
import com.sridhar.harbor.ui.theme.Harbor
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@MediumTest
@RunWith(AndroidJUnit4::class)
class MusicComponentsTest {
    @get:Rule val rule = createComposeRule()

    @Test fun generatedCover_showsTitleWhenLargeAndInitialWhenSmall() {
        rule.setThemed {
            androidx.compose.foundation.layout.Column {
                GeneratedCover("Naan Pizhai", Modifier.size(160.dp))
                GeneratedCover("Vandha Edam", Modifier.size(40.dp))
            }
        }
        rule.onNodeWithText("Naan Pizhai").assertIsDisplayed()
        rule.onNodeWithText("V").assertIsDisplayed()
    }

    @Test fun likeButton_togglesAndAnnounces() {
        var liked by mutableStateOf(false)
        rule.setThemed { LikeButton(liked) { liked = !liked } }
        rule.onNodeWithContentDescription("Add to Liked songs").performClick()
        rule.onNodeWithContentDescription("Remove from Liked songs").assertIsDisplayed()
        rule.runOnIdle { assertThat(liked).isTrue() }
    }

    @Test fun bigPlay_reflectsState() {
        var clicks = 0
        var playing by mutableStateOf(false)
        rule.setThemed { BigPlay(playing) { clicks++ } }
        rule.onNodeWithContentDescription("Play").performClick()
        playing = true
        rule.onNodeWithContentDescription("Pause").assertIsDisplayed()
        rule.runOnIdle { assertThat(clicks).isEqualTo(1) }
    }

    @Test fun scrubber_showsElapsedAndRemaining_andSeeks() {
        var seeked = -1L
        rule.setThemed { Scrubber(positionMs = 65_000, durationMs = 185_000) { seeked = it } }
        rule.onNodeWithText("1:05").assertIsDisplayed()
        rule.onNodeWithText("-2:00").assertIsDisplayed()
        // Same path TalkBack uses to scrub.
        rule.onNodeWithTag("scrubber").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(0.9f) }
        rule.runOnIdle { assertThat(seeked).isGreaterThan(150_000L) }
    }

    @Test fun lyrics_highlightFollowsPosition_andSpamIsHidden() {
        var pos by mutableLongStateOf(0L)
        val lyrics = StructuredLyrics(synced = true, line = listOf(LyricLine(0, "MassTamilan.dev - Download 320kbps"), LyricLine(1_000, "first line"), LyricLine(5_000, "second line")))
        rule.setThemed { LyricsCard(lyrics, pos, Harbor.Violet) }
        rule.onNodeWithText("first line").assertIsDisplayed()
        rule.onNodeWithText("MassTamilan.dev - Download 320kbps").assertDoesNotExist()
        pos = 6_000
        rule.onNodeWithText("second line").assertIsDisplayed()
    }

    @Test fun lyricsCard_hiddenWhenOnlySpam() {
        rule.setThemed { LyricsCard(StructuredLyrics(line = listOf(LyricLine(0, "Download from www.site.com"))), 0, Harbor.Violet) }
        rule.onNodeWithTag("lyrics").assertDoesNotExist()
    }

    @Test fun equalizerBarsRender() {
        rule.setThemed { EqualizerBars(playing = true, modifier = Modifier) }
        rule.onRoot().assertExists()
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onRoot() = onNode(androidx.compose.ui.test.isRoot())
}
