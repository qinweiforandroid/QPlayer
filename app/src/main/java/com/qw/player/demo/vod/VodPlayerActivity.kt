package com.qw.player.demo.vod

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.ui.AspectRatioFrameLayout
import com.qw.player.core.engine.PlaybackError
import com.qw.player.core.engine.PlaybackSnapshot
import com.qw.player.core.engine.PlaybackState
import com.qw.player.core.session.PlaybackMode
import com.qw.player.core.session.PlaybackSessionListener
import com.qw.player.core.session.RepeatMode
import com.qw.player.demo.R
import com.qw.player.demo.databinding.ActivityVodPlayerBinding
import com.qw.player.demo.runtime.VideoPlaybackRuntime
import com.qw.player.demo.widget.PlayerGestureHints
import com.qw.player.demo.widget.PlayerGestureLayout
import com.qw.player.demo.widget.VodPlayerControlView
import com.qw.player.media3.Media3PlayerEngine

/**
 * 点播播放页。
 *
 * 交互对齐 B 站：
 * - 封面 + 居中播放按钮起播
 * - 单击显隐控制栏，3 秒无操作隐藏
 * - 双击左右快进快退 10 秒
 * - 左半屏上下滑调亮度，右半屏调音量
 * - 水平拖动 seek，松手跳转
 * - 长按 2 倍速，松手恢复
 */
class VodPlayerActivity : AppCompatActivity() {

    private lateinit var bind: ActivityVodPlayerBinding
    private lateinit var playlist: VodPlaylist

    private val uiHandler = Handler(Looper.getMainLooper())

    private var currentEpisodeIndex = 0
    private var selectedQualityId: String? = null
    private var currentSpeed = 1f
    private var isMuted = false
    private var isLooping = false
    private var isFullscreen = false
    private var hasStartedPlayback = false
    private var pendingAutoPlayOnResume = false

    private var brightnessValue = -1f
    private var gestureBrightnessBase = -1f
    private var gestureVolumeBase = -1f
    private var speedBeforeLongPress = 1f
    private var longPressSpeedActive = false

    private val autoHideRunnable = Runnable {
        if (!bind.mPlayerControls.isTrackingSeekBar() && !bind.mPlayerControls.arePanelsVisible()) {
            bind.mPlayerControls.setControlsVisible(false)
        }
    }

    private val progressRunnable = object : Runnable {
        override fun run() {
            val snapshot = VideoPlaybackRuntime.getSnapshot()
            bind.mPlayerControls.bindProgress(snapshot.currentPositionMs, snapshot.durationMs)
            bind.mPlayerControls.bindPlaybackState(snapshot.state)
            syncGestureContext(snapshot)
            uiHandler.postDelayed(this, PROGRESS_INTERVAL_MS)
        }
    }

