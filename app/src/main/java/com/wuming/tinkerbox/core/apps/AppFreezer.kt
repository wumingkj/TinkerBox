package com.wuming.tinkerbox.core.apps

import com.wuming.tinkerbox.core.shell.RootShell

/**
 * 冻结 / 解冻执行器。
 *
 * 不同模式注入不同的命令执行器：
 *  - Shizuku：使用 shell 权限（user 2000）执行 pm 命令
 *  - Root：   使用 su root 执行 pm 命令（可操作更多系统应用）
 *
 * 冻结语义（Android pm 标准）：
 *  - DISABLE_USER（停用，图标保留但卸载入口变灰）  : pm disable-user --user 0 <pkg>
 *  - DISABLE    （完全禁用）                        : pm disable --user 0 <pkg>
 *  - HIDE       （隐藏）                            : pm hide --user 0 <pkg>
 *  - SUSPEND    （挂起，运行中应用会被瞬杀）          : pm suspend --user 0 <pkg>
 *
 * 状态检测统一基于 `dumpsys package <pkg>` 的 `User 0:` 行字段
 * （enabled= / hidden= / suspended=），不做进程内记忆——这样即便在
 * 命令行或其他工具动了状态，也能如实反映并精准解冻。
 */
class AppFreezer(
    private val executor: (Array<String>) -> RootShell.ShellResult,
) {

    /** 是否可执行 pm 冻结操作（当前模式具备 shell/root 能力）。 */
    val available: Boolean get() = true

    /**
     * 统一命令入口：执行并把结果写入 [CommandLog]。
     * 所有 freeze / unfreeze / detectStates / listFrozen 都经此记录。
     */
    private fun exec(vararg args: String): RootShell.ShellResult {
        val result = runCatching { executor(arrayOf(*args)) }
            .getOrElse { RootShell.ShellResult(-1, "", it.message ?: it.toString()) }
        CommandLog.log(
            command = args.joinToString(" "),
            exitCode = result.exitCode,
            stdout = result.stdout,
            stderr = result.stderr,
            tag = args.firstOrNull() ?: "",
        )
        return result
    }

    /** 逐个读取包的真实冻结状态（基于 dumpsys 用户 0 状态行）。 */
    fun detectStates(pkg: String): Set<FreezeState> {
        val out = exec("dumpsys", "package", pkg).stdout

        val user0 = out.lineSequence()
            .firstOrNull { it.trimStart().startsWith("User 0:") }
            .orEmpty()

        val states = linkedSetOf<FreezeState>()
        // enabled 字段（COMPONENT_ENABLED_STATE）：
        //   0=默认正常 1=启用 2=禁用(disable) 3=停用(disable-user) 4=未用启用
        val enabled = Regex("enabled=(-?\\d+)").find(user0)?.groupValues?.get(1)?.toIntOrNull()
        when (enabled) {
            2 -> states += FreezeState.DISABLE
            3 -> states += FreezeState.DISABLE_USER
        }
        if ("hidden=true" in user0) states += FreezeState.HIDE
        if ("suspended=true" in user0) states += FreezeState.SUSPEND
        return states
    }

    /** 实时校验单个包当前是否处于任何冻结态。 */
    fun isFrozen(pkg: String): Boolean = detectStates(pkg).isNotEmpty()

    /** 读取当前真实处于「冻结」状态的应用集合。
     *
     * 综合多种状态，用 pm 自带的按状态筛选一次拿全，避免逐包 dumpsys：
     *  - `pm list packages -d` → disabled（停用/禁用，enabled=2/3）
     *  - `pm list packages -s` → suspended（挂起=灰图标）
     */
    fun listFrozen(): Set<String> = runCatching {
        val result = linkedSetOf<String>()
        // disabled（停用 + 禁用）
        exec("pm", "list", "packages", "-d", "-u").stdout.lines()
            .mapNotNull(::parsePackage)
            .forEach { result += it }
        // suspended（挂起，灰图标应用）
        exec("pm", "list", "packages", "-s").stdout.lines()
            .mapNotNull(::parsePackage)
            .forEach { result += it }
        result
    }.getOrDefault(emptySet())

    private fun parsePackage(line: String): String? =
        line.trim().removePrefix("package:")
            .takeIf { it.isNotBlank() && !it.contains(":") }

    /** 冻结应用。 */
    fun freeze(pkg: String, mode: FreezeMode): Boolean = runCatching {
        val r = exec(*mode.command(pkg))
        r.exitCode == 0 || r.isSuccess
    }.getOrDefault(false)

    /** 解冻应用：先读真实状态，再对症执行所需命令。 */
    fun unfreeze(pkg: String): Boolean {
        val states = detectStates(pkg)
        if (states.isEmpty()) return true // 本就没有任何冻结态，无需处理

        var succeeded = false

        // enabled 相关（停用/禁用）用 pm enable 恢复
        if (FreezeState.DISABLE in states || FreezeState.DISABLE_USER in states) {
            succeeded = execSuccess("pm", "enable", "--user", "0", pkg) || succeeded
        }
        // 隐藏用 pm unhide 恢复
        if (FreezeState.HIDE in states) {
            succeeded = execSuccess("pm", "unhide", "--user", "0", pkg) || succeeded
        }
        // 挂起用 pm unsuspend 恢复
        if (FreezeState.SUSPEND in states) {
            succeeded = execSuccess("pm", "unsuspend", "--user", "0", pkg) || succeeded
        }
        return succeeded
    }

    /** 卸载应用（静默，面向三方应用；系统应用一般会失败并返回 false）。 */
    fun uninstall(pkg: String): Boolean = runCatching {
        // 保留 /data，仅移除当前用户视图；系统应用失败时由调用方兜底提示
        val r = exec("pm", "uninstall", "--user", "0", pkg)
        r.exitCode == 0 || r.isSuccess
    }.getOrDefault(false)

    private fun execSuccess(vararg args: String): Boolean = runCatching {
        val r = exec(*args)
        r.exitCode == 0 || r.isSuccess
    }.getOrDefault(false)

    companion object {
        /** 用 Root 权限构建（Root 模式）。 */
        fun forRoot(): AppFreezer = AppFreezer { args ->
            RootShell.exec(args.joinToString(" ")) // su -c "pm ..."
        }

        /** 用 Shizuku 权限构建（Shizuku 模式）。 */
        fun forShizuku(): AppFreezer =
            AppFreezer { args -> com.wuming.tinkerbox.core.mode.ShizukuManager.exec(args) }
    }
}

