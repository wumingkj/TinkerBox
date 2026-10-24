package com.wuming.tinkerbox.core.shell

/**
 * 极简 root shell 执行器。当前仅用于探测与只读命令，
 * 完整的写入/回读验证封装后续在此扩展。
 */
object RootShell {

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
            val stdout = p.inputStream.bufferedReader().readText()
            val stderr = p.errorStream.bufferedReader().readText()
            val exitCode = p.waitFor()
            p.destroy()
            ShellResult(exitCode, stdout.trim(), stderr.trim())
        }.getOrElse { e ->
            ShellResult(-1, "", e.message ?: "unknown")
        }
    }

    data class ShellResult(val exitCode: Int, val stdout: String, val stderr: String) {
        val isSuccess: Boolean get() = exitCode == 0
    }
}