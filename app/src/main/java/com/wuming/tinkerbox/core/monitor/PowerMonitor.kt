package com.wuming.tinkerbox.core.monitor

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import java.io.File

/** 电池 / 电流电压状态（普通模式即可采集，不走特权 shell）。 */
data class PowerState(
    val batteryLevel: Int = 0,      // 电量百分比 0..100，未知为 0
    val statusText: String = "",    // 充电状态："充电中 / 放电中 / 已充满 / 未在充电 / 未知"
    val voltageV: Double = 0.0,     // 电池电压（V），单电芯为整压；双电芯为电芯0电压
    val cellVoltages: List<Double> = emptyList(), // 双电芯时两颗电芯电压（V）；单电芯为空
    val currentMa: Double? = null,  // 电池电流（mA，保留小数），负值=放电，正值=充电；null=读不到
    val powerW: Double = 0.0,       // 功率（W）= voltage * |current|
) {
    /** 是否双电芯（已读到两颗电压）。 */
    val isDualCell: Boolean get() = cellVoltages.size >= 2
    /** 是否拿到有效电流（非未接/复位值）。 */
    val hasCurrent: Boolean get() = currentMa != null
}

/**
 * 电池电流 / 电压 / 功率监控。
 *
 * 优先走 Android 框架 [BatteryManager]，普通模式即可读取，不受 SELinux / root
 * 权限限制：
 *  - 电流：[BatteryManager.BATTERY_PROPERTY_CURRENT_NOW]（单位 µA，负=放电）
 *  - 电压：[BatteryManager.BATTERY_PROPERTY_VOLTAGE_NOW]（单位 mV）
 *  - 电量 / 充电状态：注册读取系统粘性广播 [Intent.ACTION_BATTERY_CHANGED]
 *
 * 框架读不到电流（部分机型返回 MIN_VALUE 或需要电池温度计事件）时，回退解析
 * /sys/class/power_supply/{bms,battery,main}/current_now、voltage_now（µV / mA）。
 */
object PowerMonitor {

    /** 诊断日志 Tag。 */
    private const val TAG = "PowerMonitor"

    /** 整机各供电节点候选目录。按优先顺序（越靠前越权威）。 */
    private val SUPPLY_DIRS = listOf("bms", "battery", "main", "usb", "power_supply")

    /**
     * 电流候选。priority 越小越权威——真电池电流来自电池管理芯片/燃油表（bms/battery 节点），
     * 与 LSP 模块读取的电池引擎电流同源；usb/power_supply 是输入充电电流节点，非电池自身电流，
     * 仅作兜底。framework 的 CURRENT_NOW 在部分机型返回假待机值，权威性其次。
     *
     * [raw] 为节点原始数值；[unitMicro] 表示该来源是否保证为微安(µA)：
     *  - framework / dumpsys 按 API 契约恒为 µA；
     *  - 部分 OEM（如绿厂双电芯）sysfs 电池节点直接给毫安(mA)，故需按量级自适应判定。
     */
    private data class Cand(val raw: Int, val mv: Int, val priority: Int, val unitMicro: Boolean)

    /** 节点 -> 权威优先级（越小越权威）。 */
    private fun dirPriority(dir: String): Int = when (dir) {
        "bms" -> 1
        "battery" -> 2
        "main" -> 3
        "usb" -> 8
        else -> 9
    }

    /**
     * 读取电池状态（普通等线性 Blocking）。
     *
     * @param context 用于取 [BatteryManager] 与粘性广播。
     * @param cells   电芯配置；[BatteryCells.SERIES] 会把单颗电压归算为串联总压（×2）。
     * @param execRaw 可选的权限执行器（root/shizuku）：给定 shell 命令返回其 stdout（失败返回 null）。
     *                框架 + 普通 sysfs 都拿不到电流（部分机型 SELinux 限制 / 框架报 0）时用于 `cat` 电流节点。
     */
    fun read(
        context: Context,
        cells: BatteryCells = BatteryCells.AUTO,
        execRaw: ((String) -> String?)? = null,
    ): PowerState {
        // 电量 + 充电状态：粘性广播最可靠。
        var level = 0
        var statusText = ""
        var sysVoltage = 0f
        runCatching {
            val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            if (intent != null) {
                level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                if (scale > 0) level = (level * 100f / scale).toInt().coerceIn(0, 100)
                statusText = channelStatus(intent.getIntExtra(BatteryManager.EXTRA_STATUS, 0))
                if (sysVoltage <= 0f) {
                    sysVoltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) / 1000f
                }
            }
        }

