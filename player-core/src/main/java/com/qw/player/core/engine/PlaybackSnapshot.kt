package com.qw.player.core.engine

import com.qw.player.core.engine.PlaybackError
import com.qw.player.core.engine.PlaybackState
import com.qw.player.core.engine.VideoSize

/**
 * 播放器运行时快照。
 *
 * UI、通知和埋点应优先消费这个对象，而不是自己拼多个 getter。
 */
data class PlaybackSnapshot(
    val state: PlaybackState = PlaybackState.IDLE,
    val currentMediaId: String? = null,
    val currentSourceId: String? = null,
    val isPrepared: Boolean = false,
    val playWhenReady: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedPositionMs: Long = 0L,
    val bufferedPercent: Int = 0,
    val speed: Float = 1f,
    val volume: Float = 1f,
    val isMuted: Boolean = false,
    val videoSize: VideoSize? = null,
    val error: PlaybackError? = null
)
