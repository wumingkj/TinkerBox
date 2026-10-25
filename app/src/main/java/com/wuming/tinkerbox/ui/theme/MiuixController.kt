package com.wuming.tinkerbox.ui.theme

import androidx.compose.ui.graphics.Color
import com.materialkolor.dynamiccolor.ColorSpec
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.ThemeColorSpec
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle

/**
 * 把 [AppThemeState] 映射为 miuix 的 [ThemeController]，用于 MIUI_X 皮肤。
 *
 * 与 Material3 的 [ThemeController.rememberColorScheme] 共用同一份配置/seed，保证
 * 两套主题（miuix / material）色彩一致，皮肤切换时视觉无缝。
 */
internal object MiuixThemeController {
    fun of(
        appTheme: AppThemeState,
        isDark: Boolean,
    ): ThemeController {
        val paletteStyle = try {
            ThemePaletteStyle.valueOf(appTheme.paletteStyle.name)
        } catch (_: Exception) {
            ThemePaletteStyle.TonalSpot
        }
        val colorSpec = when (appTheme.colorSpec.effectiveFor(appTheme.paletteStyle)) {
            ColorSpec.SpecVersion.SPEC_2025 -> ThemeColorSpec.Spec2025
            else -> ThemeColorSpec.Spec2021
        }
        val colorSchemeMode = when (appTheme.colorMode) {
            ColorMode.SYSTEM -> ColorSchemeMode.System
            ColorMode.LIGHT -> ColorSchemeMode.Light
            ColorMode.DARK -> ColorSchemeMode.Dark
        }
        return ThemeController(
            colorSchemeMode,
            keyColor = appTheme.colorTheme.seedColor,
            isDark = isDark,
            paletteStyle = paletteStyle,
            colorSpec = colorSpec,
        )
    }
}

private fun ColorSpec.SpecVersion.effectiveFor(style: com.materialkolor.PaletteStyle): ColorSpec.SpecVersion {
    // Monet 化调色板只有部分风格支持 Spec2025；不支持时回退到 2021，避免出错。
    val supports2025 = style == com.materialkolor.PaletteStyle.TonalSpot ||
            style == com.materialkolor.PaletteStyle.Neutral ||
            style == com.materialkolor.PaletteStyle.Vibrant ||
            style == com.materialkolor.PaletteStyle.Expressive
    return if (this == ColorSpec.SpecVersion.SPEC_2025 && !supports2025) {
        ColorSpec.SpecVersion.SPEC_2021
    } else {
        this
    }
}