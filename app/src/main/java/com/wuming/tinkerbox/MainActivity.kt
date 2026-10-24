package com.wuming.tinkerbox

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wuming.tinkerbox.ui.screen.MainScreen
import com.wuming.tinkerbox.ui.theme.TinkerBoxTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as TinkerBoxApp
        setContent {
            val themeMode by app.themeState.collectAsStateWithLifecycle()
            TinkerBoxTheme(themeMode = themeMode) {
                MainScreen()
            }
        }
    }
}