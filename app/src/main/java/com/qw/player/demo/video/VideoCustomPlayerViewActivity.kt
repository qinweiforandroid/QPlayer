package com.qw.player.demo.video

import android.graphics.Color
import android.os.Bundle
import com.qw.exoplayer.PodExoplayer
import com.qw.framework.ui.BaseActivity
import com.qw.player.core.IPodPlayer.OnPlayListener
import com.qw.player.demo.Constants
import com.qw.player.demo.databinding.VideoCustomVideoPlayerViewActivityBinding
import com.qw.player.demo.widget.AspectRatioFrameLayout
import com.qw.utils.StatusBar
import com.qw.utils.StatusBarUtil

/**
 * Created by qinwei on 2023/9/28 16:22
 * email: qinwei_it@163.com
 */
class VideoCustomPlayerViewActivity : BaseActivity() {
    private lateinit var podPlayer: PodExoplayer
    private lateinit var binding: VideoCustomVideoPlayerViewActivityBinding

    override fun setContentView() {
        binding = VideoCustomVideoPlayerViewActivityBinding.inflate(layoutInflater, null, false)
        setContentView(binding.root, false)
        StatusBar.get(this)
            .setStatusBarColor(Color.TRANSPARENT)
            .setDecorFitsSystemWindows(false)
    }

    override fun initView() {
//        binding.mVideoTextureView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT
//        binding.mVideoTextureView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
        binding.mVideoTextureView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ASPECT_FILL
    }

    override fun initData(savedInstanceState: Bundle?) {
        podPlayer = PodExoplayer(this)
        binding.mVideoTextureView.bindPodPlayer(podPlayer)
        podPlayer.registerListener(object : OnPlayListener {
            override fun onPlayStart() {
//                podPlayer.seekTo(5000)
            }
        })
        podPlayer.play(Constants.VIDEO_URL)
    }

    override fun onDestroy() {
        super.onDestroy()
        podPlayer.release()
    }
}