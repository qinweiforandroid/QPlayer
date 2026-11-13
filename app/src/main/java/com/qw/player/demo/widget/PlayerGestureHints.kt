package com.qw.player.demo.widget

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.qw.player.demo.R
import java.util.Locale

/**
 * 手势反馈浮层：快进快退、进度预览、亮度音量气泡、倍速与切台提示。
 *
 * 只做展示，所有数值由页面传入。
 */
class PlayerGestureHints @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val handler = Handler(Looper.getMainLooper())

    private val doubleTapHint: View
    private val doubleTapIcon: ImageView
    private val doubleTapLabel: TextView
    private val seekPreview: View
    private val seekTargetLabel: TextView
    private val seekTotalLabel: TextView
    private val levelBubble: View
    private val levelIcon: ImageView
    private val levelFill: View
    private val levelLabel: TextView
    private val speedHint: TextView
    private val channelSwitchHint: TextView

    private val hideDoubleTapRunnable = Runnable { doubleTapHint.visibility = View.GONE }
    private val hideSpeedRunnable = Runnable { speedHint.visibility = View.GONE }
    private val hideChannelRunnable = Runnable { channelSwitchHint.visibility = View.GONE }

    init {
        LayoutInflater.from(context).inflate(R.layout.widget_player_gesture_hints, this, true)
        doubleTapHint = findViewById(R.id.mDoubleTapHint)
        doubleTapIcon = findViewById(R.id.mDoubleTapIcon)
        doubleTapLabel = findViewById(R.id.mDoubleTapLabel)
        seekPreview = findViewById(R.id.mSeekPreview)
        seekTargetLabel = findViewById(R.id.mSeekTargetLabel)
        seekTotalLabel = findViewById(R.id.mSeekTotalLabel)
        levelBubble = findViewById(R.id.mLevelBubble)
        levelIcon = findViewById(R.id.mLevelIcon)
        levelFill = findViewById(R.id.mLevelFill)
        levelLabel = findViewById(R.id.mLevelLabel)
        speedHint = findViewById(R.id.mSpeedHint)
        channelSwitchHint = findViewById(R.id.mChannelSwitchHint)
        isClickable = false
        isFocusable = false
    }

    override fun onInterceptTouchEvent(ev: android.view.MotionEvent): Boolean = false

    /** 双击快进/快退提示。[forward] 为 true 时显示快进。 */
    fun showSeekStep(seconds: Int, forward: Boolean) {
        doubleTapIcon.setImageResource(
            if (forward) R.drawable.ic_gesture_forward else R.drawable.ic_gesture_backward
        )
        doubleTapLabel.text = context.getString(
            if (forward) R.string.gesture_forward_seconds else R.string.gesture_backward_seconds,
            seconds
        )
        doubleTapHint.visibility = View.VISIBLE
        doubleTapHint.alpha = 0f
        doubleTapHint.animate().alpha(1f).setDuration(120L).start()
        handler.removeCallbacks(hideDoubleTapRunnable)
        handler.postDelayed(hideDoubleTapRunnable, 700L)
    }

    /** 开始拖动进度。 */
    fun showSeekPreview(targetMs: Long, totalMs: Long) {
        seekPreview.visibility = View.VISIBLE
        seekTargetLabel.text = formatDuration(targetMs)
        seekTotalLabel.text = context.getString(R.string.gesture_seek_total, formatDuration(totalMs))
    }

    /** 更新拖动中的目标时间。 */
    fun updateSeekPreview(targetMs: Long) {
        seekTargetLabel.text = formatDuration(targetMs)
    }

    fun hideSeekPreview() {
        seekPreview.visibility = View.GONE
    }

    /** 亮度/音量气泡。[percent] 取值 0f..1f。 */
    fun showLevel(isBrightness: Boolean, percent: Float) {
        levelIcon.setImageResource(
            if (isBrightness) R.drawable.ic_player_brightness else R.drawable.ic_player_volume
        )
        levelBubble.visibility = View.VISIBLE
        setLevelFill(percent)
        levelLabel.text = "${(percent.coerceIn(0f, 1f) * 100).toInt()}%"
    }

    fun hideLevel() {
        levelBubble.visibility = View.GONE
    }

    /** 长按倍速提示。 */
    fun showSpeed(speed: Float) {
        speedHint.text = context.getString(R.string.gesture_speed_hint, formatSpeed(speed))
        speedHint.visibility = View.VISIBLE
        handler.removeCallbacks(hideSpeedRunnable)
    }

    fun hideSpeed() {
        handler.removeCallbacks(hideSpeedRunnable)
        speedHint.visibility = View.GONE
    }

    /** 直播切台提示。 */
    fun showChannelSwitch(text: String) {
        channelSwitchHint.text = text
        channelSwitchHint.visibility = View.VISIBLE
        channelSwitchHint.animate().alpha(1f).setDuration(120L).start()
        handler.removeCallbacks(hideChannelRunnable)
        handler.postDelayed(hideChannelRunnable, 800L)
    }

    fun clearAll() {
        handler.removeCallbacksAndMessages(null)
        doubleTapHint.visibility = View.GONE
        seekPreview.visibility = View.GONE
        levelBubble.visibility = View.GONE
        speedHint.visibility = View.GONE
        channelSwitchHint.visibility = View.GONE
    }

    private fun setLevelFill(percent: Float) {
        val trackHeight = (levelFill.parent as? View)?.height ?: 0
        val usable = if (trackHeight > 0) trackHeight else (dp(84))
        levelFill.layoutParams = (levelFill.layoutParams as LayoutParams).apply {
            height = (usable * percent.coerceIn(0f, 1f)).toInt()
        }
        levelFill.requestLayout()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDetachedFromWindow() {
        handler.removeCallbacksAndMessages(null)
        super.onDetachedFromWindow()
    }

    private fun formatDuration(positionMs: Long): String {
        val totalSeconds = positionMs.coerceAtLeast(0L) / 1000L
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
        }
    }

    companion object {
        fun formatSpeed(speed: Float): String {
            return if (speed == speed.toInt().toFloat()) {
                "${speed.toInt()}.0x"
            } else {
                "${speed}x"
            }
        }
    }
}
