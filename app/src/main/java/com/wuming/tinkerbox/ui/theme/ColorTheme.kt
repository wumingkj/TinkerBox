package com.wuming.tinkerbox.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 预设「主题主色 / keyColor seed」。
 *
 * 不再直接决定明暗自适应的 primary/secondary 颜色 —— 那交给 [ThemeController] 依据
 * [seedColor] 动态生成（Material You 配色引擎）。这里仅提供一组可挑选的色种 seed。
 */
enum class ColorTheme(val displayName: String, val seedColor: Color) {
    // 默认 · 星海蓝
    BLUE("星海蓝", Color(0xFF0561D3)),
    // 中国红
    CHINA_RED("中国红", Color(0xFFC62828)),
    // 清新绿
    FRESH_GREEN("清新绿", Color(0xFF2E7D32)),
    // 极光紫
    AURORA("极光紫", Color(0xFF5E35B1)),
    // 落日橙
    SUNSET("落日橙", Color(0xFFE65100)),

    // 系统默认（动态取色 fallback seed）
    PINK("樱粉", Color(0xFFE91E63)),
    DEEP_PURPLE("深紫", Color(0xFF673AB7)),
    INDIGO("靛蓝", Color(0xFF3F51B5)),
    CYAN("青蓝", Color(0xFF00BCD4)),
    TEAL("青绿", Color(0xFF009688)),
    LIME("浅绿", Color(0xFF8BC34A)),
    AMBER("琥珀", Color(0xFFFFC107)),
    ORANGE("橙黄", Color(0xFFFF9800)),
    BROWN("棕褐", Color(0xFF795548)),
    BLUE_GREY("蓝灰", Color(0xFF607D8B)),
    SAKURA("樱粉白", Color(0xFFFF9CA8)),
    ROSE("玫红", Color(0xFFFF4081)),
}