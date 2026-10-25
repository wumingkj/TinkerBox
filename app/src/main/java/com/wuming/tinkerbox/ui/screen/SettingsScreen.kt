@file:OptIn(ExperimentalMaterial3Api::class)

package com.wuming.tinkerbox.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wuming.tinkerbox.AppBadgeOption
import com.wuming.tinkerbox.TinkerBoxApp
import com.wuming.tinkerbox.core.mode.PermissionMode
import com.wuming.tinkerbox.core.monitor.BatteryCells
import com.wuming.tinkerbox.core.monitor.PowerConfig
import com.wuming.tinkerbox.ui.theme.ColorMode
import com.wuming.tinkerbox.ui.theme.ColorTheme
import com.wuming.tinkerbox.ui.theme.StyleTheme
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 设置页。
 *
 * 包含：
 *  1. 运行模式（下拉框选择，切换即时生效并持久化）。
 *  2. 主题（跟随系统 / 浅色 / 深色，全局生效并持久化）。
 */
@Composable
fun SettingsScreen(modeViewModel: ModeViewModel) {
    val state by modeViewModel.uiState.collectAsStateWithLifecycle()
    val app = LocalContext.current.applicationContext as TinkerBoxApp
    val theme by app.themeState.collectAsStateWithLifecycle()
    val style by app.styleState.collectAsStateWithLifecycle()
    val predictiveBack by app.predictiveBack.collectAsStateWithLifecycle()
    val blur by app.enableBlur.collectAsStateWithLifecycle()
    val floatingBar by app.enableFloatingBottomBar.collectAsStateWithLifecycle()
    val floatingBarBlur by app.enableFloatingBottomBarBlur.collectAsStateWithLifecycle()
    val navBadge by app.enableNavigationBadge.collectAsStateWithLifecycle()
    val rainbowSlider by app.enableRainbowSlider.collectAsStateWithLifecycle()
    val appBadgeOption by app.appBadgeOption.collectAsStateWithLifecycle()
    val pageScale by app.pageScale.collectAsStateWithLifecycle()

    // 设置页内的二级页面导航栈：进入时 push、返回时 pop，天然支持多级返回上级。
    val stack = remember { mutableStateListOf<SettingsSubPage>() }

    // 位于二级/三级页时，系统返回键应返回上级页面，而不是退出整个 App（回桌面）。
    BackHandler(enabled = stack.isNotEmpty()) {
        stack.removeAt(stack.lastIndex)
    }

    // 已进入二级页面：栈非空时展示栈顶页面；此时顶栏返回箭头同样 pop 上级。
    if (stack.isNotEmpty()) {
        val page = stack.last()
        when (page) {
            SettingsSubPage.THEME -> ThemeSettingsPage(
                themeMode = theme.colorMode,
                style = style,
                colorTheme = theme.colorTheme,
                predictiveBack = predictiveBack,
                blur = blur,
                floatingBar = floatingBar,
                floatingBarBlur = floatingBarBlur,
                navBadge = navBadge,
                rainbowSlider = rainbowSlider,
                pageScale = pageScale,
                badgeOption = appBadgeOption,
                onTheme = app::setColorMode,
                onStyle = app::setStyle,
                onColorTheme = app::setColorTheme,
                onPredictiveBack = app::setPredictiveBack,
                onBlur = app::setEnableBlur,
                onFloatingBar = app::setEnableFloatingBottomBar,
                onFloatingBarBlur = app::setEnableFloatingBottomBarBlur,
                onNavBadge = app::setEnableNavigationBadge,
                onPageScale = app::setPageScale,
                onAppBadgeOption = app::setAppBadgeOption,
                onRainbowSlider = app::setEnableRainbowSlider,
                onBack = { stack.removeAt(stack.lastIndex) },
            )
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionTitle("运行模式")

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ModeDropdown(
                    selected = state.mode ?: PermissionMode.NORMAL,
                    onSelect = modeViewModel::selectMode,
                )
                Text(
                    text = state.mode?.description ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, start = 4.dp),
                )
            }
        }

        // 主题入口：点击进入二级主题设置页
        SectionTitle("主题")
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { stack.add(SettingsSubPage.THEME) },
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "主题设置",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "${theme.colorMode.displayName} · ${style.displayName} · ${theme.colorTheme.displayName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        SectionTitle("电芯配置")
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            var cells by remember { mutableStateOf(BatteryCells.AUTO) }
            val scope = rememberCoroutineScope()
            LaunchedEffect(app) {
                PowerConfig.cellsFlow(app).collect { cells = it }
            }
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                CellsDropdown(
                    selected = cells,
                    onSelect = { selected ->
                        cells = selected
                        scope.launch { PowerConfig.saveCells(app, selected) }
                    },
                )
                Text(
                    text = cells.desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, start = 4.dp),
                )
            }
        }

        SectionTitle("关于")

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "TinkerBox",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "KSU / Magisk 内核调优与玩机工具箱。\n模式可在本页随时切换，切换后各模块按新权限能力矩阵运行。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ThemeSettingsPage(
    themeMode: ColorMode,
    style: StyleTheme,
    colorTheme: ColorTheme,
    predictiveBack: Boolean,
    blur: Boolean,
    floatingBar: Boolean,
    floatingBarBlur: Boolean,
    navBadge: Boolean,
    rainbowSlider: Boolean,
    pageScale: Float,
    badgeOption: AppBadgeOption,
    onTheme: (ColorMode) -> Unit,
    onStyle: (StyleTheme) -> Unit,
    onColorTheme: (ColorTheme) -> Unit,
    onPredictiveBack: (Boolean) -> Unit,
    onBlur: (Boolean) -> Unit,
    onFloatingBar: (Boolean) -> Unit,
    onFloatingBarBlur: (Boolean) -> Unit,
    onNavBadge: (Boolean) -> Unit,
    onPageScale: (Float) -> Unit,
    onAppBadgeOption: (AppBadgeOption) -> Unit,
    onRainbowSlider: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "返回",
                )
            }
            Spacer(Modifier.width(4.dp))
            Text(
                text = "主题设置",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }

        // 配色与风格：仅决定颜色与视觉语言，与下方子功能正交。
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ThemeDropdown(selected = themeMode, onSelect = onTheme)
                StyleDropdown(selected = style, onSelect = onStyle)
                Text(
                    text = style.desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp),
                )
                ColorThemeDropdown(selected = colorTheme, onSelect = onColorTheme)
                Text(
                    text = "颜色主题应用于全局主色与选中胶囊高亮。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }

        // 独立子功能：与风格完全解耦，miuix / material 任选风格下均可独立开关。
        SectionTitle("显示效果")
        SubFeatureSwitch(
            title = "毛玻璃模糊",
            desc = "对界面背景应用毛玻璃模糊效果。",
            checked = blur,
            onCheckedChange = onBlur,
        )
        SubFeatureSwitch(
            title = "悬浮底栏",
            desc = "底部导航栏悬浮于内容之上，不占布局高度。",
            checked = floatingBar,
            onCheckedChange = onFloatingBar,
        )
        SubFeatureSwitch(
            title = "液态玻璃",
            desc = "悬浮底栏叠加实时背景捕获与折射渲染（需开启悬浮底栏）。",
            checked = floatingBarBlur,
            enabled = floatingBar,
            onCheckedChange = onFloatingBarBlur,
        )
        SubFeatureSwitch(
            title = "导航栏角标",
            desc = "在导航栏图标上显示角标；应用管理的角标内容由下方「角标设置」选择。",
            checked = navBadge,
            onCheckedChange = onNavBadge,
        )
        SubFeatureSwitch(
            title = "彩虹滑块",
            desc = "选中滑块用彩虹渐变填充，关闭则使用当前主题主色。",
            checked = rainbowSlider,
            onCheckedChange = onRainbowSlider,
        )
        SectionTitle("角标设置")
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "应用管理角标",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "选择「应用管理」tab 显示的角标内容。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                AppBadgeOptionDropdown(
                    selected = badgeOption,
                    onSelect = onAppBadgeOption,
                )
            }
        }
        SubFeatureSwitch(
            title = "预见性返回手势",
            desc = "返回时显示系统预测动画与目标预览（需系统开发者选项同时开启）。",
            checked = predictiveBack,
            onCheckedChange = onPredictiveBack,
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                // 拖动期间只更新局部草稿值，界面保持不动（滑块实时跟手却不对齐失效）；
                // 松手（onValueChangeFinished）才提交真正生效。
                var draftScale by remember(pageScale) { mutableStateOf(pageScale) }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "界面缩放",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "按比例放大/缩小整体界面。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("80%", style = MaterialTheme.typography.bodySmall)
                    Slider(
                        value = draftScale,
                        onValueChange = { draftScale = it },
                        onValueChangeFinished = { onPageScale(draftScale) },
                        valueRange = 0.8f..1.2f,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    )
                    Text("120%", style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    text = "当前 ${((draftScale * 100).roundToInt())}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
        }
    }
}

