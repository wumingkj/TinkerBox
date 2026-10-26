package com.wuming.tinkerbox.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 沉浸式（edge-to-edge）可滚动容器的统一封装。
 *
 * 目标：状态栏保持透明，任何有较长内容的可滚动页面，滚动时内容能一路滑到
 * 屏幕最顶、透过透明状态栏浮现（与监控台观感一致）。
 *
 * 关键：状态栏避让【不】压在外层/全局（那种会让内容永远停在状态栏下方滚不上去），
 * 而是通过滚动内容顶部的一段动态占位实现 —— 首屏内容正好停在状态栏下方（不重合），
 * 向下滚动时占位随之滚走，后续内容就能滚进状态栏背后的绘制区。
 *
 * 顶部间距只由顶部 Spacer 控制（默认即状态栏高度，紧贴、不自带额外余量），
 * horizontalPadding 仅作用于左右两侧，绝不叠加到顶部 —— 保证所有页面顶部
 * 与监控台（界面1）一致地紧贴，不会出现"离顶太远"。
 *
 * 结构：顶部 Spacer 与内容【分属两层】。因为 verticalArrangement 的 spacedBy(16)
 * 会把它作用到的相邻子项之间都插入间距，若 Spacer 和内容同层，状态栏占位与首个
 * 内容之间也会被插入 16dp，导致顶部又被顶下去一截（监控台用 LazyColumn contentPadding，
 * 其 spacedBy 只作用 item 间、不作用 padding 边缘，故无此问题）。这里把 Spacer 放外层、
 * 内容放内层子 Column，spacing 只在内容元素之间生效，顶部保持紧贴。
 *
 * 之后新增任何可滚动页面，直接使用本容器，无需再手动适配状态栏。
 */

private val statusBarTop: Dp
    @Composable get() =
        with(LocalDensity.current) { WindowInsets.statusBars.getTop(this).toDp() }

private val navigationBarBottom: Dp
    @Composable get() =
        with(LocalDensity.current) { WindowInsets.navigationBars.getBottom(this).toDp() }

/**
 * 普通 Column + verticalScroll 版本（内容块较少、非逐条大列表时用）。
 *
 * 替换原本的：
 *   Column(
 *       modifier = Modifier.fillMaxSize().verticalScroll(...),
 *   ) { ... }
 * 为：
 *   EdgeToEdgeScrollColumn(
 *       modifier = Modifier.fillMaxSize(),
 *       horizontalPadding = 16.dp,
 *       verticalArrangement = Arrangement.spacedBy(16.dp),
 *   ) { ... }
 */
@Composable
fun EdgeToEdgeScrollColumn(
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 0.dp,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    extraTopPadding: Dp = 0.dp,
    extraBottomPadding: Dp = 12.dp,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = horizontalPadding)
            .consumeWindowInsets(WindowInsets.statusBars),
    ) {
        // 顶部占位：首屏紧贴状态栏下方（不自带额外间距），滚动时随内容滚走实现穿透。
        Spacer(modifier = Modifier.height(statusBarTop + extraTopPadding))
        // 内容放内层子 Column：spacedBy 只在内容元素之间生效，不作用于上方状态栏占位。
        Column(
            verticalArrangement = verticalArrangement,
            horizontalAlignment = horizontalAlignment,
        ) { content() }
        // 底部占位：防最后一项被手势条/导航条遮挡。
        Spacer(modifier = Modifier.height(navigationBarBottom + extraBottomPadding))
    }
}