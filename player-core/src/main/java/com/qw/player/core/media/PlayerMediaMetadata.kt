package com.qw.player.core.media

/**
 * 面向展示层的媒体描述信息。
 *
 * 这个对象故意保持轻量，只承载标题、封面、作者等展示语义。
 * URL、DRM、字幕、headers 等运行时信息属于 [PlaySource]。
 */
data class PlayerMediaMetadata(
    val title: String = "",
    val subtitle: String = "",
    val artist: String = "",
    val album: String = "",
    val description: String = "",
    val coverUrl: String = "",
    val durationMs: Long? = null,
    val extras: Map<String, String> = emptyMap()
)
