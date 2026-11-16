package com.qw.player.runtime

import com.qw.player.core.engine.PlaybackError
import com.qw.player.core.engine.PlaybackState
import com.qw.player.core.media.PlayableMedia
import com.qw.player.core.session.PlaybackSessionListener

/**
 * 会话事件的对外扇出口。
 *
 * session 只能挂一个 listener，而业务侧允许多个观察者（页面、通知、Service），
 * 这层负责把单个 session 回调复制给全部注册者，并保证注册顺序稳定。
 */
internal class PlaybackListenerRegistry : PlaybackSessionListener {

    private val listeners = linkedSetOf<PlaybackSessionListener>()

    fun add(listener: PlaybackSessionListener) {
        listeners.add(listener)
    }

    fun remove(listener: PlaybackSessionListener) {
        listeners.remove(listener)
    }

    fun clear() {
        listeners.clear()
    }

    override fun onPlaybackMediaChanged(current: PlayableMedia, previous: PlayableMedia?) {
        listeners.forEach { it.onPlaybackMediaChanged(current, previous) }
    }

    override fun onPlaybackMediaCleared() {
        listeners.forEach { it.onPlaybackMediaCleared() }
    }

    override fun onPlaybackStateChanged(media: PlayableMedia, state: PlaybackState) {
        listeners.forEach { it.onPlaybackStateChanged(media, state) }
    }

    override fun onPlaybackProgress(media: PlayableMedia, positionMs: Long, durationMs: Long) {
        listeners.forEach { it.onPlaybackProgress(media, positionMs, durationMs) }
    }

    override fun onPlaybackBufferingProgress(media: PlayableMedia, bufferedPositionMs: Long) {
        listeners.forEach { it.onPlaybackBufferingProgress(media, bufferedPositionMs) }
    }

    override fun onPlaybackCompleted(media: PlayableMedia) {
        listeners.forEach { it.onPlaybackCompleted(media) }
    }

    override fun onPlaybackError(media: PlayableMedia?, error: PlaybackError) {
        listeners.forEach { it.onPlaybackError(media, error) }
    }
}
