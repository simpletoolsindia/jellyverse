package com.sridhar.harbor.data.aria2

import com.google.common.truth.Truth.assertThat
import com.sridhar.harbor.data.ServerConfig
import com.sridhar.harbor.testutil.fakeSettings
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

class Aria2RepositoryTest {
    private val server = MockWebServer()
    private lateinit var repo: Aria2Repository
    private lateinit var state: kotlinx.coroutines.flow.MutableStateFlow<ServerConfig>

    @Before fun setUp() {
        server.start()
        val (s, st) = fakeSettings(ServerConfig(aria2Url = server.url("/").toString().trimEnd('/'), aria2Secret = "tok"))
        state = st
        repo = Aria2Repository(s, OkHttpClient())
    }

    @After fun tearDown() = server.shutdown()

    private fun lastRequest() = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject

    @Test fun rpc_prependsSecretToken() = runTest {
        server.enqueue(MockResponse().setBody("""{"id":"1","jsonrpc":"2.0","result":"OK"}"""))
        repo.pause("gid1")
        val req = lastRequest()
        assertThat(req["method"]!!.jsonPrimitive.content).isEqualTo("aria2.pause")
        assertThat(req["params"]!!.jsonArray.map { it.jsonPrimitive.content }).containsExactly("token:tok", "gid1").inOrder()
    }

    @Test fun downloads_mergesActiveWaitingAndStopped() = runTest {
        server.enqueue(MockResponse().setBody("""{"result":[{"gid":"a","status":"active","totalLength":"100","completedLength":"25","downloadSpeed":"25"}]}"""))
        server.enqueue(MockResponse().setBody("""{"result":[{"gid":"b","status":"waiting","files":[{"path":"/dl/ubuntu.iso"}]}]}"""))
        server.enqueue(MockResponse().setBody("""{"result":[{"gid":"c","status":"complete","bittorrent":{"info":{"name":"Big Buck Bunny"}}}]}"""))
        val list = repo.downloads()
        assertThat(list.map { it.gid }).containsExactly("a", "b", "c").inOrder()
        assertThat(list[0].progress).isWithin(0.001f).of(0.25f)
        assertThat(list[0].eta).isEqualTo(3)
        assertThat(list[1].name).isEqualTo("ubuntu.iso")
        assertThat(list[2].name).isEqualTo("Big Buck Bunny")
        assertThat(list[2].isTorrent).isTrue()
    }

    @Test fun remove_forceRemovesActiveButClearsFinished() = runTest {
        repeat(2) { server.enqueue(MockResponse().setBody("""{"result":"OK"}""")) }
        repo.remove(Aria2Download(gid = "a", status = "active"))
        assertThat(lastRequest()["method"]!!.jsonPrimitive.content).isEqualTo("aria2.forceRemove")
        repo.remove(Aria2Download(gid = "b", status = "complete"))
        assertThat(lastRequest()["method"]!!.jsonPrimitive.content).isEqualTo("aria2.removeDownloadResult")
    }

    @Test fun rpcError_surfacesMessage() = runTest {
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":{"code":1,"message":"Unauthorized"}}"""))
        val e = runCatching { repo.stat() }.exceptionOrNull()
        assertThat(e).hasMessageThat().isEqualTo("aria2: Unauthorized")
    }

    @Test fun test_savesNormalisedConfigOnSuccess() = runTest {
        server.enqueue(MockResponse().setBody("""{"result":{"version":"1.37.0"}}"""))
        val url = server.url("/").toString()
        assertThat(repo.test(url, "new")).isEqualTo("1.37.0")
        assertThat(state.value.aria2Secret).isEqualTo("new")
        assertThat(state.value.aria2Url).doesNotContain("//$")
    }

    @Test fun nonJsonResponse_isHttpError() = runTest {
        server.enqueue(MockResponse().setResponseCode(502).setBody("Bad gateway"))
        assertThat(runCatching { repo.stat() }.exceptionOrNull()).hasMessageThat().isEqualTo("aria2: HTTP 502")
    }
}
