package com.qw.player.core.session

import com.qw.player.core.engine.PlayerEngine
import com.qw.player.core.engine.PlaybackSnapshot
import com.qw.player.core.media.PlayableMedia
import com.qw.player.core.source.MediaSourceResolver

/**
 * 构建在 [PlayerEngine] 之上的会话层播放协调器。
 *
 * 它负责：
 * - 当前播放队列
 * - 当前索引和当前媒体
 * - 重复 / 随机播放模式
 * - source 解析与切歌编排
 *
 * 它不直接负责底层解码和渲染，那部分仍然属于 [PlayerEngine]。
 */
interface PlaybackSession {
    /** 绑定当前会话所使用的 engine。 */
    fun bindEngine(engine: PlayerEngine)

    /** 注入播放前要使用的 source resolver。 */
    fun setSourceResolver(resolver: MediaSourceResolver?)

    /** 替换播放队列，并可选择指定初始索引。 */
    fun setQueue(queue: List<PlayableMedia>, startIndex: Int = -1)

    /** 返回当前不可变队列视图。 */
    fun getQueue(): List<PlayableMedia>

    /** 返回当前逻辑媒体项。 */
    fun getCurrentMedia(): PlayableMedia?

    /** 返回当前索引；没有选中任何媒体时返回 -1。 */
    fun getCurrentIndex(): Int

    /** 更新后续切歌要使用的播放模式。 */
    fun updatePlaybackMode(mode: PlaybackMode)

    /** 返回当前会话播放模式。 */
    fun getPlaybackMode(): PlaybackMode

    /** 播放指定索引上的媒体项。 */
    fun play(index: Int)

    /** 播放 mediaId 对应的媒体项。 */
    fun play(mediaId: String)

    /** 播放一个具体媒体对象，无论它是否已经在队列中。 */
    fun play(media: PlayableMedia)

    /** 暂停当前播放。 */
    fun pause()

    /** 恢复当前播放，或重新进入当前选中媒体的播放流程。 */
    fun resume()

    /** 停止当前会话播放，但不销毁 session 对象本身。 */
    fun stop()

    /** 在当前 item 内执行 seek。 */
    fun seekTo(positionMs: Long)

    /** 按当前播放模式切到下一项。 */
    fun skipToNext(auto: Boolean = false)

    /** 按当前播放模式切到上一项。 */
    fun skipToPrevious(auto: Boolean = false)

    /** 返回当前会话已知的最新快照。 */
    fun getSnapshot(): PlaybackSnapshot

    /** 添加会话观察者。 */
    fun addListener(listener: PlaybackSessionListener)

    /** 移除会话观察者。 */
    fun removeListener(listener: PlaybackSessionListener)

    /** 释放会话持有的资源，并从 engine 上解除绑定。 */
    fun release()
}
