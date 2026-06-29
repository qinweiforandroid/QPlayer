package com.qw.player.core.media

/**
 * [MediaSourceResolver] 的解析结果。
 *
 * resolver 可以只返回更新后的 source 和 metadata，
 * 而不要求调用方重新构造整个 [PlayableMedia]。
 */
data class ResolvedMedia(
    val mediaId: String,
    val playSources: List<PlaySource>,
    val defaultSourceId: String? = null,
    val metadata: PlayerMediaMetadata? = null
) {
    /**
     * 按与 [PlayableMedia] 相同的策略选出默认解析结果。
     */
    fun resolveDefaultSource(): PlaySource? {
        if (playSources.isEmpty()) {
            return null
        }
        if (defaultSourceId.isNullOrEmpty()) {
            return playSources.first()
        }
        return playSources.firstOrNull { it.sourceId == defaultSourceId } ?: playSources.first()
    }
}
