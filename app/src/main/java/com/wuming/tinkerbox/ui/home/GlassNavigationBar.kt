package com.wuming.tinkerbox.ui.home

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wuming.tinkerbox.ui.theme.LocalEnableFloatingBottomBar
import com.wuming.tinkerbox.ui.theme.LocalEnableFloatingBottomBarBlur
import com.wuming.tinkerbox.ui.theme.LocalEnableNavigationBadge
import com.wuming.tinkerbox.ui.theme.LocalEnableRainbowSlider

/**
 * 底部导航栏（参考 SukiSU 观感）。
 *
 * 形态由独立子功能开关驱动（与风格解耦）：
 *  - [LocalEnableFloatingBottomBar]：悬浮 · 父级底栏透明、胶囊浮于页面上
 *                                      vs 贴底 · 导航栏整条铺满（含手势条区）到底部。
 *  - [LocalEnableFloatingBottomBarBlur]：液态玻璃（半透明 + 顶部高光） vs 高不透明实底。
 *  - [LocalEnableNavigationBadge]：按 [badges] 通用接口在图标右上角叠加角标。
 */
@Composable
fun GlassNavigationBar(
    items: List<GlassTab>,
    badges: List<Int> = List(items.size) { 0 },
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isFloating = LocalEnableFloatingBottomBar.current
    // 液态玻璃（透明/半透出下层）由开关独立控制，不与「悬浮」耦合：贴底也能做玻璃透明。
    val isGlass = LocalEnableFloatingBottomBarBlur.current
    val badgeEnabled = LocalEnableNavigationBadge.current
    // 普通选态滑块是否用彩虹渐变填充（主题子功能开关）。
    val rainbow = LocalEnableRainbowSlider.current

    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val base = MaterialTheme.colorScheme.surface
    // 液态玻璃：透明度显著更低，让背后的页面渐变真正透出「玻璃」观感；关闭则回归近实底。
    val bg = base.copy(alpha = if (isGlass) 0.30f else 0.97f)
    val contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    val pillColor = MaterialTheme.colorScheme.primary
    val pillText = Color.White
    // 选中滑块前景：彩虹填充时背景偏亮，改用深色文字/图标保证可读。
    val fgColor = if (rainbow) Color(0xE8000000) else pillText
    // 彩虹渐变：水平七色过渡，覆盖整个选态滑块。
    val rainbowBrush = Brush.horizontalGradient(
        listOf(
            Color(0xFFFF5E5E), Color(0xFFFFA43B), Color(0xFFFFE033), Color(0xFF5CE07A),
            Color(0xFF48C6F0), Color(0xFF7A7CFF), Color(0xFFDE6BFF),
        ),
    )
    val lensHigh = if (isDark) Color(0x26FFFFFF) else Color(0x55FFFFFF)
    // 液态玻璃：胶囊叠一层「冷白高光 → 主色透出」的剔透渐变，开启时体积感/玻璃感明显区别于关闭。
    val glassBg = Brush.verticalGradient(
        listOf(
            Color.White.copy(alpha = if (isDark) 0.22f else 0.30f),
            MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
            MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.22f else 0.16f),
        ),
    )
    // 玻璃拟态边缘高光：极细半透明白边，模拟磨砂材质的边缝折射。
    val lensEdge = if (isDark) Color(0x30FFFFFF) else Color(0x78FFFFFF)
    // 底部内阴影：叠加于高光之下，配合顶部亮边形成“凸起胶囊”的立体感。
    val lensInnerSunken = if (isDark) Color(0x1A000000) else Color(0x0A000000)

    // 悬浮 → 胶囊全圆角；贴底 → 顶圆角、底部贴地直落，配合手势条区铺满整条。
    val pillShape = if (isFloating) {
        RoundedCornerShape(28.dp)
    } else {
        RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp)
    }
    // 选中滑块造型：比标签更大更圆润，渲染半径大、居中盖住标签（标签本身保持紧凑不变）。
    val selShape = RoundedCornerShape(if (isFloating) 26.dp else 14.dp)

    // 手势条高度：悬浮时胶囊下方透出导航条/页面；贴底时该区域并入导航栏实底铺到底。
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Column(modifier = modifier.fillMaxWidth()) {
        // —— 悬浮胶囊 / 贴底主条 ——
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (isFloating) {
                        Modifier
                            .padding(horizontal = 26.dp)
                            .padding(bottom = 14.dp)
                            .shadow(
                                elevation = 16.dp,
                                shape = pillShape,
                                ambientColor = if (isDark) Color(0x66000000) else Color(0x33000000),
                                spotColor = if (isDark) Color(0x66000000) else Color(0x33000000),
                            )
                    } else Modifier,
                )
                .clip(pillShape)
                .background(if (isGlass && isFloating) glassBg else SolidColor(bg))
                .then(
                    if (isGlass && isFloating) {
                        // 液态玻璃：顶部高光渐变，营造透镜/磨砂质感，让背后内容上下透出。
                        Modifier
                            .background(Brush.verticalGradient(listOf(lensHigh, Color.Transparent)))
                            // 底部内阴影：胶囊下缘轻微沉入，增强凸起立体感。
                            .background(Brush.verticalGradient(listOf(Color.Transparent, lensInnerSunken)))
                    } else Modifier,
                )
                .then(
                    // 玻璃拟态边缘高光：极细半透明白边（悬浮胶囊更明显，贴底也保留轻微描边）。
                    Modifier.border(1.dp, lensEdge, pillShape),
                )
                .padding(horizontal = 8.dp, vertical = if (isFloating) 8.dp else 6.dp),
        ) {
            // 外层容器：各 item 居中排布，选中态用「弹性放大滑块」高亮。
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    items.forEachIndexed { index, item ->
                        val selected = index == selectedIndex
                    // 图标轻量缩放反馈。
                    val iconScale by animateFloatAsState(
                        targetValue = if (selected) 1.12f else 1f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                        label = "navIconScale",
                    )
                    // 普通选态滑块：关闭液态玻璃时用「原位弹出 + 弹性回弹」。
                    val pillProgress by animateFloatAsState(
                        targetValue = if (selected) 1f else 0f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow,
                        ),
                        label = "navPillProgress",
                    )
                    Box(
                        modifier = Modifier
                            .clickable { onSelect(index) }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        // 普通选态滑块（关闭液态玻璃时）：独立放大渲染背景，标签保持紧凑不随之伸缩。
                        // matchParentSize 垫底 + graphicsLayer 放大绘制、圆角更大：渲染半径大但不变更布局约束（避免约束溢出崩溃）。
                        if (!isGlass && pillProgress > 0.01f) {
                            Box(
                                Modifier
                                    .matchParentSize()
                                    .graphicsLayer {
                                        alpha = pillProgress
                                        scaleX = 0.9f + 0.2f * pillProgress
                                        scaleY = 0.9f + 0.2f * pillProgress
                                    }
                                    .clip(selShape)
                                    .background(if (rainbow) rainbowBrush else SolidColor(pillColor)),
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = item.label,
                                    tint = if (selected) fgColor else contentColor,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .graphicsLayer {
                                            scaleX = iconScale
                                            scaleY = iconScale
                                        },
                                )
                                // 角标：通用接口 [badges] + 开关，红底数字标。
                                if (badgeEnabled && (badges.getOrNull(index) ?: 0) > 0) {
                                    val count = badges[index].coerceAtMost(99)
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .offset(x = 6.dp, y = (-6).dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFE5484D))
                                            .padding(horizontal = 4.dp, vertical = 1.dp),
                                    ) {
                                        Text(
                                            text = if (count > 9) "9+" else count.toString(),
                                            color = Color.White,
                                            fontSize = 9.sp,
                                            lineHeight = 10.sp,
                                        )
                                    }
                                }
                            }
                            if (selected) {
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = item.label,
                                    color = fgColor,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }
            }
            }
        }
        // —— 手势条区域 ——
        // 悬浮：父级透明，露出页面/系统导航条，胶囊像浮在页面上；贴底：并入导航栏实底铺到底。
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .height(bottomInset)
                .background(if (isFloating) Color.Transparent else bg),
        )
    }
}

/** 导航单项描述。 */
data class GlassTab(
    val label: String,
    val icon: ImageVector,
)