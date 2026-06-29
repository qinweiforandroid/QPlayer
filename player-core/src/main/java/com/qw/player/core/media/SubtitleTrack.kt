package com.qw.player.core.media

/**
 * 与 [PlaySource] 关联的一条可选字幕轨。
 */
data class SubtitleTrack(
    val id: String,
    val label: String,
    val url: String,
    val mimeType: String? = null,
    val language: String? = null,
    val isDefault: Boolean = false
)
