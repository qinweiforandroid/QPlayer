package com.qw.player.demo.runtime

import android.annotation.SuppressLint
import android.content.Context
import com.qw.player.core.engine.PlaybackSnapshot
import com.qw.player.core.media.PlayableMedia
import com.qw.player.core.session.PlaybackMode
import com.qw.player.core.session.PlaybackSessionListener
import com.qw.player.core.telemetry.PlaybackTelemetry
import com.qw.player.media3.Media3PlayerEngine

/**
 * 视频专用 V2 runtime。
 *
 * 特点：
 * - 不依赖音频前台 Service
 * - 管理音频焦点，但焦点恢复后不自动续播
 * - 生命周期由视频页面自行驱动
 */
@SuppressLint("StaticFieldLeak")
object VideoPlaybackRuntime {
    private val core = BasePlaybackRuntime(
        runtimeName = "VideoPlaybackRuntime",
        allowBackgroundPlayback = false,
        manageAudioFocus = true,
        resumeOnFocusGain = false
    )

    fun initialize(context: Context) = core.initialize(context)

    fun reset() = core.reset()
    fun setQueue(queue: List<PlayableMedia>, startIndex: Int = -1) =
        core.setQueue(queue, startIndex)

    fun play(index: Int) = core.play(index)
    fun play(mediaId: String) = core.play(mediaId)
    fun resume() = core.resume()
    fun pause() = core.pause(false)
    fun stop() = core.stop()
    fun seekTo(positionMs: Long) = core.seekTo(positionMs)
    fun setPlaybackSpeed(speed: Float) = core.setPlaybackSpeed(speed)
    fun setVolume(volume: Float) = core.setVolume(volume)
    fun setMuted(muted: Boolean) = core.setMuted(muted)
    fun setLooping(looping: Boolean) = core.setLooping(looping)
    fun switchSource(sourceId: String): Boolean = core.switchSource(sourceId)
    fun updatePlaybackMode(mode: PlaybackMode) = core.updatePlaybackMode(mode)
    fun getCurrentMedia() = core.getCurrentMedia()
    fun getCurrentIndex() = core.getCurrentIndex()
    fun getQueue() = core.getQueue()
    fun getSnapshot(): PlaybackSnapshot = core.getSnapshot()
    fun setTelemetry(telemetry: PlaybackTelemetry?) =
        core.setTelemetry(telemetry)

    fun addListener(listener: PlaybackSessionListener) = core.addListener(listener)
    fun removeListener(listener: PlaybackSessionListener) = core.removeListener(listener)
    fun getEngine(): Media3PlayerEngine = core.getEngine()
    fun isPlaying() = core.isPlaying()
    fun isPaused() = core.isPaused()
    fun isBuffering() = core.isBuffering()
}
