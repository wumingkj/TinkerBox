package com.wuming.tinkerbox.core.mode

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.modeDataStore by preferencesDataStore(name = "permission_mode")

/**
 * 持久化用户选择的权限模式。
 * - 首次启动时无存档，返回 null，用于触发模式选择引导。
 * - 选择后写入 DataStore，后续直接读取。
 */
class ModeRepository(private val context: Context) {

    private val key = stringPreferencesKey("mode")

    val modeFlow: Flow<PermissionMode?> = context.modeDataStore.data.map { prefs ->
        prefs[key]?.let { raw -> runCatching { PermissionMode.valueOf(raw) }.getOrNull() }
    }

    suspend fun save(mode: PermissionMode) {
        context.modeDataStore.edit { it[key] = mode.name }
    }
}