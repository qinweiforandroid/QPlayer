package com.qw.player.runtime

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import androidx.core.content.ContextCompat

/**
 * 监听耳机拔出 / 蓝牙断开（[AudioManager.ACTION_AUDIO_BECOMING_NOISY]）。
 */
public interface NoisyAudioWatcher {
    fun register()

    fun unregister()
}

public fun interface NoisyAudioWatcherFactory {
    fun create(context: Context, onBecomingNoisy: () -> Unit): NoisyAudioWatcher
}

/**
 * 广播接收器实现；注册后拔出耳机时回调一次 [onBecomingNoisy]。
 */
public class SystemNoisyAudioWatcher(
    private val context: Context,
    private val onBecomingNoisy: () -> Unit
) : NoisyAudioWatcher {

    private var registered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                onBecomingNoisy()
            }
        }
    }

    override fun register() {
        if (registered) {
            return
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        registered = true
    }

    override fun unregister() {
        if (!registered) {
            return
        }
        runCatching { context.unregisterReceiver(receiver) }
        registered = false
    }
}
