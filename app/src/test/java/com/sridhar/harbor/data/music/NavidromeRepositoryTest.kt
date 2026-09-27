package com.sridhar.harbor.data.music

import com.google.common.truth.Truth.assertThat
import com.sridhar.harbor.data.ServerConfig
import com.sridhar.harbor.testutil.fakeSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

class NavidromeRepositoryTest {
    private val server = MockWebServer()
    private lateinit var repo: NavidromeRepository
    private lateinit var state: MutableStateFlow<ServerConfig>

    private fun ok(body: String) = MockResponse().setBody("""{"subsonic-response":{"status":"ok","version":"1.16.1","type":"navidrome","serverVersion":"0.64.0",$body}}""")

    @Before fun setUp() {
        server.start()
        val (s, st) = fakeSettings(ServerConfig(navidromeUrl = server.url("/").toString(), navidromeUser = "admin", navidromeSalt = "abc", navidromeToken = "tok"))
        state = st
        repo = NavidromeRepository(s, OkHttpClient())
    }

    @After fun tearDown() = server.shutdown()

    @Test fun signIn_storesSaltedTokenNeverPassword() = runTest {
        server.enqueue(ok(""""x":1"""))
        val v = repo.signIn(server.url("/").toString(), " admin ", "sesame")
        val req = server.takeRequest().requestUrl!!
        assertThat(req.encodedPath).isEqualTo("/rest/ping")
        assertThat(req.queryParameter("p")).isNull()
        val salt = req.queryParameter("s")!!
        assertThat(req.queryParameter("t")).isEqualTo(SubsonicAuth.token("sesame", salt))
        assertThat(v).isEqualTo("0.64.0")
        assertThat(state.value.navidromeToken).isEqualTo(SubsonicAuth.token("sesame", salt))
        assertThat(state.value.navidromeUser).isEqualTo("admin")
        assertThat(state.value.toString()).doesNotContain("sesame")
    }

    @Test fun wrongPassword_mapsToFriendlyError() = runTest {
        server.enqueue(MockResponse().setBody("""{"subsonic-response":{"status":"failed","error":{"code":40,"message":"Wrong username or password"}}}"""))
        val e = runCatching { repo.signIn(server.url("/").toString(), "admin", "x") }.exceptionOrNull()
        assertThat(e).isInstanceOf(SubsonicException::class.java)
        assertThat(e).hasMessageThat().isEqualTo("Wrong Navidrome username or password")
    }

    @Test fun emptyLibrary_hasActionableMessage() = runTest {
        server.enqueue(MockResponse().setBody("""{"subsonic-response":{"status":"failed","error":{"code":70,"message":"Library not found or empty"}}}"""))
        assertThat(runCatching { repo.artists() }.exceptionOrNull()).hasMessageThat().contains("run a scan")
    }

    @Test fun albums_parseAndPassListType() = runTest {
        server.enqueue(ok(""""albumList2":{"album":[{"id":"a1","name":"Dum - MassTamilan.com","artist":"Sadhana Sargam - MassTamilan.com","songCount":5}]}"""))
        val list = repo.albums("newest", 10)
        val url = server.takeRequest().requestUrl!!
        assertThat(url.queryParameter("type")).isEqualTo("newest")
        assertThat(url.queryParameter("size")).isEqualTo("10")
        assertThat(list.single().displayName).isEqualTo("Dum")
        assertThat(list.single().displayArtist).isEqualTo("Sadhana Sargam")
    }

    @Test fun genreFilterSwitchesToByGenre() = runTest {
        server.enqueue(ok(""""albumList2":{}"""))
        repo.albums("newest", genre = "Tamil")
        val url = server.takeRequest().requestUrl!!
        assertThat(url.queryParameter("type")).isEqualTo("byGenre")
        assertThat(url.queryParameter("genre")).isEqualTo("Tamil")
    }

    @Test fun emptyResultsDecodeToEmptyLists() = runTest {
        server.enqueue(ok(""""randomSongs":{}"""))
        assertThat(repo.randomSongs()).isEmpty()
    }

    @Test fun genres_dropSiteTags() = runTest {
        server.enqueue(ok(""""genres":{"genre":[{"value":"MassTamilan.com","songCount":297},{"value":"Tamil","songCount":47}]}"""))
        assertThat(repo.genres().map { it.value }).containsExactly("Tamil")
    }

    @Test fun lyrics_preferSynced() = runTest {
        server.enqueue(ok(""""lyricsList":{"structuredLyrics":[{"synced":false,"line":[{"value":"plain"}]},{"synced":true,"offset":100,"line":[{"start":0,"value":"a"},{"start":900,"value":"b"}]}]}"""))
        val l = repo.lyrics("s1")!!
        assertThat(l.synced).isTrue()
        assertThat(l.offset).isEqualTo(100)
    }

    @Test fun scrobbleNeverThrows() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))
        repo.scrobble("s1", true)   // no exception
    }

    @Test fun streamAndCoverUrlsCarryTokenAuth() {
        val cfg = state.value
        val stream = repo.streamUrl(cfg, "s1", 128).toHttpUrl()
        assertThat(stream.encodedPath).isEqualTo("/rest/stream")
        assertThat(stream.queryParameter("id")).isEqualTo("s1")
        assertThat(stream.queryParameter("maxBitRate")).isEqualTo("128")
        assertThat(stream.queryParameter("t")).isEqualTo("tok")
        assertThat(repo.streamUrl(cfg, "s1").toHttpUrl().queryParameter("maxBitRate")).isNull()
        assertThat(repo.coverUrl(cfg, null)).isNull()
        assertThat(repo.coverUrl(cfg, "al-1", 300)!!.toHttpUrl().queryParameter("size")).isEqualTo("300")
        assertThat(repo.coverUrl(ServerConfig(), "al-1")).isNull()
    }

    @Test fun unconfigured_throwsClearly() = runTest {
        val (s, _) = fakeSettings(ServerConfig())
        assertThat(runCatching { NavidromeRepository(s, OkHttpClient()).playlists() }.exceptionOrNull()).hasMessageThat().contains("not configured")
    }
}
