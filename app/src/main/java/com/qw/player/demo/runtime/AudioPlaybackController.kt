package com.qw.player.demo.runtime

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/**
 * V2 播放控制器。
 *
 * 页面和通知不应直接操作 runtime 内部对象，而应通过这个控制器把命令发给
 * [AudioPlaybackService]，由 service 驱动音频 runtime。
 */
object AudioPlaybackController {

    fun play(context: Context, index: Int) {
        startService(context, AudioPlaybackService.ACTION_PLAY_INDEX) {
            putExtra(AudioPlaybackService.KEY_INDEX, index)
        }
    }

    fun resume(context: Context) {
        startService(context, AudioPlaybackService.ACTION_RESUME)
    }

    fun pause(context: Context) {
        startService(context, AudioPlaybackService.ACTION_PAUSE)
    }

    fun stop(context: Context) {
        startService(context, AudioPlaybackService.ACTION_STOP)
    }

    fun skipToNext(context: Context) {
        startService(context, AudioPlaybackService.ACTION_NEXT)
    }

    fun skipToPrevious(context: Context) {
        startService(context, AudioPlaybackService.ACTION_PREVIOUS)
    }

    private fun startService(context: Context, action: String, extras: Intent.() -> Unit = {}) {
        val intent = Intent(context, AudioPlaybackService::class.java).apply {
            putExtra(AudioPlaybackService.KEY_ACTION, action)
            extras()
        }
        ContextCompat.startForegroundService(context, intent)
    }
}
