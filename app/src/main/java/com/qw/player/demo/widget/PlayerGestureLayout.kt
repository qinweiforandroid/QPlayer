package com.qw.player.demo.widget

import android.content.Context
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import kotlin.math.abs

/**
 * 视频播放页的通用手势入口。
 *
 * 作为 `PlayerView` 与控制层的父容器存在，只把触摸翻译成语义化回调，
 * 不持有播放器、不改亮度音量。
 *
 * 布局约定：子 View（播放按钮、进度条）会优先消费自己的触摸，
 * 空白区域的手势才会落到这里，因此控制层的整屏遮罩不应设置为 clickable。
 */
class PlayerGestureLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    enum class Mode {
        /** 点播：双击快进快退、长按倍速、水平拖动 seek、垂直调亮度/音量。 */
        VOD,

        /** 直播：禁用双击与拖动 seek，垂直滑动切台。 */
        LIVE
    }

    private enum class Drag {
        NONE,
        SEEK,
        BRIGHTNESS,
        VOLUME,
        CHANNEL
    }

    interface Listener {
        fun onSingleTap()

        /** 双击画面：点播用于切换播放/暂停。 */
        fun onDoubleTap() {}

        fun onLongPressStart() {}

        fun onLongPressEnd() {}

        fun onSeekStart() {}

        fun onSeekPreview(targetMs: Long) {}

        fun onSeekCommit(targetMs: Long) {}

        fun onBrightnessChange(fraction: Float) {}

        fun onVolumeChange(fraction: Float) {}
        fun onVerticalDragEnd() {}
        fun onChannelSwipe(next: Boolean) {}
    }

    var mode: Mode = Mode.VOD
        set(value) {
            field = value
            resetState()
        }

    var listener: Listener? = null

    /** 仅在 seek 手势开始时读取一次，避免拖动过程时长变化导致目标漂移。 */
    var durationMs: Long = 0L
    var positionMs: Long = 0L

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val swipeThreshold = resources.displayMetrics.density * CHANNEL_SWIPE_THRESHOLD_DP

    /** 纵向手势从 0 拉到 100% 所需的位移，固定物理距离，不随容器高度变化。 */
    private val verticalSpan = resources.displayMetrics.density * VERTICAL_DRAG_SPAN_DP

    private var drag = Drag.NONE
    private var dragging = false
    private var longPressActive = false
    private var downX = 0f
    private var downY = 0f
    private var seekTargetMs = 0L

    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {

            override fun onDown(e: MotionEvent): Boolean {
                // 必须返回 true，否则单击 / 长按不会被识别。
                return true
            }

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                listener?.onSingleTap()
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (mode != Mode.VOD || dragging) {
                    return false
                }
                listener?.onDoubleTap()
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                if (mode != Mode.VOD || dragging) {
                    return
                }
                longPressActive = true
                listener?.onLongPressStart()
            }
        }
    )

    init {
        isClickable = true
        isFocusable = true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                drag = Drag.NONE
                dragging = false
                longPressActive = false
            }

            MotionEvent.ACTION_MOVE -> {
                if (!dragging) {
                    dragging = true
                    resolveDrag(event)
                }
                if (dragging) {
                    applyDrag(event)
                }
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                commitGesture(event)
                resetState()
            }
        }
        return true
    }

    private fun resolveDrag(event: MotionEvent) {
        val dx = event.x - downX
        val dy = event.y - downY
        if (abs(dx) < touchSlop && abs(dy) < touchSlop) {
            dragging = false
            return
        }

        // 一旦进入拖动，长按倍速必须立即退出。
        if (longPressActive) {
            longPressActive = false
            listener?.onLongPressEnd()
        }

        if (abs(dx) > abs(dy)) {
            if (mode == Mode.VOD && durationMs > 0L) {
                drag = Drag.SEEK
                seekTargetMs = positionMs
                listener?.onSeekStart()
            } else {
                drag = Drag.NONE
            }
        } else {
            drag = if (mode == Mode.LIVE) {
                Drag.CHANNEL
            } else if (downX < width / 2f) {
                Drag.BRIGHTNESS
            } else {
                Drag.VOLUME
            }
        }
    }

    private fun applyDrag(event: MotionEvent) {
        when (drag) {
            Drag.SEEK -> {
                val deltaFraction = (event.x - downX) / width.toFloat()
                val target = positionMs + (deltaFraction * durationMs).toLong()
                seekTargetMs = target.coerceIn(0L, durationMs)
                listener?.onSeekPreview(seekTargetMs)
            }

            Drag.BRIGHTNESS, Drag.VOLUME -> {
                // 相对按下点的累计位移，换算跨度固定，保证小窗和全屏手感一致。
                val fraction = (downY - event.y) / verticalSpan
                if (drag == Drag.BRIGHTNESS) {
                    listener?.onBrightnessChange(fraction)
                } else {
                    listener?.onVolumeChange(fraction)
                }
            }

            Drag.CHANNEL,
            Drag.NONE -> Unit
        }
    }

    private fun commitGesture(event: MotionEvent) {
        when (drag) {
            Drag.SEEK -> listener?.onSeekCommit(seekTargetMs)
            Drag.BRIGHTNESS,
            Drag.VOLUME -> listener?.onVerticalDragEnd()

            Drag.CHANNEL -> {
                // 只有越过阈值才算切台，避免误触。
                val dy = event.y - downY
                if (dy < -swipeThreshold) {
                    listener?.onChannelSwipe(true)
                } else if (dy > swipeThreshold) {
                    listener?.onChannelSwipe(false)
                }
            }

            Drag.NONE -> Unit
        }

        if (longPressActive) {
            longPressActive = false
            listener?.onLongPressEnd()
        }
    }

    private fun resetState() {
        drag = Drag.NONE
        dragging = false
        longPressActive = false
    }

    companion object {
        private const val CHANNEL_SWIPE_THRESHOLD_DP = 60

        /** 纵向手势从 0 拉到 100% 所需的位移，单位 dp。 */
        private const val VERTICAL_DRAG_SPAN_DP = 240
    }
}
