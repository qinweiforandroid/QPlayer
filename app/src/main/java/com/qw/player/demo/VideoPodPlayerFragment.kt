package com.qw.player.demo

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.SurfaceHolder
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import com.qw.framework.core.ui.BaseFragment
import com.qw.player.core.IPodPlayer
import com.qw.player.demo.databinding.VideoPodplayerFragmentBinding
import com.qw.player.demo.widget.VideoControllerView
import com.xiaoniu.exoplayer.PodExoplayer

/**
 * Created by qinwei on 2021/6/28 17:22
 */
class VideoPodPlayerFragment : BaseFragment() {
    private lateinit var bind: VideoPodplayerFragmentBinding
    override fun getCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return VideoPodplayerFragmentBinding.inflate(inflater, container, false).apply {
            bind = this
        }.root
    }

    override fun initView(view: View) {
        bind.mExoplayerVideoBtn.setOnClickListener {
            startActivity(Intent(requireContext(),VideoPodPlayerActivity::class.java))

        }
        bind.mMediaVideoBtn.setOnClickListener {

        }
    }

    override fun initData() {

    }
}