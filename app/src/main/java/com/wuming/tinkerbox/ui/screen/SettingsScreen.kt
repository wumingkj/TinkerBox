@file:OptIn(ExperimentalMaterial3Api::class)

package com.wuming.tinkerbox.ui.screen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.PredictiveBackHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.wuming.tinkerbox.AppBadgeOption
import com.wuming.tinkerbox.TinkerBoxApp
import com.wuming.tinkerbox.R
import com.wuming.tinkerbox.core.mode.PermissionMode
import com.wuming.tinkerbox.core.monitor.BatteryCells
import com.wuming.tinkerbox.core.monitor.PowerConfig
import com.wuming.tinkerbox.ui.common.EdgeToEdgeScrollColumn
import com.wuming.tinkerbox.ui.theme.ColorMode
import com.wuming.tinkerbox.ui.theme.ColorTheme
import com.wuming.tinkerbox.ui.theme.StyleTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import java.net.HttpURLConnection
import java.net.URL

/**
 * 设置页。
 *
 * 包含：
 *  1. 运行模式（下拉框选择，切换即时生效并持久化）。
 *  2. 主题（跟随系统 / 浅色 / 深色，全局生效并持久化）。
 */
@Composable
fun SettingsScreen(
    modeViewModel: ModeViewModel,
    // 上报「是否已进入二级/更高级页面」，宿主可据此收起全局底栏，让子页全屏显示。
    onNestedChanged: (Boolean) -> Unit = {},
) {
    val state by modeViewModel.uiState.collectAsStateWithLifecycle()
    val app = LocalContext.current.applicationContext as TinkerBoxApp
    val theme by app.themeState.collectAsStateWithLifecycle()
    val style by app.styleState.collectAsStateWithLifecycle()
    val predictiveBack by app.predictiveBack.collectAsStateWithLifecycle()
    val blur by app.enableBlur.collectAsStateWithLifecycle()
    val floatingBar by app.enableFloatingBottomBar.collectAsStateWithLifecycle()
    val floatingBarBlur by app.enableFloatingBottomBarBlur.collectAsStateWithLifecycle()
    val navBadge by app.enableNavigationBadge.collectAsStateWithLifecycle()
    val rainbowSlider by app.enableRainbowSlider.collectAsStateWithLifecycle()
    val appBadgeOption by app.appBadgeOption.collectAsStateWithLifecycle()
    val pageScale by app.pageScale.collectAsStateWithLifecycle()

    // 设置页内的二级页面导航栈：进入时 push、返回时 pop，天然支持多级返回上级。
    val stack = remember { mutableStateListOf<SettingsSubPage>() }

    // 栈非空（进入任意二级页）即通知宿主收起底栏；返回主页后恢复。切走再回时栈重建为空，
    // 该 LaunchedEffect 也会以非空=false 重新上报一次，保证宿主状态与页面一致。
    LaunchedEffect(stack.size) {
        onNestedChanged(stack.isNotEmpty())
    }

    // 位于二级/三级页时，系统返回键应返回上级页面，而不是退出整个 App（回桌面）。
    // 先 collect 进度流挂起，等系统提交（松手）后再 pop，避免不等松手就提前返回。
    PredictiveBackHandler(enabled = stack.isNotEmpty()) { progress ->
        progress.collect { }
        if (stack.isNotEmpty()) {
            stack.removeAt(stack.lastIndex)
        }
    }

    // 设置页导航：主页 + 二级页同一 AnimatedContent 承载，依据栈「前进/后退」做水平滑动过渡。
    // 原来用 if(stack 非空) + return 硬切，1→2 级进出无动画；改 AnimatedContent 后：
    //  - 前进（进二级页）：新页从右滑入，主页向左让位；
    //  - 后退（返回主页）：主页从左侧滑回，二级页向右退出。
    // 与系统/预测返回手势方向一致，消除硬切硬退的生硬感。
    // 注意：外层不再整体压 statusBarsPadding / navigationBarsPadding —— 那会让全部内容
    // 被限制在状态栏以下的区域内滚动，永远透不过透明状态栏。状态栏/底栏避让已统一下放到
    // 各子页的 EdgeToEdgeScrollColumn 里，滚动时内容可一路滑到屏幕顶透过状态栏浮现。
    AnimatedContent(
        targetState = stack.lastOrNull(),
        transitionSpec = {
            if (targetState != null && initialState == null) {
                // 前进：空栈 → 有子页
                (slideInHorizontally(animationSpec = tween(300)) { it } + fadeIn(tween(220)))
                    .togetherWith(slideOutHorizontally(tween(300)) { -it / 3 } + fadeOut(tween(160)))
            } else {
                // 后退：子页 → 空栈
                (slideInHorizontally(animationSpec = tween(300)) { -it / 3 } + fadeIn(tween(220)))
                    .togetherWith(slideOutHorizontally(tween(300)) { it } + fadeOut(tween(160)))
            }
        },
        modifier = Modifier.fillMaxSize(),
        label = "settings-nav",
    ) { page ->
        if (page == null) {
            SettingsHomePage(
                state = state,
                theme = theme,
                style = style,
                app = app,
                modeViewModel = modeViewModel,
                onOpenTheme = { stack.add(SettingsSubPage.THEME) },
                onOpenAbout = { stack.add(SettingsSubPage.ABOUT) },
            )
        } else {
            when (page) {
                SettingsSubPage.THEME -> ThemeSettingsPage(
                    themeMode = theme.colorMode,
                    style = style,
                    colorTheme = theme.colorTheme,
                    predictiveBack = predictiveBack,
                    blur = blur,
                    floatingBar = floatingBar,
                    floatingBarBlur = floatingBarBlur,
                    navBadge = navBadge,
                    rainbowSlider = rainbowSlider,
                    pageScale = pageScale,
                    badgeOption = appBadgeOption,
                    onTheme = app::setColorMode,
                    onStyle = app::setStyle,
                    onColorTheme = app::setColorTheme,
                    onPredictiveBack = app::setPredictiveBack,
                    onBlur = app::setEnableBlur,
                    onFloatingBar = app::setEnableFloatingBottomBar,
                    onFloatingBarBlur = app::setEnableFloatingBottomBarBlur,
                    onNavBadge = app::setEnableNavigationBadge,
                    onPageScale = app::setPageScale,
                    onAppBadgeOption = app::setAppBadgeOption,
                    onRainbowSlider = app::setEnableRainbowSlider,
                    onBack = { stack.removeAt(stack.lastIndex) },
                )
                SettingsSubPage.ABOUT -> AboutPage(
                    onBack = { stack.removeAt(stack.lastIndex) },
                )
            }
        }
    }
}

