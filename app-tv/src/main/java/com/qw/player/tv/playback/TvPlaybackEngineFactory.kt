package com.qw.player.tv.playback

import android.content.Context
import com.qw.player.core.engine.PlayerConfig
import com.qw.player.core.engine.RoutingPlayerEngine
import com.qw.player.core.media.MediaType
import com.qw.player.media3.Media3PlayerEngine
import com.qw.player.runtime.PlaybackEngineFactory

/**
 * app-tv 的 engine 装配：只路由视频，TV 场景没有文本朗读 / 后台音频需求。
 */
internal class TvPlaybackEngineFactory : PlaybackEngineFactory {

    lateinit var media3Engine: Media3PlayerEngine
        private set

    override fun create(context: Context, config: PlayerConfig): RoutingPlayerEngine {
        val created = Media3PlayerEngine(context)
        media3Engine = created
        return RoutingPlayerEngine(
            engines = mapOf(MediaType.VIDEO to created),
            defaultType = MediaType.VIDEO
        ).apply { initialize(config) }
    }
}
