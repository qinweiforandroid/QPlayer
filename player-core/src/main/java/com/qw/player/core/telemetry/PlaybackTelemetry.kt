package com.qw.player.core.telemetry

import com.qw.player.core.engine.PlaybackError

/**
 * 播放内核的轻量可观测性协议。
 *
 * 这一层只负责「产出事件」，具体上报后端（友盟 / 自建 / 其他）
 * 由业务层实现本接口后注入，core 不感知任何具体平台。
 */
interface PlaybackTelemetry {

    /** 一次播放会话的整体结果。 */
    fun onPlaybackResult(event: PlaybackResultEvent) {}

    /** 首帧渲染耗时。 */
    fun onFirstFrame(event: FirstFrameEvent) {}

    /** 一次卡顿（进入缓冲 → 恢复播放）。 */
    fun onStall(event: StallEvent) {}

    /** 播放错误。 */
    fun onError(event: ErrorEvent) {}

    /**
     * 一次播放事件的基础上下文。
     *
     * 全部使用内核自己就能拿到的字段，避免业务层为了上报而反向依赖业务模型。
     */
    data class Context(
        val mediaId: String?,
        val sourceId: String?,
        val mediaDurationMs: Long
    )

    enum class Outcome {
        /** 自然播放完成。 */
        COMPLETED,

        /** 用户主动停止。 */
        STOPPED,

        /** 出错中断。 */
        FAILED
    }

    data class PlaybackResultEvent(
        val context: Context,
        val outcome: Outcome,
        /** 实际播放的时长（非媒体总时长）。 */
        val playedDurationMs: Long,
        val error: PlaybackError? = null
    )

    data class FirstFrameEvent(
        val context: Context,
        /** 从 setMedia 到首帧可渲染的耗时。 */
        val timeToFirstFrameMs: Long
    )

    data class StallEvent(
        val context: Context,
        val positionMs: Long,
        /** 本次卡顿持续的时长。 */
        val stallDurationMs: Long
    )

    data class ErrorEvent(
        val context: Context,
        val error: PlaybackError
    )
}
