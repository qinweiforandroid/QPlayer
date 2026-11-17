package com.qw.player.demo

import android.app.Application
import android.content.Context
import com.qw.player.core.live.LiveChannelRepository
import com.qw.player.demo.runtime.AudioPlaybackRuntime
import com.qw.player.demo.runtime.LogcatPlaybackTelemetry

class MyApplication : Application() {

    /**
     * 音频 runtime 必须比页面活得久：音频页、前台 Service、通知共用同一条 session，
     * 所以挂在应用作用域上，由页面和 Service 通过 [Context.audioRuntime] 取用。
     */
    val audioRuntime = AudioPlaybackRuntime()

    override fun onCreate() {
        super.onCreate()
        // 直播频道清单来自 assets/live/channels.json，启动时载入一次，页面同步读取。
        LiveChannelRepository.initialize(this)
        audioRuntime.initialize(this)
        // 默认把内核指标打到 Logcat；接入真实上报平台时替换成对应实现即可。
        audioRuntime.setTelemetry(LogcatPlaybackTelemetry())
    }
}

/** 取应用级的音频 runtime。 */
val Context.audioRuntime: AudioPlaybackRuntime
    get() = (applicationContext as MyApplication).audioRuntime
