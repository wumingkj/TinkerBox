package com.wuming.tinkerbox.core.apps

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/**
 * 已安装应用条目（用于冻结列表展示）。
 */
data class AppListItem(
    val packageName: String,
    val label: String,
    val isSystem: Boolean,
    val isFrozen: Boolean,
    val frozenCause: String,
    val icon: ImageBitmap?,
    val iconKey: String,
)

/** 图标目标尺寸（像素）。固定在合理大小，避免超大位图与内存抖动。 */
private const val ICON_SIZE = 72

/**
 * 读取已安装应用并拼接冻结状态。
 *
 * 冻结判定完全基于 SDK 读取 [ApplicationInfo] 的权威字段（无需 shell）：
 *  - disabled ：`enabled == false`（停用/禁用）
 *  - suspended：`flags & FLAG_SUSPENDED`（挂起，灰图标）
 *  - hidden   ：私有字段 `privateFlags & 0x1`（隐藏，反射读取，失败时忽略）
 * 与 Hail（aistra0528/Hail）的识别方式一致，可正确读出 ColorOS 上
 * 挂起的应用（如被 pm suspend 的第三方 App）。
 *
 * 说明：
 *  - 通过 PackageManager 读取 USER_0 应用。
 *  - 系统/三方以 ApplicationInfo.FLAG_SYSTEM 区分。
 *  - [load] 提供快速模式（withIcons=false，秒出列表，图标后台补）。
 *  - 图标统一缩放到固定小尺寸，避免超大位图拖慢渲染与内存。
 */
class AppLister(
    private val pm: android.content.pm.PackageManager,
) {

    /**
     * 返回已安装应用列表，已按真实冻结状态标注。
     *
     * @param userFrozen 用户在本 App 中主动冻结过的包集合。仅用于约束
     *        `disabled`（停用/禁用）判定：ColorOS / 系统会把大量长期未打开
     *        的闲置应用自动标记为 `enabled=false`，若不加约束会把它们全部
     *        误判为冻结。而 `suspended`（挂起）与 `hidden`（隐藏）是系统
     *        明确的冻结标志，始终无条件生效，无需白名单。
     */
    fun load(
        withIcons: Boolean = true,
        userFrozen: Set<String> = emptySet(),
        showSystem: Boolean = true,
        showThirdParty: Boolean = true,
    ): List<AppListItem> {
        val apps = ArrayList<AppListItem>(128)

        pm.getInstalledApplications(
            // 与 Hail 一致只带 MATCH_UNINSTALLED_PACKAGES：
            // 既能读回用户停用/禁用的应用，又不会把 ColorOS 预禁用的
            // 系统深层组件（nativetest、devicelock 等）拉出来。
            PackageManager.MATCH_UNINSTALLED_PACKAGES,
        ).forEach { ai ->
            val isSystem = ai.flags and ApplicationInfo.FLAG_SYSTEM != 0
            if (isSystem && !showSystem) return@forEach
            if (!isSystem && !showThirdParty) return@forEach

            val state = freezeStateOf(ai, ai.packageName in userFrozen)
            val label = runCatching { ai.loadLabel(pm).toString() }.getOrDefault(ai.packageName)
            apps += AppListItem(
                packageName = ai.packageName,
                label = label,
                isSystem = isSystem,
                isFrozen = state != null,
                frozenCause = state.orEmpty(),
                icon = if (withIcons) runCatching { ai.loadIcon(pm).toImageBitmap() }.getOrNull() else null,
                iconKey = "${ai.packageName}_${ai.icon ?: 0}",
            )
        }
        apps.sortBy { it.label.lowercase() }
        return apps
    }

    /**
     * 计算单个 [ApplicationInfo] 的冻结状态；未冻结返回 null，冻结返回原因标记。
     *
     * @param userAuthored 该包是否出现在用户主动冻结记录中。仅当为 true 时
     *        `enabled==false` 才被认定为「停用/禁用」冻结；否则 ColorOS 智能
     *        停用闲置应用产生的 `enabled=false` 会被忽略，避免大量误报。
     */
    private fun freezeStateOf(ai: ApplicationInfo, userAuthored: Boolean): String? {
        // 停用 / 禁用：enabled==false（仅当用户主动冻结过才可信，过滤系统智能停用伪影）
        if (!ai.enabled && userAuthored) return "disabled"
        // 挂起（灰图标）：FLAG_SUSPENDED，系统明确标志，无条件可信
        if (ai.flags and ApplicationInfo.FLAG_SUSPENDED != 0) return "suspended"
        // 隐藏：privateFlags 最低位（反射读取，失败视为非隐藏），系统明确标志，无条件可信
        runCatching {
            val privateFlags =
                ApplicationInfo::class.java.getField("privateFlags").getInt(ai)
            if (privateFlags and 0x1 != 0) return "hidden"
        }
        return null
    }
}

/** 将任意 [android.graphics.drawable.Drawable] 绘制为固定尺寸的 [ImageBitmap]。 */
private fun android.graphics.drawable.Drawable.toImageBitmap(): ImageBitmap {
    val bitmap = Bitmap.createBitmap(
        ICON_SIZE,
        ICON_SIZE,
        Bitmap.Config.ARGB_8888,
    )
    val canvas = android.graphics.Canvas(bitmap)
    setBounds(0, 0, ICON_SIZE, ICON_SIZE)
    draw(canvas)
    return bitmap.asImageBitmap()
}