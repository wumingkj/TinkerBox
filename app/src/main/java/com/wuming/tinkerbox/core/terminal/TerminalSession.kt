package com.wuming.tinkerbox.core.terminal

import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * 一个 shell 会话后端（基于 [ProcessBuilder]，纯 Java、不依赖 NDK）。
 *
 * 第一关「最小终端」的进程宿主：起一条 /system/bin/sh（或任意命令），
 * 异步把 stdout/stderr 合并为字节流回调给前端，前端可往 stdin 写入命令。
 *
 * 该模型天然支撑「多会话」：每次 [spawn] 产生一个独立 [TerminalSession]，
 * 每个会话持有独立 [id] 与管道，前端通过 id 切换/关闭不同会话即可。
 *
 * 注意：这是「能看输出、能发命令」的最小链路；PTY 的交互特性（光标移动、
 * 反显、su 提示符等）依赖伪终端，将在后续引入 terminal-emulator 仿真层时升级。
 */
class TerminalSession {

    /** 会话唯一标识，供前端 list 中区分/切换/关闭。 */
    val id: String = UUID.randomUUID().toString()

    private val processRef = AtomicReference<Process?>()
    private val running = AtomicBoolean(false)
    private val outRef = AtomicReference<OutputStream?>()

    /** 会话显示名（默认用 shell 启动命令首项）。 */
    @Volatile
    var sessionName: String = "shell"
        private set

    /** 输出字节回调：由宿主注入，持续收到子进程 stdout/stderr 合并流。 */
    @Volatile
    var onOutput: ((ByteArray, Int) -> Unit)? = null

    /** 会话退出回调（进程结束或 close 时触发）。 */
    @Volatile
    var onExit: ((Int) -> Unit)? = null

    /** 尝试启动 [command]，成功返回 true。 */
    fun spawn(command: Array<String> = arrayOf("/system/bin/sh")): Boolean {
        if (running.get()) return false
        val builder = ProcessBuilder(*command)
            .redirectErrorStream(true) // stderr 并入 stdout，前端单流接收
        return runCatching {
            val p = builder.start()
            sessionName = command.lastOrNull()?.substringAfterLast('/') ?: "shell"
            processRef.set(p)
            outRef.set(p.outputStream)
            running.set(true)

            Thread({
                val buf = ByteArray(8192)
                try {
                    while (running.get()) {
                        val n = p.inputStream.read(buf)
                        if (n < 0) break
                        if (n > 0) onOutput?.invoke(buf, n)
                    }
                } catch (_: Exception) {
                    // 管道关闭或进程结束
                } finally {
                    running.set(false)
                    onExit?.invoke(p.exitValue())
                }
            }, "terminal-session-$id").apply { isDaemon = true }.start()

            true
        }.getOrElse { false }
    }

    /** 向子进程 stdin 写字节。 */
    fun write(bytes: ByteArray) {
        if (!running.get()) return
        runCatching { outRef.get()?.write(bytes); outRef.get()?.flush() }
    }

    /** 写一行并补回车（用于命令行发送）。 */
    fun writeLine(line: String) = write((line + "\r").toByteArray(Charsets.UTF_8))

    /** 会话是否仍在运行。 */
    fun isAlive(): Boolean = running.get()

    /** 结束会话（当前为强制杀；后续可做成 发 exit/EOF 优雅退出）。 */
    fun close() {
        running.set(false)
        runCatching { processRef.get()?.destroy() }
        runCatching { processRef.get()?.destroyForcibly() }
        runCatching { outRef.get()?.close() }
    }

    companion object {
        /** 便捷创建并启动一个会话。 */
        fun spawnNew(command: Array<String> = arrayOf("/system/bin/sh")): TerminalSession {
            val s = TerminalSession()
            s.spawn(command)
            return s
        }
    }
}