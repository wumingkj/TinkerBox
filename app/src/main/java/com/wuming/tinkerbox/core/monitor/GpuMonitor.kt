package com.wuming.tinkerbox.core.monitor

import android.content.Context
import android.util.DisplayMetrics
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.GLES20
import android.view.WindowManager
import java.io.File

/**
 * GPU 静态属性（公开 API 读取，普通模式即可得，对齐 AIDA64 无 root 效果）。
 * [model]/[vendor]/[glVersion] 经离屏 EGL + glGetString；[resolution]/[refreshRateHz] 经 Display。
 */
data class GpuInfo(
    val model: String = "",
    val vendor: String = "",
    val glVersion: String = "",
    val resolution: String = "",
    val refreshRateHz: Float = 0f,
) {
    val hasModel: Boolean get() = model.isNotBlank()

    /** 一句话摘要，用于 GPU 卡片副标题：型号 · 刷新率 · 分辨率。 */
    val summary: String by lazy {
        val parts = mutableListOf<String>()
        if (model.isNotBlank()) parts += model
        if (refreshRateHz > 0f) parts += "${refreshRateHz}Hz"
        if (resolution.isNotBlank()) parts += resolution
        parts.joinToString(" · ")
    }
}

/**
 * 通过公开 API 读取 GPU 静态属性：型号 / 厂商 / GL 版本（EGL 离屏 context + glGetString）、
 * 分辨率 / 刷新率（DisplayMetrics）。静态信息只读一次并缓存。
 */
object GpuInfoReader {
    @Volatile
    private var cached: GpuInfo? = null

    fun read(context: Context): GpuInfo {
        cached?.let { return it }
        val info = readNow(context)
        cached = info
        return info
    }

    private fun readNow(context: Context): GpuInfo {
        var vendor = ""
        var model = ""
        var gl = ""
        runCatching {
            val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            if (display == EGL14.EGL_NO_DISPLAY) return@runCatching
            val version = IntArray(2)
            if (EGL14.eglInitialize(display, version, 0, version, 1)) {
                val attribs = intArrayOf(
                    EGL14.EGL_RED_SIZE, 8,
                    EGL14.EGL_GREEN_SIZE, 8,
                    EGL14.EGL_BLUE_SIZE, 8,
                    EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                    EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                    EGL14.EGL_NONE,
                )
                val configs = arrayOfNulls<EGLConfig>(1)
                val numConfig = IntArray(1)
                if (EGL14.eglChooseConfig(display, attribs, 0, configs, 0, 1, numConfig, 0) && numConfig[0] > 0) {
                    val ctx = EGL14.eglCreateContext(
                        display, configs[0], EGL14.EGL_NO_CONTEXT,
                        intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0,
                    )
                    val surfAttribs = intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE)
                    val surf = EGL14.eglCreatePbufferSurface(display, configs[0], surfAttribs, 0)
                    if (ctx != EGL14.EGL_NO_CONTEXT && surf != EGL14.EGL_NO_SURFACE &&
                        EGL14.eglMakeCurrent(display, surf, surf, ctx)
                    ) {
                        vendor = GLES20.glGetString(GLES20.GL_VENDOR) ?: ""
                        model = GLES20.glGetString(GLES20.GL_RENDERER) ?: ""
                        gl = GLES20.glGetString(GLES20.GL_VERSION) ?: ""
                    }
                    EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                    if (surf != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surf)
                    if (ctx != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, ctx)
                }
                EGL14.eglTerminate(display)
            }
        }

        var width = 0
        var height = 0
        var refresh = 0f
        runCatching {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm?.defaultDisplay?.getRealMetrics(metrics)
            width = metrics.widthPixels
            height = metrics.heightPixels
            refresh = wm?.defaultDisplay?.refreshRate?.let { r -> kotlin.math.round(r).toFloat() } ?: 0f
        }

        val resolution = if (width > 0 && height > 0) "${width}×${height}" else ""
        return GpuInfo(model.trim(), vendor.trim(), gl.trim(), resolution, refresh)
    }
}

/**
 * GPU 状态快照。
 *
 * [curMhz]/[minMhz]/[maxMhz] 为当前/最小/最大频率（MHz，读不到时低位为 0）；
 * [loadPct] 为 busy 占比 0f..1f（无法读取时为 0）。
 * [privilegedOnly] 表示实时负载/频率在普通模式下因 SELinux 读不到、需 root/shizuku 特权。
 */
data class GpuState(
    val curMhz: Double = 0.0,
    val minMhz: Double = 0.0,
    val maxMhz: Double = 0.0,
    val loadPct: Float = 0f,
    val privilegedOnly: Boolean = false,
    val info: GpuInfo = GpuInfo(),
) {
    /** 负载驱动进度（0..1）：优先 busy%，缺失时按 min~max 频率归一化。 */
    val progress: Float
        get() {
            if (loadPct > 0f) return loadPct.coerceIn(0f, 1f)
            if (maxMhz > minMhz) return ((curMhz - minMhz) / (maxMhz - minMhz)).toFloat()
                .coerceIn(0f, 1f)
            return 0f
        }

    /** 是否取到有效频率/负载（可渲染实时负载/频率）。 */
    val available: Boolean get() = loadPct > 0f || curMhz > 0.0
}

