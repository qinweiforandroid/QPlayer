package com.qw.player.core.engine

import com.qw.player.core.media.PlaySource
import com.qw.player.core.media.PlayableMedia

/**
 * 底层播放内核协议。
 *
 * 它负责：
 * - 持有真正的播放器实例
 * - 一次只播放一条已解析的 source
 * - 对外暴露统一的状态和事件
 *
 * 它不负责：
 * - 队列管理
 * - 下一首 / 上一首决策
 * - 业务层 source 解析
 */
interface PlayerEngine {
    /** 在挂载媒体前应用播放器启动策略。 */
    fun initialize(config: PlayerConfig = PlayerConfig())

    /**
     * 把一条已经解析好的 source 挂载到 engine。
     *
     * 除了替换当前媒体外，这个调用应尽量保持轻量。
     * 真正的解码和渲染准备通常在 [prepare] 或 [play] 中发生。
     */
    fun setMedia(media: PlayableMedia, source: PlaySource, startPositionMs: Long = 0L)

    /** 可选地预热某条 source，但不立刻开始播放。 */
    fun preload(media: PlayableMedia, source: PlaySource)

    /** 异步准备当前挂载的媒体。 */
    fun prepare()

    /** 开始或恢复播放。 */
    fun play()

    /** 暂停播放，并尽量保留 prepared 状态。 */
    fun pause()

    /** 停止播放。若 [reset] 为 true，同时清空当前挂载媒体。 */
    fun stop(reset: Boolean = false)

    /** 在当前 source 内执行 seek。 */
    fun seekTo(positionMs: Long)

    /** 调整播放速度。 */
    fun setPlaybackSpeed(speed: Float)

    /** 设置播放器输出音量，范围为 0f..1f。 */
    fun setVolume(volume: Float)

    /** 设置静音状态，与上层记录的音量偏好分离。 */
    fun setMuted(muted: Boolean)

    /** 绑定或清空当前视频输出目标。 */
    fun attachVideoOutput(output: PlayerVideoOutput?)

    /** 设置 engine 级唯一事件监听器。 */
    fun setListener(listener: PlayerEventListener?)

    /** 返回当前已知的最新运行时快照。 */
    fun getSnapshot(): PlaybackSnapshot

    /** 返回当前 engine 的能力声明。 */
    fun getCapabilities(): PlayerCapabilities

    /** 永久释放播放器资源。 */
    fun release()
}
