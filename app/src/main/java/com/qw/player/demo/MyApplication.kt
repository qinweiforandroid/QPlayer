package com.qw.player.demo

import android.app.Application
import android.os.Handler
import android.os.Looper
import com.qw.framework.core.App
import com.qw.framework.core.tracker.AppStateTracker
import com.qw.player.list.IUrlLoad
import com.qw.player.list.UrlLoadCallback

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        App.init(App.Builder(this))
        AppStateTracker.init(this)
        PlayManager.init(this)
        PlayManager.injectUrlLoad(object : IUrlLoad {
            override fun load(id: String, callback: UrlLoadCallback) {
                //fixme load url by ID
                val url = "http://m10.music.126.net/20210423195257/1dea994446019b032b0da563474e3568/ymusic/0409/0558/005d/3c30ad207f221448759e7716e61df79d.mp3"
                Handler(Looper.myLooper()!!).postDelayed({
                    callback.onLoadSuccess(url)
                }, 2000)

            }
        })
    }
}