package com.sridhar.harbor.update

import android.app.PendingIntent
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
import kotlinx.coroutines.launch
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
    /** Handed to Android's installer; waiting for the user to confirm / the install to finish. */
    data class Installing(val info: UpdateInfo, val file: File) : UpdateState
    /** [info] set when it failed after a release was found – the popup then offers Retry and the GitHub page. */
    data class Failed(val message: String, val info: UpdateInfo? = null) : UpdateState
}

/**
 * Self-update for GitHub (sideload) builds: checks the latest GitHub release, downloads this app's APK,
 * verifies it is signed with the same key as the installed app, then installs it with a PackageInstaller session.
 * Updates always go through Google Play (BuildConfig.SELF_UPDATE = false in every build): the app learns about a
 * new version from Google Play – or, where Play's in-app update API isn't available (Android TV, GitHub installs),
 * the GitHub release – and "Update" opens JellyVerse's Play Store page. It never downloads or installs APKs.
 */
class Updater(private val context: Context, private val http: OkHttpClient) {
    private val prefs = context.getSharedPreferences("harbor_update", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    val enabled get() = true
    /** Play Store build: updates are installed by Google Play. */
    val viaStore get() = !BuildConfig.SELF_UPDATE
    val currentVersion: String = BuildConfig.VERSION_NAME.substringBefore('-')
    private val assetName = if (BuildConfig.FLAVOR == "tv") "JellyVerseTV-tv.apk" else "JellyVerse-phone.apk"

    /** "Check for updates automatically" – on by default. */
    var autoCheck: Boolean
        get() = prefs.getBoolean("auto_check", true)
        set(v) { prefs.edit().putBoolean("auto_check", v).apply(); _autoFlow.value = v }
    private val _autoFlow = MutableStateFlow(prefs.getBoolean("auto_check", true))
    val autoCheckFlow: StateFlow<Boolean> = _autoFlow.asStateFlow()

    /** Background worker path: no UI state changes; returns the newer release, if any. */
    suspend fun checkQuietly(): UpdateInfo? {
        prefs.edit().putLong("last_check", System.currentTimeMillis()).apply()
        return latestNewer()?.also { _state.value = UpdateState.Available(it) }
    }

    /** True the first time a version is announced by notification (so each release notifies once). */
    fun markNotified(version: String): Boolean {
        if (prefs.getString("notified", null) == version) return false
        prefs.edit().putString("notified", version).apply(); return true
    }

    /** Background check: at most once a day, and never nags again about a version the user dismissed. */
    suspend fun checkIfDue() {
        if (!enabled || !autoCheck) return
        val last = prefs.getLong("last_check", 0)
        if (System.currentTimeMillis() - last < 20 * 3_600_000L) return
        check(userInitiated = false)
    }

    suspend fun check(userInitiated: Boolean = true) {
        if (!enabled) return
        _state.value = UpdateState.Checking
        val result = runCatching { latestNewer() }
        prefs.edit().putLong("last_check", System.currentTimeMillis()).apply()
        _state.value = result.fold(
            onSuccess = { info ->
                when {
                    info == null -> UpdateState.UpToDate
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

    /** A newer version than the installed one, or null. Play builds ask Google Play first. */
    private suspend fun latestNewer(): UpdateInfo? {
        if (!viaStore) return fetchLatest()?.takeIf { isNewer(it.version, currentVersion) }
        val play = runCatching { playUpdateCode() }.getOrNull()
        val github = runCatching { fetchLatest(requireAsset = false) }.getOrNull()
        return when {
            play == null -> github?.takeIf { isNewer(it.version, currentVersion) }   // no Play API here (TV): trust the release
            play <= 0 -> null                                                          // Play says up to date
            else -> github?.takeIf { isNewer(it.version, currentVersion) } ?: UpdateInfo("${currentVersion}+", "", "", 0)
        }
    }

    /** Google Play's available version code (0 = none), or null when Play's update API can't be used. */
    private suspend fun playUpdateCode(): Int? = withContext(Dispatchers.Main) {
        val m = com.google.android.play.core.appupdate.AppUpdateManagerFactory.create(context)
        val info = kotlinx.coroutines.suspendCancellableCoroutine<com.google.android.play.core.appupdate.AppUpdateInfo?> { c ->
            m.appUpdateInfo.addOnSuccessListener { c.resumeWith(Result.success(it)) }.addOnFailureListener { c.resumeWith(Result.success(null)) }
        } ?: return@withContext null
        if (info.updateAvailability() == com.google.android.play.core.install.model.UpdateAvailability.UPDATE_AVAILABLE) info.availableVersionCode() else 0
    }

    /** Opens JellyVerse in the Play Store app (or the Play website if the Store app isn't there). */
    fun openStore() {
        val id = context.packageName
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$id")).setPackage("com.android.vending").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(market) }.onFailure {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$id")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
        _state.value = UpdateState.Idle
    }

    private suspend fun fetchLatest(requireAsset: Boolean = true): UpdateInfo? = withContext(Dispatchers.IO) {
        val req = Request.Builder().url("https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/latest")
            .header("Accept", "application/vnd.github+json").build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) error("GitHub HTTP ${r.code}")
            val o = com.sridhar.harbor.data.HarborJson.parseToJsonElement(r.body!!.string()).jsonObject
            val tag = o["tag_name"]?.jsonPrimitive?.content ?: return@use null
            val asset = o["assets"]?.jsonArray?.map { it.jsonObject }?.firstOrNull { it["name"]?.jsonPrimitive?.content == assetName }
            if (asset == null && requireAsset) return@use null
            UpdateInfo(
                version = tag.removePrefix("v"),
                notes = o["body"]?.jsonPrimitive?.content.orEmpty().trim(),
                apkUrl = asset?.get("browser_download_url")?.jsonPrimitive?.content.orEmpty(),
                size = asset?.get("size")?.jsonPrimitive?.content?.toLongOrNull() ?: 0,
            )
        }
    }

    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)
    private var job: kotlinx.coroutines.Job? = null
    /** Set while we sent the user to "Install unknown apps"; the install continues when they come back. */
    @Volatile var awaitingPermission = false
        private set

    /**
     * Downloads the APK in the app's own scope (it keeps going if the popup or screen is recreated), resuming
     * from where it stopped after a network drop, then verifies it.
     */
    fun startDownload() {
        if (viaStore) { openStore(); return }
        val info = when (val s = state.value) { is UpdateState.Available -> s.info; is UpdateState.Failed -> s.info ?: return; else -> return }
        if (job?.isActive == true) return
        job = scope.launch { download(info) }
    }

    private suspend fun download(info: UpdateInfo) {
        _state.value = UpdateState.Downloading(info, 0f)
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val out = dir.resolve("JellyVerse-${info.version}.apk")
        dir.listFiles()?.filter { it.name != out.name && it.name != out.name + ".part" }?.forEach { it.delete() }
        val part = File(out.path + ".part")
        val result = runCatching {
            if (!out.exists()) {
                var attempt = 0
                while (true) {
                    try { fetch(info, part); break }
                    catch (e: java.io.IOException) {
                        if (++attempt >= 5) throw e
                        kotlinx.coroutines.delay(1500L * attempt)   // flaky network: resume from the partial file
                    }
                }
                if (!part.renameTo(out)) error("Couldn't save the update")
            }
            verify(out)
        }
        _state.value = result.fold(
            onSuccess = { UpdateState.ReadyToInstall(info, out) },
            onFailure = { e ->
                if (e is kotlinx.coroutines.CancellationException) throw e
                if (e !is java.io.IOException) { out.delete(); part.delete() }   // bad file: start clean; network: keep for resume
                UpdateState.Failed(e.message ?: "Download failed", info)
            },
        )
    }

    private fun fetch(info: UpdateInfo, part: File) {
        val have = part.takeIf { it.exists() }?.length() ?: 0L
        val req = Request.Builder().url(info.apkUrl).apply { if (have > 0) header("Range", "bytes=$have-") }.build()
        http.newBuilder().readTimeout(60, java.util.concurrent.TimeUnit.SECONDS).build().newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw java.io.IOException("GitHub download failed (HTTP ${r.code})")
            val resumed = r.code == 206 && have > 0
            val body = r.body ?: throw java.io.IOException("Empty download")
            val total = (if (resumed) have + body.contentLength() else body.contentLength()).takeIf { it > 0 } ?: info.size
            java.io.FileOutputStream(part, resumed).use { output ->
                var done = if (resumed) have else 0L
                var lastEmit = 0L
                val buf = ByteArray(128 * 1024)
                body.byteStream().use { input ->
                    while (true) {
                        val n = input.read(buf); if (n < 0) break
                        output.write(buf, 0, n); done += n
                        if (total > 0 && done - lastEmit > 256 * 1024) { lastEmit = done; _state.value = UpdateState.Downloading(info, done.toFloat() / total) }
                    }
                }
                if (total > 0 && done < total) throw java.io.IOException("Connection closed early")
            }
        }
    }

