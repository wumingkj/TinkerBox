package com.wuming.tinkerbox.core.mode

/**
 * 权限模式。决定各模块能做什么（能力矩阵），而非三套 UI。
 */
enum class PermissionMode(val displayName: String, val description: String) {
    NORMAL(
        displayName = "普通模式",
        description = "只读监控 + 轻量调校，无需任何权限",
    ),
    SHIZUKU(
        displayName = "Shizuku",
        description = "补足应用冻结与系统设置，需 Shizuku 服务",
    ),
    ROOT(
        displayName = "Root",
        description = "解锁内核调优、温控与场景守护，需 KSU/Magisk",
    ),
}