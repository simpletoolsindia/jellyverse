package com.sridhar.harbor.data.jellyfin.admin

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Test

class PolicyEditorTest {
    private val server = JsonObject(mapOf(
        "IsAdministrator" to JsonPrimitive(false),
        "EnableAllFolders" to JsonPrimitive(true),
        "AuthenticationProviderId" to JsonPrimitive("Jellyfin.Server.Implementations.Users.DefaultAuthenticationProvider"),
        "BlockedTags" to JsonArray(listOf(JsonPrimitive("gore"))),
    ))

    @Test fun merge_overridesOnlyChangedKeysAndKeepsUnknownFields() {
        val merged = PolicyEditor.merge(server, mapOf("IsAdministrator" to JsonPrimitive(true)))
        assertThat(PolicyEditor.isOn(merged, "IsAdministrator")).isTrue()
        assertThat(merged["AuthenticationProviderId"]).isEqualTo(server["AuthenticationProviderId"])
        assertThat(merged["BlockedTags"]).isEqualTo(server["BlockedTags"])
    }

    @Test fun isOn_missingOrNonBooleanIsFalse() {
        assertThat(PolicyEditor.isOn(server, "EnableLiveTvAccess")).isFalse()
        assertThat(PolicyEditor.isOn(server, "AuthenticationProviderId")).isFalse()
    }

    @Test fun libraryAccess_allFoldersClearsList() {
        val all = PolicyEditor.libraryAccess(true, listOf("b", "a"))
        assertThat(all["EnabledFolders"]).isEqualTo(JsonArray(emptyList()))
        val some = PolicyEditor.libraryAccess(false, setOf("b", "a"))
        assertThat(some["EnableAllFolders"]).isEqualTo(JsonPrimitive(false))
        assertThat(some["EnabledFolders"]).isEqualTo(JsonArray(listOf(JsonPrimitive("a"), JsonPrimitive("b"))))
    }

    @Test fun bitrateLimit_convertsMbpsToBitsAndClampsNegatives() {
        assertThat(PolicyEditor.bitrateLimit(8.5)["RemoteClientBitrateLimit"]).isEqualTo(JsonPrimitive(8_500_000L))
        assertThat(PolicyEditor.bitrateLimit(null)["RemoteClientBitrateLimit"]).isEqualTo(JsonPrimitive(0L))
        assertThat(PolicyEditor.bitrateLimit(-3.0)["RemoteClientBitrateLimit"]).isEqualTo(JsonPrimitive(0L))
    }

    @Test fun validateNewUser() {
        val existing = listOf("admin@example.com", "Kids")
        assertThat(PolicyEditor.validateNewUser("", "", existing)).isNotNull()
        assertThat(PolicyEditor.validateNewUser("kids", "", existing)).contains("already exists")
        assertThat(PolicyEditor.validateNewUser("a/b", "", existing)).isNotNull()
        assertThat(PolicyEditor.validateNewUser("Guest", "abc", existing)).contains("4 characters")
        assertThat(PolicyEditor.validateNewUser("Guest", "", existing)).isNull()
        assertThat(PolicyEditor.validateNewUser("Guest", "secret", existing)).isNull()
    }

    @Test fun flags_haveUniqueKeysAndCoverTheDashboardGroups() {
        assertThat(PolicyEditor.flags.map { it.key }).containsNoDuplicates()
        assertThat(PolicyEditor.flags.map { it.group }.toSet()).containsAtLeast("Role", "Access", "Playback", "Content", "Live TV")
    }
}
