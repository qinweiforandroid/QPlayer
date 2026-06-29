package com.qw.player.core.media

/**
 * source 级别的裁剪播放区间。
 *
 * 常见场景：
 * - 试看
 * - 服务端下发片段播放
 * - 某个章节或片段内的断点续播
 */
data class ClipRange(
    val startPositionMs: Long = 0L,
    val endPositionMs: Long? = null
)
