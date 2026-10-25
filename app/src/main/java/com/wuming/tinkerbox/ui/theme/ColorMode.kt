package com.wuming.tinkerbox.ui.theme

/**
 * 主题「亮度」模式。
 *
 * 仅保留跟随系统 / 浅色 / 深色三种基础明暗。持久化存储其 [Int value]。
 */
enum class ColorMode(val value: Int, val displayName: String) {
    SYSTEM(0, "跟随系统"),
    LIGHT(1, "浅色"),
    DARK(2, "深色");

    companion object {
        fun fromValue(value: Int) = when (value) {
            1 -> LIGHT
            2 -> DARK
            // 兼容历史持久化的 Monet/OLED 值：去掉动态取色与 OLED 语义，仅还原用户原本的明暗意图。
            0, 3 -> SYSTEM         // 跟随系统 / Monet-跟随系统 → 跟随系统
            4 -> LIGHT             // Monet-浅色 → 浅色
            5, 6 -> DARK           // Monet-深色 / OLED → 深色
            else -> SYSTEM
        }

        fun fromName(name: String) = runCatching { valueOf(name) }.getOrNull() ?: SYSTEM
    }

    val isSystem: Boolean get() = this == SYSTEM
    val isDark: Boolean get() = this == DARK
}