/**
 * GPU 实时负载/频率监控。
 *
 * GPU 无任何公开 SDK API 可读频率/占用，只能读内核 sysfs（kgsl/devfreq）。而本机
 * （ColorOS/A15）的 /sys/class/kgsl 连 shell uid 都 `Permission denied`。因此：
 *  - root/shizuku：经 [execRaw] 特权 `cat` 读取；
 *  - 普通模式：先尝试直接 File 读——若机型未设 SELinux 限制、sysfs 0644 开放，则如
 *    AIDA64 般无 root 即可读到；读不到（如本机）则标记 [GpuState.privilegedOnly]，UI 提示。
 *
 * @param execRaw 可选的权限执行器（root/shizuku）：给定 shell 命令返回其 stdout（失败返回 null）。
 *                为 null 时尝试直接 File 读。
 */
object GpuMonitor {

    /** 读取 GPU 状态。 */
    fun read(execRaw: ((String) -> String?)? = null): GpuState {
        // —— 频率（单位为归一化到 kHz）——
        // gpuclk 节点在多数设备返回 kHz，但部分（本机骁龙 8gen2）直接返回 Hz；按量级启发式归一：
        // 远超 1e6（意味着 >1GHz）一定是 Hz，转到 kHz；否则维持 kHz。
        var curKhz = readNum("cat /sys/class/kgsl/kgsl-3d0/gpuclk", execRaw = execRaw)
        if (curKhz > 0L && curKhz >= 1_000_000L) curKhz /= 1000L
        if (curKhz <= 0L) {
            curKhz = readNum("cat /sys/class/kgsl/kgsl-3d0/devfreq/cur_freq", execRaw = execRaw, div = 1000L)
        }
        if (curKhz <= 0L) {
            curKhz = readNum("cat /sys/class/devfreq/63d00000.qcom,kgsl-3d0/cur_freq", execRaw = execRaw, div = 1000L)
        }

        // min/max 频率（devfreq 为 Hz，kgsl min/max_gpuclk 为 kHz）。
        val minFreqHz = readIdx(
            "cat /sys/class/kgsl/kgsl-3d0/devfreq/min_freq",
            "cat /sys/class/kgsl/kgsl-3d0/min_gpuclk",
            execRaw,
        )
        val maxFreqHz = readIdx(
            "cat /sys/class/kgsl/kgsl-3d0/devfreq/max_freq",
            "cat /sys/class/kgsl/kgsl-3d0/max_gpuclk",
            execRaw,
        )

        // —— 负载（busy %）——
        var load = readLoad("cat /sys/class/kgsl/kgsl-3d0/gpu_busy_percentage", execRaw)
        if (load <= 0f) load = readLoad("cat /sys/class/kgsl/kgsl-3d0/gpubusy", execRaw)
        if (load <= 0f) load = readLoad("cat /sys/class/kgsl/kgsl-3d0/gpu_busy", execRaw)

        val available = load > 0f || curKhz > 0L
        // 普通模式（无特权执行器）读不到 → 判定为被 SELinux 拦截、需特权。
        val privilegedOnly = !available && execRaw == null

        return GpuState(
            curMhz = curKhz / 1000.0,
            minMhz = minFreqHz / 1_000_000.0,
            maxMhz = maxFreqHz / 1_000_000.0,
            loadPct = load,
            privilegedOnly = privilegedOnly,
        )
    }

    /**
     * 读取节点首整数。[div] 用于 devfreq Hz → kHz 换算；[mult] 用于 kgsl kHz → Hz 换算。
     */
    private fun readNum(cmd: String, execRaw: ((String) -> String?)? = null, div: Long = 1L, mult: Long = 1L): Long {
        val x = readText(cmd, execRaw)?.toLongOrNull()?.let { it * mult / div } ?: 0L
        return if (x < 0L) 0L else x
    }

    /** 频率候选：优先 devfreq（Hz 原值，回传 Hz），兜底 kgsl min/max_gpuclk（kHz，本机可能是 Hz → 归一）。 */
    private fun readIdx(devfreq: String, kgsl: String, execRaw: ((String) -> String?)? = null): Long {
        val d = readText(devfreq, execRaw)?.toLongOrNull() ?: 0L
        if (d > 0L) return d
        var k = readNum(kgsl, execRaw = execRaw) // 原值（可能 kHz 或 Hz）
        if (k > 0L && k >= 1_000_000L) k /= 1000L // Hz → kHz
        return k * 1000L // → Hz
    }

    /** 负载类节点：可能输出 "10 90"（busy idle）或单值百分比。取首个非零且 ≤100 的数值。 */
    private fun readLoad(cmd: String, execRaw: ((String) -> String?)? = null): Float {
        val t = readText(cmd, execRaw) ?: return 0f
        for (tok in t.split(Regex("\\s+"))) {
            val v = tok.toFloatOrNull() ?: continue
            if (v in 1f..100f) return v / 100f
        }
        return 0f
    }

    /** 读取命令的输出原文（trim 后）；nil/空 → null。privileged 时走 [execRaw]，否则直读 sysfs。 */
    private fun readText(cmd: String, execRaw: ((String) -> String?)? = null): String? {
        if (execRaw != null) {
            return execRaw(cmd)?.trim()?.takeIf { it.isNotEmpty() }
        }
        val path = cmd.removePrefix("cat ").trim()
        return runCatching { File(path).readText().trim() }.getOrNull()?.takeIf { it.isNotEmpty() }
    }
}