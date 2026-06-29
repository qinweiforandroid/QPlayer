package com.qw.player.core.engine

import com.qw.player.core.engine.PlaybackError
import com.qw.player.core.engine.PlaybackSnapshot
import com.qw.player.core.engine.VideoSize

/**
 * 底层 engine 事件监听器。
 *
 * 如果关心的是队列切换、当前 item 变化等会话语义，
 * 应使用 [PlaybackSessionListener]。
 */
interface PlayerEventListener {
    /** engine 状态快照变化时回调。 */
    fun onStateChanged(snapshot: PlaybackSnapshot) {}

    /** 媒体准备好后，按固定周期上报播放进度。 */
    fun onProgress(snapshot: PlaybackSnapshot) {}

    /** 第一帧真正可渲染时回调。 */
    fun onRenderedFirstFrame() {}

    /** 视频尺寸可用或发生变化时回调。 */
    fun onVideoSizeChanged(size: VideoSize) {}

    /** 播放进入或停留在缓冲状态时回调。 */
    fun onBuffering(snapshot: PlaybackSnapshot) {}

    /** 当前 source 自然播放结束时回调。 */
    fun onCompletion(snapshot: PlaybackSnapshot) {}

    /** engine 报告不可恢复播放错误时回调。 */
    fun onError(error: PlaybackError, snapshot: PlaybackSnapshot) {}
}
