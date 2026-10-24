package com.wuming.tinkerbox.core.mode

import android.content.pm.PackageManager
import com.wuming.tinkerbox.core.shell.RootShell
import rikka.shizuku.Shizuku
import java.io.ByteArrayOutputStream

/**
 * Shizuku 服务封装：
 *  - 连接状态监听（binder received / dead）
 *  - 权限检查与请求
 *  - 通过 Shizuku 执行命令（替代 root shell）
 */
object ShizukuManager {

    /** 请求授权用的请求码（任意自定义）。 */
    const val REQUEST_CODE = 114

    /** App 是否已在 Shizuku 授权列表且授权通过。 */
    fun isGranted(): Boolean = isAvailable() && checkPermission()

    /** Shizuku 绑定是否在、且已授权。 */
    fun isAvailable(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (_: Exception) {
            false
        }
    }

    fun checkPermission(): Boolean {
        return try {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: Exception) {
            false
        }
    }

    /** 触发系统跳转到 Shizuku 授权页，让本 App 出现在授权列表。必须在主线程调用。 */
    fun requestPermission() {
        try {
            Shizuku.requestPermission(REQUEST_CODE)
        } catch (_: Exception) {
            // Shizuku 未绑定或未安装会抛异常，由调用方处理提示
        }
    }

    /** 注册授权结果监听；返回值用于 [removeRequestPermissionListener] 释放。 */
    fun addRequestPermissionListener(listener: Shizuku.OnRequestPermissionResultListener) {
        try {
            Shizuku.addRequestPermissionResultListener(listener)
        } catch (_: Exception) {
        }
    }

    fun removeRequestPermissionListener(listener: Shizuku.OnRequestPermissionResultListener) {
        try {
            Shizuku.removeRequestPermissionResultListener(listener)
        } catch (_: Exception) {
        }
    }

    /** 注册 binder 连接/断开监听。 */
    fun addBinderListener(
        onReceived: () -> Unit,
        onDead: () -> Unit,
    ): Pair<Shizuku.OnBinderReceivedListener, Shizuku.OnBinderDeadListener> {
        val received = Shizuku.OnBinderReceivedListener { onReceived() }
        val dead = Shizuku.OnBinderDeadListener { onDead() }
        Shizuku.addBinderReceivedListener(received)
        Shizuku.addBinderDeadListener(dead)
        return received to dead
    }

    fun removeBinderListener(
        received: Shizuku.OnBinderReceivedListener,
        dead: Shizuku.OnBinderDeadListener,
    ) {
        try {
            Shizuku.removeBinderReceivedListener(received)
            Shizuku.removeBinderDeadListener(dead)
        } catch (_: Exception) {
        }
    }

    /** 以 Shizuku 身份执行命令并拿回输出。 */
    fun exec(command: String): RootShell.ShellResult {
        return exec(arrayOf("sh", "-c", command), null, null)
    }

    fun exec(args: Array<String>, env: Array<String>? = null, dir: String? = null): RootShell.ShellResult {
        return try {
            val binder = Shizuku.getBinder()
                ?: return RootShell.ShellResult(-1, "", "Shizuku binder 不可用")
            val process = newProcessReflect(args, env, dir)

            val stdout = ByteArrayOutputStream()
            val stderr = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            val readOut = Thread {
                try {
                    var len: Int
                    while (process.inputStream.read(buffer).also { len = it } != -1) {
                        stdout.write(buffer, 0, len)
                    }
                } catch (_: Exception) {
                }
            }
            val readErr = Thread {
                try {
                    var len: Int
                    while (process.errorStream.read(buffer).also { len = it } != -1) {
                        stderr.write(buffer, 0, len)
                    }
                } catch (_: Exception) {
                }
            }
            readOut.start()
            readErr.start()
            val exitCode = process.waitFor()
            readOut.join(2000)
            readErr.join(2000)

            RootShell.ShellResult(
                exitCode = exitCode,
                stdout = stdout.toString("UTF-8").trim(),
                stderr = stderr.toString("UTF-8").trim(),
            )
        } catch (e: Exception) {
            RootShell.ShellResult(-1, "", e.message ?: "未知错误")
        }
    }

    /**
     * Shizuku 13 的 newProcess 是包私有/私有 API，编译器不可直接访问，
     * 通过反射调用（参考 EvalAssist 工程做法）。
     */
    private fun newProcessReflect(
        args: Array<String>,
        env: Array<String>?,
        dir: String?,
    ): Process {
        val method = Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java,
        )
        method.isAccessible = true
        return method.invoke(null, args, env, dir) as Process
    }
}