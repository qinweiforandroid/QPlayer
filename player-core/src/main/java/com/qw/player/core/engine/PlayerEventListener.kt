package com.qw.player.core.engine

/**
 * 底层 engine 事件监听器。
 *
 * 遵循最少知道原则：每个回调只携带该事件本身必需的最小信息；
 * 需要完整运行时状态的消费者应自行调用 [PlayerEngine.getSnapshot] 拉取。
 *
 * 如果关心的是队列切换、当前 item 变化等会话语义，
 * 应使用 [PlaybackSessionListener]。
 */
interface PlayerEventListener {
    /** engine 状态发生变化时回调。 */
    fun onStateChanged(state: PlaybackState) {}

    /** 媒体准备好后，按固定周期上报播放进度。 */
    fun onProgress(positionMs: Long, durationMs: Long) {}

    /** 第一帧真正可渲染时回调。 */
    fun onRenderedFirstFrame() {}

    /** 视频尺寸可用或发生变化时回调。 */
    fun onVideoSizeChanged(size: VideoSize) {}

    /** 播放进入缓冲状态期间，按缓冲进度回调。 */
    fun onBufferingProgress(bufferedPositionMs: Long, durationMs: Long) {}

    /** 当前 source 自然播放结束时回调。 */
    fun onCompleted() {}

    /** engine 报告不可恢复播放错误时回调。 */
    fun onError(error: PlaybackError) {}
}
