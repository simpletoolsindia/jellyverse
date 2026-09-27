package com.sridhar.harbor.tools

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sridhar.harbor.HarborApp
import com.sridhar.harbor.data.ServerConfig
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Not a test – a fixture for stress runs (monkey). Points every service at an unreachable address so random input
 * exercises all error/empty states without ever touching real servers. Only runs with `-e seed unreachable`.
 */
@RunWith(AndroidJUnit4::class)
class SeedConfig {
    @Test fun seedUnreachableServers() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("seed") == "unreachable")
        val app = ApplicationProvider.getApplicationContext<HarborApp>()
        val dead = "http://10.255.255.1:9"   // non-routable: every request fails fast with a timeout
        runBlocking {
            app.container.settings.update {
                ServerConfig(
                    jellyfinUrl = dead, jellyfinUser = "monkey", jellyfinToken = "x", jellyfinUserId = "x",
                    qbitUrl = dead, qbitUser = "monkey", qbitPass = "x", seerrUrl = dead, seerrApiKey = "x",
                    sonarrUrl = dead, sonarrKey = "x", radarrUrl = dead, radarrKey = "x", aria2Url = dead,
                    navidromeUrl = dead, navidromeUser = "monkey", navidromeSalt = "s", navidromeToken = "t", deviceId = it.deviceId,
                )
            }
        }
    }
}
