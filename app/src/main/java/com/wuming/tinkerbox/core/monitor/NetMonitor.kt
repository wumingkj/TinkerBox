package com.wuming.tinkerbox.core.monitor

import android.net.TrafficStats
import android.os.SystemClock

/**
 * 网络流量快照。
 *
 * [rxRateBps]/[txRateBps] 为收/发速率（字节/秒，基于相邻两次采样差分）；
 * [rxTotalBytes]/[txTotalBytes] 为累计收/发总量（收/发自开机累计）。
 */
data class NetState(
    val rxRateBps: Long = 0L,
    val txRateBps: Long = 0L,
    val rxTotalBytes: Long = 0L,
    val txTotalBytes: Long = 0L,
)

/**
 * 网络流量监控。
 *
 * 走官方 [TrafficStats] 累计计数器（系统在 JNI 层通过内核/网络栈统计，非直读 /proc/net，
 * 不受应用侧 SELinux 拦截），普通模式即可采集，无需 root。
 *
 * 方法：按「相邻两次采样的累计差分」得出实时速率，与 CPU 读取同一节奏（约 1s）轮询。
 * 部分设备因统计被限制会返回 [-TrafficStats.UNSUPPORTED]，这里归一化为 0。
 * 本对象持有上一次采样游标，设计为仅由单一采样协程串行调用。
 */
object NetMonitor {

    private var lastTimeMs = 0L
    private var lastRx = 0L
    private var lastTx = 0L

    fun read(): NetState {
        val now = SystemClock.elapsedRealtime()

        var rx = runCatching { TrafficStats.getTotalRxBytes() }.getOrDefault(-1L)
        var tx = runCatching { TrafficStats.getTotalTxBytes() }.getOrDefault(-1L)
        if (rx < 0L) rx = 0L
        if (tx < 0L) tx = 0L

        val dtMs = (now - lastTimeMs).coerceAtLeast(1L)
        var rxRate = 0L
        var txRate = 0L
        if (lastTimeMs != 0L) {
            rxRate = ((rx - lastRx) * 1000L / dtMs).coerceAtLeast(0L)
            txRate = ((tx - lastTx) * 1000L / dtMs).coerceAtLeast(0L)
        }

        lastTimeMs = now
        lastRx = rx
        lastTx = tx
        return NetState(
            rxRateBps = rxRate,
            txRateBps = txRate,
            rxTotalBytes = rx,
            txTotalBytes = tx,
        )
    }
}