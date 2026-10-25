package com.wuming.tinkerbox

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.wuming.tinkerbox.core.mode.ShizukuManager
import com.wuming.tinkerbox.ui.theme.AppThemeState
import com.wuming.tinkerbox.ui.theme.ColorMode
import com.wuming.tinkerbox.ui.theme.ColorTheme
import com.wuming.tinkerbox.ui.theme.DefaultAppTheme
import com.wuming.tinkerbox.ui.theme.StyleTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.lsposed.hiddenapibypass.HiddenApiBypass

private val Context.dataStore by preferencesDataStore(name = "app_settings")

/**
 * 「应用管理」tab 的角标类型：
 *  - [NONE] 无角标；[ALL] 全部已冻结；[THIRD_PARTY] 仅第三方已冻结；[SYSTEM] 仅系统已冻结。
 */
enum class AppBadgeOption(val value: Int, val label: String) {
    NONE(0, "无"),
    ALL(1, "已冻结应用"),
    THIRD_PARTY(2, "已冻结第三方应用"),
    SYSTEM(3, "已冻结系统应用"),
    ;

    companion object {
        fun fromValue(v: Int, fallback: AppBadgeOption = NONE): AppBadgeOption =
            entries.firstOrNull { it.value == v } ?: fallback
    }
}

/**
 * App 全局入口：
 *  - 初始化 Shizuku binder 监听。
 *  - 持有三层正交的全局状态并持久化于 DataStore：
 *    ① 主题配色 [themeState]（明暗/Monet/主色/调色板风格/色彩规格）
 *    ② 皮肤风格 [styleState]
 *    ③ 解耦 UI 子功能（[enableBlur]/[enableFloatingBottomBar]/... 各自独立开关）
 *
 * 每个子功能都是独立 StateFlow + 独立持久化键 + 独立 setter，切成设置页的子功能开关无缝对接。
 */
class TinkerBoxApp : Application() {

    private val _shizukuState = MutableStateFlow(ShizukuManager.isAvailable())
    val shizukuState: StateFlow<Boolean> = _shizukuState.asStateFlow()

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // ---- ① 主题配色 ----
    private val _themeState = MutableStateFlow(DefaultAppTheme)
    val themeState: StateFlow<AppThemeState> = _themeState.asStateFlow()

    // ---- ② 皮肤风格 ----
    private val _styleState = MutableStateFlow(StyleTheme.MIUI_X)
    val styleState: StateFlow<StyleTheme> = _styleState.asStateFlow()

    // ---- ③ 解耦 UI 子功能 ----
    private val _enableBlur = MutableStateFlow(false)
    val enableBlur: StateFlow<Boolean> = _enableBlur.asStateFlow()

    private val _enableFloatingBottomBar = MutableStateFlow(true)
    val enableFloatingBottomBar: StateFlow<Boolean> = _enableFloatingBottomBar.asStateFlow()

    private val _enableFloatingBottomBarBlur = MutableStateFlow(true)
    val enableFloatingBottomBarBlur: StateFlow<Boolean> = _enableFloatingBottomBarBlur.asStateFlow()

    private val _enableNavigationBadge = MutableStateFlow(true)
    val enableNavigationBadge: StateFlow<Boolean> = _enableNavigationBadge.asStateFlow()

    private val _enableRainbowSlider = MutableStateFlow(false)
    val enableRainbowSlider: StateFlow<Boolean> = _enableRainbowSlider.asStateFlow()

    private val _pageScale = MutableStateFlow(1.0f)
    val pageScale: StateFlow<Float> = _pageScale.asStateFlow()

    private val _predictiveBack = MutableStateFlow(true)
    val predictiveBack: StateFlow<Boolean> = _predictiveBack.asStateFlow()

    // 「应用管理」角标类型（持久化）与实时冻结计数（运行时，由 AppManageViewModel 刷新）。
    private val _appBadgeOption = MutableStateFlow(AppBadgeOption.NONE)
    val appBadgeOption: StateFlow<AppBadgeOption> = _appBadgeOption.asStateFlow()

    private val _frozenThird = MutableStateFlow(0)
    val frozenThirdParty: StateFlow<Int> = _frozenThird.asStateFlow()

    private val _frozenSystem = MutableStateFlow(0)
    val frozenSystem: StateFlow<Int> = _frozenSystem.asStateFlow()

