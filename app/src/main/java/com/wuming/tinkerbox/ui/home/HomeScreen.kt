package com.wuming.tinkerbox.ui.home

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wuming.tinkerbox.core.mode.PermissionMode
import com.wuming.tinkerbox.ui.freeze.AppManageScreen
import com.wuming.tinkerbox.ui.freeze.AppManageViewModel
import com.wuming.tinkerbox.ui.monitor.MonitorScreen
import com.wuming.tinkerbox.ui.screen.SettingsScreen

/**
 * 底部导航骨架（P0）。
 *
 * 导航项：监控台 / 应用管理 / 场景 / 模块 / 设置
 * 应用管理（应用冻结/停用）已入驻「应用管理」tab（当前为首个可用功能）。
 */
@Composable
fun HomeScreen(
    mode: PermissionMode,
    modeViewModel: com.wuming.tinkerbox.ui.screen.ModeViewModel,
) {
    var tab by remember { mutableStateOf(0) }
    val items = remember { HomeTab.entries }
    val app = LocalContext.current.applicationContext as Application

    Scaffold(
        bottomBar = {
            // 悬浮毛玻璃导航栏（悬浮于内容之上、不贴边）。
            GlassNavigationBar(
                items = items.map { GlassTab(it.label, it.icon) },
                selectedIndex = tab,
                onSelect = { tab = it },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (items[tab]) {
                HomeTab.MONITOR -> MonitorScreen(mode)
                HomeTab.TUNE -> AppManageTab(app, mode)
                HomeTab.SCENES -> Placeholder("场景", "游戏 / 均衡 / 省电场景守护（开发中）")
                HomeTab.MODULES -> Placeholder("模块", "KSU / Magisk 模块管理（开发中）")
                HomeTab.SETTINGS -> SettingsScreen(modeViewModel = modeViewModel)
            }
        }
    }
}

@Composable
private fun AppManageTab(app: Application, mode: PermissionMode) {
    val vm: AppManageViewModel = viewModel(
        key = "app-manage-$mode",
        factory = AppManageViewModelFactory(app, mode),
    )
    AppManageScreen(viewModel = vm)
}

@Composable
private fun Placeholder(title: String, desc: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            Text(
                desc,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(12.dp),
            )
        }
    }
}

private enum class HomeTab(val label: String, val icon: ImageVector) {
    MONITOR("监控台", Icons.Filled.Speed),
    TUNE("应用管理", Icons.Filled.Build),
    SCENES("场景", Icons.Filled.Style),
    MODULES("模块", Icons.Filled.Widgets),
    SETTINGS("设置", Icons.Filled.Settings),
}

private class AppManageViewModelFactory(
    private val app: Application,
    private val mode: PermissionMode,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return AppManageViewModel(app, mode) as T
    }
}