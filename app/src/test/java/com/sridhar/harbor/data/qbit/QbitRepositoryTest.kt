package com.sridhar.harbor.data.qbit

import com.google.common.truth.Truth.assertThat
import com.sridhar.harbor.data.ServerConfig
import com.sridhar.harbor.testutil.fakeSettings
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

class QbitRepositoryTest {
    private val server = MockWebServer()
    private lateinit var repo: QbitRepository
    private val paths = mutableListOf<String>()
    private var loggedIn = false
    private var modernApi = true

    @Before fun setUp() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path!!.removePrefix("/api/v2/")
                paths += path
                return when {
                    path == "auth/login" -> {
                        val body = request.body.readUtf8()
                        if ("password=secret" in body) { loggedIn = true; MockResponse().setBody("Ok.").addHeader("Set-Cookie", "SID=abc; path=/") }
                        else MockResponse().setBody("Fails.")
                    }
                    path == "app/version" -> MockResponse().setBody("v5.2.3")
                    !loggedIn || request.getHeader("Cookie")?.contains("SID=abc") != true -> MockResponse().setResponseCode(403)
                    path.startsWith("torrents/info") -> MockResponse().setBody("""[{"hash":"h1","name":"Leo","progress":0.5,"state":"downloading","dlspeed":1024,"size":2048}]""")
                    path == "torrents/stop" || path == "torrents/start" -> if (modernApi) MockResponse() else MockResponse().setResponseCode(404)
                    path == "torrents/pause" || path == "torrents/resume" -> MockResponse()
                    path == "torrents/add" -> MockResponse().setBody("Ok.")
                    else -> MockResponse()
                }
            }
        }
        server.start()
        val (settings, _) = fakeSettings(ServerConfig(qbitUrl = server.url("/").toString(), qbitUser = "admin", qbitPass = "secret"))
        repo = QbitRepository(settings, OkHttpClient())
    }

    @After fun tearDown() = server.shutdown()

    @Test fun expiredSession_reLogsInOnceAndRetries() = runTest {
        val list = repo.torrents()
        assertThat(list.single().name).isEqualTo("Leo")
        assertThat(paths.take(3)).containsExactly("torrents/info?filter=all&sort=added_on&reverse=true", "auth/login", "app/version").inOrder()
    }

    @Test fun login_returnsServerVersion() = runTest {
        assertThat(repo.login()).isEqualTo("v5.2.3")
    }

    @Test fun login_wrongPasswordThrows() = runTest {
        val e = runCatching { repo.login(pass = "nope") }.exceptionOrNull()
        assertThat(e).isInstanceOf(QbitException::class.java)
        assertThat(e).hasMessageThat().contains("login failed")
    }

    @Test fun pause_usesStopOnQbit5() = runTest {
        repo.login(); paths.clear()
        repo.pause(listOf("h1", "h2"))
        assertThat(paths).containsExactly("torrents/stop")
    }

    @Test fun pause_fallsBackToLegacyApiAndRemembersIt() = runTest {
        modernApi = false
        repo.login(); paths.clear()
        repo.pause(emptyList())
        repo.resume(emptyList())
        assertThat(paths).containsExactly("torrents/stop", "torrents/pause", "torrents/resume").inOrder()
    }

    @Test fun setGlobalLimits_postsBothLimits() = runTest {
        repo.login(); paths.clear()
        repo.setGlobalLimits(1_000_000, 0)
        assertThat(paths).containsExactly("transfer/setDownloadLimit", "transfer/setUploadLimit").inOrder()
    }

    @Test fun unconfiguredServer_throwsFriendlyError() = runTest {
        val (settings, _) = fakeSettings(ServerConfig())
        val e = runCatching { QbitRepository(settings, OkHttpClient()).torrents() }.exceptionOrNull()
        assertThat(e).hasMessageThat().contains("not configured")
    }
}
