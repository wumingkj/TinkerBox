package com.wuming.tinkerbox.ui.screen

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wuming.tinkerbox.core.mode.CapabilityState
import com.wuming.tinkerbox.core.mode.ModeRepository
import com.wuming.tinkerbox.core.mode.PermissionChecker
import com.wuming.tinkerbox.core.mode.PermissionMode
import com.wuming.tinkerbox.core.root.RootBackend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 应用级状态：管理权限模式 + 各模式能力检测。
 *
 * 启动逻辑：
 *  - 读取已保存的模式，并检测各模式当前是否可用。
 *  - 若已保存的模式所需权限已失效（如 Root 掉了），回退到未选择状态（[ModeUiState.mode] = null），
 *    跳回模式选择界面；普通模式始终可用，不受影响。
 *
 * 运行中全局降级：
 *  - 后台定时校验当前模式所需权限；若失效（root 被关 / adb 掉线），
 *    自动持久化降到更低一档仍可用的模式（Root→Shizuku→普通），
 *    让设置页「运行模式」与所有 tab 一并感知，而不是只在单个功能内局部降。
 */
class ModeViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = ModeRepository(app.applicationContext)
    private val checker = PermissionChecker(app.applicationContext)

    /** 全局降级轮询间隔（毫秒）。 */
    private val degradeIntervalMs = 3500L

    private val _uiState = MutableStateFlow(ModeUiState())
    val uiState: StateFlow<ModeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            // 并行检测各权限，再读取已保存模式。
            val saved = repository.modeFlow.first()
            val caps = detectAll()
            val mode = if (saved != null && needsPermission(saved) && caps[saved]?.available != true) {
                null // 权限失效，回退到模式选择
            } else {
                saved
            }
            _uiState.value = ModeUiState(
                mode = mode,
                capabilities = caps,
                backend = modeBackend(mode, caps),
                loading = false,
            )

            // 运行中守护：当前模式权限失效时全局降级（root → shizuku → 普通）。
            while (isActive) {
                delay(degradeIntervalMs)
                val current = _uiState.value.mode ?: continue
                if (current == PermissionMode.NORMAL) continue
                val capsNow = withContext(Dispatchers.IO) { detectAllFor(current) }
                if (capsNow[current]?.available == true) continue

                val fallback: PermissionMode = when (current) {
                    PermissionMode.ROOT ->
                        if (withContext(Dispatchers.IO) { checker.checkShizuku().available })
                            PermissionMode.SHIZUKU
                        else PermissionMode.NORMAL
                    else -> PermissionMode.NORMAL
                }
                downgradeTo(fallback)
            }
        }
    }

    /** 全局降级：持久化 + 更新能力状态与后端，供设置页与各 tab 即时刷新。 */
    private fun downgradeTo(fallback: PermissionMode) {
        viewModelScope.launch {
            repository.save(fallback)
            val fbCaps = withContext(Dispatchers.IO) { detectAllFor(fallback) }
            _uiState.value = _uiState.value.copy(
                mode = fallback,
                capabilities = _uiState.value.capabilities + fbCaps,
                backend = modeBackend(fallback, _uiState.value.capabilities + fbCaps),
            )
        }
    }

    /** 用户选择一个模式：持久化并写回最新能力状态。 */
    fun selectMode(mode: PermissionMode) {
        viewModelScope.launch {
            repository.save(mode)
            val caps = detectAllFor(mode)
            _uiState.value = _uiState.value.copy(
                mode = mode,
                capabilities = _uiState.value.capabilities + caps,
                backend = modeBackend(mode, _uiState.value.capabilities + caps),
            )
        }
    }

    /** Root 模式下手动刷新后端识别。 */
    fun refreshRoot() {
        viewModelScope.launch {
            val backend = checker.detectRootBackend()
            _uiState.value = _uiState.value.copy(backend = backend)
        }
    }

    /** 刷新某模式的能力状态（如 Shizuku 授权跳回后调用）。 */
    fun refreshCapability(mode: PermissionMode) {
        viewModelScope.launch {
            val caps = detectAllFor(mode)
            _uiState.value = _uiState.value.copy(
                capabilities = _uiState.value.capabilities + caps,
                backend = modeBackend(_uiState.value.mode, _uiState.value.capabilities + caps),
            )
        }
    }

    /** 返回当前模式下应展示的后端信息；非 Root 模式为计算的 Root 后端。 */
    private fun modeBackend(mode: PermissionMode?, caps: Map<PermissionMode, CapabilityState>): RootBackend {
        if (mode != PermissionMode.ROOT || caps[PermissionMode.ROOT]?.available != true) {
            return RootBackend.None
        }
        // Root 可用时缓存的后端可能不在状态里，这里直接检测一次（选择/启动时已在 detectAllFor/detectAll 里拿到，此处兜底）。
        return backendCache ?: RootBackend.None
    }

    private var backendCache: RootBackend? = null

    private fun detectAll(): Map<PermissionMode, CapabilityState> {
        return buildMap {
            put(PermissionMode.NORMAL, checker.checkNormal())
            put(PermissionMode.SHIZUKU, checker.checkShizuku())
            val rootBackend = checker.detectRootBackend()
            backendCache = rootBackend
            put(PermissionMode.ROOT, checker.checkRoot())
        }
    }

    private fun detectAllFor(mode: PermissionMode): Map<PermissionMode, CapabilityState> {
        return when (mode) {
            PermissionMode.NORMAL -> mapOf(PermissionMode.NORMAL to checker.checkNormal())
            PermissionMode.SHIZUKU -> mapOf(PermissionMode.SHIZUKU to checker.checkShizuku())
            PermissionMode.ROOT -> {
                val rootBackend = checker.detectRootBackend()
                backendCache = rootBackend
                mapOf(PermissionMode.ROOT to checker.checkRoot())
            }
        }
    }

    private fun needsPermission(mode: PermissionMode): Boolean = mode != PermissionMode.NORMAL
}

data class ModeUiState(
    val mode: PermissionMode? = null,
    val capabilities: Map<PermissionMode, CapabilityState> = emptyMap(),
    val backend: RootBackend = RootBackend.None,
    val loading: Boolean = true,
)