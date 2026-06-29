package com.qw.player.demo.widget

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import com.qw.player.demo.R

/**
 * Created by qinwei on 2021/6/28 18:27
 */
class VideoControllerView : ConstraintLayout {
    private var mOnSeekBarChangedListener: OnSeekBarChangeListener? = null
    private var mControllerSeekBar: SeekBar
    private var mControllerBackImg: ImageView
    private var mControllerPlayOrPauseImg: ImageView
    private var mControllerNextImg: ImageView
    private var mControllerTitleLabel: TextView
    private var mControllerPosTimeLabel: TextView
    private var mControllerTotalLabel: TextView
    private var isTrackingTouch = false

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    )

    init {
        LayoutInflater.from(context).inflate(R.layout.widget_video_controller_view, this)
        mControllerTitleLabel = findViewById<TextView>(R.id.mControllerTitleLabel)
        mControllerPosTimeLabel = findViewById<TextView>(R.id.mControllerPosTimeLabel)
        mControllerTotalLabel = findViewById<TextView>(R.id.mControllerTotalLabel)
        mControllerBackImg = findViewById<ImageView>(R.id.mControllerBackImg)
        mControllerPlayOrPauseImg = findViewById<ImageView>(R.id.mControllerPlayOrPauseImg)
        mControllerNextImg = findViewById<ImageView>(R.id.mControllerNextImg)
        mControllerSeekBar = findViewById<SeekBar>(R.id.mControllerSeekBar)
        mControllerSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {

            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                isTrackingTouch = true
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                isTrackingTouch = false
                mOnSeekBarChangedListener?.onSeekBarChanged(seekBar)
            }
        })
    }

    fun setOnBackClickListener(listener: View.OnClickListener) {
        mControllerBackImg.setOnClickListener(listener)
    }

    fun setOnSeekChangedListener(listener: OnSeekBarChangeListener) {
        this.mOnSeekBarChangedListener = listener
    }

    fun setMax(max: Int) {
        mControllerSeekBar.max = max
    }

    fun setProgress(progress: Int) {
        if (!isTrackingTouch) {
            mControllerSeekBar.progress = progress
        }
    }

    interface OnSeekBarChangeListener {
        fun onSeekBarChanged(seekBar: SeekBar)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
    }

    fun setTitle(title: String) {
        mControllerTitleLabel.text = title
    }
}
