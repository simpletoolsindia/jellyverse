package com.sridhar.harbor.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.dataStore by preferencesDataStore("harbor_settings")

data class ServerConfig(
    val jellyfinUrl: String = "",
    val jellyfinUser: String = "",
    val jellyfinToken: String = "",
    val jellyfinUserId: String = "",
    val qbitUrl: String = "",
    val qbitUser: String = "",
    val qbitPass: String = "",
    val seerrUrl: String = "",
    val seerrApiKey: String = "",
    val seerrCookie: String = "",
    val sonarrUrl: String = "",
    val sonarrKey: String = "",
    val radarrUrl: String = "",
    val radarrKey: String = "",
    val aria2Url: String = "",
    val aria2Secret: String = "",
    val deviceId: String = "",
    val navidromeUrl: String = "",
    val navidromeUser: String = "",
    /** Subsonic token auth: md5(password + salt). The password itself is never stored. */
    val navidromeSalt: String = "",
    val navidromeToken: String = "",
) {
    val navidromeReady get() = navidromeUrl.isNotBlank() && navidromeToken.isNotBlank()
    val aria2Ready get() = aria2Url.isNotBlank()
    val sonarrReady get() = sonarrUrl.isNotBlank() && sonarrKey.isNotBlank()
    val radarrReady get() = radarrUrl.isNotBlank() && radarrKey.isNotBlank()
    val arrReady get() = sonarrReady || radarrReady
    val jellyfinReady get() = jellyfinUrl.isNotBlank() && jellyfinToken.isNotBlank()
    val qbitReady get() = qbitUrl.isNotBlank()
    val seerrReady get() = seerrUrl.isNotBlank() && (seerrApiKey.isNotBlank() || seerrCookie.isNotBlank())
}

class SettingsStore(private val context: Context) {
    private object K {
        val jfUrl = stringPreferencesKey("jf_url")
        val jfUser = stringPreferencesKey("jf_user")
        val jfToken = stringPreferencesKey("jf_token")
        val jfUserId = stringPreferencesKey("jf_user_id")
        val qbUrl = stringPreferencesKey("qb_url")
        val qbUser = stringPreferencesKey("qb_user")
        val qbPass = stringPreferencesKey("qb_pass")
        val jsUrl = stringPreferencesKey("js_url")
        val jsKey = stringPreferencesKey("js_key")
        val jsCookie = stringPreferencesKey("js_cookie")
        val deviceId = stringPreferencesKey("device_id")
        val snUrl = stringPreferencesKey("sn_url")
        val snKey = stringPreferencesKey("sn_key")
        val rdUrl = stringPreferencesKey("rd_url")
        val rdKey = stringPreferencesKey("rd_key")
        val a2Url = stringPreferencesKey("a2_url")
        val a2Secret = stringPreferencesKey("a2_secret")
        val offline = stringPreferencesKey("offline_index")
        val ndUrl = stringPreferencesKey("nd_url")
        val ndUser = stringPreferencesKey("nd_user")
        val ndSalt = stringPreferencesKey("nd_salt")
        val ndToken = stringPreferencesKey("nd_token")
    }

    val config: Flow<ServerConfig> = context.dataStore.data.map { it.toConfig() }

    suspend fun current(): ServerConfig {
        val cfg = config.first()
        if (cfg.deviceId.isNotBlank()) return cfg
        val id = UUID.randomUUID().toString()
        context.dataStore.edit { it[K.deviceId] = id }
        return cfg.copy(deviceId = id)
    }

    suspend fun update(transform: (ServerConfig) -> ServerConfig) {
        context.dataStore.edit { prefs ->
            val next = transform(prefs.toConfig())
            prefs[K.jfUrl] = next.jellyfinUrl.normalizeUrl()
            prefs[K.jfUser] = next.jellyfinUser
            prefs[K.jfToken] = next.jellyfinToken
            prefs[K.jfUserId] = next.jellyfinUserId
            prefs[K.qbUrl] = next.qbitUrl.normalizeUrl()
            prefs[K.qbUser] = next.qbitUser
            prefs[K.qbPass] = next.qbitPass
            prefs[K.jsUrl] = next.seerrUrl.normalizeUrl()
            prefs[K.jsKey] = next.seerrApiKey
            prefs[K.jsCookie] = next.seerrCookie
            prefs[K.snUrl] = next.sonarrUrl.normalizeUrl()
            prefs[K.snKey] = next.sonarrKey
            prefs[K.rdUrl] = next.radarrUrl.normalizeUrl()
            prefs[K.rdKey] = next.radarrKey
            prefs[K.a2Url] = next.aria2Url.normalizeUrl()
            prefs[K.a2Secret] = next.aria2Secret
            prefs[K.ndUrl] = next.navidromeUrl.normalizeUrl()
            prefs[K.ndUser] = next.navidromeUser
            prefs[K.ndSalt] = next.navidromeSalt
            prefs[K.ndToken] = next.navidromeToken
            if (next.deviceId.isNotBlank()) prefs[K.deviceId] = next.deviceId
        }
    }

    val offlineIndex: Flow<String> = context.dataStore.data.map { it[K.offline].orEmpty() }
    suspend fun setOfflineIndex(json: String) = context.dataStore.edit { it[K.offline] = json }

    private fun Preferences.toConfig() = ServerConfig(
        jellyfinUrl = this[K.jfUrl].orEmpty(),
        jellyfinUser = this[K.jfUser].orEmpty(),
        jellyfinToken = this[K.jfToken].orEmpty(),
        jellyfinUserId = this[K.jfUserId].orEmpty(),
        qbitUrl = this[K.qbUrl].orEmpty(),
        qbitUser = this[K.qbUser].orEmpty(),
        qbitPass = this[K.qbPass].orEmpty(),
        seerrUrl = this[K.jsUrl].orEmpty(),
        seerrApiKey = this[K.jsKey].orEmpty(),
        seerrCookie = this[K.jsCookie].orEmpty(),
        sonarrUrl = this[K.snUrl].orEmpty(),
        sonarrKey = this[K.snKey].orEmpty(),
        radarrUrl = this[K.rdUrl].orEmpty(),
        radarrKey = this[K.rdKey].orEmpty(),
        aria2Url = this[K.a2Url].orEmpty(),
        aria2Secret = this[K.a2Secret].orEmpty(),
        deviceId = this[K.deviceId].orEmpty(),
        navidromeUrl = this[K.ndUrl].orEmpty(),
        navidromeUser = this[K.ndUser].orEmpty(),
        navidromeSalt = this[K.ndSalt].orEmpty(),
        navidromeToken = this[K.ndToken].orEmpty(),
    )
}

fun String.normalizeUrl(): String {
    var s = trim().trimEnd('/')
    if (s.isNotEmpty() && !s.startsWith("http://") && !s.startsWith("https://")) s = "http://$s"
    return s
}
