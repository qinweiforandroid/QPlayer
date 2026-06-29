package com.qw.player.core.session

import com.qw.player.core.engine.PlaybackError
import com.qw.player.core.engine.PlaybackSnapshot
import com.qw.player.core.engine.PlaybackState
import com.qw.player.core.engine.PlayerEngine
import com.qw.player.core.engine.PlayerEventListener
import com.qw.player.core.media.PlayableMedia
import com.qw.player.core.media.ResolvedMedia
import com.qw.player.core.source.MediaSourceResolver
import kotlin.random.Random

/**
 * V2 默认会话实现。
 *
 * 这是一层纯协议语义上的 session，核心职责包括：
 * - 管理播放队列
 * - 决定当前播放项
 * - 调用 [MediaSourceResolver] 解析 source
 * - 驱动 [PlayerEngine] 执行真实播放
 * - 处理自动下一首、随机播放、重复播放等会话级逻辑
 *
 * 这里直接围绕 V2 协议建模。
 */
class DefaultPlaybackSession : PlaybackSession {

    private val queue = mutableListOf<PlayableMedia>()
    private val listeners = linkedSetOf<PlaybackSessionListener>()

    private var engine: PlayerEngine? = null
    private var resolver: MediaSourceResolver? = null
    private var playbackMode: PlaybackMode = PlaybackMode()
    private var currentIndex: Int = -1
    private var currentMedia: PlayableMedia? = null
    private var latestSnapshot: PlaybackSnapshot = PlaybackSnapshot()
    private var resolveToken: Long = 0L

    private val engineListener = object : PlayerEventListener {
        override fun onStateChanged(snapshot: PlaybackSnapshot) {
            latestSnapshot = snapshot
            notifyPlaybackChanged(snapshot)
        }

        override fun onProgress(snapshot: PlaybackSnapshot) {
            latestSnapshot = snapshot
            notifyPlaybackChanged(snapshot)
        }

        override fun onBuffering(snapshot: PlaybackSnapshot) {
            latestSnapshot = snapshot
            notifyPlaybackChanged(snapshot)
        }

        override fun onCompletion(snapshot: PlaybackSnapshot) {
            latestSnapshot = snapshot
            notifyPlaybackCompleted(snapshot)
            skipToNext(auto = true)
        }

        override fun onError(error: PlaybackError, snapshot: PlaybackSnapshot) {
            latestSnapshot = snapshot
            notifyPlaybackError(error, snapshot)
        }
    }

    override fun bindEngine(engine: PlayerEngine) {
        this.engine?.setListener(null)
        this.engine = engine
        engine.setListener(engineListener)
        latestSnapshot = engine.getSnapshot()
    }

    override fun setSourceResolver(resolver: MediaSourceResolver?) {
        this.resolver = resolver
    }

    override fun setQueue(queue: List<PlayableMedia>, startIndex: Int) {
        val previousMedia = currentMedia
        this.queue.clear()
        this.queue.addAll(queue)
        when {
            startIndex in this.queue.indices -> {
                currentIndex = startIndex
                currentMedia = this.queue[startIndex]
            }

            currentMedia != null -> {
                currentIndex = this.queue.indexOfFirst { it.mediaId == currentMedia?.mediaId }
                currentMedia = if (currentIndex in this.queue.indices) this.queue[currentIndex] else null
            }

            else -> {
                currentIndex = -1
                currentMedia = null
            }
        }
        notifyQueueChanged()
        if (previousMedia != currentMedia) {
            notifyCurrentMediaChanged(currentMedia, previousMedia)
        }
    }

    override fun getQueue(): List<PlayableMedia> {
        return queue.toList()
    }

    override fun getCurrentMedia(): PlayableMedia? {
        return currentMedia
    }

    override fun getCurrentIndex(): Int {
        return currentIndex
    }

    override fun updatePlaybackMode(mode: PlaybackMode) {
        playbackMode = mode
        listeners.forEach { it.onPlaybackModeChanged(mode) }
    }

    override fun getPlaybackMode(): PlaybackMode {
        return playbackMode
    }

