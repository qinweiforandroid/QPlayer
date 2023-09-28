package com.qw.player.demo.video

import android.os.Bundle
import com.qw.exoplayer.PodExoplayer
import com.qw.framework.core.ui.BaseActivity
import com.qw.player.core.IPodPlayer
import com.qw.player.demo.Constants
import com.qw.player.demo.databinding.VideoMedia3PlayerViewActivityBinding

/**
 * Created by qinwei on 2021/6/28 17:22
 */
class VideoMedia3PlayerViewActivity : BaseActivity() {
    private lateinit var bind: VideoMedia3PlayerViewActivityBinding
    private lateinit var podPlayer: PodExoplayer
    override fun setContentView() {
        VideoMedia3PlayerViewActivityBinding.inflate(layoutInflater, null, false).apply {
            bind = this
            setContentView(this.root, false)
        }
    }

    override fun initView() {
        podPlayer = PodExoplayer(this)
        podPlayer.setVideoScalingMode(IPodPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING)
        bind.playerView.player = podPlayer.exoPlayer
        podPlayer.registerListener(object : IPodPlayer.OnPlayListener {
            override fun onPlayConnect() {
            }

            override fun onPlayStart() {
            }


            override fun onPlayProgressUpdated(cur: Int, total: Int) {
            }
        })
        podPlayer.play(Constants.VIDEO_URL)
    }

    override fun initData(savedInstanceState: Bundle?) {

    }

    override fun onPause() {
        super.onPause()
        podPlayer.pause()
    }

    override fun onResume() {
        super.onResume()
        if (podPlayer.isPaused) {
            podPlayer.resume()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        podPlayer.release()
    }
}