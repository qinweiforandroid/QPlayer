package com.qw.player.demo.cctv

import android.os.Handler
import android.os.Looper
import com.qw.player.core.engine.PlaybackError
import com.qw.player.core.engine.PlaybackSnapshot
import com.qw.player.core.engine.PlaybackState
import com.qw.player.core.media.MediaType
import com.qw.player.core.media.PlaySource
import com.qw.player.core.media.PlayableMedia
import com.qw.player.core.media.PlayerMediaMetadata
import com.qw.player.core.session.PlaybackSessionListener
import com.qw.player.demo.runtime.VideoPlaybackRuntime

/**
 * 直播播放编排。
 *
 * 负责三件事：
 * 1. 频道 -> 队列的转换（线路映射成 playSources）
 * 2. 线路切换，失败时自动降级到下一条线路
 * 3. 断流重连：指数退避，并轮换线路重试
 */
class LivePlaybackCoordinator(
    private val onStateChanged: (LiveState) -> Unit
) {

    /** 供 UI 渲染的直播态。 */
    data class LiveState(
        val channel: TvChannel? = null,
        val lineId: String? = null,
        val playbackState: PlaybackState = PlaybackState.IDLE,
        val isReconnecting: Boolean = false,
        val reconnectAttempt: Int = 0,
        val allLinesFailed: Boolean = false
    )

    private val handler = Handler(Looper.getMainLooper())

    private var currentChannel: TvChannel? = null
    private var currentLineIndex = 0
    private var reconnectAttempt = 0
    private var reconnecting = false
    private var allLinesFailed = false
    private var monitoring = false

    /** 线路切换保护窗口内，忽略上一条线路的迟到错误。 */
    private var switchingLine = false

    private val reconnectRunnable = Runnable { performReconnect() }

    private val sessionListener = object : PlaybackSessionListener {
        override fun onPlaybackChanged(snapshot: PlaybackSnapshot) {
            if (!monitoring) {
                return
            }
            if (snapshot.state == PlaybackState.PLAYING ||
                snapshot.state == PlaybackState.READY ||
                snapshot.state == PlaybackState.BUFFERING
            ) {
                // 重新出流，重置重连状态。
                if (reconnecting && snapshot.state == PlaybackState.PLAYING) {
                    resetReconnect()
                }
            }
            publish(snapshot.state)
        }

        override fun onPlaybackError(error: PlaybackError, snapshot: PlaybackSnapshot) {
            if (!monitoring) {
                return
            }
            handleFailure(error)
        }
    }

    fun start(channel: TvChannel, lineIndex: Int = 0) {
        currentChannel = channel
        currentLineIndex = lineIndex.coerceIn(0, (channel.lines.size - 1).coerceAtLeast(0))
        reconnectAttempt = 0
        reconnecting = false
        allLinesFailed = false
        monitoring = true

        VideoPlaybackRuntime.addListener(sessionListener)
        VideoPlaybackRuntime.setQueue(listOf(channel.toPlayableMedia()), 0)
        playCurrentLine()
    }

    fun stop() {
        monitoring = false
        handler.removeCallbacks(reconnectRunnable)
        handler.removeCallbacks(reconnectWatchdog)
        VideoPlaybackRuntime.removeListener(sessionListener)
        currentChannel = null
        reconnecting = false
        reconnectAttempt = 0
    }

    /** 用户主动切换线路。 */
    fun switchLine(lineId: String) {
        val channel = currentChannel ?: return
        val index = channel.lines.indexOfFirst { it.id == lineId }
        if (index < 0 || index == currentLineIndex) {
            return
        }
        currentLineIndex = index
        reconnectAttempt = 0
        reconnecting = false
        allLinesFailed = false
        handler.removeCallbacks(reconnectRunnable)
        handler.removeCallbacks(reconnectWatchdog)
        playCurrentLine()
    }

    fun suspendMonitoring() {
        monitoring = false
        handler.removeCallbacks(reconnectRunnable)
        handler.removeCallbacks(reconnectWatchdog)
    }

    fun resumeMonitoring() {
        monitoring = true
        publish(VideoPlaybackRuntime.getSnapshot().state)
    }

    fun currentState(): LiveState {
        return liveState(VideoPlaybackRuntime.getSnapshot().state)
    }

    private fun playCurrentLine() {
        val channel = currentChannel ?: return
        val line = channel.lines.getOrNull(currentLineIndex) ?: return
        allLinesFailed = false

        VideoPlaybackRuntime.setQueue(listOf(channel.toPlayableMedia(line.id)), 0)
        VideoPlaybackRuntime.play(0)
        publish(PlaybackState.PREPARING)
    }

    /**
     * 播放失败：先尝试剩余线路，全部失败后进入断流重连。
     *
     * 线路切换期间置 [switchingLine]，避免新线路的异步错误再次触发降级链。
     */
    private fun handleFailure(error: PlaybackError) {
        val channel = currentChannel ?: return

        if (reconnecting || switchingLine) {
            return
        }

        val nextIndex = currentLineIndex + 1
        if (nextIndex in channel.lines.indices) {
            currentLineIndex = nextIndex
            switchingLine = true
            playCurrentLine()
            handler.postDelayed({ switchingLine = false }, LINE_SWITCH_GUARD_MS)
            return
        }

        // 所有线路都试过，进入自动重连。
        scheduleReconnect()
    }

    private fun scheduleReconnect() {
        if (reconnectAttempt >= MAX_RECONNECT_ATTEMPTS) {
            reconnecting = false
            allLinesFailed = true
            publish(VideoPlaybackRuntime.getSnapshot().state)
            return
        }
        reconnectAttempt++
        reconnecting = true
        publish(VideoPlaybackRuntime.getSnapshot().state)

        val delay = RECONNECT_BASE_DELAY_MS shl (reconnectAttempt - 1)
        handler.removeCallbacks(reconnectRunnable)
        handler.postDelayed(reconnectRunnable, delay)
    }

    private fun performReconnect() {
        val channel = currentChannel ?: return
        if (channel.lines.isEmpty()) {
            return
        }
        // 每次重连轮换一条线路，避免死磕同一条坏线路。
        currentLineIndex = (currentLineIndex + 1) % channel.lines.size
        switchingLine = true
        playCurrentLine()
        handler.postDelayed({ switchingLine = false }, LINE_SWITCH_GUARD_MS)

        // 若一段时间内仍未出流，继续下一次重连。
        handler.removeCallbacks(reconnectWatchdog)
        handler.postDelayed(reconnectWatchdog, RECONNECT_WATCHDOG_MS)
    }

    private val reconnectWatchdog = Runnable {
        if (reconnecting && VideoPlaybackRuntime.getSnapshot().state != PlaybackState.PLAYING) {
            scheduleReconnect()
        }
    }

    private fun resetReconnect() {
        reconnecting = false
        reconnectAttempt = 0
        allLinesFailed = false
        handler.removeCallbacksAndMessages(null)
    }

    private fun publish(state: PlaybackState) {
        onStateChanged(liveState(state))
    }

    private fun liveState(state: PlaybackState): LiveState {
        val channel = currentChannel ?: return LiveState(playbackState = state)
        return LiveState(
            channel = channel,
            lineId = channel.lines.getOrNull(currentLineIndex)?.id,
            playbackState = state,
            isReconnecting = reconnecting,
            reconnectAttempt = reconnectAttempt,
            allLinesFailed = allLinesFailed
        )
    }

    companion object {
        private const val MAX_RECONNECT_ATTEMPTS = 4
        private const val RECONNECT_BASE_DELAY_MS = 1_000L
        private const val RECONNECT_WATCHDOG_MS = 8_000L
        private const val LINE_SWITCH_GUARD_MS = 1_500L
    }
}

/** 频道 -> 播放协议。线路映射为 playSources。 */
fun TvChannel.toPlayableMedia(selectedLineId: String? = null): PlayableMedia {
    val sources = lines.map { line ->
        PlaySource(sourceId = line.id, url = line.url, cacheable = false)
    }
    return PlayableMedia(
        mediaId = id,
        mediaType = MediaType.VIDEO,
        metadata = PlayerMediaMetadata(
            title = name,
            artist = name,
            subtitle = lines.firstOrNull { it.id == selectedLineId }?.label ?: ""
        ),
        playSources = sources,
        defaultSourceId = selectedLineId ?: sources.firstOrNull()?.sourceId
    )
}
