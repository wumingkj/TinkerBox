package com.wuming.tinkerbox.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.wuming.tinkerbox.core.mode.CapabilityState
import com.wuming.tinkerbox.core.mode.PermissionMode

/**
 * 首次启动（或上次权限失效回退）引导：选择权限模式。
 * 每个模式卡片展示该模式对应的实时检测结果，供用户判断后再选择。
 */
@Composable
fun ModeSelectionScreen(
    capabilities: Map<PermissionMode, CapabilityState>,
    onSelect: (PermissionMode) -> Unit,
) {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "选择运行模式",
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = "已为你检测各模式可用状态，根据实际情况选择。选择后可随时在设置中切换。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PermissionMode.entries.forEach { mode ->
                ModeCard(
                    mode = mode,
                    capability = capabilities[mode],
                    onClick = { onSelect(mode) },
                )
            }
        }
    }
}

@Composable
private fun ModeCard(
    mode: PermissionMode,
    capability: CapabilityState?,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 420.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = mode.displayName,
                    style = MaterialTheme.typography.titleLarge,
                )
                capability?.let { CapabilityBadge(it) }
            }
            Text(
                text = mode.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("使用 ${mode.displayName}")
            }
        }
    }
}

/** 模式可用状态徽章：绿色可用 / 红色不可用，附详情。 */
@Composable
private fun CapabilityBadge(state: CapabilityState) {
    val (bg, fg) = if (state.available) {
        Color(0xFF2E7D32) to Color.White
    } else {
        Color(0xFFC62828) to Color.White
    }
    Column(horizontalAlignment = Alignment.End) {
        Text(
            text = if (state.available) "可用" else "不可用",
            modifier = Modifier
                .background(bg, RoundedCornerShape(50))
                .padding(horizontal = 10.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelMedium,
            color = fg,
        )
        state.detail?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = if (state.available) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}