package com.qw.player.tts

import com.qw.player.core.engine.PlaybackError
import com.qw.player.core.engine.PlaybackSnapshot
import com.qw.player.core.engine.PlaybackState
import com.qw.player.core.engine.PlayerCapabilities
import com.qw.player.core.engine.PlayerConfig
import com.qw.player.core.engine.PlayerEngine
import com.qw.player.core.engine.PlayerEventListener
import com.qw.player.core.engine.PlayerVideoOutput
import com.qw.player.core.media.PlaySource
import com.qw.player.core.media.PlayableMedia
import com.qw.player.core.telemetry.PlaybackTelemetry

/**
 * 文本朗读 engine 的配置。
 *
 * [charsPerSecond] 用于把字符位置换算成对外暴露的毫秒进度；
 * 真实 TTS 无法提前拿到精确时长，所以所有时长都是估算值。
 */
data class TextSpeechConfig(
    val charsPerSecond: Float = 4.5f,
    val maxSegmentChars: Int = 120,
    val progressIntervalMs: Long = 500L
) {
    init {
        require(charsPerSecond > 0f) { "charsPerSecond 必须大于 0" }
    }
}

/**
 * 让文本朗读接入 [PlayerEngine] 协议的实现。
 *
 * 进度模型：
 * - 内部以“已读完的字符数”为准 [spokenCharCount]
 * - 对外仍按毫秒上报进度和总时长（按 [TextSpeechConfig.charsPerSecond] 折算）
 * - [seekTo] 只能定位到句子级别，句内不精确定位——这是 TTS 的物理限制
 *
 * 逐句高亮通过 [setOnSegmentListener] 单独暴露，不污染公共的
 * [com.qw.player.core.engine.PlayerEventListener]。
 */
