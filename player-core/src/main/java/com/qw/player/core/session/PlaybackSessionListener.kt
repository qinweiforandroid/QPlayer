package com.qw.player.core.session

import com.qw.player.core.engine.PlaybackError
import com.qw.player.core.engine.PlaybackSnapshot
import com.qw.player.core.media.PlayableMedia

/**
 * 队列 / 会话层监听器。
 *
 * 它位于 [PlayerEventListener] 之上，更适合页面、通知、后台服务这类关心
 * “当前播到哪一个 item” 和 “队列如何变化” 的场景。
 */
interface PlaybackSessionListener {
    /** 队列内容或当前索引变化时回调。 */
    fun onQueueChanged(queue: List<PlayableMedia>, currentIndex: Int) {}

    /** 当前逻辑媒体项切换时回调。 */
    fun onCurrentMediaChanged(current: PlayableMedia?, previous: PlayableMedia?) {}

    /** 重复 / 随机播放策略变化时回调。 */
    fun onPlaybackModeChanged(mode: PlaybackMode) {}

    /** 当前会话所对应的 engine 快照变化时回调（播放状态切换，例如 PLAYING/PAUSED/ERROR）。 */
    fun onPlaybackChanged(snapshot: PlaybackSnapshot) {}

    /**
     * 缓冲开始时回调（网络卡顿、预加载等），与播放状态切换是两类不同的信号，
     * 可能在不改变 [onPlaybackChanged] 状态的情况下单独触发。
     * 只关心整体播放状态的消费者（通知栏、埋点等）不需要重写这个方法。
     */
    fun onBuffering(snapshot: PlaybackSnapshot) {}

    /**
     * 播放进度定时更新时回调（默认 1 秒一次）。
     *
     * 只关心状态变化、不需要逐秒刷新的消费者（通知栏、埋点等）不要重写这个方法，
     * 避免被进度 tick 刷屏。
     */
    fun onProgress(snapshot: PlaybackSnapshot) {}

    /** 当前 item 在会话内播放完成时回调。 */
    fun onPlaybackCompleted(snapshot: PlaybackSnapshot) {}

    /** 当前 item 解析失败或播放失败时回调。 */
    fun onPlaybackError(error: PlaybackError, snapshot: PlaybackSnapshot) {}
}
