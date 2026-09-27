package com.sridhar.harbor.ui.admin

import com.google.common.truth.Truth.assertThat
import com.sridhar.harbor.data.jellyfin.admin.AdminSession
import com.sridhar.harbor.data.jellyfin.admin.AdminUser
import com.sridhar.harbor.data.jellyfin.admin.ItemCounts
import com.sridhar.harbor.data.jellyfin.admin.JellyfinAdminRepository
import com.sridhar.harbor.data.jellyfin.admin.NowPlaying
import com.sridhar.harbor.data.jellyfin.admin.PlayCommand
import com.sridhar.harbor.data.jellyfin.admin.ScheduledTask
import com.sridhar.harbor.data.jellyfin.admin.SessionPlayState
import com.sridhar.harbor.data.jellyfin.admin.SystemInfo
import com.sridhar.harbor.testutil.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class AdminViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    @Before fun english() = com.sridhar.harbor.testutil.EnglishStrings.install()
    private val repo = mockk<JellyfinAdminRepository>(relaxed = true)

    @Before fun stubs() {
        coEvery { repo.systemInfo() } returns SystemInfo(serverName = "deploy", version = "10.11.11")
        coEvery { repo.counts() } returns ItemCounts(movies = 3)
        coEvery { repo.sessions() } returns emptyList()
        coEvery { repo.users() } returns listOf(AdminUser("me", "admin"))
    }

    private val store = ViewModelStore()

    /** Created through a ViewModelStore so @After can clear it – cancelling viewModelScope and its poller. */
    private fun vm(): AdminViewModel = ViewModelProvider(store, object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = AdminViewModel(repo, selfUserId = "me") as T
    })[AdminViewModel::class.java]

    @After fun clear() = store.clear()

    // viewModelScope runs on Dispatchers.Main = the rule's dispatcher; drive its virtual clock directly.
    private fun runCurrent() = main.dispatcher.scheduler.runCurrent()
    private fun advanceTimeBy(ms: Long) = main.dispatcher.scheduler.advanceTimeBy(ms)

    @Test fun overviewLoadsServerInfoOnStart() {
        val vm = vm()
        assertThat(vm.info.loading).isFalse()
        runCurrent()
        assertThat(vm.info.data?.version).isEqualTo("10.11.11")
        assertThat(vm.counts.data?.movies).isEqualTo(3)
        assertThat(vm.users.data).hasSize(1)
    }

    @Test fun liveTabsPollInTheBackground() {
        val vm = vm()
        vm.select(AdminTab.Playing)
        runCurrent()
        advanceTimeBy(3_100)
        runCurrent()
        // Initial Overview load + Playing load + one poll.
        coVerify(atLeast = 3) { repo.sessions() }
    }

    @Test fun staticTabsDoNotPoll() {
        val vm = vm()
        vm.select(AdminTab.Users)
        runCurrent()
        advanceTimeBy(20_000)
        runCurrent()
        coVerify(exactly = 2) { repo.users() } // Overview (quiet) + Users tab
    }

    @Test fun loadErrorIsShownWhenNoData() {
        coEvery { repo.devices() } throws IOException("boom")
        val vm = vm()
        vm.select(AdminTab.Devices)
        runCurrent()
        assertThat(vm.devices.error).isNotNull()
        assertThat(vm.devices.data).isNull()
    }

    @Test fun seekIsClampedToRuntime() {
        val vm = vm()
        val s = AdminSession("s", nowPlaying = NowPlaying(runTimeTicks = 100_000_000), playState = SessionPlayState(positionTicks = 90_000_000))
        vm.seekBy(s, 30)
        runCurrent()
        coVerify { repo.command("s", PlayCommand.Seek, 100_000_000) }
        vm.seekBy(s, -60)
        runCurrent()
        coVerify { repo.command("s", PlayCommand.Seek, 0) }
    }

    @Test fun taskToggleStartsOrStops() {
        val vm = vm()
        vm.toggleTask(ScheduledTask("t1", "Scan", state = "Idle"))
        vm.toggleTask(ScheduledTask("t2", "Clean", state = "Running"))
        runCurrent()
        coVerify { repo.startTask("t1") }
        coVerify { repo.stopTask("t2") }
        assertThat(vm.message).isNotNull()
    }

    @Test fun actionFailureSurfacesMessage() {
        coEvery { repo.restart() } throws IOException("Restart: this account isn't a Jellyfin administrator")
        val vm = vm()
        vm.restart()
        runCurrent()
        assertThat(vm.message).contains("administrator")
    }

    @Test fun validateNewUserUsesLoadedNames() {
        val vm = vm()
        runCurrent()
        assertThat(vm.validateNewUser("ADMIN", "")).contains("already exists")
        assertThat(vm.validateNewUser("Guest", "")).isNull()
    }
}