    private val backPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            when {
                bind.mPlayerControls.arePanelsVisible() -> bind.mPlayerControls.hidePanels()
                isFullscreen -> setFullscreen(false)
                else -> finish()
            }
        }
    }

    private val playbackListener = object : PlaybackSessionListener {
        override fun onCurrentMediaChanged(current: com.qw.player.core.media.PlayableMedia?, previous: com.qw.player.core.media.PlayableMedia?) {
            current?.let { media ->
                // 自动连播到下一P时，页面记录的集数要跟着走。
                val index = playlist.episodes.indexOfFirst { it.id == media.mediaId }
                if (index >= 0 && index != currentEpisodeIndex) {
                    currentEpisodeIndex = index
                    selectedQualityId = media.defaultSourceId
                        ?: playlist.episodes[index].qualities.firstOrNull()?.id
                    buildEpisodeViews()
                    syncCover()
                }
                bind.mPlayerControls.render(buildControlState(VideoPlaybackRuntime.getSnapshot()))
                bind.mVodTitleLabel.text = media.metadata.title
            }
        }

        override fun onPlaybackChanged(snapshot: PlaybackSnapshot) {
            if (snapshot.state != PlaybackState.ERROR) {
                hideError()
            }
            bind.mPlayerControls.bindPlaybackState(snapshot.state)
            bind.mPlayerControls.bindProgress(snapshot.currentPositionMs, snapshot.durationMs)
        }

        override fun onPlaybackCompleted(snapshot: PlaybackSnapshot) {
            // 多P自动连播由 session 自行推进，这里只在最后一集结束时同步 UI。
            bind.mPlayerControls.bindPlaybackState(snapshot.state)
        }

        override fun onPlaybackError(error: PlaybackError, snapshot: PlaybackSnapshot) {
            showError(error)
        }
    }

    private val gestureListener = object : PlayerGestureLayout.Listener {
        override fun onSingleTap() {
            if (bind.mPlayerControls.arePanelsVisible()) {
                bind.mPlayerControls.hidePanels()
                return
            }
            val visible = !bind.mPlayerControls.areControlsVisible()
            bind.mPlayerControls.setControlsVisible(visible)
            if (visible) {
                scheduleAutoHide()
            }
        }

        override fun onDoubleTap() {
            togglePlayPause()
        }

        override fun onLongPressStart() {
            // 只在播放中才允许长按倍速快进，暂停/缓冲/加载中长按不生效。
            if (!VideoPlaybackRuntime.isPlaying()) {
                return
            }
            longPressSpeedActive = true
            speedBeforeLongPress = currentSpeed
            applySpeed(LONG_PRESS_SPEED)
            bind.mGestureHints.showSpeed(LONG_PRESS_SPEED)
        }

        override fun onLongPressEnd() {
            if (!longPressSpeedActive) {
                return
            }
            longPressSpeedActive = false
            applySpeed(speedBeforeLongPress)
        }

        override fun onSeekStart() {
            val snapshot = VideoPlaybackRuntime.getSnapshot()
            bind.mGestureHints.showSeekPreview(snapshot.currentPositionMs, snapshot.durationMs)
            bind.mPlayerControls.setControlsVisible(true)
        }

        override fun onSeekPreview(targetMs: Long) {
            bind.mGestureHints.updateSeekPreview(targetMs)
        }

        override fun onSeekCommit(targetMs: Long) {
            bind.mGestureHints.hideSeekPreview()
            VideoPlaybackRuntime.seekTo(targetMs)
            scheduleAutoHide()
        }

        override fun onBrightnessChange(fraction: Float) {
            if (gestureBrightnessBase < 0f) {
                gestureBrightnessBase = currentWindowBrightness()
            }
            val next = (gestureBrightnessBase + fraction).coerceIn(MIN_BRIGHTNESS, 1f)
            applyWindowBrightness(next)
            bind.mGestureHints.showLevel(isBrightness = true, percent = next)
        }

        override fun onVolumeChange(fraction: Float) {
            if (gestureVolumeBase < 0f) {
                gestureVolumeBase = currentMusicVolume()
            }
            val next = (gestureVolumeBase + fraction).coerceIn(0f, 1f)
            applyMusicVolume(next)
            bind.mGestureHints.showLevel(isBrightness = false, percent = next)
        }

        override fun onVerticalDragEnd() {
            gestureBrightnessBase = -1f
            gestureVolumeBase = -1f
            bind.mGestureHints.hideLevel()
        }
    }

    private val controlListener = object : VodPlayerControlView.ActionListener {
        override fun onBackClicked() {
            if (isFullscreen) {
                setFullscreen(false)
            } else {
                finish()
            }
        }

        override fun onControllerTapped() {
            gestureListener.onSingleTap()
        }

        override fun onPlayPauseClicked() {
            togglePlayPause()
        }

        override fun onFullscreenClicked() {
            setFullscreen(!isFullscreen)
        }

        override fun onSeekCommit(positionMs: Long) {
            VideoPlaybackRuntime.seekTo(positionMs)
            scheduleAutoHide()
        }

        override fun onQualitySelected(sourceId: String) {
            val resumedPosition = VideoPlaybackRuntime.getSnapshot().currentPositionMs
            if (!VideoPlaybackRuntime.switchSource(sourceId)) {
                return
            }
            selectedQualityId = sourceId
            VideoPlaybackRuntime.seekTo(resumedPosition)
            bind.mPlayerControls.render(buildControlState(VideoPlaybackRuntime.getSnapshot()))
        }

        override fun onSpeedSelected(speed: Float) {
            applySpeed(speed)
        }

        override fun onMuteToggled(muted: Boolean) {
            isMuted = muted
            VideoPlaybackRuntime.setMuted(muted)
            bind.mPlayerControls.render(buildControlState(VideoPlaybackRuntime.getSnapshot()))
        }

        override fun onLoopToggled(looping: Boolean) {
            isLooping = looping
            VideoPlaybackRuntime.setLooping(looping)
            bind.mPlayerControls.render(buildControlState(VideoPlaybackRuntime.getSnapshot()))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bind = ActivityVodPlayerBinding.inflate(layoutInflater)
        setContentView(bind.root)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        onBackPressedDispatcher.addCallback(this, backPressedCallback)

        playlist = VodRepository.loadPlaylist()
        currentEpisodeIndex = savedInstanceState?.getInt(STATE_EPISODE_INDEX) ?: 0
        selectedQualityId = savedInstanceState?.getString(STATE_QUALITY_ID)
            ?: playlist.episodes.getOrNull(currentEpisodeIndex)?.qualities?.firstOrNull()?.id
        hasStartedPlayback = savedInstanceState?.getBoolean(STATE_STARTED) ?: false
        currentSpeed = savedInstanceState?.getFloat(STATE_SPEED, 1f) ?: 1f
        isMuted = savedInstanceState?.getBoolean(STATE_MUTED) ?: false
        isLooping = savedInstanceState?.getBoolean(STATE_LOOPING) ?: false

        VideoPlaybackRuntime.updatePlaybackMode(
            PlaybackMode(repeatMode = RepeatMode.ALL, shuffleEnabled = false)
        )
        VideoPlaybackRuntime.addListener(playbackListener)

        initViews()
        if (hasStartedPlayback) {
            bind.mCoverLayer.visibility = View.GONE
            startOrResumePlayback()
        } else {
            bind.mCoverLayer.visibility = View.VISIBLE
            prepareWithoutPlay()
        }

        setFullscreen(resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)
    }

    private fun prepareWithoutPlay() {
        bind.mPlayerView.player = VideoPlaybackRuntime.getEngine().getExoPlayer()
        bind.mPlayerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT)
        val queue = playlist.episodes.map { episode ->
            VodMediaMapper.toPlayableMedia(
                episode = episode,
                playlistTitle = playlist.title,
                coverUrl = playlist.coverUrl,
                defaultQualityId = selectedQualityId
            )
        }
        VideoPlaybackRuntime.setQueue(queue, currentEpisodeIndex)
        VideoPlaybackRuntime.setMuted(isMuted)
        VideoPlaybackRuntime.setPlaybackSpeed(currentSpeed)
        VideoPlaybackRuntime.setLooping(isLooping)
        syncCover()
        renderControls()
    }

    private fun initViews() {
        bind.mPlayerView.player = VideoPlaybackRuntime.getEngine().getExoPlayer()
        bind.mPlayerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT)

        bind.mPlayerContainer.listener = gestureListener
        bind.mPlayerControls.setActionListener(controlListener)

        bind.mPlayBigBtn.setOnClickListener { startOrResumePlayback() }
        bind.mRetryBtn.setOnClickListener {
            hideError()
            startOrResumePlayback()
        }

        bind.mPlayerControls.render(buildControlState(VideoPlaybackRuntime.getSnapshot()))
        bind.mPlayerControls.setControlsVisible(false)

        bind.mVodTitleLabel.text = playlist.title
        bind.mVodAuthorLabel.text = playlist.author
        buildEpisodeViews()

        videoSizeListenerSetup()
    }

    private fun videoSizeListenerSetup() {
        // 竖屏视频全屏时保持竖屏并铺满，横屏视频全屏切横屏。
        val size = VideoPlaybackRuntime.getSnapshot().videoSize
        if (size != null && size.height > size.width) {
            bind.mPlayerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_ZOOM)
        }
    }

    private fun buildEpisodeViews() {
        bind.mEpisodeGroup.removeAllViews()
        playlist.episodes.forEachIndexed { index, episode ->
            val item = TextView(this).apply {
                text = episode.title
                textSize = 13f
                gravity = android.view.Gravity.CENTER
                setTextColor(getColor(
                    if (index == currentEpisodeIndex) R.color.bili_pink else R.color.bili_text_primary
                ))
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    dp(36)
                ).apply { marginEnd = dp(10) }
                setPadding(dp(14), 0, dp(14), 0)
                setBackgroundResource(
                    if (index == currentEpisodeIndex) {
                        R.drawable.bg_quality_option_selected
                    } else {
                        R.drawable.bg_channel_card
                    }
                )
                setOnClickListener {
                    if (index != currentEpisodeIndex) {
                        currentEpisodeIndex = index
                        selectedQualityId = episode.qualities.firstOrNull()?.id
                        startOrResumePlayback()
                        buildEpisodeViews()
                    }
                }
            }
            bind.mEpisodeGroup.addView(item, item.layoutParams)
        }
    }

    private fun startOrResumePlayback() {
        hasStartedPlayback = true
        bind.mCoverLayer.visibility = View.GONE
        hideError()

        val snapshot = VideoPlaybackRuntime.getSnapshot()
        when {
            snapshot.state == PlaybackState.PAUSED || snapshot.state == PlaybackState.READY ->
                VideoPlaybackRuntime.resume()

            VideoPlaybackRuntime.getCurrentIndex() == currentEpisodeIndex &&
                (snapshot.state == PlaybackState.PLAYING || snapshot.state == PlaybackState.BUFFERING) -> Unit

            else -> VideoPlaybackRuntime.play(currentEpisodeIndex)
        }
        syncCover()
        bind.mPlayerControls.setControlsVisible(true)
        scheduleAutoHide()
        startProgressLoop()
    }

    private fun syncCover() {
        val episode = playlist.episodes.getOrNull(currentEpisodeIndex) ?: return
        bind.mCoverTitleLabel.text = episode.title
        bind.mVodTitleLabel.text = episode.title
    }

    private fun togglePlayPause() {
        when (VideoPlaybackRuntime.getSnapshot().state) {
            PlaybackState.PLAYING,
            PlaybackState.BUFFERING,
            PlaybackState.PREPARING -> VideoPlaybackRuntime.pause()

            else -> startOrResumePlayback()
        }
        bind.mPlayerControls.render(buildControlState(VideoPlaybackRuntime.getSnapshot()))
        scheduleAutoHide()
    }

    private fun applySpeed(speed: Float) {
        currentSpeed = speed
        VideoPlaybackRuntime.setPlaybackSpeed(speed)
        bind.mPlayerControls.render(buildControlState(VideoPlaybackRuntime.getSnapshot()))
    }

    private fun showError(error: PlaybackError) {
        val kind = error.extras[Media3PlayerEngine.EXTRA_ERROR_KIND]
        val messageRes = if (kind == Media3PlayerEngine.ERROR_KIND_NETWORK) {
            R.string.vod_error_network
        } else {
            R.string.vod_error_load
        }
        bind.mErrorLabel.text = getString(messageRes)
        bind.mErrorLayer.visibility = View.VISIBLE
        bind.mPlayerControls.setControlsVisible(true)
    }

    private fun hideError() {
        bind.mErrorLayer.visibility = View.GONE
    }

    private fun scheduleAutoHide() {
        uiHandler.removeCallbacks(autoHideRunnable)
        if (VideoPlaybackRuntime.isPlaying() || isFullscreen) {
            uiHandler.postDelayed(autoHideRunnable, AUTO_HIDE_DELAY_MS)
        }
    }

    private fun startProgressLoop() {
        uiHandler.removeCallbacks(progressRunnable)
        uiHandler.post(progressRunnable)
    }

    private fun buildControlState(snapshot: PlaybackSnapshot): VodPlayerControlView.State {
        val episode = playlist.episodes.getOrNull(
            VideoPlaybackRuntime.getCurrentIndex().takeIf { it >= 0 } ?: currentEpisodeIndex
        )
        return VodPlayerControlView.State(
            title = episode?.title ?: playlist.title,
            isFullscreen = isFullscreen,
            playbackState = snapshot.state,
            positionMs = snapshot.currentPositionMs,
            durationMs = snapshot.durationMs,
            qualities = episode?.qualities?.map {
                VodPlayerControlView.QualityOption(it.id, it.label)
            } ?: emptyList(),
            selectedQualityId = selectedQualityId,
            speed = currentSpeed,
            muted = isMuted,
            looping = isLooping
        )
    }

    private fun renderControls() {
        bind.mPlayerControls.render(buildControlState(VideoPlaybackRuntime.getSnapshot()))
    }

    private fun setFullscreen(fullscreen: Boolean) {
        isFullscreen = fullscreen
        if (fullscreen) {
            // 竖屏视频全屏时保持竖屏并铺满，横屏视频才转到横屏。
            val portraitVideo = isPortraitVideo()
            requestedOrientation = if (portraitVideo) {
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            } else {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }
            bind.mPlayerView.setResizeMode(
                if (portraitVideo) {
                    AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                } else {
                    AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            )
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowInsetsControllerCompat(window, bind.root).apply {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
            bind.mPlayerContainer.layoutParams = bind.mPlayerContainer.layoutParams.apply {
                height = ViewGroup.LayoutParams.MATCH_PARENT
            }
            bind.mDetailContainer.visibility = View.GONE
        } else {
            // 退出全屏固定回竖屏：不用 UNSPECIFIED，避免设备物理姿态是横向时
            // 传感器判定仍停在横屏。
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            bind.mPlayerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT)
            WindowCompat.setDecorFitsSystemWindows(window, true)
            WindowInsetsControllerCompat(window, bind.root)
                .show(WindowInsetsCompat.Type.systemBars())
            bind.mPlayerContainer.layoutParams = bind.mPlayerContainer.layoutParams.apply {
                height = resources.getDimensionPixelSize(R.dimen.player_bili_height)
            }
            bind.mDetailContainer.visibility = View.VISIBLE
        }
        bind.mPlayerContainer.requestLayout()
        bind.mPlayerControls.bindFullscreen(fullscreen)
        androidx.core.view.ViewCompat.requestApplyInsets(bind.mPlayerControls)
        renderControls()
    }

    /** 当前视频是否竖屏。未知尺寸时按横屏处理。 */
    private fun isPortraitVideo(): Boolean {
        val size = VideoPlaybackRuntime.getSnapshot().videoSize ?: return false
        return size.height > size.width
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // 只同步全屏外观，不重建播放器。
        if (isFullscreen) {
            bind.mPlayerContainer.layoutParams = bind.mPlayerContainer.layoutParams.apply {
                height = ViewGroup.LayoutParams.MATCH_PARENT
            }
            bind.mDetailContainer.visibility = View.GONE
        }
        bind.mPlayerControls.bindFullscreen(isFullscreen)
    }

    private fun currentWindowBrightness(): Float {
        val value = window.attributes.screenBrightness
        return if (value < 0f) {
            // 跟随系统亮度时用一个居中值作为手势起点。
            0.5f
        } else {
            value
        }
    }

    private fun applyWindowBrightness(value: Float) {
        brightnessValue = value
        window.attributes = window.attributes.apply {
            screenBrightness = value.coerceIn(MIN_BRIGHTNESS, 1f)
        }
    }

    private fun currentMusicVolume(): Float {
        val am = getSystemService(AUDIO_SERVICE) as android.media.AudioManager
        val max = am.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
        return if (max > 0) am.getStreamVolume(android.media.AudioManager.STREAM_MUSIC) / max.toFloat() else 0f
    }

    private fun applyMusicVolume(value: Float) {
        val am = getSystemService(AUDIO_SERVICE) as android.media.AudioManager
        val max = am.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
        am.setStreamVolume(
            android.media.AudioManager.STREAM_MUSIC,
            (value * max).toInt().coerceIn(0, max),
            0
        )
    }

    override fun onStart() {
        super.onStart()
        startProgressLoop()
        syncGestureContext(VideoPlaybackRuntime.getSnapshot())
    }

    private fun syncGestureContext(snapshot: PlaybackSnapshot) {
        bind.mPlayerControls.bindProgress(snapshot.currentPositionMs, snapshot.durationMs)
        bind.mPlayerControls.bindPlaybackState(snapshot.state)
        // 手势层需要知道当前时长和播放位置，才能算拖动目标。
        bind.mPlayerContainer.durationMs = snapshot.durationMs
        bind.mPlayerContainer.positionMs = snapshot.currentPositionMs
    }

    override fun onStop() {
        uiHandler.removeCallbacks(progressRunnable)
        uiHandler.removeCallbacks(autoHideRunnable)
        // 退到后台或锁屏：暂停，返回时不自动恢复。
        if (VideoPlaybackRuntime.isPlaying() || VideoPlaybackRuntime.isBuffering()) {
            VideoPlaybackRuntime.pause()
            pendingAutoPlayOnResume = false
        }
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_EPISODE_INDEX, currentEpisodeIndex)
        outState.putString(STATE_QUALITY_ID, selectedQualityId)
        outState.putBoolean(STATE_STARTED, hasStartedPlayback)
        outState.putFloat(STATE_SPEED, currentSpeed)
        outState.putBoolean(STATE_MUTED, isMuted)
        outState.putBoolean(STATE_LOOPING, isLooping)
    }

    override fun onDestroy() {
        uiHandler.removeCallbacksAndMessages(null)
        VideoPlaybackRuntime.removeListener(playbackListener)
        bind.mPlayerView.player = null
        // 退出播放页完全停止，避免残留声音与解码资源占用。
        VideoPlaybackRuntime.stop()
        super.onDestroy()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val LONG_PRESS_SPEED = 2f
        private const val AUTO_HIDE_DELAY_MS = 3_000L
        private const val PROGRESS_INTERVAL_MS = 500L
        private const val MIN_BRIGHTNESS = 0.01f

        private const val STATE_EPISODE_INDEX = "state_episode_index"
        private const val STATE_QUALITY_ID = "state_quality_id"
        private const val STATE_STARTED = "state_started"
        private const val STATE_SPEED = "state_speed"
        private const val STATE_MUTED = "state_muted"
        private const val STATE_LOOPING = "state_looping"
    }
}
