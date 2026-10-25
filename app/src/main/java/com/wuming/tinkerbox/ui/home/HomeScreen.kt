package com.wuming.tinkerbox.ui.home

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.background
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wuming.tinkerbox.AppBadgeOption
import com.wuming.tinkerbox.TinkerBoxApp
import com.wuming.tinkerbox.core.mode.PermissionMode
import com.wuming.tinkerbox.ui.freeze.AppManageScreen
import com.wuming.tinkerbox.ui.freeze.AppManageViewModel
import com.wuming.tinkerbox.ui.monitor.MonitorScreen
import com.wuming.tinkerbox.ui.screen.SettingsScreen
import com.wuming.tinkerbox.ui.theme.LocalEnableBlur
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

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
    // tab 放在绑定 Activity 的 ViewModel 里，而非 remember：切换皮肤（MiuixTheme 包裹有/无）会
    // 重建整棵子树导致 remember 清零回到监控台，用 ViewModel 可跨重建稳定保持当前页。
    val uiState: HomeScreenViewModel = viewModel()
    val tab by uiState.tab.collectAsStateWithLifecycle()
    val items = remember { HomeTab.entries }
    val app = LocalContext.current.applicationContext as TinkerBoxApp
    val scope = rememberCoroutineScope()

    // 桌面式横向拼接分页：页面可左右滑动切换（像桌面划动），最左侧接最右侧不循环。
    val pagerState = rememberPagerState(
        initialPage = tab,
        pageCount = { items.size },
    )
    // 页面滑动 → 同步底栏选中态（点击底栏则 animateScrollToPage 触发 currentPage 变化）。
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { uiState.tab.value = it }
    }
    val enableBlur = LocalEnableBlur.current
    // 角标数据源：应用管理 tab 的角标 = 已冻结应用数量（按角标设置里的应用管理选项过滤）。
    val badgeOption by app.appBadgeOption.collectAsStateWithLifecycle()
    val frozenThird by app.frozenThirdParty.collectAsStateWithLifecycle()
    val frozenSystem by app.frozenSystem.collectAsStateWithLifecycle()
    val appBadge = when (badgeOption) {
        AppBadgeOption.NONE -> 0
        AppBadgeOption.ALL -> frozenThird + frozenSystem
        AppBadgeOption.THIRD_PARTY -> frozenThird
        AppBadgeOption.SYSTEM -> frozenSystem
    }
    // 角标通用接口：仅「应用管理」按选项携带真实计数，其他 tab 一律无角标。
    val badges = listOf(0, appBadge, 0, 0, 0)

    // 返回键策略：默认回到首页监控台。当前不在监控台时，返回键不回桌面、先切回监控台，
    // 在监控台再按返回才正常退出（更高优先级的页内 BackHandler，如设置二级页，会先行接管）。
    BackHandler(enabled = tab != HomeTab.MONITOR.ordinal) {
        scope.launch { pagerState.animateScrollToPage(HomeTab.MONITOR.ordinal) }
    }

    // 供半透明玻璃 / 液态玻璃「透出的氛围墙纸」：柔和竖向渐变，让玻璃在纯背景上也能呈现透视层次。
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val wallpaper = Brush.verticalGradient(
        listOf(
            // 顶部轻微主色 → 中段纯净背景 → 底部主色渐深，让悬浮玻璃胶囊下方有明显色阶可透。
            MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
            MaterialTheme.colorScheme.primary.copy(alpha = 0.02f),
            MaterialTheme.colorScheme.background.copy(alpha = 0f),
            MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
        ),
    )
    // 毛玻璃氛围遮罩：开启后内容叠加一层透镜高光+柔光，配合渐变墙纸呈现磨砂玻璃观感。
    val glassOverlay = Brush.verticalGradient(
        listOf(
            if (isDark) Color(0x0DFFFFFF) else Color(0x2EFFFFFF),
            Color(0x00000000),
            if (isDark) Color(0x0DFFFFFF) else Color(0x1FFFFFFF),
        ),
    )

    Box(Modifier.fillMaxSize().background(wallpaper)) {
        // 内容区：直接铺满整屏（edge-to-edge 后一路延伸到手势条之后），并在其上叠一层毛玻璃氛围遮罩。
        // 页面背景一直到底，悬浮玻璃胶囊浮在这片页面之上；底部不再有一条「独立的背景色带」。
        // AGP9 / targetSdk 35+ 强制 edge-to-edge：系统栏不再自动避让，须用 insets 显式处理。
        Box(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding(),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(if (enableBlur) glassOverlay else SolidColor(Color.Transparent)),
            ) {
                // 桌面式横向拼接分页：页面左右滑动直接拼接切换，向左滑下一屏即从右侧推入。
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    when (items[page]) {
                        HomeTab.MONITOR -> MonitorScreen(mode)
                        HomeTab.TUNE -> AppManageTab(app, mode)
                        HomeTab.SCENES -> Placeholder("场景", "游戏 / 均衡 / 省电场景守护（开发中）")
                        HomeTab.MODULES -> Placeholder("模块", "KSU / Magisk 模块管理（开发中）")
                        HomeTab.SETTINGS -> SettingsScreen(modeViewModel = modeViewModel)
                    }
                }
            }
        }

        // 玻璃导航栏：覆盖在内容之上、不挤占布局。
        // 悬浮模式下父级（含手势条区）透明呈现胶囊，贴底模式下整条铺满到底。
        GlassNavigationBar(
            items = items.map { GlassTab(it.label, it.icon) },
            badges = badges,
            selectedIndex = tab,
            onSelect = { page -> scope.launch { pagerState.animateScrollToPage(page) } },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
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

/**
 * 首页 UI 状态宿主：与 Activity 的 ViewModelStore 绑定，皮肤/主题切换导致的子树重建不影响其存活。
 * 注意必须可被 Compose viewModel() 反射构造（无参构造可见），不能用 private 类。
 */
internal class HomeScreenViewModel : ViewModel() {
    val tab = MutableStateFlow(HomeTab.MONITOR.ordinal)
}