/** 设置主页（一级）：运行模式 / 主题入口 / 电芯配置 / 关于。作为 AnimatedContent 的一个内容态。 */
@Composable
private fun SettingsHomePage(
    state: ModeUiState,
    theme: com.wuming.tinkerbox.ui.theme.AppThemeState,
    style: StyleTheme,
    app: TinkerBoxApp,
    modeViewModel: ModeViewModel,
    onOpenTheme: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    EdgeToEdgeScrollColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalPadding = 16.dp,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionTitle("运行模式")

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ModeDropdown(
                    selected = state.mode ?: PermissionMode.NORMAL,
                    onSelect = modeViewModel::selectMode,
                )
                if (state.acquiring) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 4.dp, start = 4.dp),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                        )
                        Text(
                            text = "正在获取权限（请在弹窗中授权），若超时自动退回普通模式",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                } else {
                    Text(
                        text = state.mode?.description ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, start = 4.dp),
                    )
                }
            }
        }

        // 主题入口：点击进入二级主题设置页
        SectionTitle("主题")
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenTheme() },
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "主题设置",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "${theme.colorMode.displayName} · ${style.displayName} · ${theme.colorTheme.displayName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        SectionTitle("电芯配置")
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            var cells by remember { mutableStateOf(BatteryCells.AUTO) }
            val scope = rememberCoroutineScope()
            LaunchedEffect(app) {
                PowerConfig.cellsFlow(app).collect { cells = it }
            }
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                CellsDropdown(
                    selected = cells,
                    onSelect = { selected ->
                        cells = selected
                        scope.launch { PowerConfig.saveCells(app, selected) }
                    },
                )
                Text(
                    text = cells.desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, start = 4.dp),
                )
            }
        }

        // 关于入口：点击进入二级「关于」霓虹页（logo / 版本 / 检测更新 / 源码）。
        SectionTitle("关于")
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenAbout() },
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "关于 TinkerBox",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "版本信息 · 检测更新 · 查看源码",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 项目仓库地址（1.0.0 前为私有，仍可查 releases API）。 */
private const val GITHUB_REPO_URL = "https://github.com/wumingkj/TinkerBox"
private const val GITHUB_API_RELEASES = "https://api.github.com/repos/wumingkj/TinkerBox/releases/latest"

