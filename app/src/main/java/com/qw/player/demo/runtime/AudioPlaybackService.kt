package com.qw.player.demo.runtime

import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.Glide
import com.bumptech.glide.request.transition.Transition
import com.qw.player.core.media.PlayableMedia
import com.qw.player.core.engine.PlaybackError
import com.qw.player.core.session.PlaybackSessionListener
import com.qw.player.core.engine.PlaybackSnapshot
import com.qw.player.demo.R

/**
 * 基于 V2 runtime 的前台播放 Service。
 *
 * 当前版本先承载最小闭环：
 * - 前台通知
 * - 基础播放命令转发
 * - 与共享 V2 runtime 保持状态同步
 */
class AudioPlaybackService : Service() {

    companion object {
        const val KEY_ACTION = "key_action"
        const val ACTION_PLAY_INDEX = "action_play_index"
        const val ACTION_RESUME = "action_resume"
        const val ACTION_PAUSE = "action_pause"
        const val ACTION_STOP = "action_stop"
        const val ACTION_NEXT = "action_next"
        const val ACTION_PREVIOUS = "action_previous"
        const val KEY_INDEX = "key_index"
    }

    private lateinit var playNotification: NotificationRenderer
    private val handler = Handler(Looper.getMainLooper())

    private val sessionListener = object : PlaybackSessionListener {
        override fun onCurrentMediaChanged(current: PlayableMedia?, previous: PlayableMedia?) {
            notifyNotificationUpdated()
        }

        override fun onPlaybackChanged(snapshot: PlaybackSnapshot) {
            notifyNotificationUpdated()
        }

        override fun onPlaybackCompleted(snapshot: PlaybackSnapshot) {
            notifyNotificationUpdated()
        }

        override fun onPlaybackError(error: PlaybackError, snapshot: PlaybackSnapshot) {
            notifyNotificationUpdated()
        }
    }

    override fun onCreate() {
        super.onCreate()
        playNotification = PlaybackNotification(this)
        playNotification.registerListener()
        AudioPlaybackRuntime.addListener(sessionListener)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.getStringExtra(KEY_ACTION)) {
            ACTION_PLAY_INDEX -> {
                AudioPlaybackRuntime.play(intent.getIntExtra(KEY_INDEX, 0))
            }

            ACTION_RESUME -> AudioPlaybackRuntime.resume()
            ACTION_PAUSE -> AudioPlaybackRuntime.pause()
            ACTION_STOP -> {
                AudioPlaybackRuntime.stop()
                stopSelf()
            }
            ACTION_NEXT -> AudioPlaybackRuntime.skipToNext()
            ACTION_PREVIOUS -> AudioPlaybackRuntime.skipToPrevious()
        }
        notifyNotificationUpdated()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        AudioPlaybackRuntime.removeListener(sessionListener)
        playNotification.unRegisterListener()
    }

    private fun notifyNotificationUpdated(bitmap: Bitmap? = null) {
        val current = AudioPlaybackRuntime.getCurrentMedia() ?: return
        handler.removeCallbacksAndMessages(null)
        handler.post {
            playNotification.notifyNotification(
                NotificationModel(
                    defaultIconRes = R.drawable.ic_launcher_background,
                    icon = bitmap,
                    title = current.metadata.title,
                    subTitle = current.metadata.artist,
                    isPlaying = AudioPlaybackRuntime.isPlaying()
                ),
                this
            )
        }
        if (bitmap == null && current.metadata.coverUrl.isNotEmpty()) {
            Glide.with(this)
                .asBitmap()
                .load(current.metadata.coverUrl)
                .into(object : CustomTarget<Bitmap>() {
                    override fun onResourceReady(
                        resource: Bitmap,
                        transition: Transition<in Bitmap>?
                    ) {
                        notifyNotificationUpdated(resource)
                    }

                    override fun onLoadCleared(placeholder: android.graphics.drawable.Drawable?) = Unit
                })
        }
    }
}
