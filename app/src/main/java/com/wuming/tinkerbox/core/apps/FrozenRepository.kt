package com.wuming.tinkerbox.core.apps

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.frozenDataStore by preferencesDataStore(name = "frozen_apps")

/**
 * 持久化「用户手动冻结的包」集合。
 *
 * 背景：`pm list packages -d` 会返回大量系统默认/ColorOS 智能停用的应用，
 * 无法作为「用户真正冻结」的依据。因此仅记录用户在本 App 中主动冻结的包，
 * 作为候选集合，再通过对每个包做实时状态校验（见 AppFreezer.isFrozen）得到
 * 最终的「已冻结」列表。这样用户在命令行/其他工具解冻后，实时校验会把它剔除，
 * 避免残留。
 *
 * 以逗号分隔字符串存储；空串表示无冻结项。
 */
class FrozenRepository(private val context: Context) {

    private val key = stringPreferencesKey("frozen_set")

    val frozenFlow: Flow<Set<String>> = context.frozenDataStore.data.map { prefs ->
        prefs[key]
            ?.split(',')
            ?.filter { it.isNotBlank() }
            ?.toSet()
            ?: emptySet()
    }

    suspend fun add(pkg: String) {
        val current = frozenFlow.first()
        context.frozenDataStore.edit { it[key] = (current + pkg).joinToString(",") }
    }

    suspend fun remove(pkg: String) {
        val current = frozenFlow.first()
        context.frozenDataStore.edit { it[key] = (current - pkg).joinToString(",") }
    }
}