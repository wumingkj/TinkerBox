package com.wuming.tinkerbox.ui.monitor

import android.app.Application
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wuming.tinkerbox.core.mode.PermissionMode
import com.wuming.tinkerbox.core.mode.ShizukuManager
import com.wuming.tinkerbox.core.monitor.BatteryCells
import com.wuming.tinkerbox.core.monitor.CpuClusterState
import com.wuming.tinkerbox.core.monitor.CpuMonitor
import com.wuming.tinkerbox.core.monitor.DeviceInfo
import com.wuming.tinkerbox.core.monitor.GpuInfoReader
import com.wuming.tinkerbox.core.monitor.GpuMonitor
import com.wuming.tinkerbox.core.monitor.GpuState
import com.wuming.tinkerbox.core.monitor.MemoryMonitor
import com.wuming.tinkerbox.core.monitor.MemoryState
import com.wuming.tinkerbox.core.monitor.NetMonitor
import com.wuming.tinkerbox.core.monitor.NetState
import com.wuming.tinkerbox.core.monitor.PowerConfig
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

/** 监控台进程页状态（低频数据，独立于硬件 StateFlow，专门 3 秒刷新）。 */
data class ProcessState(
    val processCount: Int = 0,
    val processes: List<ProcessEntry> = emptyList(),
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

    /**
     * 应用图标缓存：按包名缓存已解码的位图，避免每秒轮询时为每个进程重复
     * getApplicationIcon + 位图解压。否则每秒 new 数十个 ImageBitmap 大对象，
     * 会周期性触发 GC 停顿、在滚动时表现为"固定几秒卡一下"。
     * 图标只在一轮全量采样时经 withContext(Main) 发布，缓存仅供 IO 读取，无需加锁。
     */
    private val iconCache = HashMap<String, ImageBitmap>()

    // 状态拆分：每一块硬件各自独立 StateFlow、独立发布。
    // 这样 UI 每张卡片只订阅自己那块 state，某块未更新时 Compose 因参数相等而跳过该卡片重组，
    // 实现"哪个数字动、哪块区域重组"，不再一颗大 state 每秒整棵重建。
    private val _cpuState = MutableStateFlow<List<CpuClusterState>>(emptyList())
    val cpuState: StateFlow<List<CpuClusterState>> = _cpuState.asStateFlow()

    private val _gpuState = MutableStateFlow(GpuState())
    val gpuState: StateFlow<GpuState> = _gpuState.asStateFlow()

    private val _tempState = MutableStateFlow<List<ThermalState>>(emptyList())
    val tempState: StateFlow<List<ThermalState>> = _tempState.asStateFlow()

    private val _memState = MutableStateFlow(MemoryState())
    val memState: StateFlow<MemoryState> = _memState.asStateFlow()

    private val _powerState = MutableStateFlow(PowerState())
    val powerState: StateFlow<PowerState> = _powerState.asStateFlow()

    private val _netState = MutableStateFlow(NetState())
    val netState: StateFlow<NetState> = _netState.asStateFlow()

    private val _deviceState = MutableStateFlow(DeviceInfo.DeviceConf("", "", "", 0))
    val deviceState: StateFlow<DeviceInfo.DeviceConf> = _deviceState.asStateFlow()

    private val _processState = MutableStateFlow(ProcessState())
    val processState: StateFlow<ProcessState> = _processState.asStateFlow()

    /** 当前电芯配置（来自设置页持久化，切换后实时生效）。 */
    private var cells = BatteryCells.AUTO

    /** 后台采样间隔（毫秒）：每秒采集一次原始电流/电压。 */
    private val sampleIntervalMs = 1000L

    /** UI 刷新节奏：每 N 次采样 = 5 秒更新一次界面。 */
    private val uiEverySamples = 5

    /** 电流/电压滑动窗口：保留最近 [uiEverySamples] 次采样，UI 用其做中位数滤波。 */
    private val powerWindow = ArrayDeque<PowerState>()

    /** 距上次 UI 刷新已累计的采样数。 */
    private var samplesSinceUi = 0

    /** 最近一次中位数滤波后的电源状态；两次 5 秒刷新之间 UI 复用它。 */
    private var filteredPower: PowerState = PowerState()

    /** 进程列表降频刷新周期（秒）：进程 CPU% 不需要每秒精确，3 秒一次足以，
     *  可把监控台"每秒整块重建"的大头（80 行列表）降为低频，主循环每秒只刷轻量硬件。 */
    private val processEveryMs = 3000L

    /** 最近一次解析好的进程列表缓存：非进程采样周期内复用同一实例（引用不变），
     *  避免每秒 publish 时进程 list 内容变化触发 80 行全量重组。 */
    private var cachedProcesses: List<ProcessEntry> = emptyList()

    /** sortByCpu 切换。 */
    fun onSortChange(byCpu: Boolean) {
        _processState.value = _processState.value.copy(sortByCpu = byCpu)
    }

    /** 切换排序：CPU / 应用名。 */
    fun toggleSort() {
        val next = !_processState.value.sortByCpu
        onSortChange(next)
        refreshProcesses()
    }

    /** 应用进程判断是否可操作（需 Shizuku / Root 且为应用进程）。 */
    fun canOperate(): Boolean = mode != PermissionMode.NORMAL

    init {
        startPolling()
    }

    private fun startPolling() {
        viewModelScope.launch {
            PowerConfig.cellsFlow(getApplication()).collect { cells = it }
        }

        viewModelScope.launch(Dispatchers.IO) {
            val device = DeviceInfo.read()
            // GPU 静态属性（型号/刷新率/分辨率）公开 API 一次读出，后续复用。
            val gpuInfo = GpuInfoReader.read(getApplication())
            while (isActive) {
                // 1) 后台每秒采样：电流/电压原始读数及其它硬件读数。
                val clusters = CpuMonitor.readClusters()
                // 注入特权执行器（root/shizuku），GPU sysfs 被 SELinux 拦截时用它 `cat`。
                val execRaw: ((String) -> String?)? = when (mode) {
                    PermissionMode.ROOT -> { cmd ->
                        com.wuming.tinkerbox.core.shell.RootShell.exec(cmd)
                            .takeIf { it.isSuccess }?.stdout?.trim()
                    }
                    PermissionMode.SHIZUKU -> { cmd ->
                        ShizukuManager.exec(cmd).takeIf { it.isSuccess }?.stdout?.trim()
                    }
                    else -> null
                }
                val gpu = GpuMonitor.read(execRaw).copy(info = gpuInfo)
                // 网络走官方 TrafficStats 累计计数器（普通模式即可读，无需 root）。
                val net = NetMonitor.read()
                val temps = ThermalMonitor.read()
                val mem = MemoryMonitor.read()
                android.util.Log.d(
                    "TinkerBoxMnt",
                    "gpu(cur=${gpu.curMhz}MHz load=${gpu.loadPct} avail=${gpu.available} priv=${gpu.privilegedOnly}) " +
                            "net(down=${net.rxRateBps}B/s up=${net.txRateBps}B/s tot=${net.rxTotalBytes}/${net.txTotalBytes})",
                )
                // 取 Application 上下文（无需 Activity），普通模式即可读电流电压。
                // 框架 + 普通 sysfs 读不到电流时，root/shizuku 下用特权 `cat` 兜底（部分机型 SELinux 限制）。
                val powerRaw = PowerMonitor.read(getApplication<android.app.Application>(), cells, execRaw)

                // 2) 电源原始读数入滑动窗口，只保留最近 uiEverySamples 次。
                powerWindow.addLast(powerRaw)
                while (powerWindow.size > uiEverySamples) powerWindow.removeFirst()
                samplesSinceUi++

                // 3) 电源按 5 秒节奏做中位数滤波，其余硬件（CPU/温度/内存）每秒常刷。
                val displayPower = if (samplesSinceUi >= uiEverySamples) {
                    samplesSinceUi = 0
                    filteredPower = medianPower(powerWindow.toList())
                    filteredPower
                } else {
                    filteredPower
                }

                // 各硬件块独立发布到各自 StateFlow：某块值没变时，StateFlow 不通知订阅者，
                // 对应卡片就跳过重组——CPU/GPU/温度各自只更新自己那张卡，互不牵连。
                withContext(Dispatchers.Main) {
                    _deviceState.value = device
                    _cpuState.value = clusters
                    _gpuState.value = gpu
                    _netState.value = net
                    _tempState.value = temps
                    _memState.value = mem
                    _powerState.value = displayPower
                }
                delay(sampleIntervalMs)
            }
        }

        // 进程列表降频循环：独立于每秒硬读，按 3 秒节奏采样 + 解析 + 发布。
        // 进程采样/图标解析整体较重，若并进每秒循环会成为整块监控台每秒重建的大头；
        // 抽出来后每秒只有轻量硬件卡出图，进程列表 3 秒才翻新一次。
        viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                val processCount = ProcessMonitor.procCount()
                val procs = ProcessMonitor.sample(mode, topN = 80)
                val sortByCpu = _processState.value.sortByCpu
                val resolved = procs.map { resolve(it) }.let { sortEntries(it, sortByCpu) }
                cachedProcesses = resolved
                withContext(Dispatchers.Main) {
                    _processState.value = ProcessState(
                        processCount = processCount,
                        processes = resolved,
                        sortByCpu = sortByCpu,
                    )
                }
                delay(processEveryMs)
            }
        }
    }

    /**
     * 滑动窗口中位数滤波。
     *
     * 关键设计：**量值与符号解耦**——
     *  - 量值（|电流|）对窗口内样本的绝对值取中位，抗瞬时毛刺（如 usb 假值混入）；
     *  - 符号（方向）不参与排序取中，而是由「充电状态」强绑定决定：
     *      充电/已充满 → 正（充电流入）；放电/未在充电 → 负（电池输出）；
     *      状态未知 → 采用最新一次采样的符号。
     *    这样符号稳定，只在状态真正切换时才翻转，不会再因窗口内个别方向相反的
     *    噪声样本而时正时负、滞后一帧翻转。
     * 电量/充电状态取窗口最新值。功率用滤波后电压 × |电流| 重算，三者自洽。
     */
    private fun medianPower(samples: List<PowerState>): PowerState {
        if (samples.isEmpty()) return PowerState()
        if (samples.size == 1) return samples[0]
        val latest = samples.last()

        // 判断窗口「期望」的电流方向：由最新充电状态强绑定。
        val isPositive = when (latest.statusText) {
            "充电中", "已充满" -> true
            "放电中", "未在充电" -> false
            else -> latest.currentMa?.let { it >= 0 } ?: false
        }

        // 量值：对窗口内样本的绝对值取中位（只统计 hasCurrent 的样本）。
        val magMa = samples.mapNotNull { it.currentMa }.map { kotlin.math.abs(it) }.sorted()
            .let { arr -> if (arr.isEmpty()) null else arr[arr.size / 2] }

        // 附上方向符号。
        val cur = when {
            magMa == null -> null
            isPositive -> magMa
            else -> -magMa
        }
        // 电压：非零电压样本的中位数。
        val volt = samples.map { it.voltageV }.filter { it > 0 }.sorted().let { arr ->
            if (arr.isEmpty()) latest.voltageV else arr[arr.size / 2]
        }
        // 功率：用滤波后电压 × 电芯数 × 电流量值重算（电压为单芯值，串联双电芯总功率须 ×2），
        // 保证三者自洽并对齐工程模式的真实总功率。
        val cellCount = if (cells == BatteryCells.SERIES) 2 else 1
        val w = if (cur != null) volt * cellCount * kotlin.math.abs(cur) / 1000.0 else 0.0
        return PowerState(
            batteryLevel = latest.batteryLevel,
            statusText = latest.statusText,
            voltageV = volt,
            cellVoltages = latest.cellVoltages,
            currentMa = cur,
            powerW = w,
        )
    }

    /** 仅刷新进程列表（用于排序切换后立即生效，不重新读硬件）。 */
    private fun refreshProcesses() = viewModelScope.launch(Dispatchers.IO) {
        val procs = ProcessMonitor.sample(mode, topN = 80)
        val sortByCpu = _processState.value.sortByCpu
        val resolved = procs.map { resolve(it) }.let { sortEntries(it, sortByCpu) }
        cachedProcesses = resolved
        withContext(Dispatchers.Main) {
            _processState.value = _processState.value.copy(processes = resolved)
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
                // 图标只解码一次并缓存，秒级轮询直接复用，避免每轮 new 位图引发 GC。
                val icon = iconCache[pkg] ?: runCatching {
                    pm.getApplicationIcon(pkg).let { d ->
                        com.wuming.tinkerbox.core.apps.ProcessIcon.toImageBitmap(d)
                    }
                }.getOrNull()?.also { iconCache[pkg] = it }
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