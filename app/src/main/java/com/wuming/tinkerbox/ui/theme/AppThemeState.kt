package com.wuming.tinkerbox.ui.theme

import androidx.compose.ui.graphics.Color
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.rememberDynamicColorScheme
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

/**
 * 主题「配色」配置 —— 与皮肤([StyleTheme])、解耦子功能([FeatureLocals])完全正交。
 *
 * 依据 [colorMode]（明暗 + Monet）与 [colorTheme]（主色 seed）以及调色板风格/色彩规格，
 * 通过 [ThemeController] 动态生成一套完成 [androidx.compose.material3.ColorScheme]。
 *
 * 新增配色维度（如暗色 OLED、自定义任意 ARGB 主色）时，在此扩展并用 [TinkerBoxApp] 持久化。
 */
data class AppThemeState(
    val colorMode: ColorMode = ColorMode.SYSTEM,
    val colorTheme: ColorTheme = ColorTheme.BLUE,
    val paletteStyle: PaletteStyle = PaletteStyle.TonalSpot,
    val colorSpec: ColorSpec.SpecVersion = ColorSpec.SpecVersion.Default,
)

/** 依据当前 [AppThemeState] 缓存推导（ThemeController 动态配色入口）。 */
object ThemeController {
    @Composable
    fun rememberColorScheme(state: AppThemeState, isDark: Boolean): ColorScheme {
        val seed = state.colorTheme.seedColor
        return rememberDynamicColorScheme(
            seedColor = seed,
            isDark = isDark,
            style = state.paletteStyle,
            specVersion = state.colorSpec,
        )
    }

    /** 纯计算：明暗是否生效（跟随系统时取系统值，Monet 模式覆盖）。 */
    fun isDark(mode: ColorMode, systemDark: Boolean): Boolean {
        if (mode.isSystem) return systemDark
        return mode.isDark
    }
}

/** 默认配色配置。 */
val DefaultAppTheme = AppThemeState()