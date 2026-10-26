package com.wuming.tinkerbox.ui.freeze

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wuming.tinkerbox.core.apps.AppListItem
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
    var sortKey by remember { mutableStateOf(SortKey.NAME) }
    var ascending by remember { mutableStateOf(true) }
    var showSortMenu by remember { mutableStateOf(false) }
    val tabs = arrayOf("第三方", "系统", "已冻结")

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
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
            Box {
                IconButton(onClick = { showSortMenu = true }) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.Sort, contentDescription = "排序")
                }
                DropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = { showSortMenu = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("排序方式") },
                        enabled = false,
                        onClick = {},
                    )
                    SortKey.entries.forEach { key ->
                        DropdownMenuItem(
                            text = {
                                Text(if (key == sortKey) "${key.title} ✓" else key.title)
                            },
                            onClick = {
                                sortKey = key
                                showSortMenu = false
                            },
                        )
                    }
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = {
                            Text(if (ascending) "升序 ✓" else "降序 ✓")
                        },
                        onClick = {
                            ascending = !ascending
                            showSortMenu = false
                        },
                    )
                }
            }
        }

        PrimaryTabRow(selectedTabIndex = tabIndex) {
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
                        canSilentUninstall = viewModel.canSilentUninstall,
                        onSilentUninstall = viewModel::uninstall,
                        query = query,
                        onQueryChange = { query = it },
                        sortKey = sortKey,
                        ascending = ascending,
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
                        canSilentUninstall = viewModel.canSilentUninstall,
                        onSilentUninstall = viewModel::uninstall,
                        onModeClick = { app, mode -> pendingAction = PendingAction(app, mode) },
                        sortKey = sortKey,
                        ascending = ascending,
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

/** 未冻结应用列表（可冻结 + 打开/详情/卸载）。 */
@Composable
private fun AppList(
    apps: List<AppListItem>,
    isSystemTab: Boolean,
    canFreeze: Boolean,
    canSilentUninstall: Boolean,
    onSilentUninstall: (String) -> Boolean,
    onModeClick: (AppListItem, FreezeMode) -> Unit,
    sortKey: SortKey,
    ascending: Boolean,
) {
    if (apps.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("暂无${if (isSystemTab) "系统" else "第三方"}应用可管理")
        }
        return
    }
    val sorted = remember(apps, sortKey, ascending) {
        sortApps(apps, sortKey, ascending)
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().navigationBarsPadding(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(sorted, key = { it.packageName }) { app ->
            AppRowItem(
                app = app,
                canFreeze = canFreeze,
                canSilentUninstall = canSilentUninstall,
                onSilentUninstall = onSilentUninstall,
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
    canSilentUninstall: Boolean,
    onSilentUninstall: (String) -> Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    sortKey: SortKey,
    ascending: Boolean,
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

    val filtered = remember(apps, query, sortKey, ascending) {
        val f = if (query.isBlank()) apps
        else apps.filter {
            it.label.contains(query, ignoreCase = true) ||
                it.packageName.contains(query, ignoreCase = true)
        }
        sortApps(f, sortKey, ascending)
    }

    if (filtered.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(if (apps.isEmpty()) "当前没有已冻结的应用" else "无匹配结果")
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().navigationBarsPadding(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(filtered, key = { it.packageName }) { app ->
            FrozenRow(
                app = app,
                canFreeze = canFreeze,
                canSilentUninstall = canSilentUninstall,
                onSilentUninstall = onSilentUninstall,
                onUnfreezeClick = { onUnfreezeClick(app) },
                onModeClick = { },
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
    canSilentUninstall: Boolean,
    onSilentUninstall: (String) -> Boolean,
    onModeClick: (FreezeMode) -> Unit,
) {
    val context = LocalContext.current
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
            AppActionsMenu(
                app = app,
                canFreeze = canFreeze,
                canUninstall = canSilentUninstall || !app.isSystem,
                onOpen = { openApp(context, app) },
                onDetails = { showAppDetails(context, app) },
                onUninstall = { uninstallApp(context, app, canSilentUninstall, onSilentUninstall) },
                onUnfreezeClick = {
                    // 未冻结列表不会触发解冻；此处给冻结模式选择兜底
                    // （若应用意外处于冻结态，直接走解冻对话框语义）
                },
                onModeClick = onModeClick,
            )
        }
    }
}

private typealias OnModeClick = (FreezeMode) -> Unit
private typealias OnUnfreezeClick = () -> Unit

/** 已冻结应用行：显式展示状态，右侧提供「三点菜单」供解冻/打开/详情/卸载。 */
@Composable
private fun FrozenRow(
    app: AppListItem,
    canFreeze: Boolean,
    canSilentUninstall: Boolean,
    onSilentUninstall: (String) -> Boolean,
    onUnfreezeClick: () -> Unit,
    onModeClick: (FreezeMode) -> Unit,
) {
    val context = LocalContext.current
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
            AppActionsMenu(
                app = app,
                canFreeze = canFreeze,
                canUninstall = canSilentUninstall || !app.isSystem,
                onOpen = { openApp(context, app) },
                onDetails = { showAppDetails(context, app) },
                onUninstall = { uninstallApp(context, app, canSilentUninstall, onSilentUninstall) },
                onUnfreezeClick = onUnfreezeClick,
                onModeClick = onModeClick,
            )
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

/**
 * 应用行操作菜单（三点）。
 *
 * 所有模式可用：打开 / 详情 / 卸载（卸载依权限走静默或系统界面）。
 * 仅具备冻结能力时追加：解冻（若已冻结）/ 挂起 / 隐藏。
 */
@Composable
private fun AppActionsMenu(
    app: AppListItem,
    canFreeze: Boolean,
    canUninstall: Boolean,
    onOpen: () -> Unit,
    onDetails: () -> Unit,
    onUninstall: () -> Unit,
    onUnfreezeClick: () -> Unit,
    onModeClick: OnModeClick,
) {
    var menu by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { menu = true }) {
            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = "更多操作",
            )
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text("打开") },
                onClick = { menu = false; onOpen() },
            )
            DropdownMenuItem(
                text = { Text("详情") },
                onClick = { menu = false; onDetails() },
            )
            // 普通模式（无静默卸载权限）下，系统应用本就无法卸载，不提供「卸载」，避免误导。
            if (canUninstall) {
                DropdownMenuItem(
                    text = { Text("卸载") },
                    onClick = { menu = false; onUninstall() },
                )
            }
            if (canFreeze) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                if (app.isFrozen) {
                    DropdownMenuItem(
                        text = { Text("解冻") },
                        onClick = { menu = false; onUnfreezeClick() },
                    )
                } else {
                    FreezeMode.entries.forEach { mode ->
                        DropdownMenuItem(
                            text = { Text("${mode.title} ${mode.desc}") },
                            onClick = { menu = false; onModeClick(mode) },
                        )
                    }
                }
            }
        }
    }
}

/** 打开应用（主启动器）。 */
private fun openApp(context: Context, app: AppListItem) {
    val launch = context.packageManager.getLaunchIntentForPackage(app.packageName)
    if (launch != null) {
        runCatching { context.startActivity(launch) }
    }
}

/** 前往系统应用信息页。 */
private fun showAppDetails(context: Context, app: AppListItem) {
    runCatching {
        val i = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${app.packageName}"),
        )
        context.startActivity(i)
    }
}