    /**
     * Same package and same signing certificate as the running app – otherwise refuse to install.
     *
     * Android 9–10 (common on TVs) can't read the certificate of a v2-signed APK that isn't installed yet: the archive
     * comes back with no signers. Then we can't compare – and don't need to: Android's installer itself refuses an
     * update signed with a different key (INSTALL_FAILED_UPDATE_INCOMPATIBLE), so we leave the final word to it.
     * A *readable* mismatch is still refused here.
     */
    private fun verify(apk: File) {
        val pm = context.packageManager
        val archive = archiveInfo(pm, apk) ?: error("Downloaded file isn't a valid app")
        if (archive.packageName != context.packageName) error("Update is for a different app")
        val theirs = certs(archive).ifEmpty {
            @Suppress("DEPRECATION") pm.getPackageArchiveInfo(apk.path, PackageManager.GET_SIGNATURES)?.let(::certs).orEmpty()
        }
        if (theirs.isEmpty()) return   // unreadable on this Android version – the system installer verifies it
        val ours = certs(installedInfo(pm))
        if (ours.isNotEmpty() && theirs.intersect(ours).isEmpty()) error("Update isn't signed by the JellyVerse developer – not installing")
    }

    private fun archiveInfo(pm: PackageManager, apk: File): android.content.pm.PackageInfo? =
        if (Build.VERSION.SDK_INT >= 28) pm.getPackageArchiveInfo(apk.path, PackageManager.GET_SIGNING_CERTIFICATES)
        else @Suppress("DEPRECATION") pm.getPackageArchiveInfo(apk.path, PackageManager.GET_SIGNATURES)

