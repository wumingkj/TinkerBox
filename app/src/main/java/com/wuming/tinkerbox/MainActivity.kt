package com.wuming.tinkerbox

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wuming.tinkerbox.ui.screen.MainScreen
import com.wuming.tinkerbox.ui.theme.TinkerBoxTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 启用 edge-to-edge：内容延伸到系统栏之后，状态栏/导航栏背景透明。
        // 这样底部的悬浮玻璃胶囊才真正浮在页面之上，而不被系统手势/导航栏的白色底栏顶住。
        enableEdgeToEdge()
        val app = application as TinkerBoxApp
        setContent {
            val theme by app.themeState.collectAsStateWithLifecycle()
            val style by app.styleState.collectAsStateWithLifecycle()
            val enableBlur by app.enableBlur.collectAsStateWithLifecycle()
            val enableFloatingBar by app.enableFloatingBottomBar.collectAsStateWithLifecycle()
            val enableFloatingBarBlur by app.enableFloatingBottomBarBlur.collectAsStateWithLifecycle()
            val enableNavBadge by app.enableNavigationBadge.collectAsStateWithLifecycle()
            val enableRainbowSl by app.enableRainbowSlider.collectAsStateWithLifecycle()
            val pageScale by app.pageScale.collectAsStateWithLifecycle()
            TinkerBoxTheme(
                appTheme = theme,
                styleTheme = style,
                enableBlur = enableBlur,
                enableFloatingBottomBar = enableFloatingBar,
                enableFloatingBottomBarBlur = enableFloatingBarBlur,
                enableNavigationBadge = enableNavBadge,
                enableRainbowSlider = enableRainbowSl,
                pageScale = pageScale,
            ) {
                MainScreen()
            }
        }
    }
}