/** 「关于」二级页：霓虹动态 logo 区 + App 名/版本号 + 操作卡片。 */
@Composable
private fun AboutPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // 从 PackageManager 读当前版本号（无需开启 buildConfig 生成）。
    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "0.0.0"
    }
    // 版本检测状态：null=未检测 / ""=检测中 / 其他=远端最新版本号。
    var latest by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }
    val (checkDesc, checkTitle) = when {
        checking -> "正在拉取 GitHub 最新版本…" to "检测 App 版本更新"
        latest == null -> "检查是否有可用新版本" to "检测 App 版本更新"
        isNewerVersion(latest, versionName) -> "发现新版本 v$latest，可打开详情查看" to "检测到新版本 · v$latest"
        else -> "已是最新版本 v$versionName" to "App 已是最新版本"
    }

    EdgeToEdgeScrollColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalPadding = 16.dp,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "返回",
                )
            }
            Spacer(Modifier.width(4.dp))
            Text(
                text = "关于",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }

        // 炫彩霓虹 hero：动效 logo + 名称 + 版本号。
        NeonHero(versionName = versionName)

        // 操作卡片列：检测更新 / 查看源码 / 占位。
        AboutActionCard(
            title = checkTitle,
            desc = if (checking) "正在拉取 GitHub 最新版本…" else checkDesc,
            loading = checking,
            onClick = {
                if (!checking) {
                    checking = true
                    scope.launch {
                        latest = fetchLatestVersion()
                        checking = false
                        if (latest == null) showToast(context, "网络异常，请稍后重试")
                        else if (isNewerVersion(latest, versionName)) showToast(context, "发现新版本 v${latest}")
                        else showToast(context, "已是最新版本 v$versionName")
                    }
                }
            },
        )
        AboutActionCard(
            title = "去 GitHub 查看源码",
            desc = "TinkerBox",
            onClick = { openUrl(context, GITHUB_REPO_URL) },
        )
        AboutActionCard(
            title = "暂时还没想到",
            desc = "敬请期待",
            onClick = { showToast(context, "待开发") },
        )
    }
}

