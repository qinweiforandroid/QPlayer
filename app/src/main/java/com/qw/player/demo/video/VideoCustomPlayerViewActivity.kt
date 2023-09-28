package com.qw.player.demo.video

import android.graphics.Color
import android.graphics.SurfaceTexture
import android.os.Bundle
import android.view.Surface
import android.view.TextureView.SurfaceTextureListener
import com.qw.exoplayer.PodExoplayer
import com.qw.framework.core.ui.BaseActivity
import com.qw.player.core.IPodPlayer.OnPlayListener
import com.qw.player.demo.Constants
import com.qw.player.demo.databinding.VideoCustomVideoPlayerViewActivityBinding
import com.qw.player.demo.widget.AspectRatioFrameLayout
import com.qw.utils.DensityUtil
import com.qw.utils.StatusBar

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
        binding.mAspectRatioFrameLayout.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT
    }

    override fun initData(savedInstanceState: Bundle?) {
        podPlayer = PodExoplayer(this)
        binding.mAspectRatioFrameLayout.addOnLayoutChangeListener { v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            val size = right - left - DensityUtil.getScreenWidth(this)
            if (size > 0) {
                binding.mTextureView.translationX = -size / 2F
            } else {
                binding.mTextureView.translationX = 0F
            }
        }
        binding.mTextureView.keepScreenOn = true
        binding.mTextureView.surfaceTextureListener = object : SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                podPlayer.setSurface(Surface(surface))
            }

            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
            }

            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                podPlayer.setSurface(null)
                return true
            }

            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
            }

        }
        podPlayer.registerListener(object : OnPlayListener {
            override fun onPlayStart() {
                podPlayer.seekTo(5000)
//                podPlayer.pause()
            }

            override fun onVideoSizeChanged(width: Int, height: Int) {
                binding.mAspectRatioFrameLayout.setAspectRatio(width / height.toFloat())
            }
        })
        podPlayer.play(Constants.VIDEO_URL)

    }

    override fun onDestroy() {
        super.onDestroy()
        podPlayer.release()
    }
}