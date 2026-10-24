package com.wuming.tinkerbox.core.monitor

import java.io.File

/** 内存压力量化：按 [usedPct]（已用百分比）返回 0..1，供进度条着色。 */
data class MemoryState(
    val totalBytes: Long = 0L,     // 总物理内存
    val availBytes: Long = 0L,     // 可用内存（MemAvailable）
    val swapTotalBytes: Long = 0L, // swap 总量
    val swapFreeBytes: Long = 0L,  // swap 空闲量
) {
    val usedBytes: Long get() = (totalBytes - availBytes).coerceAtLeast(0L)
    /** 已用百分比 0f..1f（totalBytes 为 0 时为 0）。 */
    val usedPct: Float
        get() = if (totalBytes > 0L) (usedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f
}

/**
 * 内存只读监控。
 *
 * 直接解析 /proc/meminfo（普通模式亦可读，无需 root），等价于博客中
 * ActivityManager.MemoryInfo 的 totalMem/availMem，但更通用：App 无需持有
 * 系统服务引用即可在 IO 线程采集，且 /proc/meminfo 的 MemAvailable 语义
 * 与系统设置里"可用内存"一致。
 */
object MemoryMonitor {

    fun read(): MemoryState {
        val text = runCatching { File("/proc/meminfo").readText() }.getOrNull()
            ?: return MemoryState()
        var total = 0L
        var avail = 0L
        var swapTotal = 0L
        var swapFree = 0L
        for (line in text.lineSequence()) {
            val v = memValue(line) ?: continue
            when {
                line.startsWith("MemTotal:") -> total = v
                line.startsWith("MemAvailable:") -> avail = v
                line.startsWith("SwapTotal:") -> swapTotal = v
                line.startsWith("SwapFree:") -> swapFree = v
            }
        }
        // 无 MemAvailable（老内核）时回退为 MemFree + Cached。
        if (avail <= 0L) {
            var free = 0L
            var cached = 0L
            for (line in text.lineSequence()) {
                val v = memValue(line) ?: continue
                when {
                    line.startsWith("MemFree:") -> free = v
                    line.startsWith("Cached:") -> cached = v
                }
            }
            avail = free + cached
        }
        return MemoryState(total, avail, swapTotal, swapFree)
    }

    /** 从 "MemTotal:       123456 kB" 中解析字节数（kB → Byte）。 */
    private fun memValue(line: String): Long? {
        val idx = line.indexOf(':')
        if (idx < 0) return null
        val rest = line.substring(idx + 1).trim()
        // 形如 "123456 kB" 或 "123456"（老格式无单位）。
        val num = rest.split(Regex("\\s+")).firstOrNull()?.toLongOrNull() ?: return null
        return if (rest.contains("kB")) num * 1024L else num
    }
}