/** 检测到的一个具体冻结状态。 */
enum class FreezeState {
    DISABLE,       // 完全禁用 enabled=0
    DISABLE_USER,  // 停用 enabled=2
    HIDE,          // 隐藏 hidden=true
    SUSPEND,       // 挂起 suspended=true
}

/**
 * 操作模式集合（冻结时可选择的动作）。
 *
 * 只保留轻量的「挂起」与「隐藏」两类：
 *  - 挂起 SUSPEND：立即停掉运行中进程并挂起（灰图标、不可启动），解冻即恢复。
 *  - 隐藏 HIDE   ：从桌面/启动器中隐藏图标，应用本体仍在、可用于不再想看到的预装应用。
 * 停用 / 禁用已被移除：语义过重，系统应用深受其害，一般不作为日常玩法操作。
 */
enum class FreezeMode(val title: String, val desc: String) {
    HIDE("隐藏", "(隐藏桌面图标,无法运行)"),
    SUSPEND("挂起", "(图标变灰,无法运行)"),
    ;

    /** 生成对应 pm 冻结命令。 */
    fun command(pkg: String): Array<String> = when (this) {
        HIDE -> arrayOf("pm", "hide", "--user", "0", pkg)
        SUSPEND -> arrayOf("pm", "suspend", "--user", "0", pkg)
    }
}