package com.wuming.tinkerbox.core.monitor

import android.os.Build
import java.io.File

/**
 * 设备 / SoC / 硬件信息（无需 Root 出厂可读）。
 *
 *  - SoC 型号：优先 /proc/cpuinfo 的 Hardware/model name，其次 Build.SOC_MODEL。
 *    骁龙 8 Gen 2 返回 "SM8550"；天机返回如 "MT6985"；展锐/瑞芯微为各自型号。
 *  - 架构 / ABI：Build.SUPPORTED_ABIS。
 *  - 核数：Runtime.availableProcessors / /proc/cpuinfo 数。
 */
object DeviceInfo {

    data class DeviceConf(
        val soc: String,        // SoC 型号（如 SM8550 / 骁龙8 Gen2 描述）
        val hardware: String,   // /proc/cpuinfo Hardware（如 "San Francisco" / "MTXXXX"）
        val abi: String,        // 主 ABI
        val cores: Int,         // 可感知核数
    )

    fun read(): DeviceConf {
        val cpuinfo = readCpuinfo()
        val hw = cpuinfo.hardware?.takeIf { it.isNotBlank() && it != "Unknown" }
            ?: Build.HARDWARE.takeIf { it.isNotBlank() }
            ?: "未知"
        val soc = Build.SOC_MODEL.takeIf { it.isNotBlank() }
            ?: hw
            ?: cpuinfo.modelName
            ?: "未知"
        return DeviceConf(
            soc = soc,
            hardware = hw,
            abi = Build.SUPPORTED_ABIS.firstOrNull { it.isNotBlank() } ?: "未知",
            cores = Runtime.getRuntime().availableProcessors(),
        )
    }

    private class Cpuinfo(
        val hardware: String? = null,
        val modelName: String? = null,
    )

    private fun readCpuinfo(): Cpuinfo {
        val text = runCatching { File("/proc/cpuinfo").readText() }.getOrNull() ?: return Cpuinfo()
        var hw: String? = null
        var model: String? = null
        for (line in text.lineSequence()) {
            if (hw == null && line.startsWith("Hardware", true)) {
                hw = line.substringAfter(':').trim()
            } else if (model == null && line.startsWith("model name", true)) {
                model = line.substringAfter(':').trim()
            }
        }
        return Cpuinfo(hw, model)
    }
}