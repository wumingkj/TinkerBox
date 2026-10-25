package com.wuming.tinkerbox.ui.monitor

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.wuming.tinkerbox.core.mode.PermissionMode
import com.wuming.tinkerbox.core.monitor.MemoryState
import com.wuming.tinkerbox.core.monitor.PowerState
import java.util.Locale

/**
 * 监控台（P1 阶段 1 核心）。
 *
 * 只读实时数据，无需 Root，普通模式即可查看：
 *  - 顶部：整体最高温徽章
 *  - CPU：各簇频率泳道（按 max 频率自动分梯队，兼容骁龙/天机/展锐/瑞芯微）
 */
@Composable
fun MonitorScreen(
    mode: com.wuming.tinkerbox.core.mode.PermissionMode,
    viewModel: MonitorViewModel = monitorViewModel(mode),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val maxTemp = state.temps.maxOfOrNull { it.temperatureC }
    var selectedEntry by remember { mutableStateOf<ProcessEntry?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // 顶部概览：温度徽章 + 簇数说明。
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("实时监控", style = MaterialTheme.typography.titleLarge)
                Text(
                    "CPU · 温度 · 电池",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ThermalBadge(temperatureC = maxTemp)
        }

        // 设备 SoC 卡片（置顶，先看型号），内置内存与进程总数的紧凑信息。
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "CPU 型号",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                InfoRow("SoC", state.device.soc)
                InfoRow("硬件", state.device.hardware)
                InfoRow("ABI", state.device.abi)
                InfoRow("核心", "${state.device.cores}")
                MemoryLine(mem = state.mem)
            }
        }

        // CPU 频率泳道卡片（独立成卡，不复用 SoC 卡片空间）。
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Column(Modifier.padding(12.dp)) {
                Text(
                    "CPU 频率",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                if (state.clusters.isEmpty()) {
                    Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                        Text(
                            "未读取到 CPU 频率\n（设备可能不支持 cpufreq sysfs）",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    state.clusters.forEach { FreqBar(it) }
                }
            }
        }

        // 温度明细（可选）：列出部分最高温传感器。
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "温度传感器",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (state.temps.isEmpty()) {
                    Text(
                        "未读取到温度",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    state.temps.take(10).forEach { t ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                t.type,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                "${"%.2f".format(t.temperatureC)}°",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                            )
                        }
                    }
                }
            }
        }

        // 电池 / 电流电压（对标 Scene）。
        BatteryCard(power = state.power)

        // 进程占用（top 风格），列表弹在温度卡片之后。
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "CPU 占用 (Top)",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // 排序切换：CPU / 应用名
                            Text(
                                if (state.sortByCpu) "按 CPU" else "按名称",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable { viewModel.toggleSort() },
                            )
                            Text(
                                "进程 ${state.processCount}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                if (state.processes.isEmpty()) {
                    if (mode == PermissionMode.NORMAL) {
                        Text(
                            "普通模式受系统限制，无法读取完整进程列表\n（建议切换到 Shizuku / Root）",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            "正在采样进程…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    // 固定高度内的可滚动列表，进程多时出现滑动条，避免“看不全”。
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        state.processes.forEach { p ->
                            ProcessRow(entry = p, onClick = { selectedEntry = p })
                        }
                    }
                }
            }
        }

        // 选中进程后的操作菜单（进程级操作，与应用管理无关）。
        selectedEntry?.let { entry ->
            ProcessActionDialog(
                entry = entry,
                operateEnabled = viewModel.canOperate(),
                onStop = { viewModel.stopProcess(entry) },
                onDismiss = { selectedEntry = null },
            )
        }
    }
}

