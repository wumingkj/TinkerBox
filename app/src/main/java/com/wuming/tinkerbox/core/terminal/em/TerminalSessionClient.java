package com.wuming.tinkerbox.core.terminal.em;

/**
 * Minimal client callback interface required by the migrated terminal-emulator core.
 *
 * 仅保留已迁移的仿真核心（TerminalEmulator / TerminalBuffer / Logger / 图像类）实际调用的方法。
 * 与原版 termux 的 TerminalSessionClient 不同：不依赖 PTY 会话对象（TerminalSession），
 * 因此可在「无 NDK / ProcessBuilder」的骨架后端下直接编译运行。
 */
public interface TerminalSessionClient {

    Integer getTerminalCursorStyle();

    void onTerminalCursorStateChange(boolean setting);

    void logError(String logTag, String message);

    void logWarn(String logTag, String message);

    void logInfo(String logTag, String message);

    void logDebug(String logTag, String message);

    void logVerbose(String logTag, String message);

}