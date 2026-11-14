package com.qw.player.media3

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import com.qw.player.core.common.PlayLog
import com.qw.player.core.media.ClipRange
import com.qw.player.core.media.DrmConfiguration
import com.qw.player.core.media.PlaySource
import com.qw.player.core.media.PlayableMedia
import com.qw.player.core.media.SubtitleTrack
import com.qw.player.core.engine.PlaybackError
import com.qw.player.core.engine.PlaybackSnapshot
import com.qw.player.core.engine.PlaybackState
import com.qw.player.core.engine.PlayerCapabilities
import com.qw.player.core.engine.PlayerConfig
import com.qw.player.core.engine.PlayerEngine
import com.qw.player.core.engine.PlayerEventListener
import com.qw.player.core.engine.PlayerVideoOutput
import com.qw.player.core.telemetry.PlaybackTelemetry
import com.qw.player.core.common.PodPlayerTimer
import com.qw.player.core.engine.VideoScaleMode
import com.qw.player.core.engine.VideoSize
import java.io.File

/**
 * 基于 Media3 的 [PlayerEngine] 实现。
 *
 * 这个类直接服务于 V2 协议，
 * 用于承接完整音频 / 视频产品的统一播放内核能力。
 */
class Media3PlayerEngine(context: Context) : PlayerEngine {

    private val appContext = context.applicationContext
    private val player: ExoPlayer = ExoPlayer.Builder(appContext).build()
    private val timer = PodPlayerTimer()
    private val retryHandler = Handler(Looper.getMainLooper())

    private var config: PlayerConfig = PlayerConfig()
    private var listener: PlayerEventListener? = null
    private var currentMedia: PlayableMedia? = null
    private var currentSource: PlaySource? = null
    private var playWhenReady = false
    private var isPrepared = false
    private var isReleased = false
    private var lastKnownVideoSize: VideoSize? = null
    private var lastError: PlaybackError? = null
    private var currentState: PlaybackState = PlaybackState.IDLE
    private var hasRenderedFirstFrame = false
    private var volumeBeforeMuted = 1f
    private var retryAttempt = 0
    private var pendingRetry: Runnable? = null
    private var lastKnownPositionMs = 0L
    private var muted = false

    private var telemetry: PlaybackTelemetry? = null
    private var mediaAttachStartedAtMs = 0L
    private var firstFrameReported = false
    private var stallStartedAtMs = 0L
    private var stallReported = false
    private var playedAccumulatedMs = 0L
    private var playedSegmentStartedAtMs = 0L
    private var resultReported = false

    init {
        // 这里用轮询进度而不是直接暴露底层实现细节，
        // 是为了保证不同 engine 对上层输出一致的协议形状。
        timer.setOnPodPlayerTimerListener {
            if (isPrepared && !isReleased) {
                listener?.onProgress(currentPositionMs(), currentDurationMs())
            }
        }
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                log("onPlaybackStateChanged:$state")
                currentState = when (state) {
                    Player.STATE_IDLE -> {
                        if (isReleased) PlaybackState.RELEASED else PlaybackState.IDLE
                    }

                    Player.STATE_BUFFERING -> PlaybackState.BUFFERING
                    Player.STATE_READY -> {
                        isPrepared = true
                        retryAttempt = 0
                        if (player.isPlaying) {
                            PlaybackState.PLAYING
                        } else {
                            PlaybackState.READY
                        }
                    }

                    Player.STATE_ENDED -> PlaybackState.ENDED
                    else -> currentState
                }
                dispatchState()
                if (state == Player.STATE_READY && player.isPlaying && !hasRenderedFirstFrame) {
                    hasRenderedFirstFrame = true
                    reportFirstFrame()
                    listener?.onRenderedFirstFrame()
                }
                if (state == Player.STATE_ENDED) {
                    timer.stop()
                    stopPlayedSegment()
                    reportResult(PlaybackTelemetry.Outcome.COMPLETED)
                    listener?.onCompleted()
                }
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                log("onPlayWhenReadyChanged:$playWhenReady reason:$reason")
                this@Media3PlayerEngine.playWhenReady = playWhenReady
                dispatchState()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                log("onIsPlayingChanged:$isPlaying")
                currentState = when {
                    isPlaying -> PlaybackState.PLAYING
                    currentState == PlaybackState.ENDED -> PlaybackState.ENDED
                    player.playbackState == Player.STATE_BUFFERING -> PlaybackState.BUFFERING
                    isPrepared -> PlaybackState.PAUSED
                    else -> currentState
                }
                if (isPlaying) {
                    timer.start(config.progressUpdateIntervalMs)
                    startPlayedSegment()
                    endStallIfNeeded()
                    if (!hasRenderedFirstFrame) {
                        hasRenderedFirstFrame = true
                        reportFirstFrame()
                        listener?.onRenderedFirstFrame()
                    }
                } else {
                    timer.stop()
                    stopPlayedSegment()
                }
                dispatchState()
            }

