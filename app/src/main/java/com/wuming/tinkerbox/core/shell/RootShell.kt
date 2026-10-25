package com.wuming.tinkerbox.core.shell

import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

/**
 * 极简 root shell 执行器。当前仅用于探测与只读命令，
 * 完整的写入/回读验证封装后续在此扩展。
 */
object RootShell {

    /** 单条命令最长执行时间：root 被关 / su 授权卡住时避免无限阻塞。 */
    private const val TIMEOUT_SECONDS = 6L

    /**
     * 执行一条命令并返回输出。带 root 前缀。
     */
    fun exec(command: String): ShellResult {
        return execArgs(arrayOf("su", "-c", command))
    }

    /**
     * 以 root 身份执行，但把参数直接作为 su 自身参数（如 su -v），
     * 用于获取 KSU/Magisk 的版本特征串。
     */
    fun suDirect(vararg args: String): ShellResult {
        return execArgs(arrayOf("su") + args)
    }

    private fun execArgs(cmd: Array<String>): ShellResult {
        return runCatching {
            val p = Runtime.getRuntime().exec(cmd)
            val stdout = ByteArrayOutputStream()
            val stderr = ByteArrayOutputStream()
            val readOut = Thread { readAll(p.inputStream, stdout) }
            val readErr = Thread { readAll(p.errorStream, stderr) }
            readOut.start()
            readErr.start()

            // root 被关或授权无响应时 su 可能一直挂着；超时强制结束，绝不阻塞调用方。
            val finished = p.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            if (!finished) {
                p.destroyForcibly()
                readOut.join(500)
                readErr.join(500)
                return@runCatching ShellResult(-1, stdout.toString().trim(), "root 执行超时")
            }
            ShellResult(p.exitValue(), stdout.toString().trim(), stderr.toString().trim())
        }.getOrElse { e ->
            ShellResult(-1, "", e.message ?: "unknown")
        }
    }

    private fun readAll(stream: java.io.InputStream, out: ByteArrayOutputStream) {
        try {
            val buffer = ByteArray(8192)
            var len: Int
            while (stream.read(buffer).also { len = it } != -1) {
                out.write(buffer, 0, len)
            }
        } catch (_: Exception) {
        }
    }

    data class ShellResult(val exitCode: Int, val stdout: String, val stderr: String) {
        val isSuccess: Boolean get() = exitCode == 0
    }
}