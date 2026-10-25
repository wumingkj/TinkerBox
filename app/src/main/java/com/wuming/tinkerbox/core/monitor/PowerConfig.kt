package com.wuming.tinkerbox.core.monitor

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.powerDataStore by preferencesDataStore(name = "power_config")

/**
 * 电芯配置。用于把读取到的电压/电流归算为真实电池数值，
 * 适配部分双电芯机型（传感器只暴露单颗电芯电压，串联总压为单颗 ×2）。
 *
 *  - [AUTO]：单颗直读，不做换算（旧行为，兼容普通/单电芯机型）。
 *  - [SERIES]：双电芯。电压显示两颗电芯各自的单芯电压，功率按串联合计总压（单芯×2）计算。
 */
enum class BatteryCells(val displayName: String, val desc: String) {
    AUTO("自动（单电芯）", "按单颗电芯直读，电压不换算"),
    SERIES("双电芯", "显示电芯0/电芯1两颗电压，功率按串联合计总压算"),
}

/** 电芯配置的 DataStore 持久化。 */
object PowerConfig {
    private const val DEFAULT = "auto"
    private val KEY = stringPreferencesKey("battery_cells")

    /** 读取当前电芯配置（默认 [BatteryCells.AUTO]）。 */
    fun cellsFlow(context: Context): Flow<BatteryCells> =
        context.powerDataStore.data.map { prefs ->
            val v = prefs[KEY] ?: DEFAULT
            BatteryCells.entries.firstOrNull { it.name == v } ?: BatteryCells.AUTO
        }

    /** 保存电芯配置。 */
    suspend fun saveCells(context: Context, cells: BatteryCells) {
        context.powerDataStore.edit { it[KEY] = cells.name }
    }
}