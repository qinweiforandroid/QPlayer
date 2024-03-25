package com.qw.player.demo.video

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.qw.framework.ui.BaseFragment
import com.qw.player.demo.databinding.VideoPodplayerFragmentBinding

/**
 * Created by qinwei on 2021/6/28 17:22
 */
class VideoPodPlayerFragment : BaseFragment() {
    private lateinit var bind: VideoPodplayerFragmentBinding
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return VideoPodplayerFragmentBinding.inflate(inflater, container, false).apply {
            bind = this
        }.root
    }

    override fun initView(view: View) {
        bind.mExoplayerVideoBtn.setOnClickListener {
            startActivity(Intent(requireContext(), VideoMedia3PlayerViewActivity::class.java))

        }
        bind.mMediaVideoBtn.setOnClickListener {
            startActivity(Intent(requireContext(), VideoCustomPlayerViewActivity::class.java))
        }
    }

    override fun initData() {

    }
}