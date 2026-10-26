package com.wuming.tinkerbox.ui.monitor

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
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
import androidx.compose.ui.platform.LocalDensity
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
import com.wuming.tinkerbox.core.monitor.GpuState
import com.wuming.tinkerbox.core.monitor.CpuClusterState
import com.wuming.tinkerbox.core.monitor.DeviceInfo
import com.wuming.tinkerbox.core.monitor.MemoryState
import com.wuming.tinkerbox.core.monitor.NetState
import com.wuming.tinkerbox.core.monitor.PowerState
import com.wuming.tinkerbox.core.monitor.ThermalState
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
    // 每块硬件独立订阅各自的 StateFlow：
    // 某块没更新时不会触发整棵重组，只有变了的那张卡片动，其余卡片原样保留。
    val cpuState by viewModel.cpuState.collectAsStateWithLifecycle()
    val gpuState by viewModel.gpuState.collectAsStateWithLifecycle()
    val tempState by viewModel.tempState.collectAsStateWithLifecycle()
    val memState by viewModel.memState.collectAsStateWithLifecycle()
    val powerState by viewModel.powerState.collectAsStateWithLifecycle()
    val netState by viewModel.netState.collectAsStateWithLifecycle()
    val deviceState by viewModel.deviceState.collectAsStateWithLifecycle()
    val processState by viewModel.processState.collectAsStateWithLifecycle()
    val maxTemp = tempState.maxOfOrNull { it.temperatureC }
    var selectedEntry by remember { mutableStateOf<ProcessEntry?>(null) }

    // 惰性列表：卡片 / 进程行都是 LazyColumn 的 item，屏幕外的项不组合、不测量、不绘制。
    // 监控台内容很高（SoC+CPU+GPU+温度+电池+网络+80 行进程），若用 Column+verticalScroll，
    // 滚出屏幕的项仍全在组合树里，滚动时每帧都得测量/绘制这一大堆 →"界面里上下滑动就卡"。
    // 改 LazyColumn 后滚动只在可见项上工作，遍历量与视口成正比，不再与总行数成正比。
    // 其中进程行按 pid 作 key：3 秒发布时只有 CPU% 变化的那几行重组，其余原样复用。
    // 动态读取系统 insets（随各机型状态栏/手势条高度自适应，非写死）：
    // 顶部让出状态栏、底部让出手势条，否则最后一张卡会被手势条挡住。
    // 用 contentPadding 而非外层整体 padding：首屏标题初始停在状态栏下方（不重合），
    // 但向下滚动时内容可一路滚到状态栏后面，透过透明状态栏浮现（edge-to-edge 沉浸）。
    // 左右 16dp 仍需保留，防止内容贴屏边缘。
    val statusBars = WindowInsets.statusBars.getTop(LocalDensity.current)
    val navBars = WindowInsets.navigationBars.getBottom(LocalDensity.current)
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .consumeWindowInsets(WindowInsets.statusBars),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(
            top = with(LocalDensity.current) { statusBars.toDp() },
            bottom = with(LocalDensity.current) { navBars.toDp() + 12.dp },
        ),
    ) {
        // 顶部概览：温度徽章 + 簇数说明。
        item(key = "header") {
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
        }

        // 设备 SoC 卡片（置顶，先看型号），内置内存与进程总数的紧凑信息。
        item(key = "soc") { SocCard(device = deviceState, mem = memState) }

        // CPU 频率泳道卡片（独立成卡，不复用 SoC 卡片空间）。
        item(key = "cpu") { CpuFreqCard(clusters = cpuState) }

        // GPU 负载/频率卡片。
        item(key = "gpu") { GpuCard(gpu = gpuState) }
        item(key = "temp") { TempCard(temps = tempState) }

        // 电池 / 电流电压（对标 Scene）。
        item(key = "battery") { BatteryCard(power = powerState) }

        // 网络流量（实时收发速率 + 累计）。
        item(key = "net") { NetCard(net = netState) }

        // 进程占用：一张卡片 + 内部固定高度(320dp)的惰性列表。
        // 外层 LazyColumn 只感知这一张卡；内层 LazyColumn 惰性渲染 80 行 —— 既恢复"可滚动卡片"
        // 观感，又不全量组合：滚出内层视口的行不参与测量/绘制，3 秒发布也只重组变化的行。
        item(key = "process_card") {
            ProcessCard(
                count = processState.processCount,
                sortByCpu = processState.sortByCpu,
                mode = mode,
                processes = processState.processes,
                onToggleSort = { viewModel.toggleSort() },
                onSelect = { selectedEntry = it },
            )
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

/** 设备 SoC 卡片：型号/硬件/ABI/核心 + 内存。只读 device 与 mem 两块 state。 */
@Composable
private fun SocCard(device: DeviceInfo.DeviceConf, mem: MemoryState) {
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
            InfoRow("SoC", device.soc)
            InfoRow("硬件", device.hardware)
            InfoRow("ABI", device.abi)
            InfoRow("核心", "${device.cores}")
            MemoryLine(mem = mem)
        }
    }
}

