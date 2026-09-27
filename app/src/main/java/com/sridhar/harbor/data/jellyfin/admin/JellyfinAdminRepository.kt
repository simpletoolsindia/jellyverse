package com.sridhar.harbor.data.jellyfin.admin

import com.sridhar.harbor.data.jellyfin.JellyfinRepository
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import okhttp3.ResponseBody
import retrofit2.Response
import java.io.IOException

class AdminException(message: String) : IOException(message)

/** Everything the Jellyfin web dashboard does, for administrators. */
class JellyfinAdminRepository(private val jellyfin: JellyfinRepository) {

    private suspend fun api() = jellyfin.adminApi()

    private fun Response<ResponseBody>.ok(action: String) {
        try {
            if (!isSuccessful) throw AdminException(when (code()) {
                401, 403 -> "$action: this account isn't a Jellyfin administrator"
                404 -> "$action: not found on this server"
                else -> "$action failed (HTTP ${code()})"
            })
        } finally { body()?.close(); errorBody()?.close() }
    }

    // ---- Server ----
    suspend fun systemInfo() = api().systemInfo()
    suspend fun counts() = runCatching { api().counts() }.getOrDefault(ItemCounts())
    suspend fun restart() = api().restart().ok("Restart")
    suspend fun shutdown() = api().shutdown().ok("Shutdown")
    suspend fun logFiles() = api().logs().sortedByDescending { it.modified }
    /** Last [maxChars] of a server log – they can be tens of MB. */
    suspend fun logTail(name: String, maxChars: Int = 60_000): String = api().log(name).use { body ->
        val text = body.charStream().readText()
        if (text.length > maxChars) "… (showing the last ${maxChars / 1000}k characters)\n" + text.takeLast(maxChars) else text
    }

    // ---- Users ----
    suspend fun users() = api().users().sortedWith(compareByDescending<AdminUser> { it.policy.isAdministrator }.thenBy { it.name.lowercase() })

    suspend fun createUser(name: String, password: String): AdminUser = api().createUser(NewUser(name.trim(), password))

    suspend fun deleteUser(id: String) = api().deleteUser(id).ok("Delete user")

    suspend fun rename(id: String, newName: String) {
        val raw = api().userRaw(id)
        api().updateUser(id, JsonObject(raw + ("Name" to JsonPrimitive(newName.trim())))).ok("Rename")
    }

    suspend fun policy(id: String): JsonObject = api().userRaw(id)["Policy"]?.jsonObject ?: JsonObject(emptyMap())

    suspend fun updatePolicy(id: String, changes: Map<String, JsonElement>) {
        if (changes.isEmpty()) return
        api().updatePolicy(id, PolicyEditor.merge(policy(id), changes)).ok("Save permissions")
    }

    suspend fun setPassword(id: String, newPassword: String) = api().updatePassword(id, PasswordChange(newPw = newPassword)).ok("Change password")

    suspend fun resetPassword(id: String) = api().updatePassword(id, PasswordChange(resetPassword = true)).ok("Reset password")

    // ---- Sessions ----
    suspend fun sessions() = api().sessions().sortedWith(compareByDescending<AdminSession> { it.nowPlaying != null }.thenByDescending { it.lastActivity })
    suspend fun command(sessionId: String, cmd: PlayCommand, seekTicks: Long? = null) = api().playCommand(sessionId, cmd.name, seekTicks).ok(cmd.name)
    suspend fun message(sessionId: String, header: String, text: String) = api().message(sessionId, SessionMessage(header, text)).ok("Send message")

    // ---- Libraries ----
    suspend fun libraries() = api().libraries()
    suspend fun scanAll() = api().scanAll().ok("Scan libraries")
    suspend fun scan(lib: LibraryFolder) = api().scanLibrary(lib.itemId).ok("Scan ${lib.name}")
    suspend fun addLibrary(name: String, type: LibraryType, path: String) = api().addLibrary(name.trim(), type.api, listOf(path.trim())).ok("Add library")
    suspend fun removeLibrary(name: String) = api().removeLibrary(name).ok("Remove library")
    suspend fun deleteItem(id: String) = api().deleteItem(id).ok("Delete")

    // ---- Tasks ----
    suspend fun tasks() = api().tasks().sortedWith(compareByDescending<ScheduledTask> { it.running }.thenBy { it.category }.thenBy { it.name })
    suspend fun startTask(id: String) = api().startTask(id).ok("Start task")
    suspend fun stopTask(id: String) = api().stopTask(id).ok("Stop task")

    // ---- Activity / devices / plugins / keys ----
    suspend fun activity(start: Int = 0, limit: Int = 50) = api().activity(start, limit)
    suspend fun devices() = api().devices().items.sortedByDescending { it.lastActivity }
    suspend fun deleteDevice(id: String) = api().deleteDevice(id).ok("Remove device")
    suspend fun plugins() = api().plugins().sortedBy { it.name.lowercase() }
    suspend fun setPluginEnabled(p: PluginInfo, enabled: Boolean) =
        (if (enabled) api().enablePlugin(p.id, p.version) else api().disablePlugin(p.id, p.version)).ok(if (enabled) "Enable plugin" else "Disable plugin")
    /** Installs a plugin from the official catalog (takes effect after a Jellyfin restart). */
    suspend fun installPlugin(name: String) = api().installPackage(name).ok("Install $name")
    suspend fun apiKeys() = api().apiKeys().items
    suspend fun createApiKey(app: String) = api().createApiKey(app.trim()).ok("Create API key")
    suspend fun revokeApiKey(token: String) = api().revokeApiKey(token).ok("Revoke API key")

    companion object {
        const val TICKS_PER_MS = 10_000L
    }
}
