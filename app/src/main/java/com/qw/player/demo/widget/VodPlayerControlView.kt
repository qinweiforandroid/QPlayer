package com.qw.player.demo.widget

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import com.qw.player.core.engine.PlaybackState
import com.qw.player.demo.R
import java.util.Locale

/**
 * 点播控制层：顶部返回/标题/清晰度/更多，底部播放暂停/时间/进度/全屏。
 *
 * 清晰度与更多设置以面板形式内嵌，避免额外弹窗层。
 */
class VodPlayerControlView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    interface ActionListener {
        fun onBackClicked()
        fun onControllerTapped()
        fun onPlayPauseClicked()
        fun onFullscreenClicked()
        fun onSeekCommit(positionMs: Long)
        fun onQualitySelected(sourceId: String)
        fun onSpeedSelected(speed: Float)
        fun onMuteToggled(muted: Boolean)
        fun onLoopToggled(looping: Boolean)
    }

    data class QualityOption(val sourceId: String, val label: String)

    data class State(
        val title: String = "",
        val isFullscreen: Boolean = false,
        val playbackState: PlaybackState = PlaybackState.IDLE,
        val positionMs: Long = 0L,
        val durationMs: Long = 0L,
        val qualities: List<QualityOption> = emptyList(),
        val selectedQualityId: String? = null,
        val speed: Float = 1f,
        val muted: Boolean = false,
        val looping: Boolean = false
    )

    private val topScrim: View
    private val bottomScrim: View
    private val topControls: View
    private val bottomControls: View
    private val backBtn: View
    private val titleLabel: TextView
    private val qualityBtn: TextView
    private val moreBtn: View
    private val playPauseBtn: ImageView
    private val timeLabel: TextView
    private val fullscreenBtn: ImageView
    private val seekBar: SeekBar
    private val qualityPanel: LinearLayout
    private val morePanel: View
    private val speedGroup: LinearLayout
    private val muteSwitch: Switch
    private val loopSwitch: Switch
    private val muteIcon: ImageView

    private var actionListener: ActionListener? = null
    private var controlsVisible = true
    private var trackingSeekBar = false
    private var durationMs = 0L
    private var qualities: List<QualityOption> = emptyList()
    private var selectedQualityId: String? = null
    private var currentSpeed = 1f
    private var applyingState = false

    private val speedOptions = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

    init {
        LayoutInflater.from(context).inflate(R.layout.widget_vod_player_controls, this, true)

        topScrim = findViewById(R.id.mTopScrim)
        bottomScrim = findViewById(R.id.mBottomScrim)
        topControls = findViewById(R.id.mTopControls)
        bottomControls = findViewById(R.id.mBottomControls)
        backBtn = findViewById(R.id.mBackBtn)
        titleLabel = findViewById(R.id.mTitleLabel)
        qualityBtn = findViewById(R.id.mQualityBtn)
        moreBtn = findViewById(R.id.mMoreBtn)
        playPauseBtn = findViewById(R.id.mPlayPauseBtn)
        timeLabel = findViewById(R.id.mTimeLabel)
        fullscreenBtn = findViewById(R.id.mFullscreenBtn)
        seekBar = findViewById(R.id.mSeekBar)
        qualityPanel = findViewById(R.id.mQualityPanel)
        morePanel = findViewById(R.id.mMorePanel)
        speedGroup = findViewById(R.id.mSpeedGroup)
        muteSwitch = findViewById(R.id.mMuteSwitch)
        loopSwitch = findViewById(R.id.mLoopSwitch)
        muteIcon = findViewById(R.id.mMuteIcon)

        backBtn.setOnClickListener { actionListener?.onBackClicked() }
        playPauseBtn.setOnClickListener {
            actionListener?.onPlayPauseClicked()
            showControls()
        }
        fullscreenBtn.setOnClickListener { actionListener?.onFullscreenClicked() }
        qualityBtn.setOnClickListener { togglePanel(qualityPanel) }
        moreBtn.setOnClickListener { togglePanel(morePanel) }

        seekBar.max = SEEK_BAR_MAX
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    timeLabel.text = formatTime(positionFromProgress(progress)) + " / " + formatTime(durationMs)
                }
            }

            override fun onStartTrackingTouch(bar: SeekBar) {
                trackingSeekBar = true
            }

            override fun onStopTrackingTouch(bar: SeekBar) {
                trackingSeekBar = false
                actionListener?.onSeekCommit(positionFromProgress(bar.progress))
            }
        })

        muteSwitch.setOnCheckedChangeListener { _, checked ->
            if (!applyingState) {
                actionListener?.onMuteToggled(checked)
            }
        }
        loopSwitch.setOnCheckedChangeListener { _, checked ->
            if (!applyingState) {
                actionListener?.onLoopToggled(checked)
            }
        }

        buildSpeedOptions()
        setControlsVisible(false)
    }

    fun setActionListener(listener: ActionListener?) {
        actionListener = listener
    }

    fun render(state: State) {
        titleLabel.text = state.title
        bindPlaybackState(state.playbackState)
        bindFullscreen(state.isFullscreen)
        bindProgress(state.positionMs, state.durationMs)
        bindQualities(state.qualities, state.selectedQualityId)
        bindSpeed(state.speed)
        bindSwitches(state.muted, state.looping)
    }

    fun bindProgress(positionMs: Long, durationMs: Long) {
        this.durationMs = durationMs.coerceAtLeast(0L)
        if (!trackingSeekBar) {
            timeLabel.text = formatTime(positionMs) + " / " + formatTime(this.durationMs)
            seekBar.progress = if (this.durationMs > 0L) {
                (positionMs.coerceIn(0L, this.durationMs) * SEEK_BAR_MAX / this.durationMs).toInt()
            } else {
                0
            }
        }
    }

    fun bindPlaybackState(state: PlaybackState) {
        val playing = state == PlaybackState.PLAYING ||
            state == PlaybackState.BUFFERING ||
            state == PlaybackState.PREPARING
        playPauseBtn.setImageResource(if (playing) R.drawable.ic_player_pause else R.drawable.ic_player_play)
        playPauseBtn.contentDescription = context.getString(
            if (playing) R.string.player_pause else R.string.player_play
        )
    }

    fun bindFullscreen(fullscreen: Boolean) {
        fullscreenBtn.setImageResource(
            if (fullscreen) R.drawable.ic_player_fullscreen_exit else R.drawable.ic_player_fullscreen
        )
    }

    fun setControlsVisible(visible: Boolean) {
        controlsVisible = visible
        if (!visible) {
            qualityPanel.visibility = View.GONE
            morePanel.visibility = View.GONE
        }
        listOf(topScrim, bottomScrim, topControls, bottomControls).forEach { animateTo(it, visible) }
    }

    fun areControlsVisible(): Boolean = controlsVisible

    fun isTrackingSeekBar(): Boolean = trackingSeekBar

    fun hidePanels() {
        qualityPanel.visibility = View.GONE
        morePanel.visibility = View.GONE
    }

    fun arePanelsVisible(): Boolean =
        qualityPanel.visibility == View.VISIBLE || morePanel.visibility == View.VISIBLE

    private fun togglePanel(panel: View) {
        val show = panel.visibility != View.VISIBLE
        qualityPanel.visibility = View.GONE
        morePanel.visibility = View.GONE
        panel.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun bindQualities(options: List<QualityOption>, selectedId: String?) {
        qualities = options
        selectedQualityId = selectedId
        qualityBtn.visibility = if (options.size > 1) View.VISIBLE else View.GONE
        qualityBtn.text = options.firstOrNull { it.sourceId == selectedId }?.label
            ?: context.getString(R.string.vod_quality)

        qualityPanel.removeAllViews()
        options.forEach { option ->
            val item = TextView(context).apply {
                text = option.label
                setTextColor(context.getColor(
                    if (option.sourceId == selectedId) R.color.bili_pink else android.R.color.white
                ))
                textSize = 13f
                val h = (36 * resources.displayMetrics.density).toInt()
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    h
                ).apply { topMargin = (2 * resources.displayMetrics.density).toInt() }
                setPadding(dp(16), 0, dp(16), 0)
                gravity = android.view.Gravity.CENTER_VERTICAL
                setBackgroundResource(
                    if (option.sourceId == selectedId) {
                        R.drawable.bg_quality_option_selected
                    } else {
                        R.drawable.bg_quality_option
                    }
                )
                setOnClickListener {
                    qualityPanel.visibility = View.GONE
                    if (option.sourceId != selectedId) {
                        actionListener?.onQualitySelected(option.sourceId)
                    }
                }
            }
            qualityPanel.addView(item)
        }
    }

    private fun buildSpeedOptions() {
        speedGroup.removeAllViews()
        speedOptions.forEach { speed ->
            val item = TextView(context).apply {
                text = PlayerGestureHints.formatSpeed(speed)
                textSize = 11.5f
                gravity = android.view.Gravity.CENTER
                setTextColor(context.getColor(android.R.color.white))
                layoutParams = LinearLayout.LayoutParams(0, dp(32), 1f).apply {
                    marginEnd = dp(4)
                }
                setBackgroundResource(R.drawable.bg_speed_option)
                setOnClickListener {
                    actionListener?.onSpeedSelected(speed)
                }
            }
            speedGroup.addView(item)
        }
    }

    private fun bindSpeed(speed: Float) {
        currentSpeed = speed
        for (i in 0 until speedGroup.childCount) {
            val child = speedGroup.getChildAt(i) as TextView
            val selected = speedOptions[i] == speed
            child.setBackgroundResource(
                if (selected) R.drawable.bg_speed_option_selected else R.drawable.bg_speed_option
            )
            child.setTextColor(
                context.getColor(if (selected) android.R.color.white else android.R.color.white)
            )
        }
    }

    private fun bindSwitches(muted: Boolean, looping: Boolean) {
        applyingState = true
        muteSwitch.isChecked = muted
        muteIcon.setImageResource(if (muted) R.drawable.ic_player_muted else R.drawable.ic_player_volume)
        loopSwitch.isChecked = looping
        applyingState = false
    }

    private fun showControls() {
        setControlsVisible(true)
    }

    private fun animateTo(target: View, visible: Boolean) {
        target.animate().cancel()
        if (visible) {
            if (target.visibility != View.VISIBLE) {
                target.alpha = 0f
                target.visibility = View.VISIBLE
            }
            target.animate().alpha(1f).setDuration(180L).start()
        } else {
            target.animate().alpha(0f).setDuration(180L).withEndAction {
                target.visibility = View.GONE
            }.start()
        }
    }

    private fun positionFromProgress(progress: Int): Long {
        return if (durationMs > 0L) durationMs * progress / SEEK_BAR_MAX else 0L
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val SEEK_BAR_MAX = 1000

        fun formatTime(ms: Long): String {
            val total = ms.coerceAtLeast(0L) / 1000L
            val hours = total / 3600
            val minutes = (total % 3600) / 60
            val seconds = total % 60
            return if (hours > 0) {
                String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
            }
        }
    }
}
