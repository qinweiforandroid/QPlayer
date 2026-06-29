package com.qw.player.core.engine

/**
 * 统一的播放错误对象。
 *
 * 目标是让上层拿到稳定的错误结构，而不是直接依赖不同播放器内核的原始错误类型。
 */
data class PlaybackError(
    val code: Int,
    val message: String,
    val recoverable: Boolean = false,
    val domain: String = "player",
    val extras: Map<String, String> = emptyMap()
)
