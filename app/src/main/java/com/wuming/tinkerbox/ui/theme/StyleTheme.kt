package com.wuming.tinkerbox.ui.theme

/**
 * 「皮肤 / 风格」，决定根组合分发到哪套组件库（Miuix / Material），与主题配色、解耦子
 * 功能完全正交。
 *
 *  - [MIUI_X]：miuix 组件库（默认），SukiSU 风格 —— 液态玻璃底栏、Preference 卡片、水滴选中态。
 *  - [MATERIAL]：标准 Material3 组件库。
 *
 * 新增皮肤时：在此追加枚举项并补上 [TinkerBoxTheme] 内的分发分支即可，配色与子功能无需改动。
 */
enum class StyleTheme(val displayName: String, val desc: String) {
    MIUI_X(
        "MIUI_X",
        "miuix 风格：悬浮液态玻璃胶囊底栏、更圆润的大圆角、水滴胶囊选中态，SukiSU 同款视觉效果。",
    ),
    MATERIAL(
        "Material",
        "标准 Material3：常规贴底纯色半透底栏、更方正的小圆角、主色药丸选中态。",
    ),
}

/** 该皮肤是否为液态玻璃风格（影响圆角/底栏形态等细节）。 */
val StyleTheme.isLiquidGlass: Boolean get() = this == StyleTheme.MIUI_X