/** 霓虹 logo 区块：暗底 + 动态流光描边 + 脉动光晕 + 星星粒子。中上渲染 logo 图形，下方 App 名与版本号。 */
@Composable
private fun NeonHero(versionName: String) {
    val context = LocalContext.current
    val transition = rememberInfiniteTransition(label = "neon")
    // 0..1 无限循环：驱动流光扫过的位置、光晕呼吸、粒子旋转。
    val cycle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "neonCycle",
    )
    val pulse by transition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "neonPulse",
    )

    // 霓虹主色：色相随时间平移，蓝紫 → 品红 → 青 往复。
    val c1 = lerp(Color(0xFF00D2FF), Color(0xFF7A5CFF), (cycle * 1.5f).mod(1f))
    val c2 = lerp(Color(0xFFFF2D95), Color(0xFF00FFA3), (cycle * 1.5f).mod(1f))
    val glow = lerp(Color(0xFF7A5CFF), Color(0xFF00D2FF), pulse)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFF0B0E17)),
        contentAlignment = Alignment.Center,
    ) {
        // 星尘粒子层：随 cycle 缓慢自转。
        Canvas(Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            repeat(46) { i ->
                val angle = (cycle * 360f + i * 137.5f)
                val rad = (i.toFloat() % 9f + 4f) / 9f
                val r = rad * size.minDimension / 2f
                val px = cx + kotlin.math.cos(angle * 0.017f) * r
                val py = cy + kotlin.math.sin(angle * 0.017f) * r
                val tint = lerp(c1, c2, i % 3 / 3f)
                drawCircle(
                    color = tint.copy(alpha = 0.25f + pulse * 0.2f),
                    radius = 1.4f + (i % 3) * 0.6f,
                    center = androidx.compose.ui.geometry.Offset(px, py),
                )
            }
        }

        // 中心霓虹圆环：两圈，一圈呼吸放大、一圈旋转流光。
        Canvas(
            modifier = Modifier
                .size(140.dp)
                .blur(6.dp),
        ) {
            val stroke = 3.dp.toPx() * pulse
            drawCircle(color = glow.copy(alpha = 0.55f), radius = size.minDimension / 2, style = Stroke(stroke))
        }
        Canvas(
            modifier = Modifier.size(140.dp).blur(4.dp),
        ) {
            // 用旋转流光做霓虹切角描边。
            rotate(cycle * 360f, pivot = center) {
                drawArc(
                    brush = Brush.sweepGradient(listOf(c1, c2, c1)),
                    startAngle = 0f,
                    sweepAngle = 220f,
                    useCenter = false,
                    style = Stroke(width = 3.dp.toPx() * pulse, cap = StrokeCap.Round),
                    topLeft = androidx.compose.ui.geometry.Offset.Zero,
                    size = size,
                )
            }
        }
        // 圆环中心 logo 字母 T(B)，霓虹辉光字。
        Text(
            text = "T",
            fontSize = 64.sp,
            fontWeight = FontWeight.Black,
            color = Color.White,
            style = MaterialTheme.typography.headlineLarge.copy(
                fontSize = 64.sp,
                shadow = androidx.compose.ui.graphics.Shadow(glow, androidx.compose.ui.geometry.Offset.Zero, 28f),
            ),
        )

        // 底部信息：App 名 + 版本号。
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = context.getString(R.string.app_name),
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                ),
                color = Color.White,
            )
            Text(
                text = "玩机杂货铺 · v$versionName",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF9AA3B5),
            )
        }
    }
}

/** 关于页操作卡片。 */
@Composable
private fun AboutActionCard(
    title: String,
    desc: String,
    loading: Boolean = false,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !loading) { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(8.dp))
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 网络拉取 GitHub 最新 release 的版本号（tag_name），失败返回 null。 */
private suspend fun fetchLatestVersion(): String? = withContext(Dispatchers.IO) {
    runCatching {
        val conn = URL(GITHUB_API_RELEASES).openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 6000
        conn.readTimeout = 6000
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        conn.setRequestProperty("User-Agent", "TinkerBox")
        try {
            if (conn.responseCode != 200) return@withContext null
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            // 从 JSON 里提取 "tag_name":"v1.2.3"，私有仓库对 release 的 tag 依旧可读。
            val m = Regex("\"tag_name\"\\s*:\\s*\"([^\"]+)\"").find(body)
            val raw = m?.groupValues?.get(1)?.trim().orEmpty()
            raw.removePrefix("v")
        } finally {
            conn.disconnect()
        }
    }.getOrNull()?.takeIf { it.isNotBlank() }
}

