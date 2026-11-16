package com.qw.player.demo.cctv

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.ui.AspectRatioFrameLayout
import com.qw.player.core.engine.PlaybackState
import com.qw.player.demo.R
import com.qw.player.demo.databinding.ActivityCctvPlayerBinding
import com.qw.player.demo.runtime.VideoPlaybackRuntime
import com.qw.player.demo.widget.LivePlayerControlView
import com.qw.player.demo.widget.PlayerGestureHints
import com.qw.player.demo.widget.PlayerGestureLayout

/**
 * 直播播放页。
 *
 * 与点播页的差异：
 * - 进入即自动播放，无封面
 * - 无进度条 / 无时间显示 / 不可 seek
 * - 双击快进快退禁用
 * - 上下滑动切换频道，侧边栏快速选台
 * - 断流自动重连，线路失败自动降级
 */
class CctvPlayerActivity : AppCompatActivity() {

    private lateinit var bind: ActivityCctvPlayerBinding
    private lateinit var coordinator: LivePlaybackCoordinator

    private val uiHandler = Handler(Looper.getMainLooper())

    private var currentChannel: TvChannel? = null
    private var isFullscreen = true
    private var isMuted = false
    private var gestureVolumeBase = -1f
    private var gestureBrightnessBase = -1f

    private val autoHideRunnable = Runnable {
        if (!bind.mPlayerControls.arePanelsVisible() && !bind.mChannelSidebar.isSidebarShown()) {
            bind.mPlayerControls.setControlsVisible(false)
        }
    }

    private val epgRunnable = object : Runnable {
        override fun run() {
            refreshEpg()
            uiHandler.postDelayed(this, EPG_REFRESH_INTERVAL_MS)
        }
    }

    private val backPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            when {
                bind.mChannelSidebar.isSidebarShown() -> bind.mChannelSidebar.hide()
                bind.mPlayerControls.arePanelsVisible() -> bind.mPlayerControls.hidePanels()
                isFullscreen -> setFullscreen(false)
                else -> finish()
            }
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