    private fun installedInfo(pm: PackageManager): android.content.pm.PackageInfo =
        if (Build.VERSION.SDK_INT >= 28) pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        else @Suppress("DEPRECATION") pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)

    private fun certs(p: android.content.pm.PackageInfo): Set<String> {
        val sigs = (if (Build.VERSION.SDK_INT >= 28) p.signingInfo?.let { si -> if (si.hasMultipleSigners()) si.apkContentsSigners else si.signingCertificateHistory }?.toList() else null)
            ?: @Suppress("DEPRECATION") p.signatures?.toList().orEmpty()
        return sigs.filterNotNull().map { MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).joinToString("") { b -> "%02x".format(b) } }.toSet()
    }

    /**
     * Installs through Android's PackageInstaller session (works on Xiaomi / Oppo / Vivo skins that block the older
     * "open APK" route, and reports *why* an install failed). First, if needed, the "Install unknown apps" screen –
     * the install then continues by itself when the user comes back ([resumeAfterPermission]).
     */
    fun install(ctx: Context) {
        val s = state.value as? UpdateState.ReadyToInstall ?: return
        if (Build.VERSION.SDK_INT >= 26 && !ctx.packageManager.canRequestPackageInstalls()) {
            awaitingPermission = true
            ctx.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        awaitingPermission = false
        _state.value = UpdateState.Installing(s.info, s.file)
        scope.launch {
            runCatching {
                val pi = context.packageManager.packageInstaller
                val params = android.content.pm.PackageInstaller.SessionParams(android.content.pm.PackageInstaller.SessionParams.MODE_FULL_INSTALL)
                    .apply {
                        setAppPackageName(context.packageName)
                        setSize(s.file.length())
                        if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(android.content.pm.PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                    }
                val id = pi.createSession(params)
                pi.openSession(id).use { session ->
                    session.openWrite("base.apk", 0, s.file.length()).use { o -> s.file.inputStream().use { it.copyTo(o, 256 * 1024) }; session.fsync(o) }
                    val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
                    val status = PendingIntent.getBroadcast(context, id, Intent(context, InstallResultReceiver::class.java).setPackage(context.packageName), flags)
                    session.commit(status.intentSender)
                }
            }.onFailure { e -> _state.value = UpdateState.Failed(e.message ?: "Couldn't start the installer", s.info) }
        }
    }

    /** Back from "Install unknown apps": carry on if it was allowed. */
    fun resumeAfterPermission(ctx: Context) {
        if (!awaitingPermission) return
        if (Build.VERSION.SDK_INT < 26 || ctx.packageManager.canRequestPackageInstalls()) install(ctx) else awaitingPermission = false
    }

    /** Called by [InstallResultReceiver] with the installer's verdict. */
    internal fun onInstallResult(ctx: Context, status: Int, message: String?, confirm: Intent?) {
        val cur = state.value
        val info = (cur as? UpdateState.Installing)?.info ?: (cur as? UpdateState.ReadyToInstall)?.info
        when (status) {
            android.content.pm.PackageInstaller.STATUS_PENDING_USER_ACTION ->
                confirm?.let { runCatching { ctx.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
            android.content.pm.PackageInstaller.STATUS_SUCCESS -> Unit   // the app is replaced and restarted
            android.content.pm.PackageInstaller.STATUS_FAILURE_ABORTED ->   // user tapped Cancel
                _state.value = (cur as? UpdateState.Installing)?.let { UpdateState.ReadyToInstall(it.info, it.file) } ?: cur
            else -> _state.value = UpdateState.Failed(explain(status, message), info)
        }
    }

    private fun explain(status: Int, message: String?): String = when (status) {
        android.content.pm.PackageInstaller.STATUS_FAILURE_CONFLICT,
        android.content.pm.PackageInstaller.STATUS_FAILURE_INCOMPATIBLE ->
            "This copy of JellyVerse was signed differently (e.g. installed from another source). Uninstall it once, then install from GitHub."
        android.content.pm.PackageInstaller.STATUS_FAILURE_STORAGE -> "Not enough free storage to install the update."
        android.content.pm.PackageInstaller.STATUS_FAILURE_BLOCKED -> "Your phone blocked the install. Allow JellyVerse under Settings → Install unknown apps, or install from GitHub."
        else -> message?.takeIf { it.isNotBlank() } ?: "The installer couldn't update the app."
    }

    /** GitHub release page – the manual fallback. */
    fun releasePage(version: String) = "https://github.com/${BuildConfig.UPDATE_REPO}/releases/tag/v$version"

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

/** Receives the PackageInstaller session status (confirmation needed, success, or why it failed). */
class InstallResultReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val status = intent.getIntExtra(android.content.pm.PackageInstaller.EXTRA_STATUS, android.content.pm.PackageInstaller.STATUS_FAILURE)
        val msg = intent.getStringExtra(android.content.pm.PackageInstaller.EXTRA_STATUS_MESSAGE)
        @Suppress("DEPRECATION")
        val confirm = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java) else intent.getParcelableExtra(Intent.EXTRA_INTENT)
        (ctx.applicationContext as? com.sridhar.harbor.HarborApp)?.container?.updater?.onInstallResult(ctx, status, msg, confirm)
    }
}
