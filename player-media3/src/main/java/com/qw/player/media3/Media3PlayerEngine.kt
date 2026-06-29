package com.qw.player.media3

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.qw.player.core.common.PlayLog
import com.qw.player.core.media.PlaySource
import com.qw.player.core.media.PlayableMedia
import com.qw.player.core.engine.PlaybackError
import com.qw.player.core.engine.PlaybackSnapshot
import com.qw.player.core.engine.PlaybackState
import com.qw.player.core.engine.PlayerCapabilities
import com.qw.player.core.engine.PlayerConfig
import com.qw.player.core.engine.PlayerEngine
import com.qw.player.core.engine.PlayerEventListener
import com.qw.player.core.engine.PlayerVideoOutput
import com.qw.player.core.common.PodPlayerTimer
import com.qw.player.core.engine.VideoScaleMode
import com.qw.player.core.engine.VideoSize

/**
 * 基于 Media3 的 [PlayerEngine] 实现。
 *
 * 这个类直接服务于 V2 协议，
 * 用于承接完整音频 / 视频产品的统一播放内核能力。
 */
class Media3PlayerEngine(
    context: Context
) : PlayerEngine {

    private val appContext = context.applicationContext
    private val player: ExoPlayer = ExoPlayer.Builder(appContext).build()
    private val timer = PodPlayerTimer()

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
    private var muted = false

    init {
        // 这里用轮询进度而不是直接暴露底层实现细节，
        // 是为了保证不同 engine 对上层输出一致的协议形状。
        timer.setOnPodPlayerTimerListener {
            if (isPrepared && !isReleased) {
                listener?.onProgress(buildSnapshot())
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
                    listener?.onRenderedFirstFrame()
                }
                if (state == Player.STATE_ENDED) {
                    timer.stop()
                    listener?.onCompletion(buildSnapshot())
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
                    if (!hasRenderedFirstFrame) {
                        hasRenderedFirstFrame = true
                        listener?.onRenderedFirstFrame()
                    }
                } else {
                    timer.stop()
                }
                dispatchState()
            }

            override fun onIsLoadingChanged(isLoading: Boolean) {
                if (isLoading) {
                    listener?.onBuffering(buildSnapshot())
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                log("onPlayerError:${error.message}")
                lastError = PlaybackError(
                    code = error.errorCode,
                    message = error.message ?: "Unknown playback error",
                    recoverable = false,
                    domain = "media3"
                )
                currentState = PlaybackState.ERROR
                dispatchState()
                listener?.onError(lastError!!, buildSnapshot())
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
        currentMedia = media
        currentSource = source
        lastError = null
        isPrepared = false
        hasRenderedFirstFrame = false
        currentState = PlaybackState.INITIALIZED

        // 当前先只接入最核心的 uri/mediaId 路径。
        // headers、DRM、字幕、裁剪播放等能力后续再和业务 resolver 一起补齐。
        val mediaItemBuilder = MediaItem.Builder()
            .setUri(source.url)
            .setMediaId(media.mediaId)

        player.stop()
        player.clearMediaItems()
        player.setMediaItem(mediaItemBuilder.build(), startPositionMs)
        currentState = PlaybackState.IDLE
        dispatchState()
    }

    override fun preload(media: PlayableMedia, source: PlaySource) {
        ensureNotReleased()
        currentMedia = media
        currentSource = source
        isPrepared = false
        hasRenderedFirstFrame = false
        lastError = null

        val item = MediaItem.Builder()
            .setUri(source.url)
            .setMediaId(media.mediaId)
            .build()
        player.stop()
        player.clearMediaItems()
        player.setMediaItem(item, 0L)
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
        playWhenReady = false
        timer.stop()
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

    override fun setPlaybackSpeed(speed: Float) {
        ensureNotReleased()
        player.setPlaybackParameters(PlaybackParameters(speed))
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
        listener?.onStateChanged(buildSnapshot())
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
            supportsHeaders = false,
            supportsDrm = false,
            supportsSubtitles = false,
            supportsClipping = false,
            minSpeed = 0.5f,
            maxSpeed = 3.0f
        )
    }

    override fun release() {
        if (isReleased) {
            return
        }
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
        val duration = player.duration.takeIf { it >= 0 } ?: 0L
        return PlaybackSnapshot(
            state = currentState,
            currentMediaId = currentMedia?.mediaId,
            currentSourceId = currentSource?.sourceId,
            isPrepared = isPrepared,
            playWhenReady = playWhenReady,
            currentPositionMs = player.currentPosition.coerceAtLeast(0L),
            durationMs = duration,
            bufferedPositionMs = player.bufferedPosition.coerceAtLeast(0L),
            bufferedPercent = player.bufferedPercentage,
            speed = player.playbackParameters.speed,
            volume = player.volume,
            isMuted = muted,
            videoSize = lastKnownVideoSize,
            error = lastError
        )
    }

    private fun dispatchState() {
        val snapshot = buildSnapshot()
        listener?.onStateChanged(snapshot)
        if (snapshot.state == PlaybackState.BUFFERING) {
            listener?.onBuffering(snapshot)
        }
    }

    private fun ensureNotReleased() {
        check(!isReleased) { "Media3PlayerEngine has already been released." }
    }

    private fun log(msg: String) {
        PlayLog.d("Media3PlayerEngine > $msg")
    }
}
