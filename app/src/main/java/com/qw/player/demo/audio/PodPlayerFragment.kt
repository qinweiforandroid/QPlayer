package com.qw.player.demo.audio

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import com.qw.framework.ui.BaseFragment
import com.qw.player.core.IPodPlayer
import com.qw.player.core.IPodPlayer.OnPlayListener
import com.qw.player.demo.R
import com.qw.player.media.PodMediaPlayer


class PodPlayerFragment : BaseFragment(R.layout.pod_player_fragment) {
    private var url = "http://mpge.5nd.com/2016/2016-3-18/71210/1.mp3"
    private lateinit var podPlayer: IPodPlayer
    private lateinit var mPlayerBtn: Button
    private lateinit var mPlayerSeekBar: SeekBar

    private var isTrackingTouch = false
    override fun initView(view: View) {
        mPlayerBtn = findViewById(R.id.mPlayerBtn)
        mPlayerSeekBar = findViewById(R.id.mPlayerSeekBar)
        mPlayerSeekBar.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {}
            override fun onStartTrackingTouch(seekBar: SeekBar) {
                isTrackingTouch = true
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                isTrackingTouch = false
                if (podPlayer.isPrepared) {
                    podPlayer.seekTo(seekBar.progress)
                }
            }
        })
        mPlayerBtn.setOnClickListener(View.OnClickListener {
            if (podPlayer.isPlaying) {
                podPlayer.pause()
            } else if (podPlayer.isPaused) {
                podPlayer.resume()
            } else {
                podPlayer.play(url)
            }
        })
        podPlayer = PodMediaPlayer(requireContext())
        podPlayer.registerListener(object : OnPlayListener {
            override fun onPlayConnect() {
                mPlayerBtn.text = "加载中"
                mPlayerBtn.isEnabled = false
            }

            override fun onPlayStart() {
                mPlayerBtn.isEnabled = true
                setButtonText()
            }

            override fun onPlayPaused() {
                setButtonText()
            }

            override fun onPlayResumed() {
                setButtonText()
            }

            override fun onPlayStopped() {
                setButtonText()
            }

            override fun onPlayBufferingUpdated(percent: Int) {
                mPlayerSeekBar.secondaryProgress = (podPlayer.during * percent * 0.01).toInt()
            }

            override fun onPlayProgressUpdated(cur: Int, total: Int) {
                if (!isTrackingTouch) {
                    mPlayerSeekBar.post {
                        mPlayerSeekBar.max = total
                        mPlayerSeekBar.progress = cur
                    }
                }
            }

            override fun onPlayCompleted() {
                setButtonText()
            }

            override fun onPlayError(code: Int, message: String) {
                mPlayerBtn.isEnabled = true
            }
        })
    }

    override fun initData() {

    }

    private fun setButtonText() {
        if (podPlayer.isPlaying) {
            mPlayerBtn.text = "暂停"
        } else {
            mPlayerBtn.text = "播放"
        }
    }

    override fun onStop() {
        super.onStop()
        podPlayer.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        podPlayer.release()
    }
}