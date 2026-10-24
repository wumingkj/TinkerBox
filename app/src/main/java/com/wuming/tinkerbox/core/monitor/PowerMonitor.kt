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
    val voltageV: Double = 0.0,     // 电池电压（V）
    val currentMa: Int = 0,         // 电池电流（mA），负值=放电，正值=充电
    val powerW: Double = 0.0,       // 功率（W）= voltage * |current|
) {
    /** 是否拿到有效电流（非未接/复位值）。 */
    val hasCurrent: Boolean get() = currentMa != Int.MIN_VALUE
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

    /** 整机各供电节点候选目录，按优先级探测。 */
    private val SUPPLY_DIRS = listOf("bms", "battery", "main", "power_supply")

    /**
     * 读取电池状态（普通等线性 Blocking）。
     *
     * @param context 用于取 [BatteryManager] 与粘性广播。
     */
    fun read(context: Context): PowerState {
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

        // 电流 / 电压：优先框架 BatteryManager。
        var voltageMv = (sysVoltage * 1000f).toInt()
        var currentUa = Int.MIN_VALUE
        runCatching {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            // 电压优先用 EXTRA_VOLTAGE / sysfs（BATTERY_PROPERTY_VOLTAGE_NOW 部分机型不可解析）。
            val c = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            if (c != Int.MIN_VALUE) currentUa = c
        }

        // 框架电流无效时，回退 sysfs。
        if (currentUa == Int.MIN_VALUE) {
            val sys = readSupplySysfs()
            if (sys != null) {
                val (cMa, vMv) = sys
                currentUa = if (cMa != Int.MIN_VALUE) cMa * 1000 else Int.MIN_VALUE
                if (vMv > 0) voltageMv = vMv
            }
        }

        val v = voltageMv / 1000.0
        val curMa = if (currentUa == Int.MIN_VALUE) Int.MIN_VALUE else currentUa / 1000
        val w = if (curMa != Int.MIN_VALUE) (v * kotlin.math.abs(curMa.toDouble()) / 1000.0) else 0.0
        return PowerState(
            batteryLevel = level,
            statusText = statusText,
            voltageV = v,
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

    /** 回退读 sysfs 电源节点。返回 (currentMa, voltageMv)；均无则 null。 */
    private fun readSupplySysfs(): Pair<Int, Int>? {
        val base = File("/sys/class/power_supply")
        val candidates = base.listFiles { f -> f.name in SUPPLY_DIRS }
            ?.mapNotNull { it.canonicalFile }
        if (candidates.isNullOrEmpty()) return null

        // current_now（µA）；无则 current_avg。
        var currentMa = Int.MIN_VALUE
        var voltageMv = 0
        for (dir in candidates) {
            runCatching {
                val c = File(dir, "current_now").readText().trim().toIntOrNull()
                    ?: File(dir, "current_avg").readText().trim().toIntOrNull()
                if (c != null && c != 0) currentMa = c / 1000
            }
            runCatching {
                val v = File(dir, "voltage_now").readText().trim().toLongOrNull()
                if (v != null && v > 0) voltageMv = (v / 1000).toInt()
            }
        }
        if (currentMa == Int.MIN_VALUE && voltageMv <= 0) return null
        return currentMa to voltageMv
    }
}