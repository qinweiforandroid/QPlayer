package com.qw.player.core.engine

/**
 * [PlayerEngine] 的启动配置。
 *
 * 这些字段描述的是播放器策略，而不是一次性的播放命令。
 */
data class PlayerConfig(
    val autoPlayWhenReady: Boolean = true,
    val respectAudioFocus: Boolean = true,
    val allowBackgroundPlayback: Boolean = true,
    val enablePreload: Boolean = false,
    val rememberPlaybackPosition: Boolean = true,
    val progressUpdateIntervalMs: Long = 1000L,
    val preferredSeekBackMs: Long = 15_000L,
    val preferredSeekForwardMs: Long = 15_000L
)
