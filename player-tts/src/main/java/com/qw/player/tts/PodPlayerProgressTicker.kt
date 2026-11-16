package com.qw.player.tts

import com.qw.player.core.common.PodPlayerTimer

/** 基于 [PodPlayerTimer] 的默认 [ProgressTicker] 实现。 */
class PodPlayerProgressTicker : ProgressTicker {

    private val timer = PodPlayerTimer()

    init {
        timer.setOnPodPlayerTimerListener {
            tickListener?.invoke()
        }
    }

    private var tickListener: (() -> Unit)? = null

    override fun setOnTick(listener: (() -> Unit)?) {
        tickListener = listener
    }

    override fun start(intervalMs: Long) {
        timer.start(intervalMs)
    }

    override fun stop() {
        timer.stop()
    }
}