/** CPU 频率泳道卡片：只读 cpu 这块 state，CPU 卡单独重组。 */
@Composable
private fun CpuFreqCard(clusters: List<CpuClusterState>) {
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
            if (clusters.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "未读取到 CPU 频率\n（设备可能不支持 cpufreq sysfs）",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                clusters.forEach { FreqBar(it) }
            }
        }
    }
}

/** 温度传感器卡片：只读 temp 这块 state。 */
@Composable
private fun TempCard(temps: List<ThermalState>) {
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
            if (temps.isEmpty()) {
                Text(
                    "未读取到温度",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                temps.take(10).forEach { t ->
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
}

/** 进程占用卡片：标题头 + 固定高度内惰性进程行。内层 LazyColumn 只组合可见行，惰性渲染 80 行。 */
@Composable
private fun ProcessCard(
    count: Int,
    sortByCpu: Boolean,
    mode: PermissionMode,
    processes: List<ProcessEntry>,
    onToggleSort: () -> Unit,
    onSelect: (ProcessEntry) -> Unit,
) {
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
                        if (sortByCpu) "按 CPU" else "按名称",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { onToggleSort() },
                    )
                    Text(
                        "进程 $count",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (processes.isEmpty()) {
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
                // 固定高度内的惰性列表：进程多时内部滚动，同时避免 80 行一次全量组合。
                // 按 pid 作 key：3 秒发布时只有 CPU% 变化的行重组，其余原样复用，滚动也只在可见行上工作。
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(
                        items = processes,
                        key = { it.pid },
                    ) { p ->
                        ProcessRow(entry = p, onClick = { onSelect(p) })
                    }
                }
            }
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

/** 电流格式化：负值=放电（带负号），正值=充电（带 + 号）；超 1024mA 进位到 A（均保留一位小数）。 */
private fun fmtCurrent(ma: Double): String {
    if (ma.isNaN()) return "未就绪"
    val abs = kotlin.math.abs(ma)
    val sign = if (ma < 0) "-" else "+"
    return if (abs >= 1024.0) {
        String.format(Locale.US, "%s%.1f A", sign, abs / 1024.0)
    } else {
        String.format(Locale.US, "%s%.1f mA", sign, abs)
    }
}

/** GPU 负载/频率卡片：负载进度条 + 当前频率 + min~max。 */
@Composable
private fun GpuCard(gpu: GpuState) {
    // 缓冲动画：负载与频率平滑过渡，避免每 1s 采样跳动突兀。
    // 用短 tween(150ms)：数据本身每秒才换一次，无需长补间；动画每帧都会 invalidate 这张卡，
    // 时长越短，滚动时它占据的"每帧重绘"时间窗越少，界面内上下滑动更跟手。
    val animatedLoad by animateFloatAsState(
        targetValue = gpu.progress,
        animationSpec = tween(durationMillis = 150),
        label = "gpuLoad",
    )
    val animatedCur by animateFloatAsState(
        targetValue = gpu.curMhz.toFloat(),
        animationSpec = tween(durationMillis = 150),
        label = "gpuCur",
    )
    val load = animatedLoad.coerceIn(0f, 1f)
    val barColor = when {
        load < 0.5f -> Color(0xFF7A7CFF)
        load < 0.8f -> Color(0xFFFB8C00)
        else -> Color(0xFFE53935)
    }

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
                    "GPU",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (!gpu.available && gpu.privilegedOnly) {
                    Text(
                        "实时负载/频率需 Root/Shizuku（本机 sysfs 受限）",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // 型号/厂商/分辨率/刷新率：公开 API 读出，普通模式即可见（对齐 AIDA64 无 root）。
            val gpuInfo = gpu.info
            if (gpuInfo.hasModel) {
                InfoRow("型号", gpuInfo.summary)
                if (gpuInfo.vendor.isNotBlank()) {
                    InfoRow("厂商", gpuInfo.vendor)
                }
            }
            when {
                // 取到实时频率/负载（开放机型无 root 即可，否则经特权）。
                gpu.available -> {
                    InfoRow("当前", fmtMhz(animatedCur.toDouble()))
                    if (gpu.maxMhz > 0.0) {
                        InfoRow("范围", "${fmtMhz(gpu.minMhz)} - ${fmtMhz(gpu.maxMhz)}")
                    }
                    // 负载进度条。
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surface),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(load)
                                .height(8.dp)
                                .background(
                                    Brush.horizontalGradient(listOf(barColor.copy(alpha = 0.6f), barColor)),
                                    RoundedCornerShape(4.dp),
                                ),
                        )
                    }
                    Text(
                        "负载 ${(load * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** 网络流量卡片：实时下行/上行速率 + 累计收发。 */
@Composable
private fun NetCard(net: NetState) {
    val downStr = fmtRate(net.rxRateBps)
    val upStr = fmtRate(net.txRateBps)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "网络流量",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NetMetric("↓ 下行", downStr, Color(0xFF1E88E5))
                NetMetric("↑ 上行", upStr, Color(0xFF5CE07A))
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NetMetric(
                    "累计接收",
                    fmtBytes(net.rxTotalBytes),
                    MaterialTheme.colorScheme.onSurface,
                )
                NetMetric(
                    "累计发送",
                    fmtBytes(net.txTotalBytes),
                    MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/** 单个网络速率指标列（带颜色箭头）。 */
@Composable
private fun NetMetric(label: String, value: String, color: Color) {
    Column(
        verticalArrangement = Arrangement.spacedBy(0.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color,
        )
    }
}

/** 频率格式化：<1000 → MHz，否则 → GHz。 */
private fun fmtMhz(mhz: Double): String {
    return if (mhz >= 1000f) String.format(Locale.US, "%.2fGHz", mhz / 1000f)
    else "${mhz.toInt()}MHz"
}

/** 速率格式化：字节/秒 → B/KB/MB/… 逐级（非零速率才带 /s 单位）。 */
private fun fmtRate(bps: Long): String {
    if (bps <= 0L) return "0 B/s"
    return fmtBytes(bps) + "/s"
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
                "${fmtBytes(used)} / ${fmtBytes(total)}",
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

/**
 * 字节数格式化为人性化单位（基于 1024）：B → KB → MB → GB → TB → PB → EB。
 * 超过当前单位下限即进位到下一档，保留 1 位小数；纯字节（<1024B）显示整数。
 */
private fun fmtBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB", "PB", "EB")
    var value = bytes.toDouble()
    var i = 0
    while (value >= 1024.0 && i < units.lastIndex) {
        value /= 1024.0
        i++
    }
    return if (i == 0) "$bytes ${units[i]}"
    else String.format(Locale.US, "%.1f %s", value, units[i])
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