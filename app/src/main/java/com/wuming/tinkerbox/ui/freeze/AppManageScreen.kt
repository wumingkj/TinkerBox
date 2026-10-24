package com.wuming.tinkerbox.ui.freeze

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wuming.tinkerbox.core.apps.AppListItem
import com.wuming.tinkerbox.core.apps.CommandLog
import com.wuming.tinkerbox.core.apps.FreezeMode

/**
 * 应用冻结 / 解冻管理页。
 *
 * 只有 Shizuku / Root 模式可冻结；普通模式展示只读列表 + 能力说明。
 * 系统应用冻结需要双层确认。
 *
 * 三个 Tab：第三方 / 系统 / 已冻结。前两个支持搜索与完整冻结模式选择。
 *
 * 页面内容本身不包含 Scaffold / 顶栏，由外层导航容器统一提供。
 */
@Composable
fun AppManageScreen(viewModel: AppManageViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // 待处理动作：pkg / mode / 是否解冻
    var pendingAction by remember { mutableStateOf<PendingAction?>(null) }

    var tabIndex by remember { mutableStateOf(0) }
    var query by remember { mutableStateOf("") }
    var showLog by remember { mutableStateOf(false) }
    val tabs = arrayOf("第三方", "系统", "已冻结")

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!state.canFreeze) {
                Text(
                    modifier = Modifier.weight(1f).padding(vertical = 10.dp),
                    text = "当前「普通模式」无 shell / root 权限，无法冻结应用。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    modifier = Modifier.weight(1f).padding(vertical = 10.dp),
                    text = "执行身份：${state.freezerLabel}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            IconButton(onClick = viewModel::load) {
                Icon(imageVector = Icons.Filled.Refresh, contentDescription = "刷新")
            }
            IconButton(onClick = { showLog = true }) {
                Icon(imageVector = Icons.Filled.List, contentDescription = "命令日志")
            }
        }

        TabRow(selectedTabIndex = tabIndex) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = tabIndex == index,
                    onClick = { tabIndex = index },
                    text = { Text(title) },
                )
            }
        }

        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("加载中…")
            }
            else -> when (tabIndex) {
                2 -> {
                    // 已冻结：搜索 + 一键解冻 + 列表
                    FrozenPanel(
                        apps = state.frozenApps,
                        canFreeze = state.canFreeze,
                        query = query,
                        onQueryChange = { query = it },
                        onUnfreezeAll = viewModel::unfreezeAll,
                        onUnfreezeClick = { app ->
                            pendingAction = PendingAction(app, mode = null, currentlyFrozen = true)
                        },
                    )
                }
                else -> {
                    SearchBox(query = query, onQueryChange = { query = it })
                    val base = if (tabIndex == 0) state.thirdPartyApps else state.systemApps
                    val filtered = remember(base, query) {
                        if (query.isBlank()) base
                        else base.filter {
                            it.label.contains(query, ignoreCase = true) ||
                                it.packageName.contains(query, ignoreCase = true)
                        }
                    }
                    AppList(
                        apps = filtered,
                        isSystemTab = tabIndex == 1,
                        canFreeze = state.canFreeze,
                        onModeClick = { app, mode -> pendingAction = PendingAction(app, mode) },
                    )
                }
            }
        }
    }

    pendingAction?.let { action ->
        FreezeConfirmDialog(
            action = action,
            onDismiss = { pendingAction = null },
            onConfirmed = { mode ->
                if (action.currentlyFrozen) viewModel.unfreeze(action.pkg)
                else viewModel.freeze(action.pkg, mode)
                pendingAction = null
            },
        )
    }

    if (showLog) {
        CommandLogDialog(onClose = { showLog = false })
    }
}

/** 搜索框。 */
@Composable
private fun SearchBox(query: String, onQueryChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        placeholder = { Text("搜索应用…") },
        leadingIcon = {
            Icon(imageVector = Icons.Filled.Search, contentDescription = null)
        },
        singleLine = true,
    )
}

/** 未冻结应用列表（可冻结）。 */
@Composable
private fun AppList(
    apps: List<AppListItem>,
    isSystemTab: Boolean,
    canFreeze: Boolean,
    onModeClick: (AppListItem, FreezeMode) -> Unit,
) {
    if (apps.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("暂无${if (isSystemTab) "系统" else "第三方"}应用可管理")
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(apps, key = { it.packageName }) { app ->
            AppRowItem(
                app = app,
                canFreeze = canFreeze,
                onModeClick = { mode -> onModeClick(app, mode) },
            )
        }
    }
}

/** 已冻结面板：搜索 + 一键解冻 + 列表。 */
@Composable
private fun FrozenPanel(
    apps: List<AppListItem>,
    canFreeze: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onUnfreezeAll: () -> Unit,
    onUnfreezeClick: (AppListItem) -> Unit,
) {
    SearchBox(query = query, onQueryChange = onQueryChange)
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            modifier = Modifier.weight(1f),
            text = "已冻结 ${apps.size} 个",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (canFreeze && apps.isNotEmpty()) {
            TextButton(onClick = onUnfreezeAll) { Text("全部解冻") }
        }
    }

    val filtered = remember(apps, query) {
        if (query.isBlank()) apps
        else apps.filter {
            it.label.contains(query, ignoreCase = true) ||
                it.packageName.contains(query, ignoreCase = true)
        }
    }

    if (filtered.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(if (apps.isEmpty()) "当前没有已冻结的应用" else "无匹配结果")
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(filtered, key = { it.packageName }) { app ->
            FrozenRow(
                app = app,
                canFreeze = canFreeze,
                onUnfreezeClick = { onUnfreezeClick(app) },
            )
        }
    }
}

