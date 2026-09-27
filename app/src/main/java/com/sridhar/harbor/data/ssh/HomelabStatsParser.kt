package com.sridhar.harbor.data.ssh

/** Parses the sectioned (##CPU, ##MEM, ##DISK, …) output of the homelab stats script into a [SystemSnapshot]. Pure – no I/O. */
object HomelabStatsParser {
    fun parse(out: String): SystemSnapshot {
        val sections = mutableMapOf<String, MutableList<String>>()
        var cur = ""
        out.lines().forEach { line ->
            if (line.startsWith("##")) { cur = line.removePrefix("##"); sections[cur] = mutableListOf() }
            else if (line.isNotBlank()) sections[cur]?.add(line)
        }
        val cpuLines = sections["CPU"].orEmpty()
        fun cpuFields(l: String) = l.trim().split(Regex("\\s+")).drop(1).mapNotNull { it.toLongOrNull() }
        val cpu = if (cpuLines.size >= 2) {
            val a = cpuFields(cpuLines[0]); val b = cpuFields(cpuLines[1])
            val idleA = a.getOrElse(3) { 0 } + a.getOrElse(4) { 0 }; val idleB = b.getOrElse(3) { 0 } + b.getOrElse(4) { 0 }
            val total = b.sum() - a.sum(); if (total > 0) (1f - (idleB - idleA).toFloat() / total).coerceIn(0f, 1f) else 0f
        } else 0f
        val cores = cpuLines.getOrNull(2)?.trim()?.toIntOrNull() ?: 1

        val mem = sections["MEM"].orEmpty().associate { l ->
            val p = l.split(Regex("[:\\s]+")); p[0] to (p.getOrNull(1)?.toLongOrNull() ?: 0L)
        }
        val temp = sections["TEMP"]?.firstOrNull()?.trim()?.toFloatOrNull()?.let { if (it > 1000) it / 1000f else it }

        val sys = sections["SYS"].orEmpty()
        val load = sys.getOrNull(0)?.split(" ")?.take(3)?.mapNotNull { it.toFloatOrNull() }.orEmpty()
        val uptime = sys.getOrNull(1)?.toDoubleOrNull()?.toLong() ?: 0L

        val disks = sections["DISK"].orEmpty().mapNotNull { l ->
            val p = l.trim().split(Regex("\\s+"))
            if (p.size < 6) null else DiskUsage(p[0], p.drop(5).joinToString(" "), p[1].toLongOrNull() ?: 0, p[2].toLongOrNull() ?: 0, p[3].toLongOrNull() ?: 0)
        }.filter { it.total > 0 && !it.mount.startsWith("/boot") }

        fun net(lines: List<String>) = lines.mapNotNull { l ->
            val (name, rest) = l.split(':', limit = 2).let { if (it.size < 2) return@mapNotNull null else it[0].trim() to it[1] }
            val f = rest.trim().split(Regex("\\s+")).mapNotNull { it.toLongOrNull() }
            if (f.size < 9) null else name to (f[0] to f[8])
        }.toMap()
        val n1 = net(sections["NET1"].orEmpty()); val n2 = net(sections["NET2"].orEmpty())
        val iface = n2.filterKeys { k -> !k.startsWith("lo") && !k.startsWith("veth") && !k.startsWith("br-") && !k.startsWith("docker") && !k.startsWith("tailscale") }
            .maxByOrNull { it.value.first + it.value.second }?.key
        val rx = iface?.let { ((n2[it]!!.first - (n1[it]?.first ?: 0)) * 2).coerceAtLeast(0) } ?: 0
        val tx = iface?.let { ((n2[it]!!.second - (n1[it]?.second ?: 0)) * 2).coerceAtLeast(0) } ?: 0

        val procs = sections["PS"].orEmpty().mapNotNull { l ->
            val p = l.trim().split(Regex("\\s+"), limit = 7)
            if (p.size < 7) null else ProcessInfo(p[0].toIntOrNull() ?: return@mapNotNull null, p[1], p[2].toFloatOrNull() ?: 0f,
                p[3].toFloatOrNull() ?: 0f, p[4].toLongOrNull() ?: 0, p[5], p[6])
        }.filterNot { it.command == "ps" || it.command == "sleep" }

        return SystemSnapshot(
            hostname = sys.getOrNull(2).orEmpty(), kernel = sys.getOrNull(3).orEmpty(), cores = cores, cpu = cpu,
            memTotalKb = mem["MemTotal"] ?: 0, memAvailKb = mem["MemAvailable"] ?: 0,
            swapTotalKb = mem["SwapTotal"] ?: 0, swapFreeKb = mem["SwapFree"] ?: 0,
            tempC = temp, load = load, uptimeSec = uptime, disks = disks,
            netIface = iface, rxBps = rx, txBps = tx, processes = procs,
        )
    }
}