/** 比较两个语义版本（x.y.z ...）：newer 严格大于 current 时为 true。 */
private fun isNewerVersion(newer: String?, current: String): Boolean {
    if (newer == null) return false
    if (newer == current) return false
    fun strip(s: String) = s.filter { it.isDigit() || it == '.' }
    val a = strip(newer).split('.').map { it.toIntOrNull() ?: 0 }
    val b = strip(current).split('.').map { it.toIntOrNull() ?: 0 }
    val max = maxOf(a.size, b.size)
    for (i in 0 until max) {
        val x = a.getOrElse(i) { 0 }
        val y = b.getOrElse(i) { 0 }
        if (x != y) return x > y
    }
    return false
}

@Composable
private fun ThemeSettingsPage(
    themeMode: ColorMode,
    style: StyleTheme,
    colorTheme: ColorTheme,
    predictiveBack: Boolean,
    blur: Boolean,
    floatingBar: Boolean,
    floatingBarBlur: Boolean,
    navBadge: Boolean,
    rainbowSlider: Boolean,
    pageScale: Float,
    badgeOption: AppBadgeOption,
    onTheme: (ColorMode) -> Unit,
    onStyle: (StyleTheme) -> Unit,
    onColorTheme: (ColorTheme) -> Unit,
    onPredictiveBack: (Boolean) -> Unit,
    onBlur: (Boolean) -> Unit,
    onFloatingBar: (Boolean) -> Unit,
    onFloatingBarBlur: (Boolean) -> Unit,
    onNavBadge: (Boolean) -> Unit,
    onPageScale: (Float) -> Unit,
    onAppBadgeOption: (AppBadgeOption) -> Unit,
    onRainbowSlider: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    EdgeToEdgeScrollColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalPadding = 16.dp,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "返回",
                )
            }
            Spacer(Modifier.width(4.dp))
            Text(
                text = "主题设置",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }

        // 配色与风格：仅决定颜色与视觉语言，与下方子功能正交。
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ThemeDropdown(selected = themeMode, onSelect = onTheme)
                StyleDropdown(selected = style, onSelect = onStyle)
                Text(
                    text = style.desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp),
                )
                ColorThemeDropdown(selected = colorTheme, onSelect = onColorTheme)
                Text(
                    text = "颜色主题应用于全局主色与选中胶囊高亮。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }

        // 独立子功能：与风格完全解耦，miuix / material 任选风格下均可独立开关。
        SectionTitle("显示效果")
        SubFeatureSwitch(
            title = "毛玻璃模糊",
            desc = "对界面背景应用毛玻璃模糊效果。",
            checked = blur,
            onCheckedChange = onBlur,
        )
        SubFeatureSwitch(
            title = "悬浮底栏",
            desc = "底部导航栏悬浮于内容之上，不占布局高度。",
            checked = floatingBar,
            onCheckedChange = onFloatingBar,
        )
        SubFeatureSwitch(
            title = "液态玻璃",
            desc = "悬浮底栏叠加实时背景捕获与折射渲染（需开启悬浮底栏）。",
            checked = floatingBarBlur,
            enabled = floatingBar,
            onCheckedChange = onFloatingBarBlur,
        )
        SubFeatureSwitch(
            title = "导航栏角标",
            desc = "在导航栏图标上显示角标；应用管理的角标内容由下方「角标设置」选择。",
            checked = navBadge,
            onCheckedChange = onNavBadge,
        )
        SubFeatureSwitch(
            title = "彩虹滑块",
            desc = "选中滑块用彩虹渐变填充，关闭则使用当前主题主色。",
            checked = rainbowSlider,
            onCheckedChange = onRainbowSlider,
        )
        SectionTitle("角标设置")
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "应用管理角标",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "选择「应用管理」tab 显示的角标内容。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                AppBadgeOptionDropdown(
                    selected = badgeOption,
                    onSelect = onAppBadgeOption,
                )
            }
        }
        SubFeatureSwitch(
            title = "预见性返回手势",
            desc = "返回时显示系统预测动画与目标预览（需系统开发者选项同时开启）。",
            checked = predictiveBack,
            onCheckedChange = onPredictiveBack,
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                // 拖动期间只更新局部草稿值，界面保持不动（滑块实时跟手却不对齐失效）；
                // 松手（onValueChangeFinished）才提交真正生效。
                var draftScale by remember(pageScale) { mutableStateOf(pageScale) }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "界面缩放",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "按比例放大/缩小整体界面。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("80%", style = MaterialTheme.typography.bodySmall)
                    Slider(
                        value = draftScale,
                        onValueChange = { draftScale = it },
                        onValueChangeFinished = { onPageScale(draftScale) },
                        valueRange = 0.8f..1.2f,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    )
                    Text("120%", style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    text = "当前 ${((draftScale * 100).roundToInt())}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
        }
    }
}