/** 卸载：有权限则静默 pm 卸载（结果走命令日志）；无权限则按应用类别顺畅降级到系统界面。 */
private fun uninstallApp(
    context: Context,
    app: AppListItem,
    canSilent: Boolean,
    onSilentUninstall: (String) -> Boolean,
) {
    // 1) 有权静默卸载：直接执行，成功即返回
    if (canSilent) {
        val ok = onSilentUninstall(app.packageName)
        Log.i("TinkerBoxUninstall", "canSilent=true result=$ok pkg=${app.packageName}")
        if (ok) return
    } else {
        Log.i("TinkerBoxUninstall", "canSilent=false pkg=${app.packageName} isSystem=${app.isSystem}")
    }

    // 2) 无权静默 / 静默失败：走系统界面降级。
    //    - 系统应用：普通模式本无卸载权限（即使声明 REQUEST_DELETE_PACKAGES 也卸不了系统应用），
    //      跳详情页观察，避免误导。
    //    - 三方应用：调起系统确认卸载器（ACTION_UNINSTALL_PACKAGE）。
    //      ⚠ 依赖 manifest 已声明 android.permission.REQUEST_DELETE_PACKAGES，
    //      否则 ColorOS 等的定卸载器会静默丢弃（需系统判定调用者有「请求删除」资格）。
    if (app.isSystem) {
        Log.i("TinkerBoxUninstall", "系统应用，跳详情页而非卸载器")
        runCatching { showAppDetails(context, app) }
        return
    }

    runCatching {
        val intent = Intent(
            Intent.ACTION_UNINSTALL_PACKAGE,
            Uri.parse("package:${app.packageName}"),
        )
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        Log.i("TinkerBoxUninstall", "startActivity ACTION_UNINSTALL_PACKAGE pkg=${app.packageName}")
        context.startActivity(intent)
    }.onFailure { e ->
        Log.e("TinkerBoxUninstall", "ACTION_UNINSTALL_PACKAGE 启动失败: ${e.javaClass.name}: ${e.message}", e)
        // 部分 ROM 无独立卸载器 Activity：兜底跳详情页，由系统详情页内的「卸载」按钮完成
        runCatching { showAppDetails(context, app) }
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
                TextButton(onClick = { onConfirmed(action.mode ?: FreezeMode.HIDE) }) {
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
                TextButton(onClick = { onConfirmed(action.mode ?: FreezeMode.HIDE) }) {
                    Text(if (action.currentlyFrozen) "确认解冻" else "确认冻结")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("取消") }
            },
        )
    }
}

/** 应用列表排序维度。 */
private enum class SortKey(val title: String) {
    NAME("按名称"),
    INSTALL_TIME("按安装时间"),
    SIZE("按占用空间"),
}

/** 按 [SortKey] 与升降序对应用列表排序。 */
private fun sortApps(apps: List<AppListItem>, key: SortKey, ascending: Boolean): List<AppListItem> {
    val sorted = when (key) {
        SortKey.NAME -> apps.sortedBy { it.label.lowercase() }
        SortKey.INSTALL_TIME -> apps.sortedBy { it.firstInstallTime }
        SortKey.SIZE -> apps.sortedBy { it.sizeBytes }
    }
    return if (ascending) sorted else sorted.reversed()
}