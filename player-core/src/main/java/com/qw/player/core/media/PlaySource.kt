package com.qw.player.core.media

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
    /** 是否允许 engine 对这条 source 做本地缓存；直播流应设为 false。 */
    val cacheable: Boolean = true,
    /** 标记这条 source 所属的清晰度分组；同 [qualityId] 的多条 source 视为同一清晰度的不同线路。 */
    val qualityId: String? = null,
    val tag: Any? = null
) {
    /** 判断这条 source 是否已过有效期；没有配置 [expiresAtMs] 时视为不过期。 */
    fun isExpired(nowMs: Long): Boolean {
        val expiresAt = expiresAtMs ?: return false
        return nowMs >= expiresAt
    }
}
