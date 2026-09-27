package com.sridhar.harbor.data.ssh

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.ChannelShell
import com.jcraft.jsch.HostKey
import com.jcraft.jsch.HostKeyRepository
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.jcraft.jsch.UserInfo
import com.sridhar.harbor.data.HarborJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID

@Serializable
data class SshHost(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val host: String,
    val port: Int = 22,
    val user: String,
    val password: String = "",
    val color: Int = 0,
)

class HostKeyChanged(host: String, val expected: String, val actual: String) :
    IOException("Host key for $host CHANGED!\nExpected $expected\nGot $actual\nThis could be a man-in-the-middle attack. Forget the host key in Terminal → host menu only if you rebuilt the server.")

/**
 * SSH hosts + host-key pinning (trust on first use), with credentials in EncryptedSharedPreferences.
 * Exec channels share one cached session per host; interactive shells get their own session.
 */
class SshRepository(context: Context) {
    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context, "harbor_ssh",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )
    private val listSer = ListSerializer(SshHost.serializer())
    private val _hosts = MutableStateFlow(load())
    val hosts: StateFlow<List<SshHost>> = _hosts

    private fun load(): List<SshHost> = prefs.getString("hosts", null)?.let {
        runCatching { HarborJson.decodeFromString(listSer, it) }.getOrNull()
    }.orEmpty()

    fun save(host: SshHost) {
        val next = _hosts.value.filterNot { it.id == host.id } + host
        prefs.edit().putString("hosts", HarborJson.encodeToString(listSer, next)).apply()
        _hosts.value = next
    }

    fun delete(host: SshHost) {
        val next = _hosts.value.filterNot { it.id == host.id }
        prefs.edit().putString("hosts", HarborJson.encodeToString(listSer, next)).remove(pinKey(host.host, host.port)).apply()
        _hosts.value = next
    }

    /** The first saved host is "the homelab" used by the Lab dashboard. */
    val primary: SshHost? get() = _hosts.value.firstOrNull()

    fun setPrimary(host: SshHost) {
        val next = listOf(host) + _hosts.value.filterNot { it.id == host.id }
        prefs.edit().putString("hosts", HarborJson.encodeToString(listSer, next)).apply()
        _hosts.value = next
    }

    fun forgetHostKey(host: SshHost) = prefs.edit().remove(pinKey(host.host, host.port)).apply()

    // ---------------- host key pinning ----------------

    private fun pinKey(host: String, port: Int) = "pin:$host:$port"

    private fun fingerprint(key: ByteArray): String =
        "SHA256:" + Base64.getEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(key))

    private inner class PinningRepo(private val host: String, private val port: Int) : HostKeyRepository {
        var mismatch: Pair<String, String>? = null
        override fun check(h: String?, key: ByteArray): Int {
            val fp = fingerprint(key)
            val pinned = prefs.getString(pinKey(host, port), null)
            return when {
                pinned == null -> { prefs.edit().putString(pinKey(host, port), fp).apply(); HostKeyRepository.OK }
                pinned == fp -> HostKeyRepository.OK
                else -> { mismatch = pinned to fp; HostKeyRepository.CHANGED }
            }
        }
        override fun add(hostkey: HostKey?, ui: UserInfo?) {}
        override fun remove(host: String?, type: String?) {}
        override fun remove(host: String?, type: String?, key: ByteArray?) {}
        override fun getKnownHostsRepositoryID() = "harbor"
        override fun getHostKey(): Array<HostKey> = emptyArray()
        override fun getHostKey(host: String?, type: String?): Array<HostKey> = emptyArray()
    }

    fun pinnedFingerprint(h: SshHost): String? = prefs.getString(pinKey(h.host, h.port), null)

    fun connect(h: SshHost, timeoutMs: Int = 10_000): Session {
        val repo = PinningRepo(h.host, h.port)
        val jsch = JSch().apply { hostKeyRepository = repo }
        val s = jsch.getSession(h.user, h.host, h.port)
        s.setPassword(h.password)
        s.setConfig("StrictHostKeyChecking", "yes")
        s.setConfig("PreferredAuthentications", "password,keyboard-interactive")
        s.userInfo = object : UserInfo {   // answers keyboard-interactive password prompts
            override fun getPassphrase() = null
            override fun getPassword() = h.password
            override fun promptPassword(message: String?) = true
            override fun promptPassphrase(message: String?) = false
            override fun promptYesNo(message: String?) = false
            override fun showMessage(message: String?) {}
        }
        s.serverAliveInterval = 15_000
        try {
            s.connect(timeoutMs)
        } catch (e: Exception) {
            repo.mismatch?.let { (exp, got) -> throw HostKeyChanged(h.host, exp, got) }
            val msg = e.message.orEmpty()
            throw IOException(when {
                msg.contains("Auth fail", true) -> "Authentication failed – wrong username or password"
                msg.contains("UnknownHost", true) -> "Unknown host ${h.host}"
                msg.contains("timeout", true) || msg.contains("connect", true) -> "Can't reach ${h.host}:${h.port}"
                else -> msg.ifBlank { e.javaClass.simpleName }
            }, e)
        }
        return s
    }

    // ---------------- exec (dashboard) ----------------

    private val execLock = Mutex()
    private var execSession: Session? = null
    private var execHostId: String? = null

    suspend fun exec(h: SshHost, command: String, timeoutMs: Long = 20_000): String = withContext(Dispatchers.IO) {
        val session = execLock.withLock {
            execSession?.takeIf { it.isConnected && execHostId == h.id } ?: run {
                execSession?.disconnect()
                connect(h).also { execSession = it; execHostId = h.id }
            }
        }
        val ch = session.openChannel("exec") as ChannelExec
        ch.setCommand(command)
        val out = ByteArrayOutputStream()
        ch.outputStream = null
        val input = ch.inputStream
        ch.setErrStream(out, true)
        ch.connect(5_000)
        val deadline = System.currentTimeMillis() + timeoutMs
        val buf = ByteArray(16 * 1024)
        while (true) {
            while (input.available() > 0) { val n = input.read(buf); if (n < 0) break; out.write(buf, 0, n) }
            if (ch.isClosed && input.available() == 0) break
            if (System.currentTimeMillis() > deadline) { ch.disconnect(); throw IOException("Command timed out") }
            Thread.sleep(30)
        }
        ch.disconnect()
        out.toString(Charsets.UTF_8.name())
    }

    fun openShell(session: Session, cols: Int, rows: Int): ChannelShell =
        (session.openChannel("shell") as ChannelShell).apply {
            setPtyType("xterm-256color", cols, rows, 0, 0)
            setEnv("COLORTERM", "truecolor")
            setEnv("LANG", "C.UTF-8")
        }
}