            override fun onIsLoadingChanged(isLoading: Boolean) {
                if (isLoading) {
                    beginStallIfNeeded()
                    listener?.onBufferingProgress(currentBufferedPositionMs(), currentDurationMs())
                } else {
                    endStallIfNeeded()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                log("onPlayerError:${error.message}")
                val kind = Media3ErrorClassifier.classify(error.errorCode)
                val canRetry = Media3ErrorClassifier.isRecoverable(kind) &&
                        retryAttempt < Media3ErrorClassifier.MAX_RETRY_ATTEMPTS
                val playbackError = PlaybackError(
                    code = error.errorCode,
                    message = error.message ?: "Unknown playback error",
                    recoverable = canRetry,
                    domain = "media3",
                    extras = mapOf(EXTRA_ERROR_KIND to kind)
                )
                lastError = playbackError
                currentState = PlaybackState.ERROR
                dispatchState()

                if (canRetry) {
                    scheduleRetry(playbackError)
                } else {
                    retryAttempt = 0
                    telemetry?.onError(buildTelemetryErrorEvent(playbackError))
                    stopPlayedSegment()
                    reportResult(PlaybackTelemetry.Outcome.FAILED, playbackError)
                    listener?.onError(playbackError)
                }
            }

            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                if (videoSize == androidx.media3.common.VideoSize.UNKNOWN
                    || player.playbackState == Player.STATE_IDLE
                ) {
                    return
                }
                lastKnownVideoSize = VideoSize(
                    width = videoSize.width,
                    height = videoSize.height,
                    rotationDegrees = videoSize.unappliedRotationDegrees,
                    pixelWidthHeightRatio = videoSize.pixelWidthHeightRatio
                )
                listener?.onVideoSizeChanged(lastKnownVideoSize!!)
                dispatchState()
            }
        })
    }

    override fun initialize(config: PlayerConfig) {
        this.config = config
        currentState = PlaybackState.INITIALIZED
        player.playWhenReady = config.autoPlayWhenReady
        playWhenReady = config.autoPlayWhenReady
        dispatchState()
    }

    override fun setMedia(media: PlayableMedia, source: PlaySource, startPositionMs: Long) {
        ensureNotReleased()
        cancelPendingRetryCallback()
        val isNewSession = media.mediaId != currentMedia?.mediaId ||
                source.sourceId != currentSource?.sourceId
        if (isNewSession) {
            retryAttempt = 0
        }
        currentMedia = media
        currentSource = source
        lastError = null
        isPrepared = false
        hasRenderedFirstFrame = false
        currentState = PlaybackState.INITIALIZED
        // 重试会对同一条 source 重新 setMedia，不能把已累计的播放时长与首帧计时清零。
        if (isNewSession) {
            beginTelemetrySession()
        } else {
            hasRenderedFirstFrame = false
        }

        val mediaSource = buildMediaSource(media, source)

        player.stop()
        player.clearMediaItems()
        player.setMediaSource(mediaSource, startPositionMs)
        currentState = PlaybackState.IDLE
        dispatchState()
    }

    override fun preload(media: PlayableMedia, source: PlaySource) {
        ensureNotReleased()
        cancelPendingRetryCallback()
        currentMedia = media
        currentSource = source
        isPrepared = false
        hasRenderedFirstFrame = false
        lastError = null

        val mediaSource = buildMediaSource(media, source)
        player.stop()
        player.clearMediaItems()
        player.setMediaSource(mediaSource, 0L)
        currentState = PlaybackState.PREPARING
        player.prepare()
        player.playWhenReady = false
        playWhenReady = false
        dispatchState()
    }

    override fun prepare() {
        ensureNotReleased()
        currentState = PlaybackState.PREPARING
        player.prepare()
        dispatchState()
    }

    override fun play() {
        ensureNotReleased()
        playWhenReady = true
        if (player.playbackState == Player.STATE_IDLE) {
            player.prepare()
        }
        player.playWhenReady = true
        player.play()
        dispatchState()
    }

    override fun pause() {
        ensureNotReleased()
        playWhenReady = false
        player.pause()
        currentState = if (isPrepared) PlaybackState.PAUSED else currentState
        dispatchState()
    }

    override fun stop(reset: Boolean) {
        ensureNotReleased()
        if (reset) {
            cancelPendingRetry()
        }
        playWhenReady = false
        timer.stop()
        // 用户主动停止：把这段播放计入结果事件，避免播放时长统计漏掉。
        stopPlayedSegment()
        reportResult(PlaybackTelemetry.Outcome.STOPPED)
        player.stop()
        currentState = PlaybackState.IDLE
        if (reset) {
            player.clearMediaItems()
            currentMedia = null
            currentSource = null
            isPrepared = false
            lastError = null
            lastKnownVideoSize = null
        }
        dispatchState()
    }

    override fun seekTo(positionMs: Long) {
        ensureNotReleased()
        player.seekTo(positionMs)
        dispatchState()
    }

    override fun setSpeed(speed: Float) {
        ensureNotReleased()
        player.setPlaybackParameters(PlaybackParameters(speed))
        dispatchState()
    }

    override fun setLooping(looping: Boolean) {
        ensureNotReleased()
        player.repeatMode = if (looping) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        dispatchState()
    }

    override fun setVolume(volume: Float) {
        ensureNotReleased()
        player.volume = volume.coerceIn(0f, 1f)
        muted = player.volume == 0f
        if (!muted) {
            volumeBeforeMuted = player.volume
        }
        dispatchState()
    }

    override fun setMuted(muted: Boolean) {
        ensureNotReleased()
        this.muted = muted
        if (muted) {
            if (player.volume > 0f) {
                volumeBeforeMuted = player.volume
            }
            player.volume = 0f
        } else {
            player.volume = volumeBeforeMuted.coerceIn(0f, 1f)
        }
        dispatchState()
    }

    override fun attachVideoOutput(output: PlayerVideoOutput?) {
        ensureNotReleased()
        player.clearVideoSurface()
        output?.surface?.let { player.setVideoSurface(it) }
        if (output?.scaleMode == VideoScaleMode.ZOOM) {
            log("attachVideoOutput scaleMode=ZOOM")
        }
    }

    override fun setListener(listener: PlayerEventListener?) {
        this.listener = listener
        listener?.onStateChanged(currentState)
    }

    override fun setTelemetry(telemetry: PlaybackTelemetry?) {
        this.telemetry = telemetry
    }

    override fun getSnapshot(): PlaybackSnapshot {
        return buildSnapshot()
    }

    override fun getCapabilities(): PlayerCapabilities {
        return PlayerCapabilities(
            supportsVideoOutput = true,
            supportsSpeedControl = true,
            supportsPreload = true,
            supportsBackgroundPlayback = true,
            supportsHeaders = true,
            supportsDrm = true,
            supportsSubtitles = true,
            supportsClipping = true,
            minSpeed = 0.5f,
            maxSpeed = 3.0f
        )
    }

    override fun release() {
        if (isReleased) {
            return
        }
        cancelPendingRetry()
        timer.stop()
        player.release()
        isReleased = true
        currentState = PlaybackState.RELEASED
        dispatchState()
        listener = null
    }

    fun getExoPlayer(): ExoPlayer {
        return player
    }

    /**
     * 构建内部快照，确保事件回调和主动查询拿到的是同一份状态语义。
     */
    private fun buildSnapshot(): PlaybackSnapshot {
        return PlaybackSnapshot(
            state = currentState,
            currentMediaId = currentMedia?.mediaId,
            currentSourceId = currentSource?.sourceId,
            isPrepared = isPrepared,
            playWhenReady = playWhenReady,
            currentPositionMs = currentPositionMs(),
            durationMs = currentDurationMs(),
            bufferedPositionMs = currentBufferedPositionMs(),
            bufferedPercent = player.bufferedPercentage,
            speed = player.playbackParameters.speed,
            volume = player.volume,
            isMuted = muted,
            videoSize = lastKnownVideoSize,
            error = lastError
        )
    }

    private fun currentPositionMs(): Long = player.currentPosition.coerceAtLeast(0L)

    private fun currentDurationMs(): Long = player.duration.takeIf { it >= 0 } ?: 0L

    private fun currentBufferedPositionMs(): Long = player.bufferedPosition.coerceAtLeast(0L)

    private fun dispatchState() {
        if (isPrepared && currentState != PlaybackState.ERROR) {
            lastKnownPositionMs = currentPositionMs()
        }
        listener?.onStateChanged(currentState)
        if (currentState == PlaybackState.BUFFERING) {
            listener?.onBufferingProgress(currentBufferedPositionMs(), currentDurationMs())
        }
    }

    /**
     * 对可恢复错误做指数退避重试：对当前挂载的 media/source 重新 setMedia + prepare + play。
     *
     * 重试次数耗尽、或在重试排队期间切换了新 media（[cancelPendingRetry] 会被调用），
     * 都不会再触发这次排队的重试，避免旧重试打到新媒体上。
     */
    private fun scheduleRetry(error: PlaybackError) {
        cancelPendingRetryCallback()
        retryAttempt++
        val delayMs = Media3ErrorClassifier.retryDelayMs(retryAttempt)
        val media = currentMedia
        val source = currentSource
        if (media == null || source == null) {
            retryAttempt = 0
            listener?.onError(error)
            return
        }
        log("scheduleRetry attempt=$retryAttempt delayMs=$delayMs")
        val runnable = Runnable {
            pendingRetry = null
            if (isReleased) {
                return@Runnable
            }
            log("retry attempt=$retryAttempt executing")
            setMedia(media, source, lastKnownPositionMs)
            prepare()
            play()
        }
        pendingRetry = runnable
        retryHandler.postDelayed(runnable, delayMs)
    }

    private fun cancelPendingRetryCallback() {
        pendingRetry?.let { retryHandler.removeCallbacks(it) }
        pendingRetry = null
    }

    private fun cancelPendingRetry() {
        cancelPendingRetryCallback()
        retryAttempt = 0
    }

    private fun buildMediaSource(media: PlayableMedia, source: PlaySource): MediaSource {
        val factory = DefaultMediaSourceFactory(
            buildDataSourceFactory(source.headers, source.userAgent, source.cacheable)
        )
        return factory.createMediaSource(buildMediaItem(media, source))
    }

    private fun buildMediaItem(media: PlayableMedia, source: PlaySource): MediaItem {
        val builder = MediaItem.Builder()
            .setUri(source.url)
            .setMediaId(media.mediaId)

        source.mimeType?.let { builder.setMimeType(it) }
        source.clipRange?.let { builder.setClippingConfiguration(it.toClippingConfiguration()) }
        source.drm?.let { builder.setDrmConfiguration(it.toDrmConfiguration()) }

        val subtitleConfigurations = source.subtitles.map { it.toSubtitleConfiguration() }
        if (subtitleConfigurations.isNotEmpty()) {
            builder.setSubtitleConfigurations(subtitleConfigurations)
        }
        return builder.build()
    }

    private fun buildDataSourceFactory(
        headers: Map<String, String>,
        userAgent: String?,
        cacheable: Boolean
    ): DataSource.Factory {
        val httpFactory = DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(HTTP_CONNECT_TIMEOUT_MS)
            .setReadTimeoutMs(HTTP_READ_TIMEOUT_MS)
            .setAllowCrossProtocolRedirects(true)
        if (headers.isNotEmpty()) {
            httpFactory.setDefaultRequestProperties(headers)
        }
        userAgent?.let { httpFactory.setUserAgent(it) }

        val baseFactory = DefaultDataSource.Factory(appContext, httpFactory)
        val cache = if (cacheable) getSharedCache(appContext) else null
        return if (cache != null) {
            CacheDataSource.Factory()
                .setCache(cache)
                .setUpstreamDataSourceFactory(baseFactory)
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        } else {
            baseFactory
        }
    }

    private fun ClipRange.toClippingConfiguration(): MediaItem.ClippingConfiguration {
        val builder = MediaItem.ClippingConfiguration.Builder()
            .setStartPositionMs(startPositionMs)
        endPositionMs?.let { builder.setEndPositionMs(it) }
        return builder.build()
    }

    private fun DrmConfiguration.toDrmConfiguration(): MediaItem.DrmConfiguration {
        val builder = MediaItem.DrmConfiguration.Builder(uuidForScheme(scheme))
            .setLicenseUri(licenseUrl)
            .setMultiSession(multiSession)
        if (requestHeaders.isNotEmpty()) {
            builder.setLicenseRequestHeaders(requestHeaders)
        }
        return builder.build()
    }

    private fun SubtitleTrack.toSubtitleConfiguration(): MediaItem.SubtitleConfiguration {
        val builder = MediaItem.SubtitleConfiguration.Builder(Uri.parse(url))
            .setId(id)
            .setLabel(label)
            .setSelectionFlags(
                if (isDefault) C.SELECTION_FLAG_DEFAULT else C.SELECTION_FLAG_AUTOSELECT
            )
        mimeType?.let { builder.setMimeType(it) }
        language?.let { builder.setLanguage(it) }
        return builder.build()
    }

    private fun uuidForScheme(scheme: String): java.util.UUID {
        return when (scheme.lowercase()) {
            "widevine" -> C.WIDEVINE_UUID
            "playready" -> C.PLAYREADY_UUID
            "clearkey" -> C.CLEARKEY_UUID
            else -> C.WIDEVINE_UUID
        }
    }

    private fun ensureNotReleased() {
        check(!isReleased) { "Media3PlayerEngine has already been released." }
    }

    private fun log(msg: String) {
        PlayLog.d("Media3PlayerEngine > $msg")
    }

    private fun beginTelemetrySession() {
        mediaAttachStartedAtMs = System.currentTimeMillis()
        firstFrameReported = false
        stallStartedAtMs = 0L
        stallReported = false
        stopPlayedSegment()
        playedAccumulatedMs = 0L
        resultReported = false
    }

    private fun telemetryContext(): PlaybackTelemetry.Context {
        return PlaybackTelemetry.Context(
            mediaId = currentMedia?.mediaId,
            sourceId = currentSource?.sourceId,
            mediaDurationMs = player.duration.takeIf { it >= 0 } ?: 0L
        )
    }

    private fun reportFirstFrame() {
        if (firstFrameReported || telemetry == null) {
            return
        }
        firstFrameReported = true
        telemetry?.onFirstFrame(
            PlaybackTelemetry.FirstFrameEvent(
                context = telemetryContext(),
                timeToFirstFrameMs = System.currentTimeMillis() - mediaAttachStartedAtMs
            )
        )
    }

    private fun beginStallIfNeeded() {
        if (stallStartedAtMs != 0L) {
            return
        }
        stallStartedAtMs = System.currentTimeMillis()
        stallReported = false
    }

    private fun endStallIfNeeded() {
        val startedAt = stallStartedAtMs
        if (startedAt == 0L) {
            return
        }
        val durationMs = System.currentTimeMillis() - startedAt
        stallStartedAtMs = 0L
        // 极短的 loading 抖动不上报，只关心真正影响观看的卡顿。
        if (stallReported || durationMs < MIN_STALL_REPORT_MS) {
            return
        }
        stallReported = true
        telemetry?.onStall(
            PlaybackTelemetry.StallEvent(
                context = telemetryContext(),
                positionMs = player.currentPosition.coerceAtLeast(0L),
                stallDurationMs = durationMs
            )
        )
    }

    private fun startPlayedSegment() {
        if (playedSegmentStartedAtMs == 0L) {
            playedSegmentStartedAtMs = System.currentTimeMillis()
        }
    }

    private fun stopPlayedSegment() {
        val startedAt = playedSegmentStartedAtMs
        if (startedAt == 0L) {
            return
        }
        playedAccumulatedMs += System.currentTimeMillis() - startedAt
        playedSegmentStartedAtMs = 0L
    }

    private fun reportResult(outcome: PlaybackTelemetry.Outcome, error: PlaybackError? = null) {
        val sink = telemetry ?: return
        if (resultReported) {
            return
        }
        resultReported = true
        sink.onPlaybackResult(
            PlaybackTelemetry.PlaybackResultEvent(
                context = telemetryContext(),
                outcome = outcome,
                playedDurationMs = playedAccumulatedMs,
                error = error
            )
        )
    }

    private fun buildTelemetryErrorEvent(error: PlaybackError): PlaybackTelemetry.ErrorEvent {
        return PlaybackTelemetry.ErrorEvent(context = telemetryContext(), error = error)
    }

    companion object {
        /** [PlaybackError.extras] 中的错误分类 key。 */
        const val EXTRA_ERROR_KIND = "errorKind"

        /** 网络连通类错误。 */
        const val ERROR_KIND_NETWORK = Media3ErrorClassifier.ERROR_KIND_NETWORK

        /** 资源 / 容器 / 清单格式错误，通常等同于“视频加载失败”。 */
        const val ERROR_KIND_SOURCE = Media3ErrorClassifier.ERROR_KIND_SOURCE

        /** 解码失败。 */
        const val ERROR_KIND_DECODE = Media3ErrorClassifier.ERROR_KIND_DECODE

        /** 无法归类的错误。 */
        const val ERROR_KIND_UNKNOWN = Media3ErrorClassifier.ERROR_KIND_UNKNOWN

        /** 短于该时长的 loading 视为正常抖动，不计入卡顿。 */
        private const val MIN_STALL_REPORT_MS = 500L

        private const val HTTP_CONNECT_TIMEOUT_MS = 10_000
        private const val HTTP_READ_TIMEOUT_MS = 15_000
        private const val CACHE_MAX_BYTES = 256L * 1024 * 1024

        @Volatile
        private var sharedCache: SimpleCache? = null

        /** 进程内共享的磁盘缓存，避免多个 engine 实例重复占用缓存目录。 */
        private fun getSharedCache(context: Context): SimpleCache? {
            sharedCache?.let { return it }
            return synchronized(this) {
                sharedCache ?: runCatching {
                    val cacheDir = File(context.cacheDir, "media3_cache")
                    SimpleCache(
                        cacheDir,
                        LeastRecentlyUsedCacheEvictor(CACHE_MAX_BYTES),
                        StandaloneDatabaseProvider(context)
                    )
                }.onFailure {
                    PlayLog.d("Media3PlayerEngine > cache init failed: ${it.message}")
                }.getOrNull().also { sharedCache = it }
            }
        }
    }
}
