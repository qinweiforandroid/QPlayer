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
 * 音频专用 V2 runtime。
 *
 * 特点：
 * - 共享前台 Service
 * - 管理音频焦点
 * - 允许后台播放
 */
@SuppressLint("StaticFieldLeak")
object AudioPlaybackRuntime {
    private val core = BasePlaybackRuntime(
        runtimeName = "AudioPlaybackRuntime",
        allowBackgroundPlayback = true,
        manageAudioFocus = true
    )

    fun initialize(context: Context, resolver: MediaSourceResolver? = null) = core.initialize(context, resolver)
    fun setSourceResolver(resolver: MediaSourceResolver?) = core.setSourceResolver(resolver)
    fun setQueue(queue: List<PlayableMedia>, startIndex: Int = -1) = core.setQueue(queue, startIndex)
    fun play(index: Int) = core.play(index)
    fun play(mediaId: String) = core.play(mediaId)
    fun resume() = core.resume()
    fun pause(resumeWhenFocusGain: Boolean = false) = core.pause(resumeWhenFocusGain)
    fun stop() = core.stop()
    fun seekTo(positionMs: Long) = core.seekTo(positionMs)
    fun skipToNext() = core.skipToNext()
    fun skipToPrevious() = core.skipToPrevious()
    fun updatePlaybackMode(mode: PlaybackMode) = core.updatePlaybackMode(mode)
    fun getCurrentMedia() = core.getCurrentMedia()
    fun getCurrentIndex() = core.getCurrentIndex()
    fun getQueue() = core.getQueue()
    fun getSnapshot(): PlaybackSnapshot = core.getSnapshot()
    fun addListener(listener: PlaybackSessionListener) = core.addListener(listener)
    fun removeListener(listener: PlaybackSessionListener) = core.removeListener(listener)
    fun getEngine(): Media3PlayerEngine = core.getEngine()
    fun isPlaying() = core.isPlaying()
    fun isPaused() = core.isPaused()
    fun isBuffering() = core.isBuffering()
}
