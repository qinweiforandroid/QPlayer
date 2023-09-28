package com.qw.player.demo.video

import android.os.Bundle
import android.view.SurfaceHolder
import android.view.View
import android.widget.SeekBar
import com.qw.framework.core.ui.BaseActivity
import com.qw.player.core.IPodPlayer
import com.qw.player.demo.databinding.VideoPodplayerActivityBinding
import com.qw.player.demo.widget.VideoControllerView
import com.qw.exoplayer.PodExoplayer

/**
 * Created by qinwei on 2021/6/28 17:22
 */
class VideoPodPlayerActivity : BaseActivity() {
    private lateinit var bind: VideoPodplayerActivityBinding
    private lateinit var podPlayer: PodExoplayer
    override fun setContentView() {
        VideoPodplayerActivityBinding.inflate(layoutInflater, null, false).apply {
            bind = this
            setContentView(this.root, false)
        }
    }

    override fun initView() {
        podPlayer = PodExoplayer(this)
        bind.playerView.player = podPlayer.exoPlayer
        podPlayer.registerListener(object : IPodPlayer.OnPlayListener {
            override fun onPlayConnect() {
            }

            override fun onPlayStart() {
            }


            override fun onPlayProgressUpdated(cur: Int, total: Int) {
            }
        })
        podPlayer.play("https://storage.googleapis.com/exoplayer-test-media-1/mp4/dizzy-with-tx3g.mp4")
    }

    override fun initData(savedInstanceState: Bundle?) {

    }
}