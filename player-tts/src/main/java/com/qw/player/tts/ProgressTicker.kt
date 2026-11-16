package com.qw.player.tts

/**
 * [TextPlayerEngine] 用来驱动周期进度上报的计时器。
 *
 * 独立成接口是为了让 engine 的进度逻辑可以在纯 JVM 单测里验证，
 * 生产环境默认走 [PodPlayerProgressTicker]。
 */
interface ProgressTicker {
    fun setOnTick(listener: (() -> Unit)?)
    fun start(intervalMs: Long)
    fun stop()
}
