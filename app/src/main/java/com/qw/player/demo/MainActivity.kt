package com.qw.player.demo

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.qw.framework.App
import com.qw.framework.AppStateTracker
import com.qw.framework.ui.QFragmentActivity.Clazz
import com.qw.framework.ui.SupportFragmentListActivity
import java.util.*

class MainActivity : AppCompatActivity() {
    private val handler = Handler(Looper.myLooper()!!)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        App.init(App.Builder(this))
        AppStateTracker.init(application)
        handler.postDelayed({ goNext() }, 500)
    }

    private fun goNext() {
        val list = ArrayList<Clazz>()
        list.add(Clazz("media_player", PodPlayerFragment::class.java))
//        list.add(Clazz("播放详情", AudioPlayFragment::class.java))
        SupportFragmentListActivity.startActivity(this, list, false)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}