        override fun onChannelSwipe(next: Boolean) {
            switchChannelBySwipe(next)
        }
    }

    private val controlListener = object : LivePlayerControlView.ActionListener {
        override fun onBackClicked() {
            if (isFullscreen) {
                setFullscreen(false)
            } else {
                finish()
            }
        }

        override fun onPlayPauseClicked() {
            val state = VideoPlaybackRuntime.getSnapshot().state
            if (state == PlaybackState.PLAYING || state == PlaybackState.BUFFERING) {
                VideoPlaybackRuntime.pause()
            } else {
                VideoPlaybackRuntime.resume()
            }
            renderControls()
            scheduleAutoHide()
        }

        override fun onLineClicked() {
            renderControls()
        }

        override fun onLineSelected(lineId: String) {
            coordinator.switchLine(lineId)
            renderControls()
        }

        override fun onVolumeClicked() {
            isMuted = !isMuted
            VideoPlaybackRuntime.setMuted(isMuted)
            bind.mPlayerControls.bindVolume(isMuted)
        }

        override fun onBrightnessClicked() {
            bind.mGestureHints.showLevel(isBrightness = true, percent = currentWindowBrightness())
            uiHandler.postDelayed({ bind.mGestureHints.hideLevel() }, 900L)
        }

        override fun onFullscreenClicked() {
            setFullscreen(!isFullscreen)
        }

        override fun onChannelListClicked() {
            bind.mChannelSidebar.show(currentChannel?.id)
        }

        override fun onControllerTapped() {
            gestureListener.onSingleTap()
        }
    }

    private val sidebarListener = object : ChannelSidebarView.Listener {
        override fun onChannelSelected(channel: TvChannel) {
            bind.mChannelSidebar.hide()
            playChannel(channel)
        }

        override fun onDismiss() {
            bind.mChannelSidebar.hide()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bind = ActivityCctvPlayerBinding.inflate(layoutInflater)
        setContentView(bind.root)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        onBackPressedDispatcher.addCallback(this, backPressedCallback)

        VideoPlaybackRuntime.bindPlayerView(bind.mPlayerView)
        bind.mPlayerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT)
        bind.mPlayerContainer.mode = PlayerGestureLayout.Mode.LIVE
        bind.mPlayerContainer.listener = gestureListener
        bind.mPlayerControls.setActionListener(controlListener)
        bind.mChannelSidebar.setListener(sidebarListener)
        bind.mSwitchChannelBtn.setOnClickListener {
            bind.mChannelSidebar.show(currentChannel?.id)
        }

        coordinator = LivePlaybackCoordinator { state ->
            runOnUiThread { renderLiveState(state) }
        }

        val channelId = intent.getStringExtra(EXTRA_CHANNEL_ID)
        val channel = channelId?.let { TvChannelRepository.findById(it) }
            ?: TvChannelRepository.loadChannels().firstOrNull()

        // 竖屏进入时保留频道信息区，横屏直接全屏。
        setFullscreen(resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)
        channel?.let { playChannel(it) } ?: run {
            bind.mReconnectLabel.text = getString(R.string.live_all_lines_failed)
            bind.mReconnectLayer.visibility = View.VISIBLE
        }
    }

    private fun playChannel(channel: TvChannel) {
        currentChannel = channel
        bind.mReconnectLayer.visibility = View.GONE
        coordinator.stop()
        coordinator.start(channel)
        bind.mPlayerControls.render(
            LivePlayerControlView.State(
                channelName = channel.name,
                epgText = epgText(channel),
                isFullscreen = isFullscreen,
                playbackState = VideoPlaybackRuntime.getSnapshot().state,
                lines = channel.lines.map { LivePlayerControlView.LineOption(it.id, it.label) },
                selectedLineId = channel.lines.firstOrNull()?.id
            )
        )
        bind.mPlayerControls.setControlsVisible(true)
        scheduleAutoHide()
    }

    private fun switchChannelBySwipe(next: Boolean) {
        val channels = TvChannelRepository.loadChannels()
        val currentId = currentChannel?.id ?: return
        val index = TvChannelRepository.indexOf(currentId)
        if (index < 0) {
            return
        }
        val targetIndex = if (next) {
            if (index + 1 >= channels.size) 0 else index + 1
        } else {
            if (index - 1 < 0) channels.size - 1 else index - 1
        }
        val target = channels[targetIndex]
        bind.mGestureHints.showChannelSwitch(
            getString(
                if (next) R.string.gesture_channel_next else R.string.gesture_channel_previous
            ) + " · " + target.name
        )
        playChannel(target)
    }

    private fun renderLiveState(state: LivePlaybackCoordinator.LiveState) {
        bind.mPlayerControls.bindPlaybackState(state.playbackState)
        bind.mPlayerControls.bindVolume(isMuted)

        state.channel?.let { channel ->
            bind.mPlayerControls.render(
                LivePlayerControlView.State(
                    channelName = channel.name,
                    epgText = epgText(channel),
                    isFullscreen = isFullscreen,
                    playbackState = state.playbackState,
                    lines = channel.lines.map { LivePlayerControlView.LineOption(it.id, it.label) },
                    selectedLineId = state.lineId
                )
            )
        }

        when {
            state.allLinesFailed -> {
                bind.mReconnectLabel.text = getString(R.string.live_all_lines_failed)
                bind.mReconnectLayer.visibility = View.VISIBLE
            }

            state.isReconnecting -> {
                bind.mReconnectLabel.text = getString(
                    R.string.live_signal_lost_attempt,
                    state.reconnectAttempt.coerceAtLeast(1),
                    MAX_RECONNECT_HINT
                )
                bind.mReconnectLayer.visibility = View.VISIBLE
            }

            else -> bind.mReconnectLayer.visibility = View.GONE
        }
    }

    private fun renderControls() {
        val channel = currentChannel ?: return
        val state = coordinator.currentState()
        bind.mPlayerControls.render(
            LivePlayerControlView.State(
                channelName = channel.name,
                epgText = epgText(channel),
                isFullscreen = isFullscreen,
                playbackState = VideoPlaybackRuntime.getSnapshot().state,
                lines = channel.lines.map { LivePlayerControlView.LineOption(it.id, it.label) },
                selectedLineId = state.lineId ?: channel.lines.firstOrNull()?.id
            )
        )
    }

    private fun refreshEpg() {
        currentChannel?.let {
            bind.mPlayerControls.bindPlaybackState(VideoPlaybackRuntime.getSnapshot().state)
            renderControls()
        }
    }

    private fun epgText(channel: TvChannel): String {
        val entry = channel.currentEpg(System.currentTimeMillis())
        return if (entry != null) {
            getString(R.string.live_epg_now, entry.title)
        } else {
            ""
        }
    }

    private fun scheduleAutoHide() {
        uiHandler.removeCallbacks(autoHideRunnable)
        uiHandler.postDelayed(autoHideRunnable, AUTO_HIDE_DELAY_MS)
    }

    private fun setFullscreen(fullscreen: Boolean) {
        isFullscreen = fullscreen
        if (fullscreen) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowInsetsControllerCompat(window, bind.root).apply {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
            bind.mPlayerContainer.layoutParams = bind.mPlayerContainer.layoutParams.apply {
                height = ViewGroup.LayoutParams.MATCH_PARENT
            }
        } else {
            WindowCompat.setDecorFitsSystemWindows(window, true)
            WindowInsetsControllerCompat(window, bind.root)
                .show(WindowInsetsCompat.Type.systemBars())
            bind.mPlayerContainer.layoutParams = bind.mPlayerContainer.layoutParams.apply {
                height = resources.getDimensionPixelSize(R.dimen.player_video_height)
            }
        }
        bind.mPlayerContainer.requestLayout()
        bind.mPlayerControls.bindFullscreen(fullscreen)
        if (currentChannel != null) {
            renderControls()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // 旋转不重建播放器，仅同步全屏外观。
        setFullscreen(newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE)
    }

    override fun onStart() {
        super.onStart()
        // 页面回到前台：重建被系统销毁的 Surface 绑定。
        if (bind.mPlayerView.player == null) {
            VideoPlaybackRuntime.bindPlayerView(bind.mPlayerView)
        }
        coordinator.resumeMonitoring()
        uiHandler.post(epgRunnable)
        renderControls()
    }

    override fun onStop() {
        uiHandler.removeCallbacks(epgRunnable)
        uiHandler.removeCallbacks(autoHideRunnable)
        coordinator.suspendMonitoring()
        // 后台 / 锁屏暂停，返回不自动恢复。
        if (VideoPlaybackRuntime.isPlaying() || VideoPlaybackRuntime.isBuffering()) {
            VideoPlaybackRuntime.pause()
        }
        VideoPlaybackRuntime.unbindPlayerView(bind.mPlayerView)
        super.onStop()
    }

    override fun onDestroy() {
        uiHandler.removeCallbacksAndMessages(null)
        coordinator.stop()
        VideoPlaybackRuntime.unbindPlayerView(bind.mPlayerView)
        VideoPlaybackRuntime.stop()
        super.onDestroy()
    }

    private fun currentWindowBrightness(): Float {
        val value = window.attributes.screenBrightness
        return if (value < 0f) 0.5f else value
    }

    private fun applyWindowBrightness(value: Float) {
        window.attributes = window.attributes.apply {
            screenBrightness = value.coerceIn(MIN_BRIGHTNESS, 1f)
        }
    }

    private fun currentMusicVolume(): Float {
        val am = getSystemService(AudioManager::class.java)
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        return if (max > 0) am.getStreamVolume(AudioManager.STREAM_MUSIC) / max.toFloat() else 0f
    }

    private fun applyMusicVolume(value: Float) {
        val am = getSystemService(AudioManager::class.java)
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        am.setStreamVolume(
            AudioManager.STREAM_MUSIC,
            (value * max).toInt().coerceIn(0, max),
            0
        )
    }

    companion object {
        private const val EXTRA_CHANNEL_ID = "extra_channel_id"
        private const val AUTO_HIDE_DELAY_MS = 3_000L
        private const val EPG_REFRESH_INTERVAL_MS = 60_000L
        private const val MAX_RECONNECT_HINT = 4
        private const val MIN_BRIGHTNESS = 0.01f

        fun intent(context: Context, channelId: String): Intent {
            return Intent(context, CctvPlayerActivity::class.java)
                .putExtra(EXTRA_CHANNEL_ID, channelId)
        }
    }
}