        // 电压基准：优先粘性广播 EXTRA_VOLTAGE。
        var voltageMv = (sysVoltage * 1000f).toInt()

        // 收集低压电池节点（bms/battery/main）各自电压，用于双电芯时显示电芯0/电芯1。
        val sysCellMv = mutableListOf<Int>()

        // 电流候选：(电流µA, 该节点电压mV, 权威优先级)。
        val cand = mutableListOf<Cand>()

        // 1) 框架 BatteryManager（普通权限可读，权威性其次）。
        runCatching {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val cNow = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            if (cNow != 0 && cNow != Int.MIN_VALUE) cand.add(Cand(cNow, 0, 4, unitMicro = false))
            val cAvg = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE)
            if (cAvg != 0 && cAvg != Int.MIN_VALUE) cand.add(Cand(cAvg, 0, 6, unitMicro = false))
        }

        // 2) sysfs 各节点（特权优先，普通兜底）。bms/battery 为燃油表真电流，最权威。
        val sys = if (execRaw != null) readSupplySysfsPrivileged(execRaw) else readSupplySysfs()
        android.util.Log.d(TAG, "sysfs 候选数=${sys.size}  execRaw=$execRaw")
        for (c in sys) {
            if (c.raw != 0 && c.raw != Int.MIN_VALUE) cand.add(c)
            // 电压只认电池节点（priority<=3）：usb/power_supply 的 voltage_now 是充电输入侧
            // 高压（如 4897000µV），会污染电池电压，绝不采用。
            if (c.mv > 0 && c.priority <= 3) {
                voltageMv = c.mv
                sysCellMv.add(c.mv)
            }
        }

        // 3) 特权 `dumpsys batteryproperties`：贴近 LSP 的电池引擎电流（次权威，高于 usb）。
        if (execRaw != null) {
            readDumpsysBatteryProperties(execRaw)?.let { raw ->
                if (raw != 0 && raw != Int.MIN_VALUE) cand.add(Cand(raw, 0, 5, unitMicro = false))
            }
        }

        // 4) 特权 dumpsys battery（**Max charging current 是充电协商上限，非实时电流**，仅作最低优先级兜底）。
        if (execRaw != null) {
            readDumpsysBattery(execRaw)?.let { (ua, mv) ->
                if (ua != 0 && ua != Int.MIN_VALUE) cand.add(Cand(ua, mv, 10, unitMicro = false))
                if (mv > 0 && voltageMv <= 0) voltageMv = mv
            }
        }

        // 电流来源只应认「电池自身电流」节点：bms/battery/main 来自电池管理芯片/燃油表，最权威。
        // usb/power_supply 的 current_now 是充电器输入端电流、dumpsys battery 的 Max charging
        // 是充电协商上限——都不是电池电流，绝不能选为电流（否则会像日志那样选中 +2000ma）。
        // 也不做 expectedPositive 强过滤：各 OEM 符号约定不一（绿厂 battery 充电为负、放电为正，
        // 与常规相反），强过滤会把真实电池节点踢掉而误选 usb；最终方向交给 UI 层按充电状态校准。
        val batteryCand = cand.filter { it.priority <= 3 } // bms=1 battery=2 main=3
        val fireCand = cand.filter { it.priority <= 6 }   // 框架(4/6)/batteryproperties(5) 兜底
        val chosen = when {
            batteryCand.isNotEmpty() -> batteryCand.minByOrNull { it.priority }
            else -> fireCand.minByOrNull { it.priority }
        }
        var currentRaw = Int.MIN_VALUE
        var chosenUnitMicro = true
        var chosenVoltageMv = 0
        if (chosen != null) {
            currentRaw = chosen.raw
            chosenUnitMicro = chosen.unitMicro
            if (chosen.mv > 0) chosenVoltageMv = chosen.mv
        }
        if (chosenVoltageMv > 0) voltageMv = chosenVoltageMv
        android.util.Log.d(TAG, "候选=${cand.map { Triple(it.raw, it.mv, it.priority) }} 状态=$statusText 选中 raw=$currentRaw 单位µA=$chosenUnitMicro")

        // 电芯归算：电压「始终显示单芯值」，对齐工程模式 BatteryInfoShow 的 4.12v（单颗电压）。
        // 串联双电芯时，总功率须按串联合计总压（=单芯电压 × 电芯数）乘电流计算，
        // 串联电路电流处处相等（电流保持单芯值不变），故仅功率乘 cellCount。
        val cellCount = if (cells == BatteryCells.SERIES) 2 else 1
        val v = voltageMv / 1000.0

        // 原始值 -> mA。unitMicro=true 的来源（framework/dumpsys）恒为 µA，直接 /1000。
        // 其余（sysfs 电池节点 / batteryproperties）可能直接给 mA（如绿厂双电芯），
        // 按量级自适应：若按 µA 解读后 |值|<5mA（真实电池不可能这么小），判定原始单位本就是 mA。
        val curMa: Double?
        if (currentRaw == Int.MIN_VALUE) {
            curMa = null
        } else if (chosenUnitMicro) {
            curMa = currentRaw / 1000.0
        } else {
            val asUa = currentRaw / 1000.0
            curMa = if (kotlin.math.abs(asUa) < 5.0) currentRaw.toDouble() else asUa
        }
        val w = if (curMa != null) (v * cellCount * kotlin.math.abs(curMa) / 1000.0) else 0.0

        // 双电芯时给出电芯0/电芯1 两颗电压：优先取 sysfs 收集到的不同低压电池节点
        //（bms/battery/main 可能分属两芯）；不足取单芯电压拆分（两芯近似相等）。
        val cellVoltages: List<Double> = if (cells == BatteryCells.SERIES) {
            val distinct = sysCellMv.distinct().map { it / 1000.0 }
            when {
                distinct.size >= 2 -> distinct.take(2)
                distinct.size == 1 -> listOf(distinct[0], v)
                else -> listOf(v, v)
            }
        } else emptyList()

        return PowerState(
            batteryLevel = level,
            statusText = statusText,
            voltageV = v,
            cellVoltages = cellVoltages,
            currentMa = curMa,
            powerW = w,
        )
    }

    /** 把电池状态码映射为可读中文。 */
    private fun channelStatus(status: Int): String = when (status) {
        BatteryManager.BATTERY_STATUS_CHARGING -> "充电中"
        BatteryManager.BATTERY_STATUS_DISCHARGING -> "放电中"
        BatteryManager.BATTERY_STATUS_FULL -> "已充满"
        BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "未在充电"
        BatteryManager.BATTERY_STATUS_UNKNOWN -> "未知"
        else -> "未知"
    }

    /** 普通读 sysfs 电源节点。返回每个节点的电流候选（含权威优先级），电流 µA、电压 mV。 */
    private fun readSupplySysfs(): List<Cand> {
        val base = File("/sys/class/power_supply")
        val candidates = base.listFiles { f -> f.name in SUPPLY_DIRS }
            ?.mapNotNull { it.canonicalFile } ?: emptyList()
        val out = mutableListOf<Cand>()
        for (dir in candidates) {
            val ua = runCatching {
                File(dir, "current_now").readText().trim().toIntOrNull()
                    ?: File(dir, "current_avg").readText().trim().toIntOrNull()
            }.getOrNull() ?: 0
            val mv = runCatching {
                File(dir, "voltage_now").readText().trim().toLongOrNull()?.let { (it / 1000).toInt() } ?: 0
            }.getOrNull() ?: 0
            android.util.Log.d(TAG, "普通节点 ${dir.name}: ua=$ua mv=$mv")
            // unitMicro=false：该来源可能直接给 mA（OEM 特例），保留原值待自适应换算。
            if (ua != 0) out.add(Cand(ua, mv, dirPriority(dir.name), unitMicro = false))
        }
        return out
    }

    /**
     * 特权读 sysfs 电源节点（root/shizuku 下 `cat`）。部分机型 battery current 受 SELinux 限制，
     * 普通进程读不走 / 框架报 0，需提权读取（最权威：bms/battery）。
     */
    private fun readSupplySysfsPrivileged(execRaw: (String) -> String?): List<Cand> {
        val out = mutableListOf<Cand>()
        for (dir in SUPPLY_DIRS) {
            val cRaw = execRaw("cat /sys/class/power_supply/$dir/current_now")?.trim()
            val vRaw = execRaw("cat /sys/class/power_supply/$dir/voltage_now")?.trim()
            val c = cRaw?.toLongOrNull() ?: 0L
            val v = vRaw?.toLongOrNull() ?: 0L
            android.util.Log.d(TAG, "特权节点 $dir: current_now='$cRaw' voltage_now='$vRaw'")
            // unitMicro=false：sysfs 电池节点可能直接给 mA，保留原值待自适应换算。
            if (c != 0L) out.add(Cand(c.toInt(), if (v > 0L) (v / 1000).toInt() else 0, dirPriority(dir), unitMicro = false))
        }
        return out
    }

    /**
     * 特权读 `dumpsys batteryproperties`：系统电池属性服务，能够读取到与 LSP 模块贴近的
     * 真实电池引擎电流（燃油表 current_now，µA）。解析失败返回 null。
     * 输出形如：
     *   ...
     *   AC powered: false
     *   ...
     *   batteryStatus/detail/health/level/scale
     *   BatteryProvider HI uS
     *   ...
     */
    private fun readDumpsysBatteryProperties(execRaw: (String) -> String?): Int? {
        val out = execRaw("dumpsys batteryproperties") ?: return null
        // 逐行打印，便于对齐 LSP 真实电流来源；len>0 才说明该服务可用。
        val lines = out.lineSequence().filter { it.isNotBlank() }.toList()
        android.util.Log.d(TAG, "dumpsys batteryproperties ${lines.size} 行")
        for (l in lines) android.util.Log.d(TAG, "  BP| $l")
        var currentUa: Int? = null
        for (line in lines) {
            val t = line.trim()
            if (t.startsWith("current_now", true) || t.startsWith("current:", true)) {
                val n = t.substringAfter(':').substringBefore(';').trim().toLongOrNull()
                if (n != null && n != 0L) currentUa = n.toInt()
            }
        }
        return currentUa
    }

    /**
     * 通过 `dumpsys battery` 兜底读充电信息。部分机型 sysfs 的 current 受 SELinux 限制，
     * 但 shell/root 下 `dumpsys battery` 能给出协商的充电电流（Max charging current，µA）
     * 与电压（voltage，mV）。返回 (currentMa, voltageMv)；均无则 null。
     */
    private fun readDumpsysBattery(execRaw: (String) -> String?): Pair<Int, Int>? {
        val out = execRaw("dumpsys battery") ?: return null
        var currentUa = Int.MIN_VALUE
        var voltageMv = 0
        for (line in out.lineSequence()) {
            val t = line.trim()
            when {
                t.startsWith("Max charging current:") ->
                    t.substringAfter(':').trim().toLongOrNull()?.let {
                        if (it != 0L) currentUa = it.toInt()
                    }
                t.startsWith("voltage:") ->
                    t.substringAfter(':').trim().toIntOrNull()?.let {
                        if (it > 0) voltageMv = it
                    }
                t.startsWith("Max charging voltage:") ->
                    t.substringAfter(':').trim().toLongOrNull()?.let {
                        if (it > 0 && voltageMv <= 0) voltageMv = (it / 1000).toInt()
                    }
            }
        }
        if (currentUa == Int.MIN_VALUE && voltageMv <= 0) return null
        return currentUa to voltageMv
    }
}