    // ---- 持久化键 ----
    private val colorModeKey = intPreferencesKey("color_mode")
    private val colorThemeKey = stringPreferencesKey("theme_color")
    private val paletteStyleKey = stringPreferencesKey("palette_style")
    private val colorSpecKey = stringPreferencesKey("color_spec")
    private val styleKey = stringPreferencesKey("theme_style")
    private val enableBlurKey = booleanPreferencesKey("feature_enable_blur")
    private val enableFloatingBarKey = booleanPreferencesKey("feature_enable_floating_bar")
    private val enableFloatingBarBlurKey = booleanPreferencesKey("feature_enable_floating_bar_blur")
    private val enableNavBadgeKey = booleanPreferencesKey("feature_enable_nav_badge")
    private val enableRainbowKey = booleanPreferencesKey("feature_enable_rainbow_slider")
    private val pageScaleKey = floatPreferencesKey("feature_page_scale")
    private val predictiveBackKey = booleanPreferencesKey("predictive_back_gesture")
    private val appBadgeOptionKey = intPreferencesKey("app_badge_option")

    private var binderListeners: Pair<rikka.shizuku.Shizuku.OnBinderReceivedListener, rikka.shizuku.Shizuku.OnBinderDeadListener>? =
        null

    override fun onCreate() {
        super.onCreate()

        // 预留性返回手势必须在任何 Activity 创建之前同步应用，否则系统已在 Activity 启动时
        // 缓存了旧 ApplicationInfo.flags，之后反射修改不生效（SukiSU 即在此处同步设置）。
        runBlocking {
            val savedPredictive = runCatching {
                dataStore.data.first()[predictiveBackKey] ?: true
            }.getOrDefault(true)
            _predictiveBack.value = savedPredictive
        }
        applyPredictiveBack(_predictiveBack.value)

        binderListeners = ShizukuManager.addBinderListener(
            onReceived = { _shizukuState.value = true },
            onDead = { _shizukuState.value = false },
        )
        appScope.launch { restorePreferences() }
    }

    private suspend fun restorePreferences() {
        val prefs = runCatching { dataStore.data.first() }.getOrNull() ?: return

        val colorMode = runCatching { ColorMode.fromValue(prefs[colorModeKey] ?: 0) }.getOrDefault(ColorMode.SYSTEM)
        val colorTheme = prefs[colorThemeKey]
            ?.let { runCatching { ColorTheme.valueOf(it) }.getOrNull() }
            ?: ColorTheme.BLUE
        val paletteStyle = prefs[paletteStyleKey]
            ?.let { runCatching { PaletteStyle.valueOf(it) }.getOrNull() }
            ?: PaletteStyle.TonalSpot
        val colorSpec = prefs[colorSpecKey]
            ?.let { runCatching { ColorSpec.SpecVersion.valueOf(it) }.getOrNull() }
            ?: ColorSpec.SpecVersion.Default
        _themeState.value = AppThemeState(colorMode, colorTheme, paletteStyle, colorSpec)

        _styleState.value = prefs[styleKey]
            ?.let { runCatching { StyleTheme.valueOf(it) }.getOrNull() }
            ?: StyleTheme.MIUI_X

        _enableBlur.value = prefs[enableBlurKey] ?: false
        _enableFloatingBottomBar.value = prefs[enableFloatingBarKey] ?: true
        _enableFloatingBottomBarBlur.value = prefs[enableFloatingBarBlurKey] ?: true
        _enableNavigationBadge.value = prefs[enableNavBadgeKey] ?: true
        _enableRainbowSlider.value = prefs[enableRainbowKey] ?: false
        _pageScale.value = prefs[pageScaleKey] ?: 1.0f
        _appBadgeOption.value = AppBadgeOption.fromValue(prefs[appBadgeOptionKey] ?: 0)
    }

    // ---- ① 主题配色 setters ----
    fun setColorMode(mode: ColorMode) {
        _themeState.update { it.copy(colorMode = mode) }
        persistInt(colorModeKey, mode.value)
    }

    fun setColorTheme(colorTheme: ColorTheme) {
        _themeState.update { it.copy(colorTheme = colorTheme) }
        persistString(colorThemeKey, colorTheme.name)
    }

    fun setPaletteStyle(style: PaletteStyle) {
        _themeState.update { it.copy(paletteStyle = style) }
        persistString(paletteStyleKey, style.name)
    }

    fun setColorSpec(spec: ColorSpec.SpecVersion) {
        _themeState.update { it.copy(colorSpec = spec) }
        persistString(colorSpecKey, spec.name)
    }

    // ---- ② 皮肤风格 ----
    fun setStyle(style: StyleTheme) {
        _styleState.value = style
        persistString(styleKey, style.name)
    }

