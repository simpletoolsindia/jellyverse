package com.sridhar.harbor.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.sridhar.harbor.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest

data class UpdateInfo(val version: String, val notes: String, val apkUrl: String, val size: Long)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(val info: UpdateInfo, val progress: Float) : UpdateState
    data class ReadyToInstall(val info: UpdateInfo, val file: File) : UpdateState
    data class Failed(val message: String) : UpdateState
}

/**
 * Self-update for GitHub (sideload) builds: checks the latest GitHub release, downloads this app's APK,
 * verifies it is signed with the same key as the installed app, then hands it to Android's installer.
 * Play Store builds never compile this in (BuildConfig.SELF_UPDATE = false).
 */
class Updater(private val context: Context, private val http: OkHttpClient) {
    private val prefs = context.getSharedPreferences("harbor_update", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    val enabled get() = BuildConfig.SELF_UPDATE
    val currentVersion: String = BuildConfig.VERSION_NAME.substringBefore('-')
    private val assetName = if (BuildConfig.FLAVOR == "tv") "JellyVerseTV-tv.apk" else "JellyVerse-phone.apk"

    /** Background check: at most once a day, and never nags again about a version the user dismissed. */
    suspend fun checkIfDue() {
        if (!enabled) return
        val last = prefs.getLong("last_check", 0)
        if (System.currentTimeMillis() - last < 20 * 3_600_000L) return
        check(userInitiated = false)
    }

    suspend fun check(userInitiated: Boolean = true) {
        if (!enabled) return
        _state.value = UpdateState.Checking
        val result = runCatching { fetchLatest() }
        prefs.edit().putLong("last_check", System.currentTimeMillis()).apply()
        _state.value = result.fold(
            onSuccess = { info ->
                when {
                    info == null || !isNewer(info.version, currentVersion) -> UpdateState.UpToDate
                    !userInitiated && prefs.getString("dismissed", null) == info.version -> UpdateState.Idle
                    else -> UpdateState.Available(info)
                }
            },
            onFailure = { if (userInitiated) UpdateState.Failed(it.message ?: "network error") else UpdateState.Idle },
        )
    }

    fun dismiss() {
        (state.value as? UpdateState.Available)?.let { prefs.edit().putString("dismissed", it.info.version).apply() }
        _state.value = UpdateState.Idle
    }

    private suspend fun fetchLatest(): UpdateInfo? = withContext(Dispatchers.IO) {
        val req = Request.Builder().url("https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/latest")
            .header("Accept", "application/vnd.github+json").build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) error("GitHub HTTP ${r.code}")
            val o = com.sridhar.harbor.data.HarborJson.parseToJsonElement(r.body!!.string()).jsonObject
            val tag = o["tag_name"]?.jsonPrimitive?.content ?: return@use null
            val asset = o["assets"]?.jsonArray?.map { it.jsonObject }?.firstOrNull { it["name"]?.jsonPrimitive?.content == assetName } ?: return@use null
            UpdateInfo(
                version = tag.removePrefix("v"),
                notes = o["body"]?.jsonPrimitive?.content.orEmpty().trim(),
                apkUrl = asset["browser_download_url"]!!.jsonPrimitive.content,
                size = asset["size"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0,
            )
        }
    }

    suspend fun download() {
        val info = when (val s = state.value) { is UpdateState.Available -> s.info; is UpdateState.Failed -> return; else -> return }
        _state.value = UpdateState.Downloading(info, 0f)
        val out = File(context.cacheDir, "updates").apply { mkdirs(); listFiles()?.forEach { it.delete() } }.resolve("JellyVerse-${info.version}.apk")
        val ok = runCatching {
            withContext(Dispatchers.IO) {
                http.newCall(Request.Builder().url(info.apkUrl).build()).execute().use { r ->
                    if (!r.isSuccessful) error("HTTP ${r.code}")
                    val body = r.body!!; val total = body.contentLength().takeIf { it > 0 } ?: info.size
                    body.byteStream().use { input ->
                        out.outputStream().use { output ->
                            val buf = ByteArray(64 * 1024); var done = 0L; var lastEmit = 0L
                            while (true) {
                                val n = input.read(buf); if (n < 0) break
                                output.write(buf, 0, n); done += n
                                if (total > 0 && done - lastEmit > 256 * 1024) { lastEmit = done; _state.value = UpdateState.Downloading(info, done.toFloat() / total) }
                            }
                        }
                    }
                }
            }
            verify(out)
        }
        _state.value = ok.fold(onSuccess = { UpdateState.ReadyToInstall(info, out) }, onFailure = { out.delete(); UpdateState.Failed(it.message ?: "download failed") })
    }

    /** Same package and same signing certificate as the running app – otherwise refuse to install. */
    private fun verify(apk: File) {
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES
        val archive = pm.getPackageArchiveInfo(apk.path, flags) ?: error("Downloaded file isn't a valid app")
        if (archive.packageName != context.packageName) error("Update is for a different app")
        val installed = pm.getPackageInfo(context.packageName, flags)
        if (certs(archive) != certs(installed)) error("Update isn't signed by the JellyVerse developer – not installing")
    }

    private fun certs(p: android.content.pm.PackageInfo): Set<String> {
        val sigs = if (Build.VERSION.SDK_INT >= 28) p.signingInfo?.apkContentsSigners?.toList().orEmpty() else @Suppress("DEPRECATION") p.signatures?.toList().orEmpty()
        return sigs.map { MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).joinToString("") { b -> "%02x".format(b) } }.toSet()
    }

    /** Opens Android's installer (or first the "allow installs from this app" screen). */
    fun install(ctx: Context) {
        val s = state.value as? UpdateState.ReadyToInstall ?: return
        if (Build.VERSION.SDK_INT >= 26 && !ctx.packageManager.canRequestPackageInstalls()) {
            ctx.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.updates", s.file)
        ctx.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    companion object {
        /** "2.10.0" > "2.9.3"; ignores suffixes like "-tv". */
        fun isNewer(remote: String, local: String): Boolean {
            fun parts(v: String) = v.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
            val a = parts(remote); val b = parts(local)
            for (i in 0 until maxOf(a.size, b.size)) {
                val x = a.getOrElse(i) { 0 }; val y = b.getOrElse(i) { 0 }
                if (x != y) return x > y
            }
            return false
        }
    }
}
