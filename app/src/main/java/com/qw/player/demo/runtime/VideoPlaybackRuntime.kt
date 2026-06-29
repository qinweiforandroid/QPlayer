package com.qw.player.demo.runtime

import android.annotation.SuppressLint
import android.content.Context
import com.qw.player.core.source.MediaSourceResolver
import com.qw.player.core.media.PlayableMedia
import com.qw.player.core.session.PlaybackMode
import com.qw.player.core.session.PlaybackSessionListener
import com.qw.player.core.engine.PlaybackSnapshot
import com.qw.player.media3.Media3PlayerEngine

/**
 * 视频专用 V2 runtime。
 *
 * 特点：
 * - 不依赖音频前台 Service
 * - 不管理音频焦点
 * - 生命周期由视频页面自行驱动
 */
@SuppressLint("StaticFieldLeak")
object VideoPlaybackRuntime {
    private val core = BasePlaybackRuntime(
        runtimeName = "VideoPlaybackRuntime",
        allowBackgroundPlayback = false,
        manageAudioFocus = false
    )

    fun initialize(context: Context, resolver: MediaSourceResolver? = null) = core.initialize(context, resolver)
    fun setSourceResolver(resolver: MediaSourceResolver?) = core.setSourceResolver(resolver)
    fun setQueue(queue: List<PlayableMedia>, startIndex: Int = -1) = core.setQueue(queue, startIndex)
    fun play(index: Int) = core.play(index)
    fun play(mediaId: String) = core.play(mediaId)
    fun resume() = core.resume()
    fun pause() = core.pause(false)
    fun stop() = core.stop()
    fun seekTo(positionMs: Long) = core.seekTo(positionMs)
    fun updatePlaybackMode(mode: PlaybackMode) = core.updatePlaybackMode(mode)
    fun getCurrentMedia() = core.getCurrentMedia()
    fun getSnapshot(): PlaybackSnapshot = core.getSnapshot()
    fun addListener(listener: PlaybackSessionListener) = core.addListener(listener)
    fun removeListener(listener: PlaybackSessionListener) = core.removeListener(listener)
    fun getEngine(): Media3PlayerEngine = core.getEngine()
}
