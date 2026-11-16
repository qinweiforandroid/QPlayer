package com.qw.player.runtime

import android.content.Context
import android.net.wifi.WifiManager

/**
 * 后台播放期间维持 WiFi 性能档位的协议。
 */
public interface WifiLockKeeper {
    /** 获取 WiFi 锁，已持有时是幂等的。 */
    fun acquire()

    /** 释放 WiFi 锁，未持有或重复释放是幂等的。 */
    fun release()
}

public fun interface WifiLockFactory {
    fun create(context: Context, tag: String, enabled: Boolean): WifiLockKeeper
}

/**
 * 后台音频流媒体占住 WifiLock，避免熄屏后 WiFi 进入省电模式导致缓冲中断。
 *
 * [enabled] 为 false 时（视频页）所有调用都是空操作。
 */
public class AndroidWifiLockKeeper(
    private val context: Context,
    private val tag: String,
    private val enabled: Boolean
) : WifiLockKeeper {

    private var lock: WifiManager.WifiLock? = null

    override fun acquire() {
        if (!enabled || lock != null) {
            return
        }
        val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return
        val created = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, tag)
        created.setReferenceCounted(false)
        created.acquire()
        lock = created
    }

    override fun release() {
        val current = lock ?: return
        runCatching { current.release() }
        lock = null
    }
}
