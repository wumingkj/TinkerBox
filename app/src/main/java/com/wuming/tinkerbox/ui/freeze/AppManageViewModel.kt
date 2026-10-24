package com.wuming.tinkerbox.ui.freeze

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wuming.tinkerbox.core.apps.AppFreezer
import com.wuming.tinkerbox.core.apps.AppListItem
import com.wuming.tinkerbox.core.apps.AppLister
import com.wuming.tinkerbox.core.apps.FreezeMode
import com.wuming.tinkerbox.core.apps.FrozenRepository
import com.wuming.tinkerbox.core.mode.PermissionMode
import com.wuming.tinkerbox.core.mode.ShizukuManager
import com.wuming.tinkerbox.core.root.RootDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 应用管理（冻结 / 解冻）ViewModel。
 *
 * 冻结能力随权限模式灰度：
 *  - 普通模式：不可冻结（无 shell/root），仅展示
 *  - Shizuku：  shell 身份执行 pm
 *  - Root：     su root 执行 pm（可操作系统应用）
 *
 * 系统应用冻结需要「双层确认」（一次确认冻结动机，一次确认风险）。
 */
class AppManageViewModel(
    app: Application,
    private val mode: PermissionMode,
) : AndroidViewModel(app) {

    private val freezer: AppFreezer? = when (mode) {
        PermissionMode.ROOT -> {
            if (RootDetector.detect() is com.wuming.tinkerbox.core.root.RootBackend.None) null
            else AppFreezer.forRoot()
        }
        PermissionMode.SHIZUKU -> {
            if (ShizukuManager.isGranted()) AppFreezer.forShizuku() else null
        }
        PermissionMode.NORMAL -> null
    }

    private val lister = AppLister(getApplication<Application>().packageManager)

    /** 本应用自身包名（debug 后缀已含），在列表中排除，避免误冻结自己。 */
    private val ownPackage = getApplication<Application>().packageName

    /** 持久化用户手动冻结的包集合（区别于系统检测的大量 disabled）。 */
    private val frozenRepo = FrozenRepository(getApplication())

    private val _uiState = MutableStateFlow(
        AppManageUiState(canFreeze = freezer != null, freezerLabel = mode.displayName),
    )
    val uiState: StateFlow<AppManageUiState> = _uiState.asStateFlow()

    private val _frozenNames = MutableStateFlow<Set<String>>(emptySet())
    val frozenNames: StateFlow<Set<String>> = _frozenNames.asStateFlow()

    init {
        load()
    }

    /** 刷新应用列表：先快速出列表（无图标），后台再补图标。
     *
     * 冻结状态完全由 SDK 直读（enabled / FLAG_SUSPENDED / privateFlags），
     * 不依赖 shell 命令——与 Hail 一致，可正确识别 ColorOS 上挂起的应用。
     */
    fun load() {
        viewModelScope.launch(Dispatchers.IO) {
            // 读取用户主动冻结记录，作为 disabled 判定白名单，
            // 过滤掉 ColorOS 智能停用闲置应用产生的大量 enabled=false 伪影。
            val userFrozen = runCatching { frozenRepo.frozenFlow.first() }.getOrDefault(emptySet())

            // 阶段一：快速出列表（不加载图标，立即展示）。
            val fast = runCatching { lister.load(withIcons = false, userFrozen = userFrozen) }
                .getOrDefault(emptyList())
                .filterNot { it.packageName == ownPackage }
            emit(applyAppState(fast))

            // 阶段二：后台补图标，再一次完整刷新。
            val full = runCatching { lister.load(withIcons = true, userFrozen = userFrozen) }
                .getOrDefault(emptyList())
                .filterNot { it.packageName == ownPackage }
            emit(applyAppState(full))
        }
    }

    private fun emit(s: AppManageUiState) {
        _frozenNames.value = s.frozenApps.map { it.packageName }.toSet()
        _uiState.value = s
    }

    /** 依据全量列表 + 真实冻结集合组装分区状态。 */
    private fun applyAppState(
        all: List<AppListItem>,
    ): AppManageUiState {
        val frozen = all.filter { it.isFrozen }.map { it.packageName }.toSet()
        val thirdParty = all.filter { !it.isSystem && !it.isFrozen }
        val systemApps = all.filter { it.isSystem && !it.isFrozen }
        val frozenApps = all.filter { it.isFrozen }
        return AppManageUiState(
            apps = all,
            thirdPartyApps = thirdParty,
            systemApps = systemApps,
            frozenApps = frozenApps,
            systemDisabled = frozen,
            canFreeze = freezer != null,
            freezerLabel = mode.displayName,
            loading = false,
        )
    }

    /** 应用是否为系统应用。 */
    fun isSystem(pkg: String): Boolean =
        _uiState.value.apps.firstOrNull { it.packageName == pkg }?.isSystem ?: false

    fun isFrozen(pkg: String): Boolean = _frozenNames.value.contains(pkg)

    /** 冻结（mode 决定具体 pm 命令）。冻结成功后写入持久化集合。 */
    fun freeze(pkg: String, mode: FreezeMode) {
        viewModelScope.launch(Dispatchers.IO) {
            val f = freezer ?: return@launch
            if (f.freeze(pkg, mode)) {
                runCatching { frozenRepo.add(pkg) }
            }
            load()
        }
    }

    /** 解冻。解冻成功后从持久化集合移除。 */
    fun unfreeze(pkg: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val f = freezer ?: return@launch
            if (f.unfreeze(pkg)) {
                runCatching { frozenRepo.remove(pkg) }
            }
            load()
        }
    }

    /** 一键解冻所有已冻结应用。 */
    fun unfreezeAll() {
        viewModelScope.launch(Dispatchers.IO) {
            val f = freezer ?: return@launch
            val frozen = _frozenNames.value
            frozen.forEach { pkg -> runCatching { f.unfreeze(pkg) } }
            runCatching { frozen.forEach { frozenRepo.remove(it) } }
            load()
        }
    }
}

data class AppManageUiState(
    val loading: Boolean = true,
    val apps: List<AppListItem> = emptyList(),
    val thirdPartyApps: List<AppListItem> = emptyList(),
    val systemApps: List<AppListItem> = emptyList(),
    val frozenApps: List<AppListItem> = emptyList(),
    val systemDisabled: Set<String> = emptySet(),
    val canFreeze: Boolean = false,
    val freezerLabel: String = "",
)