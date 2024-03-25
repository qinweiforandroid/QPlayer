package com.qw.player.demo

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.qw.framework.ui.tools.ContainerClazz
import com.qw.framework.ui.tools.FragmentListActivity
import com.qw.player.demo.audio.PlayListFragment
import com.qw.player.demo.audio.PodPlayerFragment
import com.qw.player.demo.video.VideoPodPlayerFragment
import java.util.*

class MainActivity : AppCompatActivity() {
    private val handler = Handler(Looper.myLooper()!!)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        handler.postDelayed({ goNext() }, 500)
    }

    private fun goNext() {
        val list = ArrayList<ContainerClazz>()
        list.add(ContainerClazz("MediaPlayer", PodPlayerFragment::class.java))
        list.add(ContainerClazz("PlayList", PlayListFragment::class.java))
        list.add(ContainerClazz("VideoPodPlayer", VideoPodPlayerFragment::class.java))
        FragmentListActivity.startActivity(this, list, false)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}