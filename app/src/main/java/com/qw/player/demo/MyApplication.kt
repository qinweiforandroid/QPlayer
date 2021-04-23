package com.qw.player.demo

import android.app.Application
import com.qw.framework.App
import com.qw.framework.AppStateTracker

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        App.init(App.Builder(this))
        AppStateTracker.init(this)
        PlayManager.init(this)
    }
}