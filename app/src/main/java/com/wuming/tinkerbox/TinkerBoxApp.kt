package com.wuming.tinkerbox

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.wuming.tinkerbox.core.mode.ShizukuManager
import com.wuming.tinkerbox.ui.theme.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Context.dataStore by preferencesDataStore(name = "app_settings")

/**
 * App 全局入口：
 *  - 初始化 Shizuku binder 监听，追踪其可用/断开状态，供权限模式按需刷新。
 *  - 持有全局主题偏好（[themeState]），持久化于 DataStore，供 MainActivity 实时应用。
 */
class TinkerBoxApp : Application() {

    private val _shizukuState = MutableStateFlow(ShizukuManager.isAvailable())
    val shizukuState: StateFlow<Boolean> = _shizukuState.asStateFlow()

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _themeState = MutableStateFlow(ThemeMode.SYSTEM)
    val themeState: StateFlow<ThemeMode> = _themeState.asStateFlow()

    private var themeKey = stringPreferencesKey("theme_mode")
    private var binderListeners: Pair<rikka.shizuku.Shizuku.OnBinderReceivedListener, rikka.shizuku.Shizuku.OnBinderDeadListener>? =
        null

    override fun onCreate() {
        super.onCreate()
        binderListeners = ShizukuManager.addBinderListener(
            onReceived = { _shizukuState.value = true },
            onDead = { _shizukuState.value = false },
        )
        appScope.launch {
            val saved = runCatching {
                dataStore.data.first()[themeKey]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
            }.getOrNull()
            _themeState.value = saved ?: ThemeMode.SYSTEM
        }
    }

    /** 设置并持久化主题偏好。 */
    fun setTheme(mode: ThemeMode) {
        _themeState.value = mode
        appScope.launch {
            withContext(Dispatchers.IO) {
                runCatching {
                    dataStore.edit { it[themeKey] = mode.name }
                }
            }
        }
    }

    /** 读取已保存主题（挂起，供启动时同步取）。 */
    suspend fun loadThemeSync(): ThemeMode {
        return runCatching {
            dataStore.data.first()[themeKey]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
        }.getOrNull() ?: ThemeMode.SYSTEM
    }

    override fun onTerminate() {
        binderListeners?.let { (r, d) -> ShizukuManager.removeBinderListener(r, d) }
        super.onTerminate()
    }
}