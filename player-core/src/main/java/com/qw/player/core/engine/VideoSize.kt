package com.qw.player.core.engine

/**
 * 视频尺寸信息。
 *
 * 由 engine 在流信息可用后回调给上层。
 */
data class VideoSize(
    val width: Int,
    val height: Int,
    val rotationDegrees: Int = 0,
    val pixelWidthHeightRatio: Float = 1f
)