/** 独立子功能开关行：优先级高于风格，material / miuix 风格下均可独立开关。 */
@Composable
private fun SubFeatureSwitch(
    title: String,
    desc: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = checked,
                enabled = enabled,
                onCheckedChange = onCheckedChange,
            )
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 4.dp),
    )
}

/** 运行模式下拉框（ExposedDropdown）。 */
@Composable
private fun ModeDropdown(
    selected: PermissionMode,
    onSelect: (PermissionMode) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = selected.displayName,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text("当前模式") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            PermissionMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = { Text(mode.displayName) },
                    onClick = {
                        expanded = false
                        onSelect(mode)
                    },
                )
            }
        }
    }
}

/** 「应用管理」角标下拉框（ExposedDropdown）。 */
@Composable
private fun AppBadgeOptionDropdown(
    selected: AppBadgeOption,
    onSelect: (AppBadgeOption) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = selected.label,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text("应用管理角标") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            AppBadgeOption.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    },
                )
            }
        }
    }
}

/** 主题下拉框（ExposedDropdown）。 */
@Composable
private fun ThemeDropdown(
    selected: ColorMode,
    onSelect: (ColorMode) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = selected.displayName,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text("主题") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            ColorMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = { Text(mode.displayName) },
                    onClick = {
                        expanded = false
                        onSelect(mode)
                    },
                )
            }
        }
    }
}

