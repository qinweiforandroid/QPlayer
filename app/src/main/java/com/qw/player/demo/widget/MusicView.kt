package com.qw.player.demo.widget

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import com.bumptech.glide.Glide
import com.qw.player.demo.R

/**
 * Created by qinwei on 4/18/21 8:50 PM
 * email: qinwei_it@163.com
 */
class MusicView : ConstraintLayout {

    private var mMusicIconImg: ImageView
    private var mMusicPlayStateImg: ImageView
    private var mMusicNameLabel: TextView
    private var mMusicSingerLabel: TextView
    private var mMusicLoadingProgressBar: ProgressBar
    private var mMusicSeekBar: SeekBar
    private var listener: OnSeekChangedListener? = null
    private var mMusicVO = MusicVO()

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
            context,
            attrs,
            defStyleAttr
    )

    private var isTrackingTouch = false

    init {
        LayoutInflater.from(context).inflate(R.layout.widget_music, this)
        mMusicIconImg = findViewById(R.id.mMusicIconImg)
        mMusicPlayStateImg = findViewById(R.id.mMusicPlayStateImg)
        mMusicNameLabel = findViewById(R.id.mMusicNameLabel)
        mMusicSingerLabel = findViewById(R.id.mMusicSingerLabel)
        mMusicLoadingProgressBar = findViewById(R.id.mMusicLoadingProgressBar)
        mMusicSeekBar = findViewById(R.id.mMusicSeekBar)
        mMusicSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {

            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
                isTrackingTouch = true

            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                isTrackingTouch = false
                listener?.onSeekChanged(seekBar)
            }
        })
    }

    fun setCover(cover: String): MusicView {
        mMusicVO.cover = cover
        return this
    }

    fun setUrl(url: String): MusicView {
        mMusicVO.url = url
        return this
    }

    fun setSinger(singer: String): MusicView {
        mMusicVO.singer = singer
        return this;
    }

    fun setName(name: String): MusicView {
        mMusicVO.name = name
        return this;
    }

    fun setMusicVO(vo: MusicVO) {
        this.mMusicVO = vo
    }

    fun notifyDataChanged() {
        mMusicVO.apply {
            mMusicNameLabel.text = name
            Glide.with(this@MusicView).load(cover).into(mMusicIconImg)
            mMusicSingerLabel.text = singer
        }
    }

    fun paused() {
        mMusicPlayStateImg.setImageResource(R.drawable.ic_baseline_play_arrow_24)
    }

    fun playing() {
        mMusicPlayStateImg.setImageResource(R.drawable.ic_baseline_pause_24)
    }

    fun loading(isLoading: Boolean) {
        if (isLoading) {
            mMusicLoadingProgressBar.visibility = View.VISIBLE
        } else {
            mMusicLoadingProgressBar.visibility = View.GONE
        }
    }

    fun setOnMusicPlayStateClickListener(listener: View.OnClickListener) {
        mMusicPlayStateImg.setOnClickListener(listener)
    }

    fun setOnSeekChangedListener(listener: OnSeekChangedListener) {
        this.listener = listener
    }

    fun setMax(max: Int) {
        mMusicSeekBar.max = max
    }

    fun setProgress(progress: Int) {
        mMusicSeekBar.progress = progress
    }

    interface OnSeekChangedListener {
        fun onSeekChanged(seekBar: SeekBar)
    }

    class MusicVO {
        var cover = ""
        var url = ""
        var name = ""
        var singer = ""
    }
}