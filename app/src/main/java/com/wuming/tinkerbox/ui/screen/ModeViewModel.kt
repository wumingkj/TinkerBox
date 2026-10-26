package com.wuming.tinkerbox.ui.screen

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wuming.tinkerbox.core.mode.CapabilityState
import com.wuming.tinkerbox.core.mode.ModeRepository
import com.wuming.tinkerbox.core.mode.PermissionChecker
import com.wuming.tinkerbox.core.mode.PermissionMode
import com.wuming.tinkerbox.core.mode.ShizukuManager
import com.wuming.tinkerbox.core.root.RootBackend
import com.wuming.tinkerbox.core.root.RootDetector
import com.wuming.tinkerbox.core.shell.RootShell
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

    private val TAG = "TinkerBoxMode"

    private val repository = ModeRepository(app.applicationContext)
    private val checker = PermissionChecker(app.applicationContext)

    /** 全局降级轮询间隔（毫秒）。 */
    private val degradeIntervalMs = 3500L

    /** 切换模式时自动获取权限的轮询：周期与上限（毫秒，约 12s 内取到即提交，否则退级）。 */
    private val acquireTickMs = 500L
    private val acquireMaxTicks = 24L

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

    /**
     * 用户选择一个模式：先自动获取所需权限，取到才提交；取不到则退级到普通模式。
     *
     * 流程：
     *  - 普通模式 / 权限本就可用 → 直接提交；
     *  - Root → 触发一次 su 授权弹窗（root 管理器），轮询等待授权；
     *  - Shizuku → 若服务未启动且持有 root 则尝试用 root 自启，再弹系统授权框，轮询等待；
     *  - 达到时限仍不可用 → 降级到始终可用的普通模式（「退级」）。
     */
    fun selectMode(mode: PermissionMode) {
        android.util.Log.d(TAG, "selectMode(mode=$mode) called")
        viewModelScope.launch {
            var caps = withContext(Dispatchers.IO) { detectAllFor(mode) }
            android.util.Log.d(
                TAG,
                "selectMode: detect($mode) available=${caps[mode]?.available} detail=${caps[mode]?.detail}",
            )
            if (mode == PermissionMode.NORMAL || caps[mode]?.available == true) {
                applyMode(mode, caps)
                return@launch
            }

            // 所需权限未就绪：先自动获取（su 弹窗 / Shizuku 授权），期间展示「获取中」。
            android.util.Log.d(TAG, "selectMode: permission not ready, begin acquire($mode)")
            _uiState.value = _uiState.value.copy(acquiring = true)
            acquirePermission(mode)

            // 轮询等待授权结果。
            var ticks = 0L
            while (ticks < acquireMaxTicks) {
                delay(acquireTickMs)
                ticks++
                caps = withContext(Dispatchers.IO) { detectAllFor(mode) }
                if (caps[mode]?.available == true) {
                    applyMode(mode, caps)
                    _uiState.value = _uiState.value.copy(acquiring = false)
                    return@launch
                }
            }

            // 超时仍未取到 → 退级到普通模式（始终可用）。
            val normCaps = withContext(Dispatchers.IO) { detectAllFor(PermissionMode.NORMAL) }
            applyMode(PermissionMode.NORMAL, normCaps)
            _uiState.value = _uiState.value.copy(acquiring = false)
        }
    }

    /** 提交模式：持久化并更新能力状态与后端。 */
    private suspend fun applyMode(mode: PermissionMode, caps: Map<PermissionMode, CapabilityState>) {
        repository.save(mode)
        _uiState.value = _uiState.value.copy(
            mode = mode,
            capabilities = _uiState.value.capabilities + caps,
            backend = modeBackend(mode, _uiState.value.capabilities + caps),
            acquiring = false,
        )
    }

    /**
     * 自动获取权限：
     *  - Root：再跑一次 su 探测触发 root 管理器授权弹窗（首次检测已触发，此处兜底）；
     *  - Shizuku：服务未启动且持有 root 时用 root 自启，服务可用后弹系统授权框。
     * 必须在主线程调用 Shizuku.requestPermission，故本函数由主进程协程执行。
     */
    private suspend fun acquirePermission(mode: PermissionMode) {
        when (mode) {
            PermissionMode.ROOT -> {
                android.util.Log.d(TAG, "acquire(ROOT): run su detect to trigger root manager dialog")
                withContext(Dispatchers.IO) { RootDetector.detect() }
            }
            PermissionMode.SHIZUKU -> {
                android.util.Log.d(
                    TAG,
                    "acquire(SHIZUKU): available=${ShizukuManager.isAvailable()} " +
                        "granted=${ShizukuManager.checkPermission()}",
                )
                if (!ShizukuManager.isAvailable()) {
                    withContext(Dispatchers.IO) { tryStartShizukuViaRoot() }
                    // 等服务真正起来（root 自启需要一两秒）。
                    var waited = 0
                    while (!ShizukuManager.isAvailable() && waited < 20) {
                        delay(250)
                        waited++
                    }
                }
                // 服务在但未授权 → 触发系统授权框。
                if (ShizukuManager.isAvailable() && !ShizukuManager.checkPermission()) {
                    ShizukuManager.requestPermission()
                }
            }
            else -> {
                // NORMAL 无需任何权限。
            }
        }
    }

    /** 持有 root 时，尝试用 root 执行 Shizuku 官方 start.sh 自启服务（尽力而为）。 */
    private fun tryStartShizukuViaRoot() {
        val scripts = listOf(
            "/storage/emulated/0/Android/data/moe.shizuku.privileged.api/start.sh",
            "/storage/emulated/0/Android/data/io.rikka.shizuku/start.sh",
        )
        for (s in scripts) {
            val r = RootShell.exec("sh $s")
            if (r.isSuccess) break
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
    /** 切换模式时正在自动获取权限（su/Shizuku 授权进行中）。 */
    val acquiring: Boolean = false,
)