package com.sridhar.harbor.ui.lab

import com.sridhar.harbor.L10n
import com.sridhar.harbor.R
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sridhar.harbor.data.AppContainer
import com.sridhar.harbor.data.ssh.ContainerInfo
import com.sridhar.harbor.data.ssh.SystemSnapshot
import com.sridhar.harbor.ui.components.friendly
import kotlinx.coroutines.launch

class LabViewModel(private val c: AppContainer) : ViewModel() {
    var snapshot by mutableStateOf<SystemSnapshot?>(null); private set
    var containers by mutableStateOf<List<ContainerInfo>>(emptyList()); private set
    var error by mutableStateOf<String?>(null); private set
    var message by mutableStateOf<String?>(null)
    var busy by mutableStateOf<Set<String>>(emptySet()); private set
    val cpuHistory = ArrayDeque<Float>()
    val memHistory = ArrayDeque<Float>()
    val rxHistory = ArrayDeque<Long>()
    val txHistory = ArrayDeque<Long>()
    private var tick = 0

    val host get() = c.ssh.primary

    suspend fun poll() {
        val h = host ?: return
        runCatching { c.homelab.snapshot(h) }.onSuccess { s ->
            snapshot = s; error = null
            push(cpuHistory, s.cpu); push(memHistory, s.mem); push(rxHistory, s.rxBps); push(txHistory, s.txBps)
        }.onFailure { error = it.friendly() }
        // Container list every ~15 s, CPU stats (slow: ~2 s for 50 containers) every ~30 s.
        if (tick % 5 == 0) runCatching { c.homelab.containers(h, withStats = tick % 10 == 0 || containers.none { it.cpu != null }) }
            .onSuccess { fresh ->
                val old = containers.associateBy { it.name }
                containers = fresh.map { n -> if (n.cpu == null) old[n.name]?.let { o -> n.copy(cpu = o.cpu, memUsage = o.memUsage, memPercent = o.memPercent) } ?: n else n }
            }
        tick++
    }

    private fun <T> push(q: ArrayDeque<T>, v: T) { q.addLast(v); while (q.size > 40) q.removeFirst() }

    fun containerAction(name: String, action: String) = viewModelScope.launch(com.sridhar.harbor.CrashGuard) {
        val h = host ?: return@launch
        busy = busy + name
        runCatching { c.homelab.containerAction(h, name, action) }
            .onSuccess { message = L10n.s(when (action) { "start" -> R.string.container_started; "stop" -> R.string.container_stopped; else -> R.string.container_restarted }, name); tick = 0; runCatching { containers = c.homelab.containers(h, false).mergeStats() } }
            .onFailure { message = it.friendly() }
        busy = busy - name
    }

    private fun List<ContainerInfo>.mergeStats(): List<ContainerInfo> {
        val old = containers.associateBy { it.name }
        return map { n -> old[n.name]?.let { o -> n.copy(cpu = o.cpu, memUsage = o.memUsage, memPercent = o.memPercent) } ?: n }
    }

    suspend fun logs(name: String): String = host?.let { runCatching { c.homelab.containerLogs(it, name) }.getOrElse { e -> e.friendly() } }.orEmpty()

    fun kill(pid: Int) = viewModelScope.launch(com.sridhar.harbor.CrashGuard) {
        val h = host ?: return@launch
        message = runCatching { c.homelab.kill(h, pid) }.fold({ if (it == "ok") "Sent SIGTERM to $pid" else it }, { it.friendly() })
    }
}
