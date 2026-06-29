package com.qw.player.core.engine

/**
 * 某个播放器内核的能力描述。
 *
 * 上层在展示字幕、裁剪播放、倍速等高级能力前，
 * 应先参考这里的能力声明。
 */
data class PlayerCapabilities(
    val supportsVideoOutput: Boolean = false,
    val supportsSpeedControl: Boolean = false,
    val supportsPreload: Boolean = false,
    val supportsBackgroundPlayback: Boolean = false,
    val supportsHeaders: Boolean = true,
    val supportsDrm: Boolean = false,
    val supportsSubtitles: Boolean = false,
    val supportsClipping: Boolean = false,
    val minSpeed: Float = 1f,
    val maxSpeed: Float = 1f
)
