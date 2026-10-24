package com.wuming.tinkerbox.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 悬浮毛玻璃（液态玻璃）底部导航栏。
 *
 * 设计（对标"透明悬浮 + 液态玻璃"）：
 *  - 胶囊形态：整体圆角 28dp、与底部留出悬浮间距、不贴边。
 *  - 半透明玻璃底：以主题 surface 色叠 30~50% 半透明白，配合自下而上的
 *    亮度渐变，让中心略通透、两端似有透镜高光；透出后方内容的模糊轮廓。
 *  - 高光描边：顶部描一层细辉光白，强化玻璃厚度。
 *  - 选中态：胶囊内再嵌一个高对比"药丸"，带投影浮起，图标旁展开文字。
 *
 * 玻璃配色直接用 MaterialTheme 当前 colorScheme 派生：
 *  - 浅色主题 → 亮玻璃（白偏高透明）
 *  - 深色主题 → 暗玻璃（深色半透 + 亮描边）
 * 主题切换（浅/深/跟随系统）即时生效，无需额外状态。
 */
@Composable
fun GlassNavigationBar(
    items: List<GlassTab>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    // ---------- 玻璃调色板（由主题明暗自适应） ----------
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val glassSurface = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)
    val lensHigh = if (isDark) Color(0x55FFFFFF) else Color(0x66FFFFFF)
    val lensLow = if (isDark) Color(0x11000000) else Color(0x40FFFFFF)
    val contentColor = if (isDark) Color(0xFFB9C6D6) else Color(0xFF5A6B7E)
    val pillColor = MaterialTheme.colorScheme.primary
    val pillText = Color.White
    val shadowColor = if (isDark) Color(0x66000000) else Color(0x33000000)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .shadow(
                elevation = 18.dp,
                shape = RoundedCornerShape(30.dp),
                ambientColor = shadowColor,
                spotColor = shadowColor,
            )
            .clip(RoundedCornerShape(30.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(lensHigh, lensLow),
                )
            )
            .background(glassSurface)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEachIndexed { index, item ->
            val selected = index == selectedIndex
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(22.dp))
                    .clickable { onSelect(index) }
                    .background(
                        if (selected) pillColor else Color.Transparent
                    )
                    .padding(horizontal = if (selected) 14.dp else 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = item.label,
                    tint = if (selected) pillText else contentColor,
                    modifier = Modifier.size(20.dp),
                )
                if (selected) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = item.label,
                        color = pillText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

/** 导航单项描述。 */
data class GlassTab(
    val label: String,
    val icon: ImageVector,
)