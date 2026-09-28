package com.sridhar.harbor.tools

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sridhar.harbor.HarborApp
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Not a test – a fixture for manual device walkthroughs: signs the app in to Jellyfin (and optionally Navidrome)
 * from instrumentation args, so remote-only UI checks don't depend on on-screen typing. Credentials are never in code.
 * Run with `-e jfUrl … -e jfUser … -e jfPass … [-e ndUrl … -e ndUser … -e ndPass …]`.
 */
@RunWith(AndroidJUnit4::class)
class SignInTool {
    @Test fun signIn() {
        val a = InstrumentationRegistry.getArguments()
        val url = a.getString("jfUrl"); val user = a.getString("jfUser"); val pass = a.getString("jfPass")
        assumeTrue("Pass -e jfUrl/jfUser/jfPass", url != null && user != null && pass != null)
        val c = ApplicationProvider.getApplicationContext<HarborApp>().container
        runBlocking {
            c.jellyfin.login(url!!, user!!, pass!!)
            val nd = a.getString("ndUrl"); val nu = a.getString("ndUser"); val np = a.getString("ndPass")
            if (nd != null && nu != null && np != null) runCatching { c.music.signIn(nd, nu, np) }
        }
    }
}
