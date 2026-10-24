package com.wuming.tinkerbox.core.monitor

import java.io.File

/**
 * CPU 频率只读监控（无需 Root）。
 *
 * 通过 sysfs 读取各 cpufreq policy 的当前 / 最低 / 最高频率与可用频率表。
 * 不需要权限即可读，普通模式可用。
 *
 * 目标设备（骁龙 8 Gen 2 / kalama / SM8550）posix policy 结构：
 *  - policy0：中核簇（A510），related cpus 0,1,2
 *  - policy3：大核簇（A715），related cpus 3,4,5,6
 *  - policy7：超大核簇（X3），related cpus 7
 */
object CpuMonitor {

    private const val POLICY_BASE = "/sys/devices/system/cpu/cpufreq"

    /**
     * 读取当前各簇（policy）频率状态并构造成 [CpuClusterState]。
     *
     * @param policyIds 要监控的 policy 编号；为 null 时自动枚举系统存在的 policy。
     */
    fun readClusters(policyIds: List<Int>? = null): List<CpuClusterState> {
        val ids = policyIds ?: detectPolicyIds()
        val raw = ArrayList<CpuClusterState>(ids.size)
        for (pid in ids) {
            val dir = "$POLICY_BASE/policy$pid"
            if (!File(dir).isDirectory) continue
            val cur = readLong("$dir/scaling_cur_freq") ?: continue
            val min = readLong("$dir/scaling_min_freq") ?: 0L
            val max = readLong("$dir/scaling_max_freq") ?: cur
            raw.add(
                CpuClusterState(
                    policyId = pid,
                    name = "未命名",
                    cores = readCpus("$dir/related_cpus", "$dir/affected_cpus"),
                    governor = readString("$dir/scaling_governor") ?: "",
                    curMhz = cur / 1000.0,
                    minMhz = min / 1000.0,
                    maxMhz = max / 1000.0,
                    availableMhz = readFrequencies("$dir/scaling_available_frequencies"),
                )
            )
        }
        // 按 max 频率降序确定簇的梯队级别：最大者=超大核/大核，最小者=小核。
        // 不硬编码 policy id，兼容骁龙(0/3/7)、天机、展锐、瑞芯微等任意编号。
        if (raw.size >= 2) {
            val sorted = raw.sortedByDescending { it.maxMhz }
            val count = sorted.size
            sorted.forEachIndexed { i, s ->
                s.name = when (i) {
                    0 if count >= 3 -> "超大核"
                    0 -> "大核"
                    count - 1 -> "小核"
                    else -> "大核"
                } + " Cluster${s.policyId}"
            }
        } else if (raw.size == 1) {
            raw[0].name = "CPU Cluster${raw[0].policyId}"
        }
        return raw.sortedBy { it.policyId }
    }

    /** 枚举系统上存在的 policy（/sys/devices/system/cpu/cpufreq/policy*）。 */
    fun detectPolicyIds(): List<Int> {
        val base = File(POLICY_BASE)
        if (!base.isDirectory) return emptyList()
        return base.listFiles()?.mapNotNull { f ->
            val name = f.name
            if (name.startsWith("policy")) name.removePrefix("policy").toIntOrNull() else null
        }?.sorted() ?: emptyList()
    }

    private fun readLong(path: String): Long? =
        readString(path)?.toLongOrNull()

    private fun readString(path: String): String? =
        runCatching { File(path).readText().trim() }.getOrNull()?.takeIf { it.isNotEmpty() }

    private fun readFrequencies(path: String): List<Double> =
        readString(path)?.split(" ")?.mapNotNull { it.toLongOrNull()?.let { v -> v / 1000.0 } }
            ?: emptyList()

    private fun readCpus(related: String, affected: String): List<Int> {
        val raw = readString(related) ?: readString(affected) ?: return emptyList()
        return raw.split(Regex("\\s+")).mapNotNull { it.toIntOrNull() }
    }
}

/** 单个 cpufreq cluster（簇）的频率状态。频率单位统一为 MHz。 */
data class CpuClusterState(
    val policyId: Int,
    var name: String,
    val cores: List<Int>,
    val governor: String,
    val curMhz: Double,
    val minMhz: Double,
    val maxMhz: Double,
    val availableMhz: List<Double>,
) {
    /** 当前频率在 min..max 之间的归一化进度（0.0..1.0）。 */
    val progress: Float
        get() {
            val span = maxMhz - minMhz
            if (span <= 0.0 || curMhz <= minMhz) return 0f
            return ((curMhz - minMhz) / span).coerceIn(0.0, 1.0).toFloat()
        }
}