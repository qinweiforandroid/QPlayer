package com.qw.player.tv.playback

import android.content.Context
import androidx.media3.exoplayer.ExoPlayer
import com.qw.player.core.engine.PlaybackSnapshot
import com.qw.player.core.media.PlayableMedia
import com.qw.player.core.session.PlaybackSessionListener
import com.qw.player.runtime.BasePlaybackRuntime

/**
 * TV 专用播放 runtime。
 *
 * 客厅场景没有锁屏/切后台的情形（遥控器直接退出 Activity），
 * 所以不开后台播放；焦点恢复后不自动续播，交给页面按当前状态决定。
 */
class TvPlaybackRuntime {

    private val engineFactory = TvPlaybackEngineFactory()

    private val core = BasePlaybackRuntime(
        runtimeName = "TvPlaybackRuntime",
        allowBackgroundPlayback = false,
        manageAudioFocus = true,
        resumeOnFocusGain = false,
        engineFactory = engineFactory
    )

    fun initialize(context: Context) = core.initialize(context)

    /** 页面销毁时彻底释放 engine / session。 */
    fun release() = core.reset()

    fun setMediaList(queue: List<PlayableMedia>, startIndex: Int = -1) =
        core.setMediaList(queue, startIndex)

    fun play(index: Int) = core.play(index)
    fun resume() = core.resume()
    fun pause() = core.pause(false)
    fun stop() = core.stop()
    fun seekTo(positionMs: Long) = core.seekTo(positionMs)
    fun switchSource(sourceId: String): Boolean = core.switchSource(sourceId)
    fun setPlaybackSpeed(speed: Float) = core.setPlaybackSpeed(speed)
    fun setMuted(muted: Boolean) = core.setMuted(muted)
    fun setLooping(looping: Boolean) = core.setLooping(looping)
    fun updatePlaybackMode(mode: com.qw.player.core.session.PlaybackMode) =
        core.updatePlaybackMode(mode)

    fun skipToNext() = core.skipToNext()
    fun skipToPrevious() = core.skipToPrevious()
    fun getSnapshot(): PlaybackSnapshot = core.getSnapshot()
    fun getCurrentMedia() = core.getCurrentMedia()
    fun getCurrentIndex(): Int = core.getCurrentIndex()

    fun addListener(listener: PlaybackSessionListener) = core.addListener(listener)
    fun removeListener(listener: PlaybackSessionListener) = core.removeListener(listener)
    fun isPlaying() = core.isPlaying()

    /** 直接拿底层 ExoPlayer 挂给 Compose 的 [androidx.media3.ui.compose.PlayerSurface]。 */
    fun getExoPlayer(): ExoPlayer = engineFactory.media3Engine.getExoPlayer()
}
