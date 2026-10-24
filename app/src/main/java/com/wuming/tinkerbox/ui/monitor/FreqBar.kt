package com.wuming.tinkerbox.ui.monitor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.wuming.tinkerbox.core.monitor.CpuClusterState
import java.util.Locale

/**
 * 单条 CPU 簇频率泳道。
 *
 * 上方标题行：簇名 + 核列表 + governor + 当前频率；
 * 下方为渐变进度条（当前/最小~最大频率之间的进度）。
 */
@Composable
fun FreqBar(cluster: CpuClusterState) {
    val min = cluster.minMhz
    val max = cluster.maxMhz

    // 缓冲动画：频率与进度都做平滑过渡，避免每 1s 采样跳动突兀。
    val animatedProgress by animateFloatAsState(
        targetValue = cluster.progress,
        animationSpec = tween(durationMillis = 600),
        label = "freqProgress",
    )
    val animatedCur by animateFloatAsState(
        targetValue = cluster.curMhz.toFloat(),
        animationSpec = tween(durationMillis = 600),
        label = "freqCur",
    )
    val cur = animatedCur.toDouble()
    val progress = animatedProgress

    // 频率越高色温越暖，直观体现负载。
    val barColor = if (progress < 0.5f) {
        Color(0xFF1E88E5)
    } else if (progress < 0.8f) {
        Color(0xFFFB8C00)
    } else {
        Color(0xFFE53935)
    }

    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = cluster.name,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = curStr(cur),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                color = barColor,
            )
        }

        Text(
            text = "核 [${cluster.cores.joinToString("·")}] · ${cluster.governor} · ${curStr(min)} - ${curStr(max)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp),
        )

        // 进度条轨道。
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .background(
                    MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(4.dp),
                ),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .height(8.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(barColor.copy(alpha = 0.6f), barColor),
                        ),
                        RoundedCornerShape(4.dp),
                    ),
            )
        }
    }
}

private fun curStr(mhz: Double): String {
    val ghz = mhz / 1000.0
    return String.format(Locale.US, "%.2f", ghz) + "GHz"
}