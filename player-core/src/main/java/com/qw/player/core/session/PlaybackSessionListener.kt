package com.qw.player.core.session

import com.qw.player.core.engine.PlaybackError
import com.qw.player.core.engine.PlaybackState
import com.qw.player.core.media.PlayableMedia

/**
 * 队列 / 会话层监听器。
 *
 * 它位于 [PlayerEventListener] 之上，更适合页面、通知、后台服务这类关心
 * “当前播到哪一个 item” 和 “队列如何变化” 的场景。
 *
 * 与 engine 层的区别：会话生命周期内会经历多条 media，因此每个播放事件
 * 都显式携带事件所属的 [PlayableMedia]，避免消费者为了判断“这是哪条 media
 * 的事件”而额外维护状态。
 */
interface PlaybackSessionListener {
    /** 当前逻辑媒体项切换到另一个媒体项时回调。 */
    fun onPlaybackMediaChanged(current: PlayableMedia, previous: PlayableMedia?) {}

    /** 队列被清空或新队列中已没有当前项时回调，表示当前没有任何选中媒体。 */
    fun onPlaybackMediaCleared() {}

    /** 当前媒体对应的播放状态变化时回调。 */
    fun onPlaybackStateChanged(media: PlayableMedia, state: PlaybackState) {}

    /** 播放进度定时更新时回调（默认 1 秒一次）。 */
    fun onPlaybackProgress(media: PlayableMedia, positionMs: Long, durationMs: Long) {}

    /**
     * 缓冲进度更新时回调（网络卡顿、预加载等），与播放状态切换是两类不同的信号，
     * 可能在不改变 [onPlaybackStateChanged] 状态的情况下单独触发。
     */
    fun onPlaybackBufferingProgress(media: PlayableMedia, bufferedPositionMs: Long) {}

    /** 当前媒体在会话内播放完成时回调。 */
    fun onPlaybackCompleted(media: PlayableMedia) {}

    /**
     * 当前媒体解析失败或播放失败时回调。
     *
     * [media] 为 null 表示该错误发生在会话层、尚不属于任何一条具体媒体
     * （例如队列为空、未绑定 engine）。
     */
    fun onPlaybackError(media: PlayableMedia?, error: PlaybackError) {}
}
