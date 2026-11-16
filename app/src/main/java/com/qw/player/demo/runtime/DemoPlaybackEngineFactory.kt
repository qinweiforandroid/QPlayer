package com.qw.player.demo.runtime

import android.content.Context
import com.qw.player.core.engine.PlayerConfig
import com.qw.player.core.engine.RoutingPlayerEngine
import com.qw.player.core.media.MediaType
import com.qw.player.media3.Media3PlayerEngine
import com.qw.player.runtime.PlaybackEngineFactory
import com.qw.player.tts.AndroidTtsSynthesizer
import com.qw.player.tts.TextPlayerEngine

/**
 * demo 的 engine 装配：音视频共用 Media3，文本朗读走 TTS。
 *
 * 每个 runtime 各持有一个实例，因此 [media3Engine] 不会在两个 runtime 之间串用。
 */
internal class DemoPlaybackEngineFactory : PlaybackEngineFactory {

    lateinit var media3Engine: Media3PlayerEngine
        private set

    override fun create(context: Context, config: PlayerConfig): RoutingPlayerEngine {
        val created = Media3PlayerEngine(context)
        media3Engine = created
        return RoutingPlayerEngine(
            engines = mapOf(
                MediaType.AUDIO to created,
                MediaType.VIDEO to created,
                MediaType.TEXT to TextPlayerEngine(AndroidTtsSynthesizer(context))
            ),
            defaultType = MediaType.VIDEO
        ).apply { initialize(config) }
    }
}
