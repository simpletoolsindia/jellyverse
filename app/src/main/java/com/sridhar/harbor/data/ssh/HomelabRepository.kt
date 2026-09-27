package com.sridhar.harbor.data.ssh

import java.io.IOException

data class DiskUsage(val device: String, val mount: String, val total: Long, val used: Long, val free: Long) {
    val fraction get() = if (total > 0) used.toFloat() / total else 0f
}

data class ProcessInfo(val pid: Int, val user: String, val cpu: Float, val mem: Float, val rssKb: Long, val elapsed: String, val command: String)

data class ContainerInfo(
    val name: String, val state: String, val status: String, val image: String,
    val cpu: Float? = null, val memUsage: String? = null, val memPercent: Float? = null,
) {
    val healthy get() = state == "running" && !status.contains("unhealthy")
    val problem get() = status.contains("unhealthy") || state == "restarting" || state == "dead" ||
        (state == "exited" && !status.startsWith("Exited (0)"))
}

data class SystemSnapshot(
    val hostname: String,
    val kernel: String,
    val cores: Int,
    val cpu: Float,
    val memTotalKb: Long,
    val memAvailKb: Long,
    val swapTotalKb: Long,
    val swapFreeKb: Long,
    val tempC: Float?,
    val load: List<Float>,
    val uptimeSec: Long,
    val disks: List<DiskUsage>,
    val netIface: String?,
    val rxBps: Long,
    val txBps: Long,
    val processes: List<ProcessInfo>,
    val takenAt: Long = System.currentTimeMillis(),
) {
    val mem get() = if (memTotalKb > 0) 1f - memAvailKb.toFloat() / memTotalKb else 0f
    val swap get() = if (swapTotalKb > 0) 1f - swapFreeKb.toFloat() / swapTotalKb else 0f

    /** Human-readable warnings; empty means all good. */
    fun warnings(containers: List<ContainerInfo>): List<String> = buildList {
        if (cpu > 0.85f) add("CPU is at ${(cpu * 100).toInt()}%")
        if (mem > 0.9f) add("Memory is ${(mem * 100).toInt()}% used")
        if (swap > 0.6f) add("Swap is ${(swap * 100).toInt()}% used – RAM pressure")
        tempC?.let { if (it > 75) add("CPU temperature ${it.toInt()}°C") }
        disks.filter { it.fraction > 0.9f }.forEach { add("${it.mount} is ${(it.fraction * 100).toInt()}% full") }
        containers.filter { it.problem }.forEach { add("Container ${it.name}: ${it.status}") }
    }
}

/** Reads host metrics over SSH using only standard Linux tools – nothing is installed on the server. */
class HomelabRepository(private val ssh: SshRepository) {

    private val statsScript = """
        export LC_ALL=C
        c1=${'$'}(head -1 /proc/stat); n1=${'$'}(cat /proc/net/dev); sleep 0.5; c2=${'$'}(head -1 /proc/stat); n2=${'$'}(cat /proc/net/dev)
        echo "##CPU"; echo "${'$'}c1"; echo "${'$'}c2"; nproc
        echo "##MEM"; grep -E '^(MemTotal|MemAvailable|SwapTotal|SwapFree):' /proc/meminfo
        echo "##TEMP"; cat /sys/class/thermal/thermal_zone0/temp 2>/dev/null
        echo "##SYS"; cat /proc/loadavg; cut -d' ' -f1 /proc/uptime; hostname; uname -srm
        echo "##DISK"; df -B1 -P -x tmpfs -x devtmpfs -x overlay -x squashfs -x efivarfs 2>/dev/null | tail -n +2
        echo "##NET1"; echo "${'$'}n1" | tail -n +3
        echo "##NET2"; echo "${'$'}n2" | tail -n +3
        echo "##PS"; ps -eo pid,user,pcpu,pmem,rss,etime,comm --sort=-pcpu | tail -n +2 | head -40
    """.trimIndent()

    suspend fun snapshot(h: SshHost): SystemSnapshot = HomelabStatsParser.parse(ssh.exec(h, statsScript))

    suspend fun containers(h: SshHost, withStats: Boolean): List<ContainerInfo> {
        val ps = ssh.exec(h, "docker ps -a --format '{{.Names}}|{{.State}}|{{.Status}}|{{.Image}}'")
        if (ps.contains("permission denied", true)) throw IOException("User can't access Docker (add it to the docker group)")
        val base = ps.lines().filter { it.count { c -> c == '|' } >= 3 }.map {
            val p = it.split('|'); ContainerInfo(p[0], p[1], p[2], p[3])
        }
        if (!withStats) return base
        val stats = ssh.exec(h, "docker stats --no-stream --format '{{.Name}}|{{.CPUPerc}}|{{.MemUsage}}|{{.MemPerc}}'", 60_000)
            .lines().filter { it.count { c -> c == '|' } >= 3 }.associate {
                val p = it.split('|'); p[0] to Triple(p[1].removeSuffix("%").toFloatOrNull(), p[2], p[3].removeSuffix("%").toFloatOrNull())
            }
        return base.map { c -> stats[c.name]?.let { (cpu, mu, mp) -> c.copy(cpu = cpu, memUsage = mu.takeUnless { it.startsWith("0B /") }, memPercent = mp) } ?: c }
    }

    private val safeName = Regex("^[A-Za-z0-9][A-Za-z0-9_.-]*$")

    suspend fun containerAction(h: SshHost, name: String, action: String): String {
        require(action in setOf("start", "stop", "restart")) { "Unsupported action" }
        require(safeName.matches(name)) { "Invalid container name" }
        return ssh.exec(h, "docker $action $name 2>&1", 90_000).trim()
    }

    suspend fun containerLogs(h: SshHost, name: String, lines: Int = 300): String {
        require(safeName.matches(name)) { "Invalid container name" }
        return ssh.exec(h, "docker logs --tail $lines --timestamps $name 2>&1", 30_000)
    }

    suspend fun kill(h: SshHost, pid: Int): String = ssh.exec(h, "kill $pid 2>&1 && echo ok").trim()
}
