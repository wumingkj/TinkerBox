package com.wuming.tinkerbox.ui.monitor

import android.app.Application
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wuming.tinkerbox.core.mode.PermissionMode
import com.wuming.tinkerbox.core.monitor.CpuClusterState
import com.wuming.tinkerbox.core.monitor.CpuMonitor
import com.wuming.tinkerbox.core.monitor.DeviceInfo
import com.wuming.tinkerbox.core.monitor.MemoryMonitor
import com.wuming.tinkerbox.core.monitor.MemoryState
import com.wuming.tinkerbox.core.monitor.PowerMonitor
import com.wuming.tinkerbox.core.monitor.PowerState
import com.wuming.tinkerbox.core.monitor.ProcessMonitor
import com.wuming.tinkerbox.core.monitor.ProcStat
import com.wuming.tinkerbox.core.monitor.ThermalMonitor
import com.wuming.tinkerbox.core.monitor.ThermalState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 进程列表项（已把 pid/uid 解析为可读的应用信息）。
 * 非应用进程（root/shell/kernel 等）只显示原始进程名，无 packageName/icon。
 */
data class ProcessEntry(
    val pid: Int,
    val displayName: String,
    val packageName: String,
    val cpuPct: Double,
    val icon: ImageBitmap?,
    val isApp: Boolean,
)

/** 监控台状态快照。 */
data class MonitorUiState(
    val clusters: List<CpuClusterState> = emptyList(),
    val temps: List<ThermalState> = emptyList(),
    val device: DeviceInfo.DeviceConf = DeviceInfo.DeviceConf("", "", "", 0),
    val mem: MemoryState = MemoryState(),
    val power: PowerState = PowerState(),
    val processCount: Int = 0,
    val processes: List<ProcessEntry> = emptyList(),
    val polling: Boolean = false,
    val sortByCpu: Boolean = true,
)

/**
 * 监控台 ViewModel：周期性读取 CPU 频率 / 温度 / 进程，无需 Root。
 */
class MonitorViewModel(
    app: Application,
    private val mode: PermissionMode,
) : AndroidViewModel(app) {

    private val pm = app.packageManager

    private val _uiState = MutableStateFlow(MonitorUiState())
    val uiState: StateFlow<MonitorUiState> = _uiState.asStateFlow()

    /** 轮询间隔（毫秒）。普通模式只读，频率足够即可，取 1000ms。 */
    private val intervalMs = 1000L

    /** sortByCpu 切换。 */
    fun onSortChange(byCpu: Boolean) {
        _uiState.value = _uiState.value.copy(sortByCpu = byCpu)
    }

    /** 切换排序：CPU / 应用名。 */
    fun toggleSort() {
        val next = !_uiState.value.sortByCpu
        onSortChange(next)
        refreshProcesses()
    }

    /** 应用进程判断是否可操作（需 Shizuku / Root 且为应用进程）。 */
    fun canOperate(): Boolean = mode != PermissionMode.NORMAL

    init {
        startPolling()
    }

    private fun startPolling() {
        viewModelScope.launch(Dispatchers.IO) {
            val device = DeviceInfo.read()
            while (isActive) {
                val clusters = CpuMonitor.readClusters()
                val temps = ThermalMonitor.read()
                val mem = MemoryMonitor.read()
                // 取 Application 上下文（无需 Activity），普通模式即可读电流电压。
                val power = PowerMonitor.read(getApplication<android.app.Application>())
                val processCount = ProcessMonitor.procCount()
                val procs = ProcessMonitor.sample(mode, topN = 80)
                val sortByCpu = _uiState.value.sortByCpu
                withContext(Dispatchers.Main) {
                    _uiState.value = MonitorUiState(
                        clusters = clusters,
                        temps = temps,
                        device = device,
                        mem = mem,
                        power = power,
                        processCount = processCount,
                        processes = procs.map { resolve(it) }.let { sortEntries(it, sortByCpu) },
                        polling = true,
                        sortByCpu = sortByCpu,
                    )
                }
                delay(intervalMs)
            }
        }
    }

    /** 仅刷新进程列表（用于排序切换后立即生效，不重新读硬件）。 */
    private fun refreshProcesses() = viewModelScope.launch(Dispatchers.IO) {
        val procs = ProcessMonitor.sample(mode, topN = 80)
        val sortByCpu = _uiState.value.sortByCpu
        withContext(Dispatchers.Main) {
            _uiState.value = _uiState.value.copy(
                processes = procs.map { resolve(it) }.let { sortEntries(it, sortByCpu) },
            )
        }
    }

    private fun sortEntries(entries: List<ProcessEntry>, byCpu: Boolean): List<ProcessEntry> {
        return if (byCpu) entries.sortedByDescending { it.cpuPct }
        else entries.sortedBy { it.displayName.lowercase() }
    }

    /** 把 ProcStat 解析为应用可读信息：uid → 包名 → 应用名/图标。 */
    private fun resolve(p: ProcStat): ProcessEntry {
        if (p.uid >= 0) {
            val pkgs = runCatching { pm.getPackagesForUid(p.uid) }.getOrNull()
                ?.takeIf { it.isNotEmpty() }
            if (pkgs != null) {
                val pkg = pkgs[0]
                val label = runCatching {
                    pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString()
                }.getOrDefault(p.name)
                val icon = runCatching {
                    pm.getApplicationIcon(pkg).let { d ->
                        com.wuming.tinkerbox.core.apps.ProcessIcon.toImageBitmap(d)
                    }
                }.getOrNull()
                return ProcessEntry(
                    pid = p.pid,
                    displayName = label,
                    packageName = pkg,
                    cpuPct = p.cpuPct,
                    icon = icon,
                    isApp = true,
                )
            }
        }
        // 系统/非应用进程：保留原始名。
        return ProcessEntry(
            pid = p.pid,
            displayName = p.name,
            packageName = p.name,
            cpuPct = p.cpuPct,
            icon = null,
            isApp = false,
        )
    }

    /** 停止一个应用进程（Shizuku / Root）。返回是否成功。 */
    fun stopProcess(entry: ProcessEntry): Boolean {
        if (!entry.isApp) return false
        val cmd = if (mode == PermissionMode.ROOT) {
            com.wuming.tinkerbox.core.shell.RootShell.exec(
                "am force-stop ${entry.packageName}",
            )
        } else {
            com.wuming.tinkerbox.core.mode.ShizukuManager.exec(
                arrayOf("am", "force-stop", entry.packageName),
            )
        }
        return cmd.isSuccess
    }
}