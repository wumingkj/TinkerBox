package com.wuming.tinkerbox.core.monitor

import com.wuming.tinkerbox.core.mode.PermissionMode
import com.wuming.tinkerbox.core.mode.ShizukuManager
import com.wuming.tinkerbox.core.shell.RootShell
import java.io.File

/**
 * Linux top 风格进程采样。
 *
 * 按权限模式分级（与用户确认：普通读不到全量，root/shizuku 全量）：
 *  - [PermissionMode.NORMAL]：App 进程内直读 /proc（受 SELinux/包可见性限制，
 *    仅能读到本 App 及少数可感知进程，聊胜于无）。
 *  - [PermissionMode.SHIZUKU]：通过 Shizuku 的 shell 权限执行系统 `top`，全量。
 *  - [PermissionMode.ROOT]：通过 `su` 执行系统 `top`，全量。
 *
 * 与 Linux `top` 一致，shizuku/root 模式直接复用系统 top 输出（同一套算法，保证
 * CPU% 准确）；普通模式退化为 /proc 两次采样差值（尽力而为）。
 */
object ProcessMonitor {

    /**
     * 采集进程 CPU 占用。
     *
     * @param mode 当前权限模式，决定采集方式。
     * @param topN 返回最多数量。
     */
    fun sample(mode: PermissionMode, topN: Int = 12): List<ProcStat> {
        return when (mode) {
            PermissionMode.NORMAL -> sampleDirect(topN)
            PermissionMode.SHIZUKU -> sampleTop(topN) { cmd -> ShizukuManager.exec(cmd) }
            PermissionMode.ROOT -> sampleTop(topN) { cmd -> RootShell.exec(cmd) }
        }
    }

    /**
     * 统计当前运行中的进程总数。
     *
     * /proc 下每个数字目录即一个进程，普通模式也可遍历目录名（仅目录项可见，
     * 不涉及读取他人 stat 内容，SELinux 不拦截）。Shizuku/Root 可看到全量。
     */
    fun procCount(): Int {
        val dir = File("/proc")
        val entries = dir.listFiles { f -> f.name.all { it.isDigit() } } ?: return 0
        return entries.size
    }

    // ---------- shizuku / root：系统 top 全量 ----------

    private fun sampleTop(topN: Int, exec: (String) -> RootShell.ShellResult): List<ProcStat> {
        // -b 批处理，-n 1 采样一次。注意不能用 "-o %CPU"：它会退化为只输出单列 CPU%，
        // 丢失 PID 与进程名。用标准 top 输出，按固定列索引解析。
        val result = exec("top -b -n 1")
        if (result.exitCode != 0 || result.stdout.isBlank()) return emptyList()
        return parseTop(result.stdout, topN)
    }

    /**
     * 解析系统 top 输出。
     *
     * toybox top 数据行（实测）：
     *   列: PID USER PR NI VIRT RES SHR 状态 %CPU %MEM TIME+ ARGS...
     *   `26680 shell 20 0 2.1G 6.6M 5.1M R 33.3 0.0 0:00.06 top`
     *   注意表头是 `S[%CPU]`（一个 token），但数据行里状态字母(R)与 CPU 值
     *   被拆成两个 token，所以表头列索引与数据列不对齐，不能按表头数字索引取。
     *   改为以 TIME+（形如 "m:ss.cc"）为数据行锚点：CPU 在其前 2 列，进程名在其后。
     */
    private fun parseTop(output: String, topN: Int): List<ProcStat> {
        val rows = ArrayList<ProcStat>()
        val timeRegex = Regex("^\\d+:\\d{2}\\.\\d+$")

        for (line in output.lineSequence()) {
            if (line.isBlank()) continue
            val ts = line.trimStart()
            if (ts.startsWith("PID") || ts.startsWith("Tasks:") ||
                ts.startsWith("Mem:") || ts.startsWith("Swap:") ||
                ts.endsWith("%cpu")            // 汇总行形如 "800%cpu 33%user..."
            ) continue

            val tokens = line.trim().split(Regex("\\s+"))
            if (tokens.size < 3) continue
            val pid = tokens[0].toIntOrNull() ?: continue

            // 以 TIME+ 列为锚点定位 CPU 与进程名。
            var timeIdx = -1
            for ((i, t) in tokens.withIndex()) {
                if (timeRegex.matches(t)) { timeIdx = i; break }
            }
            if (timeIdx <= 1) continue
            val cpuPct = tokens.getOrNull(timeIdx - 2)?.toDoubleOrNull() ?: 0.0
            // USER 列（索引 1）用于反推 uid，交给上层做 包名→应用名/图标。
            // Android 多用户格式形如 "u0_a564"（= 10000 + 564），纯数字则直接用。
            val uid = parseUid(tokens.getOrNull(1))
            val name = if (timeIdx + 1 < tokens.size) {
                tokens.subList(timeIdx + 1, tokens.size).joinToString(" ")
            } else {
                tokens[0]
            }
            rows.add(ProcStat(pid = pid, name = name, cpuPct = cpuPct, uid = uid))
        }

        // 收集完整列表后按 CPU 降序取前 topN，确保展示真正最占 CPU 的进程。
        return rows.sortedByDescending { it.cpuPct }.take(topN)
    }

