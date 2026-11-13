package com.qw.player.demo.widget

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.qw.player.core.engine.PlaybackState
import com.qw.player.demo.R

/**
 * 直播控制层。
 *
 * 与点播控制层的差异：
 * - 没有进度条、没有播放时间
 * - 不提供 seek 交互
 * - 只有播放暂停、线路切换、音量、亮度、全屏
 */
class LivePlayerControlView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    interface ActionListener {
        fun onBackClicked()
        fun onPlayPauseClicked()
        fun onLineClicked()
        fun onLineSelected(lineId: String)
        fun onVolumeClicked()
        fun onBrightnessClicked()
        fun onFullscreenClicked()
        fun onChannelListClicked()
        fun onControllerTapped()
    }

    data class LineOption(val id: String, val label: String)

    data class State(
        val channelName: String = "",
        val epgText: String = "",
        val isFullscreen: Boolean = false,
        val playbackState: PlaybackState = PlaybackState.IDLE,
        val lines: List<LineOption> = emptyList(),
        val selectedLineId: String? = null
    )

    private val topScrim: View
    private val bottomScrim: View
    private val topControls: View
    private val bottomControls: View
    private val backBtn: View
    private val channelListBtn: View
    private val channelNameLabel: TextView
    private val epgLabel: TextView
    private val playPauseBtn: MaterialButton
    private val lineBtn: TextView
    private val volumeBtn: MaterialButton
    private val brightnessBtn: MaterialButton
    private val fullscreenBtn: MaterialButton
    private val linePanel: LinearLayout

    private var actionListener: ActionListener? = null
    private var controlsVisible = true
    private var lines: List<LineOption> = emptyList()
    private var selectedLineId: String? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.widget_live_player_controls, this, true)

        topScrim = findViewById(R.id.mTopScrim)
        bottomScrim = findViewById(R.id.mBottomScrim)
        topControls = findViewById(R.id.mTopControls)
        bottomControls = findViewById(R.id.mBottomControls)
        backBtn = findViewById(R.id.mBackBtn)
        channelListBtn = findViewById(R.id.mChannelListBtn)
        channelNameLabel = findViewById(R.id.mChannelNameLabel)
        epgLabel = findViewById(R.id.mEpgLabel)
        playPauseBtn = findViewById(R.id.mPlayPauseBtn)
        lineBtn = findViewById(R.id.mLineBtn)
        volumeBtn = findViewById(R.id.mVolumeBtn)
        brightnessBtn = findViewById(R.id.mBrightnessBtn)
        fullscreenBtn = findViewById(R.id.mFullscreenBtn)
        linePanel = findViewById(R.id.mLinePanel)

        backBtn.setOnClickListener { actionListener?.onBackClicked() }
        channelListBtn.setOnClickListener { actionListener?.onChannelListClicked() }
        playPauseBtn.setOnClickListener { actionListener?.onPlayPauseClicked() }
        lineBtn.setOnClickListener {
            if (lines.size <= 1) {
                actionListener?.onLineClicked()
            } else {
                linePanel.visibility =
                    if (linePanel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            }
        }
        volumeBtn.setOnClickListener { actionListener?.onVolumeClicked() }
        brightnessBtn.setOnClickListener { actionListener?.onBrightnessClicked() }
        fullscreenBtn.setOnClickListener { actionListener?.onFullscreenClicked() }
        setControlsVisible(false)
    }

    fun setActionListener(listener: ActionListener?) {
        actionListener = listener
    }

    fun render(state: State) {
        channelNameLabel.text = state.channelName
        if (state.epgText.isBlank()) {
            epgLabel.visibility = View.GONE
        } else {
            epgLabel.visibility = View.VISIBLE
            epgLabel.text = state.epgText
        }
        bindPlaybackState(state.playbackState)
        bindFullscreen(state.isFullscreen)
        // 线路列表只在真正变化时重建，避免高频状态回调反复 inflate。
        if (state.lines != lines || state.selectedLineId != selectedLineId) {
            bindLines(state.lines, state.selectedLineId)
        }
    }

    fun bindPlaybackState(state: PlaybackState) {
        val playing = state == PlaybackState.PLAYING ||
            state == PlaybackState.BUFFERING ||
            state == PlaybackState.PREPARING
        playPauseBtn.setIconResource(if (playing) R.drawable.ic_player_pause else R.drawable.ic_player_play)
        playPauseBtn.contentDescription = context.getString(
            if (playing) R.string.player_pause else R.string.player_play
        )
    }

    fun bindFullscreen(fullscreen: Boolean) {
        fullscreenBtn.setIconResource(
            if (fullscreen) R.drawable.ic_player_fullscreen_exit else R.drawable.ic_player_fullscreen
        )
    }

    fun bindVolume(muted: Boolean) {
        volumeBtn.setIconResource(if (muted) R.drawable.ic_player_muted else R.drawable.ic_player_volume)
    }

    fun setControlsVisible(visible: Boolean) {
        controlsVisible = visible
        if (!visible) {
            linePanel.visibility = View.GONE
        }
        listOf(topScrim, bottomScrim, topControls, bottomControls).forEach { animateTo(it, visible) }
    }

    fun areControlsVisible(): Boolean = controlsVisible

    fun arePanelsVisible(): Boolean = linePanel.visibility == View.VISIBLE

    fun hidePanels() {
        linePanel.visibility = View.GONE
    }

    private fun bindLines(options: List<LineOption>, selectedId: String?) {
        lines = options
        selectedLineId = selectedId
        lineBtn.visibility = if (options.size > 1) View.VISIBLE else View.GONE
        lineBtn.text = options.firstOrNull { it.id == selectedId }?.label
            ?: context.getString(R.string.live_line)

        linePanel.removeAllViews()
        options.forEach { option ->
            val item = TextView(context).apply {
                text = option.label
                textSize = 13f
                gravity = android.view.Gravity.CENTER_VERTICAL
                setTextColor(
                    context.getColor(
                        if (option.id == selectedId) R.color.qp_accent else android.R.color.white
                    )
                )
                val h = dp(36)
                layoutParams = LinearLayout.LayoutParams(dp(120), h).apply {
                    topMargin = dp(2)
                }
                setPadding(dp(14), 0, dp(14), 0)
                setBackgroundResource(
                    if (option.id == selectedId) {
                        R.drawable.bg_quality_option_selected
                    } else {
                        R.drawable.bg_quality_option
                    }
                )
                setOnClickListener {
                    linePanel.visibility = View.GONE
                    if (option.id != selectedId) {
                        actionListener?.onLineSelected(option.id)
                    }
                }
            }
            linePanel.addView(item)
        }
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

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
