package com.wuming.tinkerbox.core.terminal

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.wuming.tinkerbox.core.terminal.em.TerminalEmulator
import com.wuming.tinkerbox.core.terminal.em.TerminalColors
import com.wuming.tinkerbox.core.terminal.em.TerminalOutput
import com.wuming.tinkerbox.core.terminal.em.TerminalSessionClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * 终端仿真实例：把纯 Java 的 termux terminal-emulator（VT100 解析、光标、颜色、屏幕缓冲）
 * 接到现有 [TerminalSession]（ProcessBuilder 后端）上。
 *
 * - [TerminalSession] 负责进程与管道（无 NDK）。
 * - [TerminalEmulator.append] 消费子进程字节流，维护「屏幕缓冲」与「光标」。
 * - [version] 递增信号驱动 Compose 重绘。
 *
 * 权限模型对齐 Termux：默认普通 shell；后续在此输入 `su` 走手动提权。
 */
class EmulatorSession {

    /** 底层进程会话（ProcessBuilder / /system/bin/sh）。 */
    val session = TerminalSession()

    /** 与 TerminalSession 相同的唯一 id，供多会话切换。 */
    val id: String get() = session.id

    /** 每次缓冲内容变化递增，UI 据此重组。 */
    val version = MutableStateFlow(0L)

    /** 当前虚拟屏尺寸（按实际渲染宽度换算）。 */
    var columns by mutableStateOf(80)
        private set
    var screenRows by mutableStateOf(24)
        private set

    private var emulator: TerminalEmulator? = null
    private val colors = TerminalColors()

    /** Termux 仿真的输出回调：把进程字节 feed 进 emulator。 */
    private val output: TerminalOutput = object : TerminalOutput() {
        override fun write(data: ByteArray, offset: Int, count: Int) {
            emulator?.append(data, count) // append(byte[], length)，从 offset 0 消费
            version.update { it + 1 }
        }

        override fun titleChanged(oldTitle: String, newTitle: String) {}
        override fun onCopyTextToClipboard(text: String) {}
        override fun onPasteTextFromClipboard() {}
        override fun onBell() {}
        override fun onColorsChanged() {
            version.update { it + 1 }
        }
    }

    /** 仿真核心需要的轻量客户端（这里只用来拿光标样式/日志，均可忽略）。 */
    private val client: TerminalSessionClient = object : TerminalSessionClient {
        override fun getTerminalCursorStyle(): Int? = null
        override fun onTerminalCursorStateChange(setting: Boolean) {}
        override fun logError(logTag: String, message: String) {}
        override fun logWarn(logTag: String, message: String) {}
        override fun logInfo(logTag: String, message: String) {}
        override fun logDebug(logTag: String, message: String) {}
        override fun logVerbose(logTag: String, message: String) {}
    }

    init {
        session.onOutput = { bytes, len -> output.write(bytes, 0, len) }
    }

    /** 按实际列/行数创建或 resize 仿真器。cellW/cellH 为单位字符像素。 */
    fun ensureEmulator(cols: Int, rows: Int, cellW: Int, cellH: Int) {
        columns = cols
        screenRows = rows
        // 避免 resize 抖动：尺寸未变就直接返回。
        if (emulator?.mColumns == cols && emulator?.mRows == rows) return
        if (emulator == null) {
            emulator = TerminalEmulator(
                output,
                cols, rows, cellW, cellH,
                5000, // transcript 滚动行数
                client,
            )
        } else {
            emulator?.resize(cols, rows, cellW, cellH)
        }
    }

    /** 当前仿真器（可能尚未创建）。 */
    fun emulatorRef(): TerminalEmulator? = emulator

    /**
     * 把 emulator 解码出的颜色映射为实际 ARGB int。
     * - truecolor（>= 0xff000000）直接返回；
     * - 其它为 0..258 的索引色，查终端配色表（含默认前景/背景/光标）。
     */
    fun indexedColor(index: Int): Int =
        if (index >= 0xff000000) index else colors.mCurrentColors[index]

    /** 向子进程写一行并补回车（命令行发送）。 */
    fun submit(line: String) = session.writeLine(line)

    /** 启动底层 shell。 */
    fun spawn() = session.spawn()

    /** 关闭会话与仿真器。 */
    fun close() {
        session.close()
        emulator = null
    }

    fun isAlive(): Boolean = session.isAlive()
}