package com.qw.player.demo

import android.os.Bundle
import android.view.SurfaceHolder
import android.view.View
import android.widget.SeekBar
import com.qw.framework.core.ui.BaseActivity
import com.qw.player.core.IPodPlayer
import com.qw.player.demo.databinding.VideoPodplayerActivityBinding
import com.qw.player.demo.widget.VideoControllerView
import com.xiaoniu.exoplayer.PodExoplayer

/**
 * Created by qinwei on 2021/6/28 17:22
 */
class VideoPodPlayerActivity : BaseActivity() {
    private lateinit var bind: VideoPodplayerActivityBinding
    private lateinit var podPlayer: IPodPlayer
    override fun setContentView() {
        VideoPodplayerActivityBinding.inflate(layoutInflater,null, false).apply {
            bind = this
            setContentView(this.root,false)
        }
    }

    override fun initView() {
        bind.mVideoControllerView.setOnSeekChangedListener(object : VideoControllerView.OnSeekBarChangeListener {

            override fun onSeekBarChanged(seekBar: SeekBar) {
                podPlayer.seekTo(seekBar.progress)
            }
        })
        podPlayer = PodExoplayer(this)
        podPlayer.registerListener(object : IPodPlayer.OnPlayListener {
            override fun onPlayConnect() {
                bind.mVideoProgressBar.visibility = View.VISIBLE
            }

            override fun onPlayStart() {
                bind.mVideoProgressBar.visibility = View.GONE
            }

            override fun onPlayPaused() {

            }

            override fun onPlayResumed() {
            }

            override fun onPlayStopped() {
            }

            override fun onPlayBufferingUpdated(percent: Int) {
            }

            override fun onPlayProgressUpdated(cur: Int, total: Int) {
                bind.mVideoControllerView.setMax(total)
                bind.mVideoControllerView.setProgress(cur)
            }

            override fun onPlayCompleted() {
            }

            override fun onPlayError(code: Int, message: String?) {

            }
        })
        bind.mVideoSurfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                podPlayer.setSurface(holder.surface)
            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                podPlayer.release()
            }
        })

        podPlayer.play("http://vfx.mtime.cn/Video/2019/03/21/mp4/190321153853126488.mp4")
    }

    override fun initData(savedInstanceState: Bundle?) {

    }
}