package com.wuming.tinkerbox.ui.theme

/** 主题偏好。持久化于 DataStore，由 [TinkerBoxApp] 全局持有。 */
enum class ThemeMode(val displayName: String) {
    SYSTEM("跟随系统"),
    LIGHT("浅色"),
    DARK("深色"),
}