    override fun play(index: Int) {
        if (index !in queue.indices) {
            notifySessionError(
                PlaybackError(
                    code = -1,
                    message = "播放索引越界: $index"
                )
            )
            return
        }
        val media = queue[index]
        switchCurrentMedia(media, index)
        resolveAndPlay(media)
    }

    override fun play(mediaId: String) {
        val index = queue.indexOfFirst { it.mediaId == mediaId }
        if (index >= 0) {
            play(index)
            return
        }
        val current = currentMedia
        if (current?.mediaId == mediaId) {
            resolveAndPlay(current)
            return
        }
        notifySessionError(
            PlaybackError(
                code = -1,
                message = "队列中未找到 mediaId=$mediaId"
            )
        )
    }

    override fun play(media: PlayableMedia) {
        val index = queue.indexOfFirst { it.mediaId == media.mediaId }
        switchCurrentMedia(media, if (index >= 0) index else -1)
        resolveAndPlay(media)
    }

    override fun pause() {
        engineOrError()?.pause()
    }

    override fun resume() {
        val engine = engineOrError() ?: return
        val snapshot = engine.getSnapshot()
        when {
            snapshot.state == PlaybackState.PAUSED || snapshot.state == PlaybackState.READY -> {
                engine.play()
            }

            currentMedia != null -> {
                resolveAndPlay(requireNotNull(currentMedia))
            }

            queue.isNotEmpty() -> {
                play(if (currentIndex in queue.indices) currentIndex else 0)
            }

            else -> {
                notifySessionError(
                    PlaybackError(
                        code = -1,
                        message = "没有可恢复播放的媒体"
                    )
                )
            }
        }
    }

    override fun stop() {
        engineOrError()?.stop(reset = false)
    }

    override fun seekTo(positionMs: Long) {
        engineOrError()?.seekTo(positionMs)
    }

    override fun skipToNext(auto: Boolean) {
        val nextIndex = findNextIndex(auto) ?: return
        play(nextIndex)
    }

    override fun skipToPrevious(auto: Boolean) {
        val previousIndex = findPreviousIndex(auto) ?: return
        play(previousIndex)
    }

    override fun getSnapshot(): PlaybackSnapshot {
        return engine?.getSnapshot() ?: latestSnapshot
    }

    override fun addListener(listener: PlaybackSessionListener) {
        listeners.add(listener)
    }

    override fun removeListener(listener: PlaybackSessionListener) {
        listeners.remove(listener)
    }

    override fun release() {
        resolveToken++
        engine?.setListener(null)
        engine = null
        resolver = null
        queue.clear()
        currentIndex = -1
        currentMedia = null
        latestSnapshot = PlaybackSnapshot()
        listeners.clear()
    }

    /**
     * 解析当前媒体并驱动 engine 进入播放流程。
     *
     * 优先级：
     * 1. 先走 resolver，拿到最新可用 source
     * 2. 没有 resolver 时，退化为直接使用 media 自带 source
     */
    private fun resolveAndPlay(media: PlayableMedia) {
        val engine = engineOrError() ?: return
        val requestId = ++resolveToken
        val resolver = this.resolver
        if (resolver != null) {
            resolver.resolve(media, object : MediaSourceResolver.Callback {
                override fun onSuccess(result: ResolvedMedia) {
                    if (requestId != resolveToken) {
                        return
                    }
                    val mergedMedia = mergeResolvedMedia(media, result)
                    val source = mergedMedia.resolveDefaultSource()
                    if (source == null) {
                        notifySessionError(
                            PlaybackError(
                                code = -1,
                                message = "Resolver 未返回可用 source"
                            )
                        )
                        return
                    }
                    switchCurrentMedia(mergedMedia, currentIndex)
                    engine.setMedia(mergedMedia, source)
                    engine.prepare()
                    engine.play()
                }

                override fun onFailure(error: PlaybackError) {
                    if (requestId != resolveToken) {
                        return
                    }
                    notifySessionError(error)
                }
            })
            return
        }

        val source = media.resolveDefaultSource()
        if (source == null) {
            notifySessionError(
                PlaybackError(
                    code = -1,
                    message = "当前媒体没有可直接播放的 source，且未提供 resolver"
                )
            )
            return
        }
        engine.setMedia(media, source)
        engine.prepare()
        engine.play()
    }

