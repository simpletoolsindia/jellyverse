package com.sridhar.harbor.ui.admin

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.google.common.truth.Truth.assertThat
import com.sridhar.harbor.data.jellyfin.admin.ActivityEntry
import com.sridhar.harbor.data.jellyfin.admin.AdminPolicy
import com.sridhar.harbor.data.jellyfin.admin.AdminSession
import com.sridhar.harbor.data.jellyfin.admin.AdminUser
import com.sridhar.harbor.data.jellyfin.admin.ApiKey
import com.sridhar.harbor.data.jellyfin.admin.DeviceInfo
import com.sridhar.harbor.data.jellyfin.admin.ItemCounts
import com.sridhar.harbor.data.jellyfin.admin.LibraryFolder
import com.sridhar.harbor.data.jellyfin.admin.NowPlaying
import com.sridhar.harbor.data.jellyfin.admin.PlayCommand
import com.sridhar.harbor.data.jellyfin.admin.PluginInfo
import com.sridhar.harbor.data.jellyfin.admin.PolicyEditor
import com.sridhar.harbor.data.jellyfin.admin.PolicyFlag
import com.sridhar.harbor.data.jellyfin.admin.ScheduledTask
import com.sridhar.harbor.data.jellyfin.admin.SessionPlayState
import com.sridhar.harbor.data.jellyfin.admin.SystemInfo
import com.sridhar.harbor.data.jellyfin.admin.TaskResult
import com.sridhar.harbor.data.jellyfin.admin.TranscodingInfo
import com.sridhar.harbor.testutil.setThemed
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@MediumTest
@RunWith(AndroidJUnit4::class)
class AdminComponentsTest {
    @get:Rule val rule = createComposeRule()

    private val playing = AdminSession(
        id = "s1", userName = "kids", client = "Android TV", deviceName = "Living room", appVersion = "0.18",
        supportsMediaControl = true,
        nowPlaying = NowPlaying(name = "Leo", year = 2023, runTimeTicks = 100),
        playState = SessionPlayState(positionTicks = 50, canSeek = true),
        transcoding = TranscodingInfo(videoCodec = "h264", isVideoDirect = false, bitrate = 8_000_000),
    )

    @Test fun sessionCard_showsNowPlayingAndSendsCommands() {
        val sent = mutableListOf<Any>()
        rule.setThemed { SessionCard(playing, onCommand = { sent += it }, onSeek = { sent += it }, onMessage = { sent += "msg" }) }
        rule.onNodeWithText("Living room").assertIsDisplayed()
        rule.onNodeWithText("Leo · 2023").assertIsDisplayed()
        rule.onNodeWithText("Transcoding · H264").assertIsDisplayed()
        rule.onNodeWithText("8.0 Mbps").assertIsDisplayed()
        rule.onNodeWithContentDescription("Pause").performClick()
        rule.onNodeWithContentDescription("Forward 30 seconds").performClick()
        rule.onNodeWithContentDescription("Back 10 seconds").performClick()
        rule.onNodeWithContentDescription("Stop").performClick()
        rule.onNodeWithContentDescription("Send message").performClick()
        rule.runOnIdle { assertThat(sent).containsExactly(PlayCommand.PlayPause, 30, -10, PlayCommand.Stop, "msg").inOrder() }
    }

    @Test fun sessionCard_pausedShowsResume() {
        rule.setThemed { SessionCard(playing.copy(playState = playing.playState.copy(isPaused = true)), {}, {}, null) }
        rule.onNodeWithContentDescription("Resume").assertIsDisplayed()
        rule.onNodeWithText("Paused").assertIsDisplayed()
        rule.onNodeWithContentDescription("Send message").assertDoesNotExist()
    }

    @Test fun sessionCard_idleOrUncontrollableHasNoTransport() {
        rule.setThemed { SessionCard(AdminSession("s2", deviceName = "Chrome", client = "Jellyfin Web"), {}, {}, null) }
        rule.onNodeWithText("Chrome").assertIsDisplayed()
        rule.onNodeWithContentDescription("Stop").assertDoesNotExist()
    }

    @Test fun userRow_badgesAndClick() {
        var opened = false
        val u = AdminUser("u1", "Kids", hasPassword = false, policy = AdminPolicy(isDisabled = true, isHidden = true))
        rule.setThemed { UserRow(u, isSelf = false, avatar = null) { opened = true } }
        rule.onNodeWithText("User").assertIsDisplayed()
        rule.onNodeWithText("Disabled").assertIsDisplayed()
        rule.onNodeWithText("Hidden").assertIsDisplayed()
        rule.onNodeWithText("No password").assertIsDisplayed()
        rule.onNodeWithText("Never signed in").assertIsDisplayed()
        rule.onNodeWithTag("user_Kids").performClick()
        rule.runOnIdle { assertThat(opened).isTrue() }
    }

    @Test fun userRow_adminSelf() {
        rule.setThemed { UserRow(AdminUser("me", "root", policy = AdminPolicy(isAdministrator = true)), isSelf = true, avatar = null) {} }
        rule.onNodeWithText("Admin").assertIsDisplayed()
        rule.onNodeWithText("(you)", substring = true).assertIsDisplayed()
    }