    /** 把 top 的 USER 列解析为 Linux uid。
     *  - "u0_a564" → 10000 + 564
     *  - 纯数字    → 直接用
     *  - "root" / "shell" 等非应用用户名 → -1（交给上层当作系统进程处理） */
    private fun parseUid(user: String?): Int {
        if (user.isNullOrEmpty()) return -1
        val m = Regex("^u\\d+_a(\\d+)$").find(user)
        if (m != null) return 10000 + (m.groupValues[1].toIntOrNull() ?: -1).coerceAtLeast(0)
        return user.toIntOrNull() ?: -1
    }

    // ---------- 普通模式：/proc 直读（尽力而为） ----------

    private var prevTotal: Long = 0L
    private var prevProcs: Map<Int, Long> = emptyMap()

    private fun sampleDirect(topN: Int): List<ProcStat> {
        val curTotal = totalCpuTicks()
        val curProcs = procTicks()

        if (prevTotal <= 0L || curTotal < prevTotal) {
            prevTotal = curTotal
            prevProcs = curProcs
            return emptyList()
        }

        val deltaTotal = (curTotal - prevTotal).coerceAtLeast(1L)
        val result = ArrayList<ProcStat>(curProcs.size)
        for ((pid, ticks) in curProcs) {
            val delta = ticks - (prevProcs[pid] ?: 0L)
            if (delta <= 0L) continue
            val pct = delta.toDouble() / deltaTotal.toDouble() * 100.0
            result.add(ProcStat(pid = pid, name = procName(pid), cpuPct = pct, ticks = ticks))
        }

        prevTotal = curTotal
        prevProcs = curProcs
        return result.sortedByDescending { it.cpuPct }.take(topN)
    }

    private fun totalCpuTicks(): Long {
        val text = readFirst("/proc/stat") ?: return 0L
        val line = text.lineSequence().firstOrNull { it.startsWith("cpu ") } ?: return 0L
        val parts = line.split(Regex("\\s+")).drop(1)
        return parts.sumOf { it.toLongOrNull() ?: 0L }
    }

    private fun procTicks(): Map<Int, Long> {
        val dir = File("/proc")
        val map = HashMap<Int, Long>(256)
        val dirs = dir.listFiles { f -> f.name.all { it.isDigit() } } ?: return map
        for (d in dirs) {
            val pid = d.name.toIntOrNull() ?: continue
            val stat = readString("$d/stat") ?: continue
            val ticks = parseStatTicks(stat) ?: continue
            if (ticks > 0L) map[pid] = ticks
        }
        return map
    }

    /** /proc/[pid]/stat 中 comm 可能含空格/括号，须从第一个括号对之后解析第 14、15 字段(utime,stime)。
     *  去 comm 后：after[0]=state,... after[11]=utime, after[12]=stime。 */
    private fun parseStatTicks(stat: String): Long? {
        val open = stat.indexOf('(')
        val close = stat.lastIndexOf(')')
        if (open < 0 || close <= open) return null
        val after = stat.substring(close + 1).trim().split(Regex("\\s+"))
        if (after.size <= 12) return null
        val utime = after[11].toLongOrNull() ?: return null
        val stime = after[12].toLongOrNull() ?: return null
        return utime + stime
    }

    private fun procName(pid: Int): String {
        val cmdline = readString("/proc/$pid/cmdline")?.replace('\u0000', ' ')
        if (!cmdline.isNullOrBlank()) return cmdline.trim()
        val stat = readString("/proc/$pid/stat") ?: return pid.toString()
        val open = stat.indexOf('(')
        val close = stat.lastIndexOf(')')
        return if (open >= 0 && close > open) stat.substring(open + 1, close) else pid.toString()
    }

    private fun readString(path: String): String? =
        runCatching { File(path).readText().trim() }.getOrNull()?.takeIf { it.isNotEmpty() }

    private fun readFirst(path: String): String? =
        runCatching { File(path).useLines { l -> l.first() } }.getOrNull()
}

/** 单个进程 CPU 采样状态。cpuPct：占整机 CPU 百分比（0~100*核数）。
 *  @param uid 进程的 Linux uid；-1 表示无法识别（非 Android 应用进程，如 root/shell/kernel）。 */
data class ProcStat(
    val pid: Int,
    val name: String,
    val cpuPct: Double,
    val uid: Int = -1,
    val ticks: Long = 0L,
)