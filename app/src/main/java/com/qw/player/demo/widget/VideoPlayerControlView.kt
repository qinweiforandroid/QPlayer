package com.qw.player.demo.widget

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import com.qw.player.core.engine.PlaybackState
import com.qw.player.demo.R
import java.util.Locale

open class VideoPlayerControlView : FrameLayout, VideoPlayerControl {

    private var actionListener: VideoPlayerControl.ActionListener? = null
    private var controlsVisible = true
    private var isTrackingSeekBar = false
    private var durationMs: Long = 0L

    private val topScrim: View
    private val bottomScrim: View
    private val controlOverlay: View
    private val topControls: View
    private val bottomControls: View
    private val backButton: View
    private val playPauseButton: View
    private val fullscreenButton: View
    private val playPauseIcon: ImageView
    private val fullscreenIcon: ImageView
    private val titleLabel: TextView
    private val liveBadgeLabel: TextView
    private val clockLabel: TextView
    private val progressLabel: TextView
    private val seekBar: SeekBar

    constructor(context: Context) : this(context, null)

    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    ) {
        LayoutInflater.from(context).inflate(R.layout.widget_cctv_player_controls, this, true)
        topScrim = findViewById(R.id.mTopScrim)
        bottomScrim = findViewById(R.id.mBottomScrim)
        controlOverlay = findViewById(R.id.mControlOverlay)
        topControls = findViewById(R.id.mTopControls)
        bottomControls = findViewById(R.id.mBottomControls)
        backButton = findViewById(R.id.mBackBtn)
        playPauseButton = findViewById(R.id.mPlayPauseBtn)
        fullscreenButton = findViewById(R.id.mFullscreenBtn)
        playPauseIcon = findViewById(R.id.mPlayPauseIcon)
        fullscreenIcon = findViewById(R.id.mFullscreenIcon)
        titleLabel = findViewById(R.id.mOverlayTitleLabel)
        liveBadgeLabel = findViewById(R.id.mLiveBadgeLabel)
        clockLabel = findViewById(R.id.mClockLabel)
        progressLabel = findViewById(R.id.mProgressLabel)
        seekBar = findViewById(R.id.mSeekBar)

        controlOverlay.setOnClickListener { actionListener?.onControllerTapped() }
        backButton.setOnClickListener { actionListener?.onBackClicked() }
        playPauseButton.setOnClickListener { actionListener?.onPlayPauseClicked() }
        fullscreenButton.setOnClickListener { actionListener?.onFullscreenClicked() }
        seekBar.max = SEEK_BAR_MAX
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val seekPositionMs = durationMs * progress / SEEK_BAR_MAX
                    progressLabel.text =
                        "${formatDuration(seekPositionMs)}/${formatDuration(durationMs)}"
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
                isTrackingSeekBar = true
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                isTrackingSeekBar = false
                val seekPositionMs = durationMs * seekBar.progress / SEEK_BAR_MAX
                actionListener?.onSeekFinished(seekPositionMs)
            }
        })
    }

    override fun setActionListener(listener: VideoPlayerControl.ActionListener?) {
        actionListener = listener
    }

    override fun render(state: VideoPlayerControl.State) {
        bindTitle(state.title)
        bindLiveBadge(state.liveBadgeText)
        bindClock(state.clockText)
        bindFullscreen(state.isFullscreen)
        bindPlaybackState(state.playbackState)
        bindProgress(state.positionMs, state.durationMs)
    }

    fun bindTitle(title: String) {
        titleLabel.text = title
    }

    fun bindLiveBadge(text: String) {
        liveBadgeLabel.text = text
    }

    fun bindClock(text: String) {
        clockLabel.text = text
    }

    fun bindFullscreen(fullscreen: Boolean) {
        fullscreenIcon.setImageResource(
            if (fullscreen) {
                R.drawable.ic_player_fullscreen_exit
            } else {
                R.drawable.ic_player_fullscreen
            }
        )
        fullscreenIcon.contentDescription = context.getString(
            if (fullscreen) {
                R.string.player_exit_fullscreen
            } else {
                R.string.player_fullscreen
            }
        )
    }

    fun bindPlaybackState(state: PlaybackState) {
        val iconRes = when (state) {
            PlaybackState.PLAYING,
            PlaybackState.BUFFERING,
            PlaybackState.PREPARING -> R.drawable.ic_player_pause

            else -> R.drawable.ic_player_play
        }
        playPauseIcon.setImageResource(iconRes)
        playPauseIcon.contentDescription = context.getString(
            if (iconRes == R.drawable.ic_player_pause) {
                R.string.player_pause
            } else {
                R.string.player_play
            }
        )
    }

    fun bindProgress(positionMs: Long, durationMs: Long) {
        this.durationMs = durationMs.coerceAtLeast(0L)
        progressLabel.text =
            "${formatDuration(positionMs.coerceAtLeast(0L))}/${formatDuration(this.durationMs)}"
        if (!isTrackingSeekBar) {
            seekBar.progress = if (this.durationMs > 0L) {
                ((positionMs.coerceAtLeast(0L) * SEEK_BAR_MAX) / this.durationMs).toInt()
            } else {
                0
            }
        }
    }

    override fun setControlsVisible(visible: Boolean) {
        controlsVisible = visible
        animateControls(topScrim, visible)
        animateControls(bottomScrim, visible)
        animateControls(topControls, visible)
        animateControls(bottomControls, visible)
    }

    override fun areControlsVisible(): Boolean = controlsVisible

    override fun isTrackingSeekBar(): Boolean = isTrackingSeekBar

    private fun animateControls(target: View, visible: Boolean) {
        target.animate().cancel()
        if (visible) {
            if (target.visibility != View.VISIBLE) {
                target.alpha = 0f
                target.visibility = View.VISIBLE
            }
            target.animate()
                .alpha(1f)
                .setDuration(180L)
                .start()
        } else {
            target.animate()
                .alpha(0f)
                .setDuration(180L)
                .withEndAction {
                    target.visibility = View.GONE
                }
                .start()
        }
    }

    private fun formatDuration(durationMs: Long): String {
        val totalSeconds = durationMs / 1000L
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
        }
    }

    companion object {
        private const val SEEK_BAR_MAX = 1000
    }
}
