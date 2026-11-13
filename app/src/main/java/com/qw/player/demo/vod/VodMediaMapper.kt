package com.qw.player.demo.vod

import com.qw.player.core.media.MediaType
import com.qw.player.core.media.PlaySource
import com.qw.player.core.media.PlayableMedia
import com.qw.player.core.media.PlayerMediaMetadata

/**
 * 点播数据到播放协议的转换。
 *
 * 清晰度直接映射为 [PlayableMedia.playSources]，
 * 切换清晰度复用 runtime 的 switchSource，不新增协议。
 */
object VodMediaMapper {

    fun toPlayableMedia(
        episode: VodEpisode,
        playlistTitle: String,
        coverUrl: String,
        defaultQualityId: String? = null
    ): PlayableMedia {
        val sources = episode.qualities.map { quality ->
            PlaySource(
                sourceId = quality.id,
                url = quality.url
            )
        }
        val sourceLabels = episode.qualities.associate { it.id to it.label }
        return PlayableMedia(
            mediaId = episode.id,
            mediaType = MediaType.VIDEO,
            metadata = PlayerMediaMetadata(
                title = episode.title,
                subtitle = playlistTitle,
                artist = playlistTitle,
                coverUrl = coverUrl,
                extras = sourceLabels.mapKeys { "quality_label_${it.key}" }
            ),
            playSources = sources,
            defaultSourceId = defaultQualityId ?: sources.firstOrNull()?.sourceId
        )
    }
}