/** 进程列表单行：图标 + 应用名（副：包名） + CPU%；点击触发操作菜单。非应用进程无图标。 */
@Composable
private fun ProcessRow(entry: ProcessEntry, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (entry.icon != null) {
                Image(
                    bitmap = entry.icon,
                    contentDescription = entry.displayName,
                    modifier = Modifier.size(22.dp),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    entry.displayName,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                if (entry.isApp && entry.packageName != entry.displayName) {
                    Text(
                        entry.packageName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                entry.pid.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "%.1f%%".format(entry.cpuPct),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = if (entry.cpuPct > 30f) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** 进程操作弹窗：只提供"停止进程"，不触碰应用管理/冻结。普通模式仅提示无权限。 */
@Composable
private fun ProcessActionDialog(
    entry: ProcessEntry,
    operateEnabled: Boolean,
    onStop: () -> Boolean,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(entry.displayName) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (entry.isApp) {
                    Text("包名：${entry.packageName}", style = MaterialTheme.typography.bodySmall)
                } else {
                    Text("系统进程，不可操作", style = MaterialTheme.typography.bodySmall)
                }
                Text("PID：${entry.pid}    CPU：%.1f%%".format(entry.cpuPct),
                    style = MaterialTheme.typography.bodySmall)
                if (!operateEnabled) {
                    Text(
                        "普通模式无操作权限，请切换 Shizuku / Root",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (operateEnabled) {
                    TextButton(onClick = { onStop(); onDismiss() }) { Text("停止进程") }
                }
            }
        },
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** 电池 / 电流电压 / 功率卡片（对标 Scene 电池页）。 */
@Composable
private fun BatteryCard(power: PowerState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "电池 · 电流电压",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp, bottom = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 电量环示意：百分比 + 状态。
                Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    Text(
                        "${power.batteryLevel}%",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            power.batteryLevel <= 20 -> MaterialTheme.colorScheme.error
                            power.batteryLevel <= 50 -> Color(0xFFFB8C00)
                            else -> MaterialTheme.colorScheme.primary
                        },
                    )
                    Text(
                        power.statusText.ifEmpty { "未知" },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // 电压 / 电流 / 功率三列。双电芯时电压拆为电芯0/电芯1 两列。
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (power.isDualCell) {
                        val v0 = power.cellVoltages.getOrElse(0) { power.voltageV }
                        val v1 = power.cellVoltages.getOrElse(1) { power.voltageV }
                        PowerMetric("电芯0", String.format(Locale.US, "%.2fV", v0))
                        PowerMetric("电芯1", String.format(Locale.US, "%.2fV", v1))
                    } else {
                        PowerMetric("电压", String.format(Locale.US, "%.2fV", power.voltageV))
                    }
                    PowerMetric(
                        "电流",
                        if (power.hasCurrent) fmtCurrent(power.currentMa ?: 0.0) else "未就绪",
                        highlight = (power.currentMa ?: 0.0) < 0,
                    )
                    PowerMetric("功率", if (power.powerW > 0) String.format(Locale.US, "%.2fW", power.powerW) else "—")
                }
            }
        }
    }
}

/** 单个电流/电压/功率指标列。 */
@Composable
private fun PowerMetric(label: String, value: String, highlight: Boolean = false) {
    Column(
        verticalArrangement = Arrangement.spacedBy(0.dp),
        horizontalAlignment = Alignment.End,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = if (highlight) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** 电流格式化：负值=放电（带负号），正值=充电（带 + 号），末尾 mA；保留一位小数。 */
private fun fmtCurrent(ma: Double): String {
    if (ma.isNaN()) return "未就绪"
    val s = if (ma < 0) "%.1f" else "+%.1f"
    return String.format(Locale.US, s + " mA", ma)
}

/** 内存占用紧凑行：内存 + swap 占用率与进度条。 */
@Composable
private fun MemoryLine(mem: MemoryState) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // 内存：已用/可用 + 渐变进度条。
        MemProgressRow(
            label = "内存",
            used = mem.usedBytes,
            total = mem.totalBytes,
            pct = mem.usedPct,
        )
        // swap：有配置才展示。
        if (mem.swapTotalBytes > 0L) {
            val swapPct = (mem.swapTotalBytes - mem.swapFreeBytes).toFloat() / mem.swapTotalBytes
            MemProgressRow(
                label = "Swap",
                used = mem.swapTotalBytes - mem.swapFreeBytes,
                total = mem.swapTotalBytes,
                pct = swapPct,
            )
        }
    }
}

@Composable
private fun MemProgressRow(label: String, used: Long, total: Long, pct: Float) {
    val clamped = pct.coerceIn(0f, 1f)
    val barColor = when {
        clamped < 0.6f -> Color(0xFF1E88E5)
        clamped < 0.8f -> Color(0xFFFB8C00)
        else -> Color(0xFFE53935)
    }
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "$label ${(clamped * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "${fmtMb(used)} / ${fmtMb(total)}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.surface),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(clamped)
                    .height(6.dp)
                    .background(Brush.horizontalGradient(listOf(barColor.copy(alpha = 0.6f), barColor))),
            )
        }
    }
}

/** 字节数格式化为人性化单位（保留 1 位小数）。 */
private fun fmtMb(bytes: Long): String {
    return String.format(Locale.US, "%.1f", bytes / 1024.0 / 1024.0) + "MB"
}

/**
 * 按权限模式创建监听 ViewModel（key 含模式，切换模式会重建）。
 */
@Composable
private fun monitorViewModel(mode: PermissionMode): MonitorViewModel {
    val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as android.app.Application
    return viewModel(
        key = "monitor-$mode",
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return MonitorViewModel(app, mode) as T
            }
        },
    )
}