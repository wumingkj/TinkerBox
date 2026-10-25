package com.wuming.tinkerbox.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 解耦 UI 子功能开关 —— 与主题配色、皮肤风格完全正交。
 *
 * 每个子功能都是独立的 CompositionLocal，组件（如底栏）自行读取决定渲染形态：
 *  - [LocalEnableBlur]：启用背景模糊（毛玻璃）。依赖系统捕获背景，API 33+ 有效。
 *  - [LocalEnableFloatingBottomBar]：使用悬浮胶囊底栏（vs 常规贴底导航）。
 *  - [LocalEnableFloatingBottomBarBlur]：悬浮底栏使用液态玻璃（半透明模糊）。级联依赖悬浮底栏开启。
 *  - [LocalEnableNavigationBadge]：导航栏显示未读/更新角标。
 *  - [LocalEnableRainbowSlider]：普通选态滑块用彩虹渐变填充（vs 默认主题色）。
 *  - [LocalEnablePredictiveBack]：启用系统预留性返回手势动画。
 *  - [LocalPageScale]：整体界面缩放比例（0.8f ~ 1.1f），用于动态字体/缩放偏好。
 *
 * 新增子功能时：在此追加 Local，并在 [com.wuming.tinkerbox.TinkerBoxApp] 中补状态/持久化，
 * 于根组合 [com.wuming.tinkerbox.ui.theme.TinkerBoxTheme] 的 CompositionLocalProvider 注入。
 */

val LocalEnableBlur = staticCompositionLocalOf { false }

val LocalEnableFloatingBottomBar = staticCompositionLocalOf { false }

val LocalEnableFloatingBottomBarBlur = staticCompositionLocalOf { false }

val LocalEnableNavigationBadge = staticCompositionLocalOf { true }

val LocalEnableRainbowSlider = staticCompositionLocalOf { false }

val LocalEnablePredictiveBack = staticCompositionLocalOf { false }

val LocalPageScale = staticCompositionLocalOf { 1.0f }