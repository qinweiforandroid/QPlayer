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

    companion object {
        private const val DEFAULT_SOURCE_ID = "default"

        /**
         * 用一个 url 快速构造视频 [PlayableMedia]，省去手写 [PlaySource]/[playSources] 的套娃结构。
         *
         * 不传 [mediaId] 时用 [url] 本身兜底，复杂场景（多码率降级、自定义 headers 等）仍应使用主构造函数。
         */
        fun fromVideoUrl(url: String, mediaId: String = url): PlayableMedia {
            return PlayableMedia(
                mediaId = mediaId,
                mediaType = MediaType.VIDEO,
                playSources = listOf(PlaySource(sourceId = DEFAULT_SOURCE_ID, url = url)),
                defaultSourceId = DEFAULT_SOURCE_ID
            )
        }

        /**
         * 用一个 url 快速构造音频 [PlayableMedia]，省去手写 [PlaySource]/[playSources] 的套娃结构。
         *
         * 不传 [mediaId] 时用 [url] 本身兜底，复杂场景（多码率降级、自定义 headers 等）仍应使用主构造函数。
         */
        fun fromAudioUrl(url: String, mediaId: String = url): PlayableMedia {
            return PlayableMedia(
                mediaId = mediaId,
                mediaType = MediaType.AUDIO,
                playSources = listOf(PlaySource(sourceId = DEFAULT_SOURCE_ID, url = url)),
                defaultSourceId = DEFAULT_SOURCE_ID
            )
        }

        /**
         * 用一段正文快速构造文本朗读 [PlayableMedia]。
         *
         * 正文通过 [PlaySource.inlineText] 内联，不需要远程 url。
         */
        fun fromText(text: String, mediaId: String, title: String = ""): PlayableMedia {
            return PlayableMedia(
                mediaId = mediaId,
                mediaType = MediaType.TEXT,
                metadata = PlayerMediaMetadata(title = title),
                playSources = listOf(
                    PlaySource(sourceId = DEFAULT_SOURCE_ID, url = "", inlineText = text)
                ),
                defaultSourceId = DEFAULT_SOURCE_ID
            )
        }
    }
}
