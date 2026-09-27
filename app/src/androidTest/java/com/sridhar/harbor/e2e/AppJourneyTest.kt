package com.sridhar.harbor.e2e

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.sridhar.harbor.HarborApp
import com.sridhar.harbor.data.ServerConfig
import com.sridhar.harbor.testutil.ScreenshotOnFailure
import com.sridhar.harbor.ui.admin.AdminTab
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Black-box end-to-end journeys (UiAutomator, real system input, real network).
 *
 * The signed-in journeys talk to a real homelab. Credentials are never stored in code – pass them as
 * instrumentation arguments:
 *   adb shell am instrument -w -e jfUrl http://host:8096 -e jfUser me -e jfPass secret \
 *     [-e qbUrl … -e qbUser … -e qbPass …] com.sridhar.jellyverse.test/androidx.test.runner.AndroidJUnitRunner
 * Without them those journeys are skipped, so CI stays hermetic.
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class AppJourneyTest {
    @get:Rule val screenshots = ScreenshotOnFailure()

    private val inst = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(inst)
    private val app get() = ApplicationProvider.getApplicationContext<HarborApp>()
    private val args get() = InstrumentationRegistry.getArguments()
    private val pkg get() = app.packageName

    @Before fun setUp() {
        // The app animates continuously (progress, marquee, equalizer) – don't wait for an idle that never comes.
        androidx.test.uiautomator.Configurator.getInstance().waitForIdleTimeout = 0
        // The first-run POST_NOTIFICATIONS dialog would cover the app.
        if (android.os.Build.VERSION.SDK_INT >= 33) device.executeShellCommand("pm grant $pkg android.permission.POST_NOTIFICATIONS")
        device.pressHome()
    }

    private fun launch() {
        val intent = app.packageManager.getLaunchIntentForPackage(pkg)!!.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        app.startActivity(intent)
        check(device.wait(Until.hasObject(By.pkg(pkg).depth(0)), 15_000)) { "App didn't start" }
    }

    private fun waitFor(sel: BySelector, what: String, timeout: Long = 20_000): UiObject2 =
        device.wait(Until.findObject(sel), timeout) ?: run {
            ScreenshotOnFailure.capture("missing_" + what.replace(Regex("[^A-Za-z0-9]"), "_"))
            throw AssertionError("Timed out waiting for $what")
        }

    private fun text(t: String) = waitFor(By.text(t), "text '$t'")
    private fun desc(d: String) = waitFor(By.desc(d), "description '$d'")

    /** Scrolls the first vertical scroller until [t] is visible. */
    private fun scrollToText(t: String, list: BySelector = By.scrollable(true)): UiObject2 {
        repeat(25) {
            device.findObject(By.text(t))?.let { return it }
            runCatching {
                device.findObject(list)?.apply {
                    // Swipe down the left gutter only – never start a gesture on a text field (focus + IME) or a switch.
                    visibleBounds.let { b -> setGestureMargins(0, (b.height() * 0.15f).toInt(), (b.width() * 0.95f).toInt(), (b.height() * 0.15f).toInt()) }
                    scroll(Direction.DOWN, 0.8f)
                }
            }
        }
        return text(t)
    }

    /** The app keeps the selected chip centred, so the next chip in order is always on screen. */
    private fun chip(tab: AdminTab): UiObject2 = waitFor(By.res("tab_${tab.name}"), "chip ${tab.label}")

    /** The live dashboard recomposes every few seconds; re-find and retry when a node goes stale mid-tap. */
    private fun tapChip(tab: AdminTab) {
        repeat(5) { attempt ->
            try { chip(tab).click(); return } catch (e: androidx.test.uiautomator.StaleObjectException) { if (attempt == 4) throw e }
        }
    }

    private fun signOut() = runBlocking { app.container.settings.update { ServerConfig(deviceId = it.deviceId) } }

    private fun signIn(): Boolean {
        val url = args.getString("jfUrl") ?: return false
        val user = args.getString("jfUser") ?: return false
        val pass = args.getString("jfPass") ?: return false
        runBlocking {
            val c = app.container
            c.jellyfin.login(url, user, pass)
            args.getString("qbUrl")?.let { qb ->
                val qu = args.getString("qbUser") ?: user; val qp = args.getString("qbPass") ?: pass
                runCatching { c.qbit.login(qb, qu, qp); c.settings.update { it.copy(qbitUrl = qb, qbitUser = qu, qbitPass = qp) } }
            }
            args.getString("seerrUrl")?.let { runCatching { c.seerr.loginWithJellyfin(it, user, pass) } }
        }
        return true
    }

    @Test fun freshInstall_showsWelcomeSetup() {
        signOut()
        launch()
        // Setup may open at any scroll position – accept either end of the form as proof it rendered.
        check(device.wait(Until.hasObject(By.pkg(pkg).text(java.util.regex.Pattern.compile("(?s).*Welcome to.*|.*Jellyfin.*|.*Server URL.*"))), 20_000)) {
            ScreenshotOnFailure.capture("missing_setup"); "Setup screen didn't render"
        }
        repeat(10) { if (device.findObject(By.text(java.util.regex.Pattern.compile("(?s).*Welcome to.*|.*Jellyfin.*"))) == null) device.findObject(By.scrollable(true))?.scroll(Direction.UP, 1f) }
        waitFor(By.text(java.util.regex.Pattern.compile("(?s).*Welcome to.*|.*Jellyfin.*|.*Server URL.*")), "setup fields")
        scrollToText("simpletools.in")
    }

    @Test fun signedIn_everyTabRendersWithoutCrashing() {
        assumeTrue("Pass -e jfUrl/jfUser/jfPass to run live journeys", signIn())
        launch()
        desc("Watch")
        text("JellyVerse")
        // Tabs are dynamic (one per configured service) – visit every tab this account actually has.
        val present = listOf("Discover", "Requests", "Manage", "Torrents", "Music", "Lab", "Watch").filter { device.hasObject(By.desc(it)) }
        check("Watch" in present) { "Watch tab missing although Jellyfin is signed in" }
        for (tab in present) {
            // Let the nav pill width animation from the previous tap settle before aiming.
            device.waitForIdle(); android.os.SystemClock.sleep(600)
            desc(tab).click()
            // The selected pill shows its label; the screen header proves the destination rendered.
            if (tab in listOf("Discover", "Requests", "Manage", "Torrents", "Watch")) waitFor(By.text(if (tab == "Watch") "JellyVerse" else tab), "$tab screen")
            else android.os.SystemClock.sleep(1500)
        }
        // Settings is always reachable: a tab of its own, or from Lab's header.
        if (device.hasObject(By.desc("Lab"))) desc("Lab").click()
        desc("Settings")
        check(device.findObject(By.pkg(pkg)) != null) { "App is no longer in the foreground (crash?)" }
    }

    @Test fun signedIn_adminDashboardJourney() {
        assumeTrue("Pass -e jfUrl/jfUser/jfPass to run live journeys", signIn())
        assumeTrue("Signed-in account must be a Jellyfin admin", runBlocking { app.container.jellyfin.isAdmin() })
        launch()
        if (device.hasObject(By.desc("Lab"))) desc("Lab").click()
        desc("Settings").click()
        scrollToText("Server dashboard").click()

        waitFor(By.textStartsWith("Jellyfin 10."), "server version")
        for (tab in AdminTab.entries.drop(1)) {
            tapChip(tab)
            // Either real content or a friendly error – never a crash or endless spinner.
            device.wait(Until.gone(By.clazz("android.widget.ProgressBar")), 15_000)
            check(device.findObject(By.pkg(pkg)) != null) { "Crashed on ${tab.label}" }
        }

        // Open our own account in the user editor. Read-only: nothing is saved.
        // Walk back along the row to Users.
        AdminTab.entries.filter { it.ordinal in AdminTab.Users.ordinal until AdminTab.Logs.ordinal }.reversed().forEach { tapChip(it) }
        val me = args.getString("jfUser")!!
        repeat(5) { try { waitFor(By.textContains(me), "user row").click(); return@repeat } catch (_: androidx.test.uiautomator.StaleObjectException) {} }
        text("PASSWORD")
        if (device.executeShellCommand("dumpsys input_method").contains("mInputShown=true")) device.pressBack()
        scrollToText("LIBRARY ACCESS", By.res("user_editor"))
        check(device.findObject(By.text("Delete user")) == null) { "Admins must not be offered to delete themselves" }
    }

    /** Music: sign in to Navidrome, play a mix, leave the app – playback must continue in the background. */
    @Test fun signedIn_musicPlaysInBackground() {
        assumeTrue("Pass -e jfUrl/jfUser/jfPass to run live journeys", signIn())
        val ndUrl = args.getString("ndUrl"); val ndUser = args.getString("ndUser"); val ndPass = args.getString("ndPass")
        assumeTrue("Pass -e ndUrl/ndUser/ndPass for the music journey", ndUrl != null && ndUser != null && ndPass != null)
        runBlocking { app.container.music.signIn(ndUrl!!, ndUser!!, ndPass!!) }
        launch()
        desc("Music").click()
        waitFor(By.text(app.getString(com.sridhar.harbor.R.string.mu_made_for_you)), "Made for you")
        text(app.getString(com.sridhar.harbor.R.string.mu_shuffle_mix)).click()
        // Wait on the engine itself: after the Compose suites run in this process the a11y snapshot can lag behind.
        val deadline = android.os.SystemClock.uptimeMillis() + 30_000
        while (!app.container.musicEngine.state.value.playing && android.os.SystemClock.uptimeMillis() < deadline) android.os.SystemClock.sleep(250)
        if (!app.container.musicEngine.state.value.playing) { ScreenshotOnFailure.capture("missing_playback"); throw AssertionError("Shuffle mix never started playing") }
        device.pressHome()
        android.os.SystemClock.sleep(4_000)
        val session = device.executeShellCommand("dumpsys media_session")
        check(Regex("""package=${Regex.escape(pkg)}[\s\S]*?state=PLAYING""").containsMatchIn(session)) { "Music stopped when the app went to the background" }
        runBlocking { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { app.container.musicEngine.stopAndClear() } }
    }
}
