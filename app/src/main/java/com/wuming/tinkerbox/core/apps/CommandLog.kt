package com.wuming.tinkerbox.core.apps

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 命令日志记录（全局单例）。
 *
 * 记录本 App 执行的所有 shell / pm / dumpsys 命令、退出码、输出与错误，
 * 供排查冻结/解冻等行为是否符合预期。UI 通过 [entries] 实时订阅。
 */
object CommandLog {

    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())
    val entries: StateFlow<List<LogEntry>> = _entries.asStateFlow()

    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    @Synchronized
    fun log(
        command: String,
        exitCode: Int,
        stdout: String = "",
        stderr: String = "",
        tag: String = "",
    ) {
        val entry = LogEntry(
            time = timeFormat.format(Date()),
            command = command,
            exitCode = exitCode,
            stdout = stdout.trim(),
            stderr = stderr.trim(),
            tag = tag,
        )
        _entries.value = _entries.value + entry

        // 同步写入 Android logcat，便于 adb 实时抓取（tag: TinkerBox.Cmd）
        val body = buildString {
            append("exit=").append(exitCode).append("  $").append(command)
            if (stdout.isNotBlank()) append("\nout: ").append(stdout.trim())
            if (stderr.isNotBlank()) append("\nerr: ").append(stderr.trim())
        }
        if (exitCode == 0) Log.i(TAG, body) else Log.e(TAG, body)
    }

    @Synchronized
    fun clear() {
        _entries.value = emptyList()
    }

    private const val TAG = "TinkerBox.Cmd"
}

/** 一条命令执行日志。 */
data class LogEntry(
    val time: String,
    val command: String,
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val tag: String = "",
) {
    val ok: Boolean get() = exitCode == 0
}