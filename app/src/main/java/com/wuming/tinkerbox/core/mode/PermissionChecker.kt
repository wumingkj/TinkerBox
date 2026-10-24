package com.wuming.tinkerbox.core.mode

import android.content.Context
import com.wuming.tinkerbox.core.root.RootBackend
import com.wuming.tinkerbox.core.root.RootDetector

/**
 * 某模式的可用状态。
 */
data class CapabilityState(
    val available: Boolean,
    val detail: String? = null,
) {
    companion object {
        fun available(detail: String? = null) = CapabilityState(true, detail)
        fun unavailable(reason: String? = null) = CapabilityState(false, reason)
    }
}

/**
 * 统一权限检测：普通模式永远可用；Shizuku 检测服务；Root 检测内核后端。
 */
class PermissionChecker(private val context: Context) {

    /** 检测普通模式：始终可用，无需任何权限。 */
    fun checkNormal(): CapabilityState = CapabilityState.available(detail = "无需权限")

    /** 检测 Shizuku 服务是否可用。 */
    fun checkShizuku(): CapabilityState {
        val installed = isShizukuInstalled()
        if (!installed) {
            return CapabilityState.unavailable(reason = "未安装 Shizuku App")
        }
        if (!ShizukuManager.isAvailable()) {
            return CapabilityState.unavailable(reason = "Shizuku 服务未启动，请先通过 ADB 激活")
        }
        return if (ShizukuManager.checkPermission()) {
            CapabilityState.available(detail = "Shizuku 已授权")
        } else {
            CapabilityState.unavailable(reason = "服务已启动，但未授权本应用（需前往 Shizuku 启用）")
        }
    }

    /** 检测 Root 后端。返回 None 视为不可用。 */
    fun checkRoot(): CapabilityState {
        val backend = RootDetector.detect()
        return when (backend) {
            is RootBackend.Magisk -> CapabilityState.available(detail = "Magisk")
            is RootBackend.KernelSU -> CapabilityState.available(
                detail = "KernelSU ${backend.version ?: ""}".trim(),
            )
            RootBackend.None -> CapabilityState.unavailable(reason = "未获得 Root")
        }
    }

    /** 取出 Root 模式下的后端对象，供界面展示用；不可用时返回 None。 */
    fun detectRootBackend(): RootBackend = RootDetector.detect()

    fun isShizukuInstalled(): Boolean {
        val packages = listOf("io.rikka.shizuku", "moe.shizuku.privileged.api")
        return packages.any { runCatching { context.packageManager.getPackageInfo(it, 0) }.isSuccess }
    }
}