package com.qw.player.demo

import android.app.Application
import android.os.Handler
import android.os.Looper
import com.qw.player.core.source.MediaSourceResolver
import com.qw.player.core.media.PlaySource
import com.qw.player.core.media.ResolvedMedia
import com.qw.player.demo.runtime.AudioPlaybackRuntime
import com.qw.player.demo.runtime.LogcatPlaybackTelemetry
import com.qw.player.demo.runtime.VideoPlaybackRuntime

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AudioPlaybackRuntime.initialize(this)
        VideoPlaybackRuntime.initialize(this)
        // 默认把内核指标打到 Logcat；接入真实上报平台时替换成对应实现即可。
        val telemetry = LogcatPlaybackTelemetry()
        AudioPlaybackRuntime.setTelemetry(telemetry)
        VideoPlaybackRuntime.setTelemetry(telemetry)
    }
}