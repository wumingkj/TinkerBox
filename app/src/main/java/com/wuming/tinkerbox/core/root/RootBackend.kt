package com.wuming.tinkerbox.core.root

import com.wuming.tinkerbox.core.shell.RootShell

/**
 * Root 后端抽象。SukiSU 属于 KernelSU 分支，检测统一走 KSU 通道，
 * 因此仅需区分 KernelSU / Magisk / None 三种形态。
 *
 * @property displayName 展示名
 * @property version 后端版本号（探测失败为 null）
 */
sealed interface RootBackend {
    val displayName: String
    val version: String?

    data class KernelSU(override val version: String?) : RootBackend {
        override val displayName: String = "KernelSU"
    }

    data class Magisk(override val version: String?) : RootBackend {
        override val displayName: String = "Magisk"
    }

    data object None : RootBackend {
        override val displayName: String = "未获得 Root"
        override val version: String? = null
    }
}

/**
 * 检测当前设备的 Root 后端。
 *
 * 关键点：不能靠探测 /data/adb 路径——普通 App 进程无权读取该目录，
 * 无论是否 root 都会返回 false。必须真执行 su 请求授权，再据校版本串分流。
 */
object RootDetector {

    fun detect(): RootBackend {
        // 1. 真执行 su 验证是否持有 root 权限（uid=0）
        val idProbe = RootShell.exec("id")
        val isRoot = idProbe.isSuccess && idProbe.stdout.contains("uid=0")
        if (!isRoot) return RootBackend.None

        // 2. 取 su 版本串，KSU 系与 Magisk 前缀不同
        val versionProbe = RootShell.suDirect("-v")
        val versionLine = versionProbe.stdout.lines().firstOrNull()?.trim()?.takeIf { it.isNotBlank() }
            ?: "unknown"

        return classify(versionLine)
    }

    /**
     * 依据版本串前缀分类：
     *  - 含 "magisk" 视为 Magisk
     *  - 其余（KernelSU / SukiSU / 纯数字版本号）一律归 KSU 系
     */
    private fun classify(versionLine: String): RootBackend {
        return if (versionLine.contains("magisk", ignoreCase = true)) {
            RootBackend.Magisk(versionLine)
        } else {
            RootBackend.KernelSU(versionLine)
        }
    }
}