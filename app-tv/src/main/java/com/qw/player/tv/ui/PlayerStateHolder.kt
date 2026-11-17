package com.qw.player.tv.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.qw.player.core.engine.PlaybackSnapshot
import com.qw.player.core.media.PlayableMedia
import com.qw.player.core.session.PlaybackSessionListener
import com.qw.player.tv.playback.TvPlaybackRuntime

/**
 * 订阅 runtime 并把最新快照转成 Compose 状态。
 *
 * 事件回调只标脏，真正的快照在每次重组时统一取 [TvPlaybackRuntime.getSnapshot]，
 * 避免在监听器里拼状态导致与主动查询不一致。
 */
class PlayerStateHolder(private val runtime: TvPlaybackRuntime) : PlaybackSessionListener {
    var snapshot by mutableStateOf(runtime.getSnapshot())
        private set
    var currentMedia by mutableStateOf(runtime.getCurrentMedia())
        private set

    private fun refresh() {
        snapshot = runtime.getSnapshot()
        currentMedia = runtime.getCurrentMedia()
    }

    override fun onPlaybackMediaChanged(current: PlayableMedia, previous: PlayableMedia?) = refresh()
    override fun onPlaybackMediaCleared() = refresh()
    override fun onPlaybackStateChanged(media: PlayableMedia, state: com.qw.player.core.engine.PlaybackState) = refresh()
    override fun onPlaybackProgress(media: PlayableMedia, positionMs: Long, durationMs: Long) = refresh()
    override fun onPlaybackCompleted(media: PlayableMedia) = refresh()
    override fun onPlaybackError(media: PlayableMedia?, error: com.qw.player.core.engine.PlaybackError) = refresh()
}

@Composable
fun rememberPlayerState(runtime: TvPlaybackRuntime): PlayerStateHolder {
    val holder = remember(runtime) { PlayerStateHolder(runtime) }
    DisposableEffect(runtime, holder) {
        runtime.addListener(holder)
        onDispose { runtime.removeListener(holder) }
    }
    return holder
}
