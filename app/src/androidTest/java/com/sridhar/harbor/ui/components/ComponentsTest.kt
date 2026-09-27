package com.sridhar.harbor.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.google.common.truth.Truth.assertThat
import com.sridhar.harbor.testutil.setThemed
import com.sridhar.harbor.ui.theme.Harbor
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@MediumTest
@RunWith(AndroidJUnit4::class)
class ComponentsTest {
    @get:Rule val rule = createComposeRule()

    @Test fun gradientButton_clicksWhenEnabled() {
        var clicks = 0
        rule.setThemed { GradientButton("Save", { clicks++ }) }
        rule.onNodeWithText("Save").assertIsDisplayed().assertIsEnabled().performClick()
        rule.runOnIdle { assertThat(clicks).isEqualTo(1) }
    }

    @Test fun gradientButton_disabledIgnoresClicks() {
        var clicks = 0
        rule.setThemed { GradientButton("Save", { clicks++ }, enabled = false) }
        rule.onNodeWithText("Save").assertIsNotEnabled().performClick()
        rule.runOnIdle { assertThat(clicks).isEqualTo(0) }
    }

    @Test fun messageState_showsTextAndRetries() {
        var retried = false
        rule.setThemed { MessageState("Couldn't load", "Server offline", onRetry = { retried = true }, actionLabel = "Retry") }
        rule.onNodeWithText("Couldn't load").assertIsDisplayed()
        rule.onNodeWithText("Server offline").assertIsDisplayed()
        rule.onNodeWithText("Retry").performClick()
        rule.runOnIdle { assertThat(retried).isTrue() }
    }

    @Test fun messageState_withoutRetryHasNoButton() {
        rule.setThemed { MessageState("Empty", null) }
        rule.onNodeWithText("Try again").assertDoesNotExist()
    }

    @Test fun pill_rendersLabel() {
        rule.setThemed { Pill("Admin", Harbor.Coral) }
        rule.onNodeWithText("Admin").assertIsDisplayed()
    }

    @Test fun posterCard_showsTitleSubtitleAndIsClickable() {
        var opened = false
        rule.setThemed { PosterCard(null, "Leo", "2023", progress = 0.4f, played = true) { opened = true } }
        rule.onNodeWithText("2023").assertIsDisplayed()
        // No artwork → the title is drawn on the placeholder as well as in the caption.
        rule.onAllNodesWithText("Leo", useUnmergedTree = true).assertCountEquals(2)
        rule.onNodeWithText("2023").performClick()
        rule.runOnIdle { assertThat(opened).isTrue() }
    }

    @Test fun wideCard_click() {
        var opened = false
        rule.setThemed { WideCard(null, "Dark", "S1 · E3", 0.5f) { opened = true } }
        rule.onNodeWithText("S1 · E3").assertHasClickAction().performClick()
        rule.runOnIdle { assertThat(opened).isTrue() }
    }

    @Test fun rail_hidesWhenEmptyAndShowsItems() {
        var items by mutableStateOf(emptyList<String>())
        rule.setThemed { Rail("Continue watching", items, key = { it }) { Text(it) } }
        rule.onNodeWithText("Continue watching").assertDoesNotExist()
        items = listOf("Leo", "Jailer")
        rule.onNodeWithText("Continue watching").assertIsDisplayed()
        rule.onNodeWithText("Jailer").assertIsDisplayed()
    }

    @Test fun sectionHeader_actionFires() {
        var n by mutableIntStateOf(0)
        rule.setThemed { SectionHeader("Latest", action = "See all", onAction = { n++ }) }
        rule.onNodeWithText("See all").performClick()
        rule.runOnIdle { assertThat(n).isEqualTo(1) }
    }

    @Test fun brandFooterAndLogo() {
        rule.setThemed { Column { HarborLogo(64.androidx_dp()); MadeWithLove() } }
        rule.onNodeWithContentDescription("JellyVerse").assertIsDisplayed()
        rule.onNodeWithText("simpletools.in", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun progressIndicatorsRenderAtBounds() {
        rule.setThemed { Column { GradientProgress(1.5f); GradientProgress(-1f); ProgressStrip(0.3f); Text("ok") } }
        rule.onNodeWithText("ok").assertIsDisplayed()
    }

    @Test fun languagePicker_offersEachLanguageInItsOwnScript() {
        rule.setThemed { LanguagePicker() }
        rule.onNodeWithText("English").assertIsDisplayed()
        rule.onNodeWithText("தமிழ்").assertIsDisplayed().assertHasClickAction()
        rule.onNodeWithText("மொழி", substring = true).assertIsDisplayed()
    }

    private fun Int.androidx_dp() = androidx.compose.ui.unit.Dp(this.toFloat())
}
