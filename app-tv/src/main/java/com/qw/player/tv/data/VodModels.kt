package com.qw.player.tv.data

/**
 * 清晰度档位。
 */
data class VodQuality(
    val id: String,
    val label: String,
    val url: String
)

/**
 * 单个分P（剧集）。
 */
data class VodEpisode(
    val id: String,
    val title: String,
    val qualities: List<VodQuality>
)

/**
 * 一次点播播放的完整数据。
 */
data class VodPlaylist(
    val title: String,
    val coverUrl: String,
    val author: String,
    val episodes: List<VodEpisode>
)