    @Test fun addUserDialog_validatesThenCreates() {
        var created: Pair<String, String>? = null
        rule.setThemed {
            AddUserDialog(validate = { n, p -> PolicyEditor.validateNewUser(n, p, listOf("root")) }, onDismiss = {}) { n, p -> created = n to p }
        }
        rule.onNodeWithTag("create_user").assertIsNotEnabled()
        rule.onNodeWithTag("new_user_name").performTextInput("Root")
        rule.onNodeWithTag("new_user_error").assertIsDisplayed()
        rule.onNodeWithTag("create_user").assertIsNotEnabled()
        rule.onNodeWithTag("new_user_name").performTextInput("2")
        rule.onNodeWithTag("new_user_pass").performTextInput("abc")
        rule.onNodeWithText("Use at least 4 characters", substring = true).assertIsDisplayed()
        rule.onNodeWithTag("new_user_pass").performTextInput("d")
        rule.onNodeWithTag("new_user_error").assertDoesNotExist()
        rule.onNodeWithTag("create_user").assertIsEnabled().performClick()
        rule.runOnIdle { assertThat(created).isEqualTo("Root2" to "abcd") }
    }

    @Test fun policySwitch_togglesAndRespectsLock() {
        var on by mutableStateOf(false)
        val flag = PolicyFlag("EnableContentDeletion", "Allow media deletion", "Content")
        rule.setThemed { PolicySwitch(flag, on) { on = it } }
        val sw = rule.onNode(isToggleable() and hasAnyAncestor(hasTestTag("flag_EnableContentDeletion")))
        sw.assertIsOff()
        rule.onNodeWithText("Allow media deletion").performClick()
        sw.assertIsOn()
    }

    @Test fun policySwitch_lockedCannotChange() {
        var on by mutableStateOf(true)
        rule.setThemed { PolicySwitch(PolicyFlag("IsAdministrator", "Administrator", "Role", "Full control"), on, enabled = false) { on = it } }
        rule.onNodeWithText("Full control").assertIsDisplayed()
        rule.onNodeWithText("Administrator").performClick()
        rule.runOnIdle { assertThat(on).isTrue() }
    }

    @Test fun taskRow_runStopAndStatus() {
        var toggles = 0
        var task by mutableStateOf(ScheduledTask("t", "Scan Media Library", last = TaskResult(status = "Failed")))
        rule.setThemed { TaskRow(task) { toggles++ } }
        rule.onNodeWithText("Failed", substring = true).assertIsDisplayed()
        rule.onNodeWithContentDescription("Run Scan Media Library").performClick()
        task = task.copy(state = "Running", progress = 42.0)
        rule.onNodeWithText("Running…").assertIsDisplayed()
        rule.onNodeWithContentDescription("Stop Scan Media Library").performClick()
        rule.runOnIdle { assertThat(toggles).isEqualTo(2) }
    }

    @Test fun libraryRow_scanRemoveAndProgress() {
        val calls = mutableListOf<String>()
        val lib = LibraryFolder("Movies", "movies", listOf("/media/movies"), "id1", refreshProgress = 37.0, refreshStatus = "Active")
        rule.setThemed { LibraryRow(lib, onScan = { calls += "scan" }, onRemove = { calls += "remove" }) }
        rule.onNodeWithText("/media/movies").assertIsDisplayed()
        rule.onNodeWithText("Scanning… 37%").assertIsDisplayed()
        rule.onNodeWithContentDescription("Scan Movies").performClick()
        rule.onNodeWithContentDescription("Remove Movies").performClick()
        rule.runOnIdle { assertThat(calls).containsExactly("scan", "remove").inOrder() }
    }

    @Test fun pluginRow_onlyThirdPartyPluginsCanBeToggled() {
        rule.setThemed { PluginRow(PluginInfo("p", "TMDb", "10.11", status = "Active", canUninstall = false)) {} }
        rule.onNodeWithText("v10.11 · Active").assertIsDisplayed()
        rule.onNode(isToggleable()).assertDoesNotExist()
    }

    @Test fun pluginRow_toggle() {
        var enabled: Boolean? = null
        rule.setThemed { PluginRow(PluginInfo("p", "Intro Skipper", "1.0", status = "Active", canUninstall = true)) { enabled = it } }
        rule.onNode(isToggleable()).assertIsOn().performClick()
        rule.runOnIdle { assertThat(enabled).isFalse() }
    }

    @Test fun overviewHeader_stats() {
        val sessions = listOf(playing, AdminSession("idle"))
        rule.setThemed { OverviewHeader(SystemInfo(serverName = "deploy", version = "10.11.11", pendingRestart = true), ItemCounts(movies = 812, series = 64, episodes = 2048), 5, sessions) }
        rule.onNodeWithText("deploy").assertIsDisplayed()
        rule.onNodeWithText("Jellyfin 10.11.11", substring = true).assertIsDisplayed()
        rule.onNodeWithText("Restart pending to apply changes").assertIsDisplayed()
        rule.onNodeWithText("812").assertIsDisplayed()
        rule.onNodeWithText("2048").assertIsDisplayed()
    }

    @Test fun rowsForActivityDevicesAndKeys() {
        var removed = false; var revoked = false
        rule.setThemed {
            androidx.compose.foundation.layout.Column {
                ActivityRow(ActivityEntry(1, "kids started playing Leo", "Living room", "VideoPlayback"))
                DeviceRow(DeviceInfo("d", "Pixel 9", appName = "JellyVerse", appVersion = "2.2.0")) { removed = true }
                ApiKeyRow(ApiKey("0123456789abcdef", "Home Assistant")) { revoked = true }
            }
        }
        rule.onNodeWithText("kids started playing Leo").assertIsDisplayed()
        rule.onNodeWithText("Home Assistant").assertIsDisplayed()
        rule.onNodeWithText("012345••••••cdef", substring = true).assertIsDisplayed()
        rule.onNodeWithContentDescription("Sign out Pixel 9").performClick()
        rule.onNodeWithContentDescription("Revoke key").performClick()
        rule.runOnIdle { assertThat(removed && revoked).isTrue() }
    }
}
