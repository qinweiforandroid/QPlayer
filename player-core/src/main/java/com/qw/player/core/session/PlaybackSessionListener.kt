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

    /** 当前会话所对应的 engine 快照变化时回调。 */
    fun onPlaybackChanged(snapshot: PlaybackSnapshot) {}

    /** 当前 item 在会话内播放完成时回调。 */
    fun onPlaybackCompleted(snapshot: PlaybackSnapshot) {}

    /** 当前 item 解析失败或播放失败时回调。 */
    fun onPlaybackError(error: PlaybackError, snapshot: PlaybackSnapshot) {}
}
