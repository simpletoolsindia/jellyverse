package com.sridhar.harbor.data.jellyfin.admin

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive

/** One switch in the user editor, backed by a boolean key of Jellyfin's UserPolicy. */
data class PolicyFlag(val key: String, val label: String, val group: String, val hint: String? = null)

/**
 * The permission switches the Jellyfin web dashboard exposes, grouped the same way.
 * Pure data + pure transforms, so it's fully unit-testable.
 */
object PolicyEditor {
    val flags = listOf(
        PolicyFlag("IsAdministrator", "Administrator", "Role", "Full control of the server"),
        PolicyFlag("IsHidden", "Hide from login screens", "Role"),
        PolicyFlag("IsDisabled", "Disable this user", "Role", "Blocks sign-in without deleting"),
        PolicyFlag("EnableRemoteAccess", "Allow remote connections", "Access"),
        PolicyFlag("EnableSharedDeviceControl", "Control shared devices", "Access"),
        PolicyFlag("EnableRemoteControlOfOtherUsers", "Remote-control other users", "Access"),
        PolicyFlag("EnableMediaPlayback", "Allow media playback", "Playback"),
        PolicyFlag("EnableAudioPlaybackTranscoding", "Audio transcoding", "Playback"),
        PolicyFlag("EnableVideoPlaybackTranscoding", "Video transcoding", "Playback"),
        PolicyFlag("EnablePlaybackRemuxing", "Remuxing (container change)", "Playback"),
        PolicyFlag("ForceRemoteSourceTranscoding", "Force transcode of remote sources", "Playback"),
        PolicyFlag("EnableContentDownloading", "Allow downloads", "Content"),
        PolicyFlag("EnableContentDeletion", "Allow media deletion", "Content"),
        PolicyFlag("EnableSubtitleManagement", "Manage subtitles", "Content"),
        PolicyFlag("EnableCollectionManagement", "Manage collections", "Content"),
        PolicyFlag("EnableLyricManagement", "Manage lyrics", "Content"),
        PolicyFlag("EnablePublicSharing", "Public sharing", "Content"),
        PolicyFlag("EnableLiveTvAccess", "Live TV access", "Live TV"),
        PolicyFlag("EnableLiveTvManagement", "Live TV management", "Live TV"),
    )

    fun isOn(policy: JsonObject, key: String): Boolean = policy[key]?.jsonPrimitive?.booleanOrNull ?: false

    /**
     * Applies edits on top of the server's full policy object, so fields this app doesn't know about
     * (AuthenticationProviderId, blocked tags, access schedules…) are sent back untouched.
     */
    fun merge(policy: JsonObject, changes: Map<String, JsonElement>): JsonObject = JsonObject(policy + changes)

    fun libraryAccess(allFolders: Boolean, folderIds: Collection<String>): Map<String, JsonElement> = mapOf(
        "EnableAllFolders" to JsonPrimitive(allFolders),
        "EnabledFolders" to JsonArray(if (allFolders) emptyList() else folderIds.sorted().map { JsonPrimitive(it) }),
    )

    /** Mbps from the UI → bits/s for RemoteClientBitrateLimit (0 = unlimited). */
    fun bitrateLimit(mbps: Double?): Map<String, JsonElement> =
        mapOf("RemoteClientBitrateLimit" to JsonPrimitive(((mbps ?: 0.0).coerceAtLeast(0.0) * 1_000_000).toLong()))

    /** Validates a new account before hitting the server; returns an error or null. */
    fun validateNewUser(name: String, password: String, existing: Collection<String>): String? = when {
        name.isBlank() -> "Enter a user name"
        name.length > 64 -> "Name is too long"
        name.any { it in "<>\"/\\" } -> "Name can't contain < > \" / \\"
        existing.any { it.equals(name.trim(), ignoreCase = true) } -> "\"${name.trim()}\" already exists"
        password.isNotEmpty() && password.length < 4 -> "Use at least 4 characters, or leave empty for no password"
        else -> null
    }
}