class TextPlayerEngine(
    private val synthesizer: SpeechSynthesizer,
    private val config: TextSpeechConfig = TextSpeechConfig(),
    private val ticker: ProgressTicker = PodPlayerProgressTicker()
) : PlayerEngine {

    /** 逐句朗读进度回调，供上层做高亮跟随。 */
    interface OnSegmentListener {
        fun onSegmentStarted(segment: TextSegment)
        fun onSegmentFinished(segment: TextSegment)
    }

    private var playerConfig: PlayerConfig = PlayerConfig()
    private var eventListener: PlayerEventListener? = null
    private var segmentListener: OnSegmentListener? = null
    private var telemetry: PlaybackTelemetry? = null

    private var currentMedia: PlayableMedia? = null
    private var currentSource: PlaySource? = null
    private var segments: List<TextSegment> = emptyList()
    private var totalCharCount: Int = 0
    private var spokenCharCount: Int = 0
    private var speed: Float = 1f
    private var currentState: PlaybackState = PlaybackState.IDLE
    private var playWhenReady = false
    private var released = false
    private var volume: Float = 1f
    private var muted = false
    private var lastError: PlaybackError? = null

    private val segmenter = TextSegmenter(config.maxSegmentChars)

    init {
        ticker.setOnTick {
            if (isPlayingState() && !released) {
                eventListener?.onProgress(currentPositionMs(), currentDurationMs())
            }
        }
        synthesizer.setListener(object : SpeechSynthesizer.Listener {
            override fun onSegmentStarted(segment: TextSegment) {
                if (released) return
                segmentListener?.onSegmentStarted(segment)
            }

            override fun onSegmentCompleted(segment: TextSegment) {
                if (released) return
                spokenCharCount = segment.endOffset
                segmentListener?.onSegmentFinished(segment)
                eventListener?.onProgress(currentPositionMs(), currentDurationMs())
                if (isLastSegment(segment)) {
                    finishPlayback()
                }
            }

            override fun onSegmentError(segment: TextSegment, message: String) {
                if (released) return
                lastError = PlaybackError(
                    code = ERROR_SYNTHESIS_FAILED,
                    message = message,
                    recoverable = false,
                    domain = ERROR_DOMAIN
                )
                ticker.stop()
                playWhenReady = false
                currentState = PlaybackState.ERROR
                eventListener?.onError(lastError!!)
                dispatchState()
            }
        })
    }

    /** 设置逐句朗读监听器。 */
    fun setOnSegmentListener(listener: OnSegmentListener?) {
        segmentListener = listener
    }

    override fun initialize(config: PlayerConfig) {
        playerConfig = config
        currentState = PlaybackState.INITIALIZED
        dispatchState()
    }

    override fun setMedia(media: PlayableMedia, source: PlaySource, startPositionMs: Long) {
        ensureNotReleased()
        currentMedia = media
        currentSource = source
        lastError = null
        val text = resolveText(media, source)
        if (text.isNullOrBlank()) {
            lastError = PlaybackError(
                code = ERROR_EMPTY_TEXT,
                message = "文本媒体没有可朗读的正文",
                recoverable = false,
                domain = ERROR_DOMAIN
            )
            currentState = PlaybackState.ERROR
            segments = emptyList()
            totalCharCount = 0
            spokenCharCount = 0
            eventListener?.onError(lastError!!)
            dispatchState()
            return
        }
        segments = segmenter.split(text)
        totalCharCount = text.length
        spokenCharCount = charOffsetForPosition(startPositionMs).coerceIn(0, totalCharCount)
        currentState = PlaybackState.PREPARING
        dispatchState()
    }

    override fun preload(media: PlayableMedia, source: PlaySource) {
        // TTS 预热没有收益，这里只记录当前媒体，避免调用方误以为预热成功
        currentMedia = media
        currentSource = source
    }

    override fun prepare() {
        ensureNotReleased()
        if (segments.isEmpty() && currentMedia != null) {
            val text = currentMedia?.let { resolveText(it, currentSource) }
            if (!text.isNullOrBlank()) {
                segments = segmenter.split(text)
                totalCharCount = text.length
            }
        }
        currentState = PlaybackState.READY
        dispatchState()
    }

    override fun play() {
        ensureNotReleased()
        if (currentState == PlaybackState.ERROR || currentState == PlaybackState.ENDED) {
            // 错误 / 播放结束后再次 play，从当前进度重新开始
            spokenCharCount = 0
            lastError = null
        }
        if (!synthesizer.isAvailable()) {
            lastError = PlaybackError(
                code = ERROR_SYNTHESIZER_UNAVAILABLE,
                message = "语音合成引擎不可用",
                recoverable = false,
                domain = ERROR_DOMAIN
            )
            currentState = PlaybackState.ERROR
            eventListener?.onError(lastError!!)
            dispatchState()
            return
        }
        playWhenReady = true
        currentState = PlaybackState.PLAYING
        synthesizer.setSpeed(speed)
        synthesizer.speak(pendingSegments())
        ticker.start(playerConfig.progressUpdateIntervalMs.takeIf { it > 0 } ?: config.progressIntervalMs)
        dispatchState()
        eventListener?.onProgress(currentPositionMs(), currentDurationMs())
    }

    override fun pause() {
        ensureNotReleased()
        playWhenReady = false
        synthesizer.stop()
        ticker.stop()
        currentState = if (currentState == PlaybackState.PLAYING) {
            PlaybackState.PAUSED
        } else {
            currentState
        }
        dispatchState()
    }

    override fun stop(reset: Boolean) {
        ensureNotReleased()
        playWhenReady = false
        synthesizer.stop()
        ticker.stop()
        currentState = PlaybackState.IDLE
        if (reset) {
            currentMedia = null
            currentSource = null
            segments = emptyList()
            totalCharCount = 0
            spokenCharCount = 0
            lastError = null
        }
        dispatchState()
    }

    override fun seekTo(positionMs: Long) {
        ensureNotReleased()
        val targetChar = charOffsetForPosition(positionMs).coerceIn(0, totalCharCount)
        spokenCharCount = targetChar
        if (playWhenReady && segments.isNotEmpty()) {
            // 句内无法精确定位，直接从句首重新朗读
            synthesizer.stop()
            synthesizer.speak(pendingSegments())
        }
        dispatchState()
        eventListener?.onProgress(currentPositionMs(), currentDurationMs())
    }

    override fun setSpeed(speed: Float) {
        ensureNotReleased()
        this.speed = speed.coerceIn(MIN_SPEED, MAX_SPEED)
        synthesizer.setSpeed(this.speed)
        // 语速变化会改变估算总时长，需要重新广播状态与进度
        dispatchState()
        eventListener?.onProgress(currentPositionMs(), currentDurationMs())
    }

    /** 文本朗读没有“单曲循环”语义，重复播放由 session 层的 RepeatMode 负责。 */
    override fun setLooping(looping: Boolean) {
        // no-op
    }

    override fun setVolume(volume: Float) {
        // 系统 TTS 的音量由 AudioAttributes / 音频焦点控制，这里只记录偏好
        this.volume = volume.coerceIn(0f, 1f)
        this.muted = this.volume == 0f
        dispatchState()
    }

    override fun setMuted(muted: Boolean) {
        this.muted = muted
        dispatchState()
    }

    /** 文本朗读没有视频输出。 */
    override fun attachVideoOutput(output: PlayerVideoOutput?) {
        // no-op
    }

    override fun setListener(listener: PlayerEventListener?) {
        eventListener = listener
        listener?.onStateChanged(currentState)
    }

    override fun setTelemetry(telemetry: PlaybackTelemetry?) {
        this.telemetry = telemetry
    }

    override fun getSnapshot(): PlaybackSnapshot {
        return PlaybackSnapshot(
            state = currentState,
            currentMediaId = currentMedia?.mediaId,
            currentSourceId = currentSource?.sourceId,
            isPrepared = currentState == PlaybackState.READY || currentState == PlaybackState.PLAYING,
            playWhenReady = playWhenReady,
            currentPositionMs = currentPositionMs(),
            durationMs = currentDurationMs(),
            bufferedPositionMs = currentPositionMs(),
            bufferedPercent = 0,
            speed = speed,
            volume = volume,
            isMuted = muted,
            error = lastError
        )
    }

    override fun getCapabilities(): PlayerCapabilities {
        return PlayerCapabilities(
            supportsVideoOutput = false,
            supportsSpeedControl = true,
            supportsPreload = false,
            supportsBackgroundPlayback = true,
            supportsHeaders = false,
            supportsDrm = false,
            supportsSubtitles = false,
            supportsClipping = false,
            supportsTextHighlight = true,
            minSpeed = MIN_SPEED,
            maxSpeed = MAX_SPEED
        )
    }

    override fun release() {
        if (released) {
            return
        }
        released = true
        ticker.stop()
        synthesizer.release()
        currentState = PlaybackState.RELEASED
        dispatchState()
        eventListener = null
        segmentListener = null
    }

    /** 返回当前已读字符数对应的毫秒位置。 */
    private fun currentPositionMs(): Long = charOffsetToMs(spokenCharCount)

    /** 返回按字符数和语速估算的总时长。 */
    private fun currentDurationMs(): Long = charOffsetToMs(totalCharCount)

    private fun charOffsetToMs(charOffset: Int): Long {
        if (charOffset <= 0) {
            return 0L
        }
        val effectiveCps = config.charsPerSecond * speed
        return (charOffset / effectiveCps * 1000f).toLong().coerceAtLeast(0L)
    }

    private fun charOffsetForPosition(positionMs: Long): Int {
        if (positionMs <= 0L) {
            return 0
        }
        val effectiveCps = config.charsPerSecond * speed
        return (positionMs / 1000f * effectiveCps).toInt()
    }

    /** 从当前已读位置开始、尚未朗读的片段。 */
    private fun pendingSegments(): List<TextSegment> {
        return segments.filter { it.endOffset > spokenCharCount }
    }

    private fun isLastSegment(segment: TextSegment): Boolean {
        return segments.lastOrNull()?.index == segment.index
    }

    private fun finishPlayback() {
        ticker.stop()
        playWhenReady = false
        spokenCharCount = totalCharCount
        currentState = PlaybackState.ENDED
        eventListener?.onProgress(currentPositionMs(), currentDurationMs())
        dispatchState()
        eventListener?.onCompleted()
    }

    private fun isPlayingState(): Boolean = currentState == PlaybackState.PLAYING

    private fun resolveText(media: PlayableMedia, source: PlaySource?): String? {
        val inline = source?.inlineText
        if (!inline.isNullOrBlank()) {
            return inline
        }
        // 正文也可能被业务方放在 media.extras 里，兼容 ipod 风格的 content 字段
        return media.extras[EXTRA_CONTENT]?.takeIf { it.isNotBlank() }
    }

    private fun dispatchState() {
        eventListener?.onStateChanged(currentState)
    }

    private fun ensureNotReleased() {
        check(!released) { "TextPlayerEngine has already been released." }
    }

    companion object {
        const val ERROR_DOMAIN = "tts"
        const val ERROR_SYNTHESIZER_UNAVAILABLE = 3001
        const val ERROR_SYNTHESIS_FAILED = 3002
        const val ERROR_EMPTY_TEXT = 3003

        const val EXTRA_CONTENT = "content"
        const val MIN_SPEED = 0.5f
        const val MAX_SPEED = 3f
    }
}