/** 风格主题下拉框（选择整体视觉语言）。 */
@Composable
private fun StyleDropdown(
    selected: StyleTheme,
    onSelect: (StyleTheme) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = selected.displayName,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text("风格") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            StyleTheme.entries.forEach { style ->
                DropdownMenuItem(
                    text = { Text(style.displayName) },
                    onClick = {
                        expanded = false
                        onSelect(style)
                    },
                )
            }
        }
    }
}

/** 颜色主题下拉框（选择品牌主色）。 */
@Composable
private fun ColorThemeDropdown(
    selected: ColorTheme,
    onSelect: (ColorTheme) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = selected.displayName,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text("颜色") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            ColorTheme.entries.forEach { color ->
                val swatch = color.seedColor
                DropdownMenuItem(
                    leadingIcon = {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(swatch),
                        )
                    },
                    text = { Text(color.displayName) },
                    onClick = {
                        expanded = false
                        onSelect(color)
                    },
                )
            }
        }
    }
}

/** 电芯配置下拉框（ExposedDropdown）。 */
@Composable
private fun CellsDropdown(
    selected: BatteryCells,
    onSelect: (BatteryCells) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = selected.displayName,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text("电芯配置") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            BatteryCells.entries.forEach { cells ->
                DropdownMenuItem(
                    text = { Text(cells.displayName) },
                    onClick = {
                        expanded = false
                        onSelect(cells)
                    },
                )
            }
        }
    }
}

/** 设置页内的可导航子页面。新增三级页时在此扩展枚举并在上层栈分发即可。 */
private enum class SettingsSubPage {
    THEME,
}