/** 一次待确认的冻结/解冻动作。 */
private data class PendingAction(
    val pkg: String,
    val label: String,
    val isSystem: Boolean,
    val mode: FreezeMode?,
    val currentlyFrozen: Boolean = false,
) {
    constructor(
        app: AppListItem,
        mode: FreezeMode?,
        currentlyFrozen: Boolean = false,
    ) : this(
        pkg = app.packageName,
        label = app.label,
        isSystem = app.isSystem,
        mode = mode,
        currentlyFrozen = currentlyFrozen,
    )
}

@Composable
private fun AppRowItem(
    app: AppListItem,
    canFreeze: Boolean,
    onModeClick: (FreezeMode) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIcon(app.icon)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (canFreeze) {
                Spacer(Modifier.width(12.dp))
                AppFreezeMenu(onModeClick = onModeClick)
            }
        }
    }
}

/** 已冻结应用行：显式展示状态并提供「解冻」按钮。 */
@Composable
private fun FrozenRow(
    app: AppListItem,
    canFreeze: Boolean,
    onUnfreezeClick: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIcon(app.icon)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (canFreeze) {
                Spacer(Modifier.width(12.dp))
                TextButton(onClick = onUnfreezeClick) { Text("解冻") }
            }
        }
    }
}

/** 应用图标占位 / 展示。 */
@Composable
private fun AppIcon(icon: androidx.compose.ui.graphics.ImageBitmap?) {
    val shape = MaterialTheme.shapes.medium
    if (icon != null) {
        Image(
            bitmap = icon,
            contentDescription = null,
            modifier = Modifier.size(40.dp).clip(shape),
        )
    } else {
        Box(
            modifier = Modifier.size(40.dp).clip(shape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "•",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AppFreezeMenu(onModeClick: (FreezeMode) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Button(onClick = { menu = true }) { Text("冻结") }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            FreezeMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = { Text("${mode.title} ${mode.desc}") },
                    onClick = {
                        menu = false
                        onModeClick(mode)
                    },
                )
            }
        }
    }
}

/**
 * 冻结确认对话框。
 * 系统应用双层确认；三方应用单层确认。作用是决定冻结模式并通知执行。
 */
@Composable
private fun FreezeConfirmDialog(
    action: PendingAction,
    onDismiss: () -> Unit,
    onConfirmed: (FreezeMode) -> Unit,
) {
    // 系统应用：step 0 动机说明 -> step 1 风险二次确认；三方直接执行。
    var step by remember(action) { mutableStateOf(0) }

    val title = if (action.currentlyFrozen) "解冻 ${action.label}" else "冻结 ${action.label}"

    if (!action.isSystem) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(title) },
            text = {
                Text(
                    when {
                        action.currentlyFrozen -> "确定要解冻该应用吗？解冻后它将恢复可用。"
                        action.mode != null -> "将执行「${action.mode.title}」操作。\n\n${action.mode.desc}\n\n确定吗？"
                        else -> "确定要冻结该应用吗？"
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = { onConfirmed(action.mode ?: FreezeMode.DISABLE_USER) }) {
                    Text(if (action.currentlyFrozen) "解冻" else "冻结")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("取消") }
            },
        )
        return
    }

    // 系统应用双层
    when (step) {
        0 -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(title) },
            text = {
                Text(
                    if (action.currentlyFrozen) {
                        "确定要解冻该系统应用吗？解冻后它将重新出现在桌面。"
                    } else {
                        "您正在操作一个系统应用。\n\n将使用「${action.mode?.title ?: "禁用"}」方式（${action.mode?.desc ?: ""}）。确定继续吗？"
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = { step = 1 }) {
                    Text(if (action.currentlyFrozen) "继续解冻" else "下一步")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("取消") }
            },
        )
        1 -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("二次确认") },
            text = {
                Text("系统应用可能为系统组件所依赖。冻结它可能导致部分功能失效或异常。\n\n确认后立即执行。")
            },
            confirmButton = {
                TextButton(onClick = { onConfirmed(action.mode ?: FreezeMode.DISABLE_USER) }) {
                    Text(if (action.currentlyFrozen) "确认解冻" else "确认冻结")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("取消") }
            },
        )
    }
}

/** 命令日志对话框：实时展示所有已执行命令、退出码与输出。 */
@Composable
private fun CommandLogDialog(onClose: () -> Unit) {
    val entries by CommandLog.entries.collectAsStateWithLifecycle()

    AlertDialog(
        onDismissRequest = onClose,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("命令日志", modifier = Modifier.weight(1f))
                TextButton(onClick = { CommandLog.clear() }) { Text("清空") }
            }
        },
        text = {
            if (entries.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("暂无命令记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth().height(380.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    entries.reversed().forEach { e ->
                        LogRow(entry = e)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onClose) { Text("关闭") }
        },
    )
}

/** 单条日志行。 */
@Composable
private fun LogRow(entry: com.wuming.tinkerbox.core.apps.LogEntry) {
    val color = if (entry.ok) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.error
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp)) {
            Text(
                text = "${entry.time}  exit=${entry.exitCode}",
                style = MaterialTheme.typography.labelSmall,
                color = if (entry.ok) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
            Text(
                text = entry.command,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
            )
            if (entry.stdout.isNotBlank()) {
                Text(
                    text = entry.stdout,
                    style = MaterialTheme.typography.bodySmall,
                    color = color,
                )
            }
            if (entry.stderr.isNotBlank() && entry.stderr != entry.stdout) {
                Text(
                    text = "stderr: ${entry.stderr}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}