    /**
     * 用 resolver 返回的结果更新当前媒体对象。
     */
    private fun mergeResolvedMedia(
        media: PlayableMedia,
        result: ResolvedMedia
    ): PlayableMedia {
        return media.copy(
            metadata = result.metadata ?: media.metadata,
            playSources = result.playSources,
            defaultSourceId = result.defaultSourceId
        )
    }

    private fun switchCurrentMedia(media: PlayableMedia?, newIndex: Int) {
        val previous = currentMedia
        currentMedia = media
        currentIndex = newIndex
        if (previous != media) {
            notifyCurrentMediaChanged(media, previous)
        }
        notifyQueueChanged()
    }

    private fun notifyQueueChanged() {
        val queueSnapshot = queue.toList()
        listeners.forEach { it.onQueueChanged(queueSnapshot, currentIndex) }
    }

    private fun notifyCurrentMediaChanged(current: PlayableMedia?, previous: PlayableMedia?) {
        listeners.forEach { it.onCurrentMediaChanged(current, previous) }
    }

    private fun notifyPlaybackChanged(snapshot: PlaybackSnapshot) {
        listeners.forEach { it.onPlaybackChanged(snapshot) }
    }

    private fun notifyPlaybackCompleted(snapshot: PlaybackSnapshot) {
        listeners.forEach { it.onPlaybackCompleted(snapshot) }
    }

    private fun notifyPlaybackError(error: PlaybackError, snapshot: PlaybackSnapshot) {
        listeners.forEach { it.onPlaybackError(error, snapshot) }
    }

    private fun notifySessionError(error: PlaybackError) {
        val snapshot = latestSnapshot.copy(
            state = PlaybackState.ERROR,
            currentMediaId = currentMedia?.mediaId ?: latestSnapshot.currentMediaId,
            error = error
        )
        latestSnapshot = snapshot
        notifyPlaybackError(error, snapshot)
    }

    private fun engineOrError(): PlayerEngine? {
        val boundEngine = engine
        if (boundEngine != null) {
            return boundEngine
        }
        notifySessionError(
            PlaybackError(
                code = -1,
                message = "PlaybackSession 尚未绑定 PlayerEngine"
            )
        )
        return null
    }

    /**
     * 计算下一首索引。
     *
     * 约定：
     * - 自动切歌 + RepeatMode.ONE：仍然停留在当前项
     * - shuffle 为 true 时优先随机
     * - RepeatMode.ALL 允许头尾循环
     */
    private fun findNextIndex(auto: Boolean): Int? {
        if (queue.isEmpty()) {
            return null
        }
        if (currentIndex !in queue.indices) {
            return 0
        }
        if (auto && playbackMode.repeatMode == RepeatMode.ONE) {
            return currentIndex
        }
        if (playbackMode.shuffleEnabled && queue.size > 1) {
            return randomIndexExcluding(currentIndex)
        }
        val next = currentIndex + 1
        return when {
            next < queue.size -> next
            playbackMode.repeatMode == RepeatMode.ALL -> 0
            else -> null
        }
    }

    /**
     * 计算上一首索引。
     */
    private fun findPreviousIndex(auto: Boolean): Int? {
        if (queue.isEmpty()) {
            return null
        }
        if (currentIndex !in queue.indices) {
            return 0
        }
        if (auto && playbackMode.repeatMode == RepeatMode.ONE) {
            return currentIndex
        }
        if (playbackMode.shuffleEnabled && queue.size > 1) {
            return randomIndexExcluding(currentIndex)
        }
        val previous = currentIndex - 1
        return when {
            previous >= 0 -> previous
            playbackMode.repeatMode == RepeatMode.ALL -> queue.lastIndex
            else -> null
        }
    }

    private fun randomIndexExcluding(excludedIndex: Int): Int {
        var candidate = excludedIndex
        while (candidate == excludedIndex) {
            candidate = Random.nextInt(queue.size)
        }
        return candidate
    }
}
