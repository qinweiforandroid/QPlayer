package com.qw.player.core.session

import com.qw.player.core.engine.PlaybackError
import com.qw.player.core.engine.PlaybackSnapshot
import com.qw.player.core.engine.PlaybackState
import com.qw.player.core.engine.PlayerEngine
import com.qw.player.core.engine.PlayerEventListener
import com.qw.player.core.media.PlayableMedia
import kotlin.random.Random

/**
 * V2 默认会话实现。
 *
 * 这是一层纯协议语义上的 session，核心职责包括：
 * - 管理播放队列
 * - 决定当前播放项
 * - 驱动 [PlayerEngine] 执行真实播放
 * - 处理自动下一首、随机播放、重复播放等会话级逻辑
 *
 * 这里直接围绕 V2 协议建模。调用方需要保证传入的 [PlayableMedia] 已经带有可直接播放的 source；
 * 如果有 id → 真实地址的异步解析需求，应在 session 外部完成。
 */
class DefaultPlaybackSession(
    private val sourceSelector: SourceSelector = DefaultSourceSelector()
) : PlaybackSession {

    private val medias = mutableListOf<PlayableMedia>()
    private val listeners = linkedSetOf<PlaybackSessionListener>()

    private var engine: PlayerEngine? = null
    private var playbackMode: PlaybackMode = PlaybackMode()
    private var curIndex: Int = -1
    private var curMedia: PlayableMedia? = null
    private var latestSnapshot: PlaybackSnapshot = PlaybackSnapshot()

    /**
     * 下一次 setMedia 使用的起播位置。
     *
     * 用于切换清晰度 / 线路时保持当前进度，消费后立即清零。
     */
    private var pendingStartPositionMs: Long = 0L

    /** 当前媒体内已经尝试过（并失败）的 source id，媒体切换时清空。 */
    private val failedSourceIds = mutableSetOf<String>()

    private val engineListener = object : PlayerEventListener {
        override fun onStateChanged(state: PlaybackState) {
            curMedia?.let { media ->
                listeners.forEach { it.onPlaybackStateChanged(media, state) }
            }
        }

        override fun onProgress(positionMs: Long, durationMs: Long) {
            curMedia?.let { media ->
                listeners.forEach { it.onPlaybackProgress(media, positionMs, durationMs) }
            }
        }

        override fun onBufferingProgress(bufferedPositionMs: Long, durationMs: Long) {
            curMedia?.let { media ->
                listeners.forEach { it.onPlaybackBufferingProgress(media, bufferedPositionMs) }
            }
        }

        override fun onCompleted() {
            curMedia?.let { media ->
                listeners.forEach { it.onPlaybackCompleted(media) }
            }
            skipToNext(auto = true)
        }

        override fun onError(error: PlaybackError) {
            pullSnapshot()
            if (!error.recoverable && tryFallbackToNextSource()) {
                return
            }
            notifyPlaybackError(curMedia, error)
        }
    }

    override fun bindEngine(engine: PlayerEngine) {
        this.engine?.setListener(null)
        this.engine = engine
        engine.setListener(engineListener)
        latestSnapshot = engine.getSnapshot()
    }

    /**
     * 从 engine 拉取最新快照。
     *
     * 仅用于 [tryFallbackToNextSource] 这类 session 内部降级逻辑需要的
     * sourceId / positionMs，不用于对外的 [PlaybackSessionListener] 转发。
     */
    private fun pullSnapshot(): PlaybackSnapshot {
        val snapshot = engine?.getSnapshot() ?: latestSnapshot
        latestSnapshot = snapshot
        return snapshot
    }

    override fun setMediaList(queue: List<PlayableMedia>, startIndex: Int) {
        val previousMedia = curMedia
        this.medias.clear()
        this.medias.addAll(queue)
        when {
            startIndex in this.medias.indices -> {
                curIndex = startIndex
                curMedia = this.medias[startIndex]
            }

            curMedia != null -> {
                curIndex = this.medias.indexOfFirst { it.mediaId == curMedia?.mediaId }
                curMedia = if (curIndex in this.medias.indices) this.medias[curIndex] else null
            }

            else -> {
                curIndex = -1
                curMedia = null
            }
        }
        if (previousMedia != curMedia) {
            notifyMediaTransition(curMedia, previousMedia)
        }
    }

    override fun getMediaList(): List<PlayableMedia> {
        return medias.toList()
    }

    override fun getCurrentMedia(): PlayableMedia? {
        return curMedia
    }

    override fun getCurrentIndex(): Int {
        return curIndex
    }

    override fun updatePlaybackMode(mode: PlaybackMode) {
        playbackMode = mode
    }

    override fun getPlaybackMode(): PlaybackMode {
        return playbackMode
    }

    override fun play(index: Int) {
        if (index !in medias.indices) {
            notifySessionError(PlaybackError(code = -1, message = "播放索引越界: $index"))
            return
        }
        val media = medias[index]
        switchCurrentMedia(media, index)
        resolveAndPlay(media)
    }

    override fun play(mediaId: String) {
        val index = medias.indexOfFirst { it.mediaId == mediaId }
        if (index >= 0) {
            play(index)
            return
        }
        val current = curMedia
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
        val index = medias.indexOfFirst { it.mediaId == media.mediaId }
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

            curMedia != null -> {
                resolveAndPlay(requireNotNull(curMedia))
            }

            medias.isNotEmpty() -> {
                play(if (curIndex in medias.indices) curIndex else 0)
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
        engine?.setListener(null)
        engine = null
        medias.clear()
        curIndex = -1
        curMedia = null
        latestSnapshot = PlaybackSnapshot()
        listeners.clear()
    }

    /**
     * 使用 media 自带 source 驱动 engine 进入播放流程。
     *
     * 调用方需要保证传入的 media 已经带有可直接播放的 source。
     */
    private fun resolveAndPlay(media: PlayableMedia) {
        failedSourceIds.clear()
        val engine = engineOrError() ?: return
        val source = sourceSelector.selectInitial(media)
        if (source == null) {
            notifySessionError(
                PlaybackError(
                    code = -1,
                    message = "当前媒体没有可直接播放的 source"
                )
            )
            return
        }
        if (source.isExpired(System.currentTimeMillis())) {
            notifySessionError(
                PlaybackError(
                    code = -1,
                    message = "当前 source 已过期: ${source.sourceId}"
                )
            )
            return
        }
        engine.setMedia(media, source, consumePendingStartPosition())
        engine.prepare()
        engine.play()
    }

    /** 设置下一次播放要使用的起播位置，消费一次后自动清零。 */
    fun setPendingStartPosition(positionMs: Long) {
        pendingStartPositionMs = positionMs.coerceAtLeast(0L)
    }

    private fun consumePendingStartPosition(): Long {
        val position = pendingStartPositionMs
        pendingStartPositionMs = 0L
        return position
    }

    /**
     * 当前 source 不可恢复失败后，尝试同一媒体下的下一条未失败过的 source。
     *
     * 返回 true 表示已经发起了降级播放，调用方不应再把这次错误上抛给外部监听者。
     */
    private fun tryFallbackToNextSource(): Boolean {
        val media = curMedia ?: return false
        val engine = this.engine ?: return false
        val snapshot = latestSnapshot
        val currentSourceId = snapshot.currentSourceId ?: return false
        val currentSource = media.playSources.firstOrNull { it.sourceId == currentSourceId } ?: return false
        failedSourceIds.add(currentSourceId)
        val nextSource = sourceSelector.selectFallback(media, currentSource, failedSourceIds) ?: return false
        val resumePositionMs = snapshot.currentPositionMs.coerceAtLeast(0L)
        engine.setMedia(media, nextSource, resumePositionMs)
        engine.prepare()
        engine.play()
        return true
    }

    private fun switchCurrentMedia(media: PlayableMedia?, newIndex: Int) {
        val previous = curMedia
        curMedia = media
        curIndex = newIndex
        if (previous != media) {
            notifyMediaTransition(media, previous)
        }
    }

    /** 根据 [current] 是否为空，分流到“切换到具体媒体”或“清空当前项”两种信号。 */
    private fun notifyMediaTransition(current: PlayableMedia?, previous: PlayableMedia?) {
        if (current != null) {
            listeners.forEach { it.onPlaybackMediaChanged(current, previous) }
        } else {
            listeners.forEach { it.onPlaybackMediaCleared() }
        }
    }

    private fun notifyPlaybackError(media: PlayableMedia?, error: PlaybackError) {
        listeners.forEach { it.onPlaybackError(media, error) }
    }

    private fun notifySessionError(error: PlaybackError) {
        notifyPlaybackError(curMedia, error)
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
        if (medias.isEmpty()) {
            return null
        }
        if (curIndex !in medias.indices) {
            return 0
        }
        if (auto && playbackMode.repeatMode == RepeatMode.ONE) {
            return curIndex
        }
        if (playbackMode.shuffleEnabled && medias.size > 1) {
            return randomIndexExcluding(curIndex)
        }
        val next = curIndex + 1
        return when {
            next < medias.size -> next
            playbackMode.repeatMode == RepeatMode.ALL -> 0
            else -> null
        }
    }

    /**
     * 计算上一首索引。
     */
    private fun findPreviousIndex(auto: Boolean): Int? {
        if (medias.isEmpty()) {
            return null
        }
        if (curIndex !in medias.indices) {
            return 0
        }
        if (auto && playbackMode.repeatMode == RepeatMode.ONE) {
            return curIndex
        }
        if (playbackMode.shuffleEnabled && medias.size > 1) {
            return randomIndexExcluding(curIndex)
        }
        val previous = curIndex - 1
        return when {
            previous >= 0 -> previous
            playbackMode.repeatMode == RepeatMode.ALL -> medias.lastIndex
            else -> null
        }
    }

    private fun randomIndexExcluding(excludedIndex: Int): Int {
        var candidate = excludedIndex
        while (candidate == excludedIndex) {
            candidate = Random.nextInt(medias.size)
        }
        return candidate
    }
}
