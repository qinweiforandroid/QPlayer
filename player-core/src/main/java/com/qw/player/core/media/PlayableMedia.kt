package com.qw.player.core.media

/**
 * V2 协议中的标准媒体对象。
 *
 * 它同时适配完整音频播放器和视频播放器。
 */
data class PlayableMedia(
    val mediaId: String,
    val mediaType: MediaType,
    val metadata: PlayerMediaMetadata = PlayerMediaMetadata(),
    val playSources: List<PlaySource> = emptyList(),
    val defaultSourceId: String? = null,
    val extras: Map<String, String> = emptyMap(),
    val tag: Any? = null
) {
    /**
     * 根据 [defaultSourceId] 选出默认 source。
     *
     * 如果没有显式默认值，则使用第一条 source 作为兜底。
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