/** 独立子功能开关行：优先级高于风格，material / miuix 风格下均可独立开关。 */
@Composable
private fun SubFeatureSwitch(
    title: String,
    desc: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = checked,
                enabled = enabled,
                onCheckedChange = onCheckedChange,
            )
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 4.dp),
    )
}

/** 运行模式下拉框（ExposedDropdown）。 */
@Composable
private fun ModeDropdown(
    selected: PermissionMode,
    onSelect: (PermissionMode) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = selected.displayName,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text("当前模式") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            PermissionMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = { Text(mode.displayName) },
                    onClick = {
                        expanded = false
                        onSelect(mode)
                    },
                )
            }
        }
    }
}

/** 「应用管理」角标下拉框（ExposedDropdown）。 */
@Composable
private fun AppBadgeOptionDropdown(
    selected: AppBadgeOption,
    onSelect: (AppBadgeOption) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = selected.label,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text("应用管理角标") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            AppBadgeOption.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    },
                )
            }
        }
    }
}

/** 主题下拉框（ExposedDropdown）。 */
@Composable
private fun ThemeDropdown(
    selected: ColorMode,
    onSelect: (ColorMode) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = selected.displayName,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text("主题") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            ColorMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = { Text(mode.displayName) },
                    onClick = {
                        expanded = false
                        onSelect(mode)
                    },
                )
            }
        }
    }
}

/** 风格主题下拉框（选择整体视觉语言）。 */
@Composable
private fun StyleDropdown(
    selected: StyleTheme,
    onSelect: (StyleTheme) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = selected.displayName,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text("风格") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            StyleTheme.entries.forEach { style ->
                DropdownMenuItem(
                    text = { Text(style.displayName) },
                    onClick = {
                        expanded = false
                        onSelect(style)
                    },
                )
            }
        }
    }
}

/** 颜色主题下拉框（选择品牌主色）。 */
@Composable
private fun ColorThemeDropdown(
    selected: ColorTheme,
    onSelect: (ColorTheme) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = selected.displayName,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text("颜色") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            ColorTheme.entries.forEach { color ->
                val swatch = color.seedColor
                DropdownMenuItem(
                    leadingIcon = {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(swatch),
                        )
                    },
                    text = { Text(color.displayName) },
                    onClick = {
                        expanded = false
                        onSelect(color)
                    },
                )
            }
        }
    }
}

/** 电芯配置下拉框（ExposedDropdown）。 */
@Composable
private fun CellsDropdown(
    selected: BatteryCells,
    onSelect: (BatteryCells) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = selected.displayName,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text("电芯配置") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            BatteryCells.entries.forEach { cells ->
                DropdownMenuItem(
                    text = { Text(cells.displayName) },
                    onClick = {
                        expanded = false
                        onSelect(cells)
                    },
                )
            }
        }
    }
}

/** 设置页内的可导航子页面。新增三级页时在此扩展枚举并在上层栈分发即可。 */
private enum class SettingsSubPage {
    THEME,
    ABOUT,
}

/** 弹出 Toast 提示。 */
@Suppress("DEPRECATION")
private fun showToast(context: Context, message: String) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}

/** 用系统浏览器打开链接。 */
private fun openUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}