    // ---- ③ 解耦子功能 setters ----
    fun setEnableBlur(enabled: Boolean) { _enableBlur.value = enabled; persistBool(enableBlurKey, enabled) }
    fun setEnableFloatingBottomBar(enabled: Boolean) {
        _enableFloatingBottomBar.value = enabled
        persistBool(enableFloatingBarKey, enabled)
        // 液态玻璃依赖悬浮：关悬浮时同步关掉玻璃，避免「按钮置灰但值仍锁定开启」。
        if (!enabled && _enableFloatingBottomBarBlur.value) {
            _enableFloatingBottomBarBlur.value = false
            persistBool(enableFloatingBarBlurKey, false)
        }
    }
    fun setEnableFloatingBottomBarBlur(enabled: Boolean) { _enableFloatingBottomBarBlur.value = enabled; persistBool(enableFloatingBarBlurKey, enabled) }
    fun setEnableNavigationBadge(enabled: Boolean) { _enableNavigationBadge.value = enabled; persistBool(enableNavBadgeKey, enabled) }
    fun setEnableRainbowSlider(enabled: Boolean) { _enableRainbowSlider.value = enabled; persistBool(enableRainbowKey, enabled) }
    fun setPageScale(scale: Float) { _pageScale.value = scale; persistFloat(pageScaleKey, scale) }

    /** 设置「应用管理」角标类型并持久化。 */
    fun setAppBadgeOption(option: AppBadgeOption) {
        _appBadgeOption.value = option
        persistInt(appBadgeOptionKey, option.value)
    }

    /** 应用管理刷新后同步冻结计数（第三方 / 系统），供底栏角标实时反映。 */
    fun updateFrozenCounts(thirdParty: Int, system: Int) {
        _frozenThird.value = thirdParty
        _frozenSystem.value = system
    }

    /** 设置并持久化「预留性返回手势」开关。 */
    fun setPredictiveBack(enabled: Boolean) {
        _predictiveBack.value = enabled
        applyPredictiveBack(enabled)
        persistBool(predictiveBackKey, enabled)
    }

    /**
     * 运行时动态启用 / 禁用预留性返回手势。
     *
     * 参考 SukiSU-Ultra：调用隐藏 API [android.content.pm.ApplicationInfo.setEnableOnBackInvokedCallback]
     * 仅对 API 33+ 有效，且普通应用需先用 HiddenApiBypass 放开该隐藏 API 的豁免。
     */
    private fun applyPredictiveBack(enabled: Boolean) {
        val tag = "TinkerBoxPredictiveBack"
        if (android.os.Build.VERSION.SDK_INT < 33) {
            Log.i(tag, "SDK 低于 33，跳过预留性返回设置")
            return
        }
        Log.i(tag, "尝试设置 enableOnBackInvokedCallback=$enabled")
        try {
            HiddenApiBypass.addHiddenApiExemptions(
                "Landroid/content/pm/ApplicationInfo;->setEnableOnBackInvokedCallback",
            )
            Log.i(tag, "HiddenApiBypass 豁免调用成功")
            val method = android.content.pm.ApplicationInfo::class.java
                .getDeclaredMethod("setEnableOnBackInvokedCallback", Boolean::class.javaPrimitiveType)
            method.isAccessible = true
            method.invoke(applicationInfo, enabled)
            Log.i(tag, "setEnableOnBackInvokedCallback 反射调用成功")
        } catch (e: Throwable) {
            Log.e(tag, "设置预留性返回手势失败: ${e.javaClass.name}: ${e.message}\n" +
                android.util.Log.getStackTraceString(e))
        }
        Log.i(tag, "当前 ApplicationInfo.flags=0x" + Integer.toHexString(applicationInfo.flags) +
            ", SDK=" + android.os.Build.VERSION.SDK_INT)
    }

    /** 读取已保存配色模式 value（挂起，供启动时同步取）。 */
    suspend fun loadColorModeSync(): Int {
        return runCatching { dataStore.data.first()[colorModeKey] ?: 0 }.getOrDefault(0)
    }

    // ---- 统一持久化入口 ----
    private fun persistInt(key: androidx.datastore.preferences.core.Preferences.Key<Int>, value: Int) {
        appScope.launch {
            withContext(Dispatchers.IO) { runCatching { dataStore.edit { it[key] = value } } }
        }
    }

    private fun persistString(key: androidx.datastore.preferences.core.Preferences.Key<String>, value: String) {
        appScope.launch {
            withContext(Dispatchers.IO) { runCatching { dataStore.edit { it[key] = value } } }
        }
    }

    private fun persistBool(key: androidx.datastore.preferences.core.Preferences.Key<Boolean>, value: Boolean) {
        appScope.launch {
            withContext(Dispatchers.IO) { runCatching { dataStore.edit { it[key] = value } } }
        }
    }

    private fun persistFloat(key: androidx.datastore.preferences.core.Preferences.Key<Float>, value: Float) {
        appScope.launch {
            withContext(Dispatchers.IO) { runCatching { dataStore.edit { it[key] = value } } }
        }
    }

    override fun onTerminate() {
        binderListeners?.let { (r, d) -> ShizukuManager.removeBinderListener(r, d) }
        super.onTerminate()
    }
}