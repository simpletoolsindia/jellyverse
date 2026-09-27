package com.sridhar.harbor.data.ssh

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HomelabStatsParserTest {

    private val output = """
        ##CPU
        cpu  1000 0 1000 8000 0 0 0 0 0 0
        cpu  1100 0 1100 8200 0 0 0 0 0 0
        4
        ##MEM
        MemTotal:        8000000 kB
        MemAvailable:    2000000 kB
        SwapTotal:       1000000 kB
        SwapFree:         500000 kB
        ##TEMP
        54321
        ##SYS
        0.52 0.40 0.31 2/345 6789
        93784.12
        deploy
        Linux 6.6.51+rpt-rpi-2712 aarch64
        ##DISK
        /dev/nvme0n1p2 1000000000 400000000 600000000 40% /
        /dev/nvme0n1p1 500000 100000 400000 20% /boot/firmware
        /dev/sda1 4000000000 3000000000 1000000000 75% /mnt/media disk
        ##NET1
            lo: 100 1 0 0 0 0 0 0 100 1 0 0 0 0 0 0
          eth0: 1000 10 0 0 0 0 0 0 2000 20 0 0 0 0 0 0
        ##NET2
            lo: 900 1 0 0 0 0 0 0 900 1 0 0 0 0 0 0
          eth0: 6000 10 0 0 0 0 0 0 4000 20 0 0 0 0 0 0
        ##PS
          812 jellyfin  35.5  8.1 650000 1-02:03:04 jellyfin
          901 root       2.0  0.5  40000    05:06 qbittorrent-nox
          999 sridhar    0.0  0.0   1000    00:00 ps
    """.trimIndent()

    private val s = HomelabStatsParser.parse(output)

    @Test fun cpuUsageFromTwoProcStatSamples() {
        // Δtotal = 400, Δidle = 200 → 50 %
        assertThat(s.cpu).isWithin(0.001f).of(0.5f)
        assertThat(s.cores).isEqualTo(4)
    }

    @Test fun memoryAndSwap() {
        assertThat(s.mem).isWithin(0.001f).of(0.75f)
        assertThat(s.swap).isWithin(0.001f).of(0.5f)
    }

    @Test fun temperatureMilliCelsiusIsConverted() {
        assertThat(s.tempC).isWithin(0.01f).of(54.321f)
    }

    @Test fun systemInfo() {
        assertThat(s.hostname).isEqualTo("deploy")
        assertThat(s.kernel).startsWith("Linux 6.6")
        assertThat(s.load).containsExactly(0.52f, 0.40f, 0.31f).inOrder()
        assertThat(s.uptimeSec).isEqualTo(93784)
    }

    @Test fun disksSkipBootAndKeepMountsWithSpaces() {
        assertThat(s.disks.map { it.mount }).containsExactly("/", "/mnt/media disk")
        assertThat(s.disks[1].fraction).isWithin(0.001f).of(0.75f)
    }

    @Test fun networkRateUsesBusiestPhysicalInterface() {
        assertThat(s.netIface).isEqualTo("eth0")
        assertThat(s.rxBps).isEqualTo(10_000) // (6000-1000) bytes per 0.5 s
        assertThat(s.txBps).isEqualTo(4_000)
    }

    @Test fun processesExcludeThePsProbeItself() {
        assertThat(s.processes.map { it.command }).containsExactly("jellyfin", "qbittorrent-nox").inOrder()
        assertThat(s.processes[0].cpu).isWithin(0.01f).of(35.5f)
    }

    @Test fun emptyOutputGivesSafeDefaults() {
        val e = HomelabStatsParser.parse("")
        assertThat(e.cpu).isEqualTo(0f)
        assertThat(e.cores).isEqualTo(1)
        assertThat(e.disks).isEmpty()
        assertThat(e.netIface).isNull()
    }
}
