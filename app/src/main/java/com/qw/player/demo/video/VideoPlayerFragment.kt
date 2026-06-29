package com.qw.player.demo.video

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.qw.player.demo.databinding.VideoPodplayerFragmentBinding

/**
 * Created by qinwei on 2021/6/28 17:22
 */
class VideoPlayerFragment : Fragment() {
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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bind.mExoplayerVideoBtn.setOnClickListener {
            startActivity(Intent(requireContext(), VideoMedia3PlayerViewActivity::class.java))
        }
    }
}
