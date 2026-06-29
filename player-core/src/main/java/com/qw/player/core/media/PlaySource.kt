package com.qw.player.core.media

import com.qw.player.core.media.ClipRange
import com.qw.player.core.media.DrmConfiguration
import com.qw.player.core.media.SubtitleTrack

/**
 * 某个媒体对象的一条具体可播放 source。
 *
 * 一个 [PlayableMedia] 可以包含多条 source，用于表达：
 * - 不同码率
 * - 不同 CDN
 * - 不同音视频流版本
 * - 在线 / 离线版本
 */
data class PlaySource(
    val sourceId: String,
    val url: String,
    val mimeType: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val userAgent: String? = null,
    val drm: DrmConfiguration? = null,
    val subtitles: List<SubtitleTrack> = emptyList(),
    val clipRange: ClipRange? = null,
    val expiresAtMs: Long? = null,
    val tag: Any? = null
)
