package com.sridhar.harbor.data.jellyfin.admin

import com.google.common.truth.Truth.assertThat
import com.sridhar.harbor.data.HarborJson
import com.sridhar.harbor.data.jellyfin.JellyfinRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class JellyfinAdminRepositoryTest {
    private val server = MockWebServer()
    private lateinit var repo: JellyfinAdminRepository

    @Before fun setUp() {
        server.start()
        val api = Retrofit.Builder().baseUrl(server.url("/"))
            .addConverterFactory(HarborJson.asConverterFactory("application/json".toMediaType())).build()
            .create(JellyfinAdminApi::class.java)
        val jf = mockk<JellyfinRepository>()
        coEvery { jf.adminApi() } returns api
        repo = JellyfinAdminRepository(jf)
    }

    @After fun tearDown() = server.shutdown()

    @Test fun users_adminsFirstThenAlphabetical() = runTest {
        server.enqueue(MockResponse().setBody("""[{"Id":"2","Name":"zed"},{"Id":"3","Name":"Amy"},{"Id":"1","Name":"root","Policy":{"IsAdministrator":true}}]"""))
        assertThat(repo.users().map { it.name }).containsExactly("root", "Amy", "zed").inOrder()
    }

    @Test fun createUser_postsNameAndPassword() = runTest {
        server.enqueue(MockResponse().setBody("""{"Id":"9","Name":"Kids"}"""))
        val u = repo.createUser("  Kids ", "pw12")
        val req = server.takeRequest()
        assertThat(req.path).isEqualTo("/Users/New")
        assertThat(req.body.readUtf8()).isEqualTo("""{"Name":"Kids","Password":"pw12"}""")
        assertThat(u.id).isEqualTo("9")
    }

    @Test fun updatePolicy_readsFullPolicyThenPostsMergedCopy() = runTest {
        server.enqueue(MockResponse().setBody("""{"Id":"9","Name":"Kids","Policy":{"IsAdministrator":false,"AuthenticationProviderId":"prov","EnableContentDeletion":true}}"""))
        server.enqueue(MockResponse().setResponseCode(204))
        repo.updatePolicy("9", mapOf("EnableContentDeletion" to JsonPrimitive(false)))
        assertThat(server.takeRequest().path).isEqualTo("/Users/9")
        val post = server.takeRequest()
        assertThat(post.method).isEqualTo("POST")
        assertThat(post.path).isEqualTo("/Users/9/Policy")
        val body = HarborJson.parseToJsonElement(post.body.readUtf8()).jsonObject
        assertThat(body["EnableContentDeletion"]!!.jsonPrimitive.boolean).isFalse()
        assertThat(body["AuthenticationProviderId"]!!.jsonPrimitive.content).isEqualTo("prov")
    }

    @Test fun updatePolicy_noChangesSkipsNetwork() = runTest {
        repo.updatePolicy("9", emptyMap())
        assertThat(server.requestCount).isEqualTo(0)
    }

    @Test fun rename_sendsWholeUserWithNewName() = runTest {
        server.enqueue(MockResponse().setBody("""{"Id":"9","Name":"Kids","Configuration":{"PlayDefaultAudioTrack":true}}"""))
        server.enqueue(MockResponse().setResponseCode(204))
        repo.rename("9", "Family ")
        server.takeRequest()
        val post = server.takeRequest()
        assertThat(post.path).isEqualTo("/Users?userId=9")
        val body = HarborJson.parseToJsonElement(post.body.readUtf8()).jsonObject
        assertThat(body["Name"]!!.jsonPrimitive.content).isEqualTo("Family")
        assertThat(body["Configuration"]).isInstanceOf(JsonObject::class.java)
    }

    @Test fun passwordReset_usesQueryUserId() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))
        repo.resetPassword("9")
        val req = server.takeRequest()
        assertThat(req.path).isEqualTo("/Users/Password?userId=9")
        assertThat(req.body.readUtf8()).contains("\"ResetPassword\":true")
    }

    @Test fun forbidden_mapsToFriendlyAdminError() = runTest {
        server.enqueue(MockResponse().setResponseCode(403))
        val e = runCatching { repo.deleteUser("9") }.exceptionOrNull()
        assertThat(e).isInstanceOf(AdminException::class.java)
        assertThat(e).hasMessageThat().contains("isn't a Jellyfin administrator")
    }

    @Test fun sessionCommands_andSeek() = runTest {
        repeat(2) { server.enqueue(MockResponse().setResponseCode(204)) }
        repo.command("s1", PlayCommand.PlayPause)
        repo.command("s1", PlayCommand.Seek, 600_000_000)
        assertThat(server.takeRequest().path).isEqualTo("/Sessions/s1/Playing/PlayPause")
        assertThat(server.takeRequest().path).isEqualTo("/Sessions/s1/Playing/Seek?seekPositionTicks=600000000")
    }

    @Test fun addLibrary_passesTypeAndPath() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))
        repo.addLibrary("Tamil Movies", LibraryType.Movies, "/media/tamil")
        val req = server.takeRequest()
        assertThat(req.path).isEqualTo("/Library/VirtualFolders?name=Tamil%20Movies&collectionType=movies&paths=%2Fmedia%2Ftamil&refreshLibrary=true")
    }

    @Test fun tasks_runningFirst() = runTest {
        server.enqueue(MockResponse().setBody("""[{"Id":"a","Name":"Scan","Category":"Library"},{"Id":"b","Name":"Clean","Category":"Maintenance","State":"Running"}]"""))
        assertThat(repo.tasks().map { it.id }).containsExactly("b", "a").inOrder()
    }

    @Test fun logTail_truncatesHugeLogs() = runTest {
        server.enqueue(MockResponse().setBody("x".repeat(100) + "END"))
        val t = repo.logTail("log.txt", maxChars = 10)
        assertThat(t).endsWith("xxxxxxxEND")
        assertThat(t).startsWith("…")
    }
}
