package com.wuming.tinkerbox.ui.monitor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * 温度徽章：按热度等级配色，展示整体最高温（摄氏度）。
 * null 表示未读到有效温度（显示 "--"）。
 */
@Composable
fun ThermalBadge(temperatureC: Double?) {
    if (temperatureC == null) {
        Column(
            modifier = Modifier
                .background(
                    MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(12.dp),
                )
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("--°", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("温度", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    val bg = backgroundColor(temperatureC)
    val fg = Color.White

    Column(
        modifier = Modifier
            .background(bg, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "${temperatureC.roundToInt()}°",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = fg,
        )
        Text(
            text = "温度",
            style = MaterialTheme.typography.bodySmall,
            color = fg.copy(alpha = 0.9f),
        )
    }
}

/** 按温度色阶渐变：正常(蓝绿) → 温和(黄) → 高热(橙) → 危险(红)。 */
internal fun backgroundColor(tempC: Double): Color = when {
    tempC < 40 -> Color(0xFF2E9E5B)
    tempC < 55 -> Color(0xFFF9A825)
    tempC < 70 -> Color(0xFFF57C00)
    else -> Color(0xFFE53935)
}