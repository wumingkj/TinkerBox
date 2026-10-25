package com.wuming.tinkerbox.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 根主题组合：三层解耦的装配点。
 *
 *  1. 配色：由 [appTheme] 经 [ThemeController] 动态生成 Material3 ColorScheme（16 色 seed + Monet + 调色板风格 + 色彩规格）。
 *  2. 皮肤：按 [styleTheme] 决定是否额外套用 miuix 主题（MIUI_X）或仅 Material3。
 *  3. 解耦子功能：把 [FeatureLocals] 全部注入组合，供各组件独立读取。
 *
 * 现有功能页仍用 Material3 组件，因此始终提供 [MaterialTheme]；MIUI_X 皮肤下额外提供
 * miuix 的 [MiuixTheme]，供逐步迁移到的 miuix 组件读取（两者配色由同一 ColorScheme 派生，视觉一致）。
 */
@Composable
fun TinkerBoxTheme(
    appTheme: AppThemeState = DefaultAppTheme,
    styleTheme: StyleTheme = StyleTheme.MIUI_X,
    enableBlur: Boolean = false,
    enableFloatingBottomBar: Boolean = true,
    enableFloatingBottomBarBlur: Boolean = true,
    enableNavigationBadge: Boolean = true,
    enableRainbowSlider: Boolean = false,
    enablePredictiveBack: Boolean = false,
    pageScale: Float = 1.0f,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val dark = ThemeController.isDark(appTheme.colorMode, systemDark)
    val colorScheme = ThemeController.rememberColorScheme(appTheme, dark)

    val systemDensity = LocalDensity.current
    val effectiveDensity = if (pageScale == 1.0f) systemDensity
    else Density(systemDensity.density * pageScale, systemDensity.fontScale)

    CompositionLocalProvider(
        LocalColorMode provides appTheme.colorMode.value,
        LocalAppTheme provides appTheme,
        LocalEnableBlur provides enableBlur,
        LocalEnableFloatingBottomBar provides enableFloatingBottomBar,
        LocalEnableFloatingBottomBarBlur provides enableFloatingBottomBarBlur,
        LocalEnableNavigationBadge provides enableNavigationBadge,
        LocalEnableRainbowSlider provides enableRainbowSlider,
        LocalEnablePredictiveBack provides enablePredictiveBack,
        LocalPageScale provides pageScale,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            shapes = themedShapes(styleTheme),
            content = {
                // 根容器铺满跟随明暗的主题背景：窗口 theme 固定为 Light（windowBackground 恒白），
                // 深色模式若不加这层会透出白色窗口背景，导致「白底 + 深色卡片」的高对比错乱。
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    if (styleTheme.isLiquidGlass) {
                        val miuixController = MiuixThemeController.of(appTheme, dark)
                        MiuixTheme(controller = miuixController) {
                            LocalDensityProvider(effectiveDensity, content)
                        }
                    } else {
                        LocalDensityProvider(effectiveDensity, content)
                    }
                }
            },
        )
    }
}

@Composable
private fun LocalDensityProvider(density: Density, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalDensity provides density) {
        content()
    }
}

/** 当前配色模式（明暗标识），供需要读取明暗决定形态的组件使用。 */
val LocalColorMode = androidx.compose.runtime.staticCompositionLocalOf { 0 }

/** 当前配色配置，供需要直接读取 seed/风格/规格的组件（如预览卡）使用。 */
val LocalAppTheme = androidx.compose.runtime.staticCompositionLocalOf { DefaultAppTheme }

/** 依据皮肤决定组件圆角：液态玻璃用更圆润的胶囊造型，Material 用常规圆角。 */
internal fun themedShapes(style: StyleTheme) = if (style.isLiquidGlass) {
    Shapes(
        extraSmall = RoundedCornerShape(8.dp),
        small = RoundedCornerShape(12.dp),
        medium = RoundedCornerShape(16.dp),
        large = RoundedCornerShape(24.dp),
        extraLarge = RoundedCornerShape(30.dp),
    )
} else {
    Shapes(
        extraSmall = RoundedCornerShape(4.dp),
        small = RoundedCornerShape(8.dp),
        medium = RoundedCornerShape(12.dp),
        large = RoundedCornerShape(16.dp),
        extraLarge = RoundedCornerShape(28.dp),
    )
}