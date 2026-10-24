package com.wuming.tinkerbox.core.monitor

import java.io.File

/**
 * 温度只读监控（无需 Root）。
 *
 * 从 /sys/class/thermal/thermal_zone* 读取温度传感器。该目录是 Linux 标准
 * thermal 框架输出，高通 / 联发科 / 展锐 / 瑞芯微等平台均存在，但 **zone 的
 * type 命名各平台完全不一致**：
 *  - 高通：cpu-0-x / cpuss-x / gpuss-x / xo-therm / aoss-*
 *  - 联发科：mtktsCpu / mtktsAp / mtktsBts（前缀 mtkts + 大写）
 *  - 展锐/瑞芯微：cpu0_cgpu、gpu 等自定义命名
 *
 * 因此这里**不依赖 type 名称**过滤，改为遍历所有 zone，仅按「有效温度范围」
 * 收集（毫摄氏度，剔除 modem 未插卡时常见的 -273000 无效值）。徽章取最高温。
 * temp 文件单位为毫摄氏度（m°C）。
 */
object ThermalMonitor {

    private const val THERMAL_BASE = "/sys/class/thermal"

    /** 有效温度范围（毫摄氏度）：-50°C ~ 200°C，超出视为传感器异常/无效。 */
    private const val MIN_VALID = -50_000L
    private const val MAX_VALID = 200_000L

    /**
     * 读取全部有效温度传感器（摄氏），按温度降序返回。
     * 不区分类型名，只要数值有效即纳入。
     */
    fun read(): List<ThermalState> {
        val base = File(THERMAL_BASE)
        if (!base.isDirectory) return emptyList()
        val result = ArrayList<ThermalState>(16)
        base.listFiles { f -> f.name.startsWith("thermal_zone") }
            ?.forEach { zone ->
                val type = runCatching { File(zone, "type").readText().trim() }.getOrNull() ?: return@forEach
                val tempText = runCatching { File(zone, "temp").readText().trim() }.getOrNull() ?: return@forEach
                val millic = tempText.toLongOrNull() ?: return@forEach
                if (millic < MIN_VALID || millic > MAX_VALID) return@forEach
                result.add(ThermalState(zone.name, type, millic / 1000.0))
            }
        return result.sortedByDescending { it.temperatureC }
    }

    /** 整体最高温（摄氏），无有效值返回 null。 */
    fun readMax(): Double? = read().maxOfOrNull { it.temperatureC }
}

/** 单个温度传感器状态。temperatureC 单位：摄氏度。 */
data class ThermalState(
    val zoneId: String,
    val type: String,
    val temperatureC: Double,
)