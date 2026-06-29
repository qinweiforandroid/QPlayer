package com.qw.player.demo.cctv

import android.content.res.Configuration
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.ViewModelProvider
import androidx.media3.ui.AspectRatioFrameLayout
import com.qw.player.core.engine.PlaybackError
import com.qw.player.core.engine.PlaybackSnapshot
import com.qw.player.core.engine.PlaybackState
import com.qw.player.core.media.MediaType
import com.qw.player.core.media.PlaySource
import com.qw.player.core.media.PlayableMedia
import com.qw.player.core.media.PlayerMediaMetadata
import com.qw.player.core.session.PlaybackMode
import com.qw.player.core.session.PlaybackSessionListener
import com.qw.player.core.session.RepeatMode
import com.qw.recyclerview.core.BaseViewHolder
import com.qw.recyclerview.core.OnRefreshListener
import com.qw.recyclerview.layout.MyLinearLayoutManager
import com.qw.player.demo.R
import com.qw.player.demo.databinding.ActivityCctvPlayerBinding
import com.qw.player.demo.runtime.VideoPlaybackRuntime
import com.qw.player.demo.widget.CctvPlayerControlView
import com.qw.player.demo.widget.VideoPlayerControl
import com.qw.recyclerview.swiperefresh.SwipeRecyclerView
import com.qw.recyclerview.template.SmartListCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CctvPlayerActivity : AppCompatActivity() {

    private lateinit var bind: ActivityCctvPlayerBinding
    private lateinit var list: SmartListCompat<TvChannel>
    private lateinit var vm: CctvPlayerVM
    private var isFullscreen = false
    private val uiHandler = Handler(Looper.getMainLooper())
    private val hideControlsRunnable = Runnable {
        if (isFullscreen || VideoPlaybackRuntime.isPlaying()) {
            bind.mPlayerControls.setControlsVisible(false)
        }
    }
    private val progressRunnable = object : Runnable {
        override fun run() {
            bind.mPlayerControls.bindClock(clockFormatter.format(Date()))
            bindPlaybackSnapshot(VideoPlaybackRuntime.getSnapshot())
            uiHandler.postDelayed(this, 1000L)
        }
    }
    private val clockFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val backPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            if (isFullscreen) {
                updateFullscreenUi(false)
            } else {
                finish()
            }
        }
    }

    private val playbackListener = object : PlaybackSessionListener {
        override fun onCurrentMediaChanged(current: PlayableMedia?, previous: PlayableMedia?) {
            if (current != null) {
                bind.mNowPlayingLabel.text = current.metadata.title
                renderPlayerControls(
                    snapshot = VideoPlaybackRuntime.getSnapshot(),
                    title = current.metadata.title,
                    liveBadgeText = current.metadata.artist.ifEmpty { "LIVE" }
                )
            }
            list.adapter.notifyDataSetChanged()
        }

        override fun onPlaybackChanged(snapshot: PlaybackSnapshot) {
            bindPlaybackSnapshot(snapshot)
        }

        override fun onPlaybackCompleted(snapshot: PlaybackSnapshot) {
            bindPlaybackSnapshot(snapshot)
        }

        override fun onPlaybackError(error: PlaybackError, snapshot: PlaybackSnapshot) {
            bind.mStatusLabel.text = error.message
            bindPlaybackSnapshot(snapshot)
            showControlsTemporarily()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bind = ActivityCctvPlayerBinding.inflate(layoutInflater)
        setContentView(bind.root)
        onBackPressedDispatcher.addCallback(this, backPressedCallback)
        ViewCompat.setOnApplyWindowInsetsListener(bind.root) { view, insets ->
            val topInset =
                if (isFullscreen) 0 else insets.getInsets(WindowInsetsCompat.Type.systemBars()).top
            view.setPadding(0, topInset, 0, 0)
            insets
        }
        vm = ViewModelProvider(this)[CctvPlayerVM::class.java]
        initPlayer()
        initControls()
        initList()
        observeChannels()
        updateFullscreenUi(resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)
        list.setRefreshing(true)
    }

    private fun initPlayer() {
        bind.mPlayerView.player = VideoPlaybackRuntime.getEngine().getExoPlayer()
        bind.mPlayerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT)
        VideoPlaybackRuntime.updatePlaybackMode(
            PlaybackMode(
                repeatMode = RepeatMode.ALL,
                shuffleEnabled = false
            )
        )
        VideoPlaybackRuntime.addListener(playbackListener)
    }

    private fun initControls() {
        bind.mPlayerControls.setActionListener(object : VideoPlayerControl.ActionListener {
            override fun onBackClicked() {
                if (isFullscreen) {
                    updateFullscreenUi(false)
                } else {
                    finish()
                }
            }

            override fun onPlayPauseClicked() {
                when (VideoPlaybackRuntime.getSnapshot().state) {
                    PlaybackState.PLAYING,
                    PlaybackState.BUFFERING,
                    PlaybackState.PREPARING -> VideoPlaybackRuntime.pause()

                    PlaybackState.PAUSED,
                    PlaybackState.READY -> VideoPlaybackRuntime.resume()

                    else -> {
                        val currentIndex = VideoPlaybackRuntime.getCurrentIndex()
                        if (currentIndex >= 0) {
                            VideoPlaybackRuntime.play(currentIndex)
                        } else if (VideoPlaybackRuntime.getQueue().isNotEmpty()) {
                            VideoPlaybackRuntime.play(0)
                        }
                    }
                }
                showControlsTemporarily()
            }

            override fun onFullscreenClicked() {
                updateFullscreenUi(!isFullscreen)
            }

            override fun onControllerTapped() {
                toggleControls()
            }

            override fun onSeekFinished(positionMs: Long) {
                VideoPlaybackRuntime.seekTo(positionMs)
                autoHideControls()
            }
        })
        bind.mFollowBtn.setOnClickListener { showControlsTemporarily() }
    }

    private fun initList() {
        val smart = SwipeRecyclerView(bind.mRecyclerView, bind.mSwipeRefreshLayout)
        list = object : SmartListCompat<TvChannel>(smart) {
            override fun onCreateBaseViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder {
                return ChannelHolder(
                    layoutInflater.inflate(R.layout.activity_cctv_player_item, parent, false)
                )
            }
        }
        list.setRefreshEnable(true)
            .setLoadMoreEnable(false)
            .setUpLayoutManager(MyLinearLayoutManager(this))
            .setOnRefreshListener(object : OnRefreshListener {
                override fun onRefresh() {
                    bind.mStatusLabel.text = getString(R.string.player_status_loading)
                    vm.refreshChannels()
                }
            })
    }

    private fun observeChannels() {
        vm.channels.observe(this) { result ->
            list.setRefreshing(false)
            val channels = result.getOrElse { TvChannelRepository.fallbackForUi() }
            list.modules.clear()
            list.modules.addAll(channels)
            list.adapter.notifyDataSetChanged()
            if (result.isSuccess) {
                bind.mStatusLabel.text = getString(R.string.player_status_ready, channels.size)
            } else {
                bind.mStatusLabel.text = getString(R.string.player_status_error)
            }
            bind.mAuthorMetaLabel.text = "${channels.size}个直播频道 · 筛选后公开 HLS"
            val queue = channels.map { it.toPlayableMedia() }
            VideoPlaybackRuntime.setQueue(queue, 0)
            if (channels.isNotEmpty()) {
                val current =
                    channels.firstOrNull {
                        it.streamUrl == VideoPlaybackRuntime.getCurrentMedia()?.resolveDefaultSource()?.url
                    } ?: channels.first()
                playChannel(current)
            } else {
                bind.mNowPlayingLabel.text = getString(R.string.player_empty)
                renderPlayerControls(
                    snapshot = VideoPlaybackRuntime.getSnapshot(),
                    title = getString(R.string.player_empty),
                    liveBadgeText = ""
                )
            }
        }
    }

    private fun playChannel(channel: TvChannel) {
        bind.mNowPlayingLabel.text = channel.name
        bind.mStatusLabel.text = "${channel.group} · ${channel.sourceLabel}"
        VideoPlaybackRuntime.play(channel.toPlayableMedia().mediaId)
        renderPlayerControls(
            snapshot = VideoPlaybackRuntime.getSnapshot(),
            title = channel.name,
            liveBadgeText = channel.group
        )
        list.adapter.notifyDataSetChanged()
        showControlsTemporarily()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateFullscreenUi(newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE)
    }

    private fun updateFullscreenUi(fullscreen: Boolean) {
        isFullscreen = fullscreen
        if (fullscreen) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowInsetsControllerCompat(window, bind.root).apply {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
            bind.mPlayerContainer.layoutParams = bind.mPlayerContainer.layoutParams.apply {
                height = ViewGroup.LayoutParams.MATCH_PARENT
            }
            bind.mDetailContainer.visibility = View.GONE
            bind.mSwipeRefreshLayout.visibility = View.GONE
        } else {
            WindowCompat.setDecorFitsSystemWindows(window, true)
            WindowInsetsControllerCompat(
                window,
                bind.root
            ).show(WindowInsetsCompat.Type.systemBars())
            bind.mPlayerContainer.layoutParams = bind.mPlayerContainer.layoutParams.apply {
                height = resources.getDimensionPixelSize(R.dimen.player_bili_height)
            }
            bind.mDetailContainer.visibility = View.VISIBLE
            bind.mSwipeRefreshLayout.visibility = View.VISIBLE
        }
        renderPlayerControls(
            snapshot = VideoPlaybackRuntime.getSnapshot(),
            title = VideoPlaybackRuntime.getCurrentMedia()?.metadata?.title ?: getString(R.string.player_title),
            liveBadgeText = VideoPlaybackRuntime.getCurrentMedia()?.metadata?.artist ?: "",
            fullscreen = fullscreen
        )
        bind.root.requestApplyInsets()
        bind.mPlayerContainer.requestLayout()
        showControlsTemporarily()
    }

    private fun toggleControls() {
        val visible = !bind.mPlayerControls.areControlsVisible()
        bind.mPlayerControls.setControlsVisible(visible)
        if (visible) {
            autoHideControls()
        }
    }

    private fun showControlsTemporarily() {
        bind.mPlayerControls.setControlsVisible(true)
        autoHideControls()
    }

    private fun autoHideControls() {
        uiHandler.removeCallbacks(hideControlsRunnable)
        if (!bind.mPlayerControls.isTrackingSeekBar()) {
            uiHandler.postDelayed(hideControlsRunnable, 3000L)
        }
    }

    private fun bindPlaybackSnapshot(snapshot: PlaybackSnapshot) {
        renderPlayerControls(snapshot = snapshot)
        if (snapshot.state == PlaybackState.PLAYING) {
            autoHideControls()
        }
    }

    override fun onStart() {
        super.onStart()
        uiHandler.post(progressRunnable)
        bindPlaybackSnapshot(VideoPlaybackRuntime.getSnapshot())
    }

    override fun onStop() {
        uiHandler.removeCallbacks(progressRunnable)
        uiHandler.removeCallbacks(hideControlsRunnable)
        if (VideoPlaybackRuntime.isPlaying()) {
            VideoPlaybackRuntime.pause()
        }
        super.onStop()
    }

    override fun onDestroy() {
        VideoPlaybackRuntime.removeListener(playbackListener)
        bind.mPlayerView.player = null
        super.onDestroy()
    }

    inner class ChannelHolder(itemView: View) : BaseViewHolder(itemView) {
        private val card = itemView.findViewById<LinearLayout>(R.id.mChannelCard)
        private val badge = itemView.findViewById<TextView>(R.id.mChannelBadgeLabel)
        private val title = itemView.findViewById<TextView>(R.id.mChannelNameLabel)
        private val subTitle = itemView.findViewById<TextView>(R.id.mChannelMetaLabel)
        private val tag = itemView.findViewById<TextView>(R.id.mChannelSelectedLabel)

        override fun initData(position: Int) {
            val channel = model as TvChannel
            title.text = channel.name
            subTitle.text = "${channel.group} · ${channel.sourceLabel}"
            badge.text = channel.group.take(4)
            val selected = channel.streamUrl == VideoPlaybackRuntime.getCurrentMedia()
                ?.resolveDefaultSource()
                ?.url
            tag.visibility = if (selected) View.VISIBLE else View.GONE
            title.setTypeface(null, if (selected) Typeface.BOLD else Typeface.NORMAL)
            card.setBackgroundResource(
                if (selected) R.drawable.bg_channel_card_selected else R.drawable.bg_channel_card
            )
            itemView.setOnClickListener {
                playChannel(channel)
                if (isFullscreen) {
                    updateFullscreenUi(false)
                }
            }
        }
    }

    private fun TvChannel.toPlayableMedia(): PlayableMedia {
        return PlayableMedia(
            mediaId = streamUrl,
            mediaType = MediaType.VIDEO,
            metadata = PlayerMediaMetadata(
                title = name,
                artist = group,
                subtitle = sourceLabel
            ),
            playSources = listOf(
                PlaySource(
                    sourceId = "default",
                    url = streamUrl
                )
            ),
            defaultSourceId = "default"
        )
    }

    private fun renderPlayerControls(
        snapshot: PlaybackSnapshot,
        title: String = VideoPlaybackRuntime.getCurrentMedia()?.metadata?.title ?: getString(R.string.player_title),
        liveBadgeText: String = VideoPlaybackRuntime.getCurrentMedia()?.metadata?.artist ?: "",
        fullscreen: Boolean = isFullscreen
    ) {
        bind.mPlayerControls.render(
            VideoPlayerControl.State(
                title = title,
                liveBadgeText = liveBadgeText,
                clockText = clockFormatter.format(Date()),
                isFullscreen = fullscreen,
                playbackState = snapshot.state,
                positionMs = snapshot.currentPositionMs,
                durationMs = snapshot.durationMs
            )
        )
    }
}
