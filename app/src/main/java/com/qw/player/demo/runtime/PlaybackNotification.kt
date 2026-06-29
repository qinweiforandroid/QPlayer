package com.qw.player.demo.runtime

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import com.qw.player.core.common.PlayLog
import com.qw.player.demo.MainActivity
import com.qw.player.demo.R

/**
 * 基于 V2 runtime 的通知实现。
 *
 * 当前通知只服务于音频 runtime：
 * - 状态读取来自 [AudioPlaybackRuntime]
 * - 控制命令发给 [AudioPlaybackService]
 */
class PlaybackNotification(
    private val context: Context
) : NotificationRenderer {

    private var play: NotificationModel? = null
    private lateinit var receiver: NotificationReceiver

    override fun registerListener() {
        receiver = NotificationReceiver()
        val filter = IntentFilter().apply {
            addAction(ACTION_PLAY)
            addAction(ACTION_PRE)
            addAction(ACTION_NEXT)
            addAction(ACTION_CLOSE)
        }
        context.registerReceiver(receiver, filter)
    }

    override fun unRegisterListener() {
        context.unregisterReceiver(receiver)
    }

    override fun createNotificationChannel() {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "channel_name",
                NotificationManager.IMPORTANCE_LOW
            )
            channel.description = "channel_description"
            channel.setSound(null, null)
            manager.createNotificationChannel(channel)
        }
    }

    override fun createRemoteViews(): RemoteViews {
        val remoteViews = RemoteViews(context.packageName, R.layout.music_play_notification)
        bindRemoteViews(remoteViews)
        return remoteViews
    }

    override fun createSmallRemoteViews(): RemoteViews {
        val remoteViews = RemoteViews(context.packageName, R.layout.music_play_small_notification)
        bindRemoteViews(remoteViews)
        return remoteViews
    }

    override fun createNotificationBuilder(): NotificationCompat.Builder {
        return NotificationCompat.Builder(context, CHANNEL_ID).apply {
            setContent(createSmallRemoteViews())
            setCustomBigContentView(createRemoteViews())
            priority = NotificationCompat.PRIORITY_HIGH
            setSound(null)
            setDefaults(NotificationCompat.FLAG_ONLY_ALERT_ONCE)
            setVibrate(longArrayOf(0))
            setSmallIcon(R.drawable.ic_launcher_background)
            setOngoing(true)
        }
    }

    override fun getNotificationId(): Int = 20000

    override fun notifyNotification(model: NotificationModel, service: Service) {
        this.play = model
        service.startForeground(getNotificationId(), createNotificationBuilder().build())
    }

    override fun cancel() {
        if (context is Service) {
            context.stopSelf()
        }
    }

    private fun bindRemoteViews(remoteViews: RemoteViews) {
        remoteViews.setTextViewText(R.id.mNMusicPlayTitleLabel, "")
        remoteViews.setImageViewResource(
            R.id.mNMusicPlayPlayImg,
            if (AudioPlaybackRuntime.isPlaying() || AudioPlaybackRuntime.isBuffering()) {
                R.drawable.ic_baseline_pause_24
            } else {
                R.drawable.ic_baseline_play_arrow_24
            }
        )

        play?.let {
            remoteViews.setTextViewText(R.id.mNMusicPlayTitleLabel, it.title)
            remoteViews.setTextViewText(R.id.mNMusicPlaySubTitleLabel, it.subTitle)
            if (it.icon != null) {
                remoteViews.setImageViewBitmap(R.id.mNMusicPlayIconImg, it.icon)
            } else {
                remoteViews.setImageViewResource(R.id.mNMusicPlayIconImg, it.defaultIconRes)
            }
        }

        val intentGo = PendingIntent.getActivity(
            context,
            1,
            Intent(context, MainActivity::class.java),
            notificationFlag
        )
        remoteViews.setOnClickPendingIntent(R.id.mNMusicPlayIconImg, intentGo)
        remoteViews.setOnClickPendingIntent(R.id.mNMusicPlayCloseImg, getBroadcastIntent(ACTION_CLOSE, 2))
        remoteViews.setOnClickPendingIntent(R.id.mNMusicPlayPreImg, getBroadcastIntent(ACTION_PRE, 3))
        remoteViews.setOnClickPendingIntent(R.id.mNMusicPlayPlayImg, getBroadcastIntent(ACTION_PLAY, 4))
        remoteViews.setOnClickPendingIntent(R.id.mNMusicPlayNextImg, getBroadcastIntent(ACTION_NEXT, 5))
    }

    private fun getBroadcastIntent(action: String, requestCode: Int): PendingIntent {
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(action),
            notificationFlag
        )
    }

    inner class NotificationReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            PlayLog.d("PlaybackNotification ${intent.action}")
            when (intent.action) {
                ACTION_PLAY -> when {
                    AudioPlaybackRuntime.isPaused() -> AudioPlaybackController.resume(context)
                    AudioPlaybackRuntime.isPlaying() || AudioPlaybackRuntime.isBuffering() -> {
                        AudioPlaybackController.pause(context)
                    }

                    else -> AudioPlaybackController.play(
                        context,
                        if (AudioPlaybackRuntime.getCurrentIndex() >= 0) {
                            AudioPlaybackRuntime.getCurrentIndex()
                        } else {
                            0
                        }
                    )
                }

                ACTION_PRE -> AudioPlaybackController.skipToPrevious(context)
                ACTION_NEXT -> AudioPlaybackController.skipToNext(context)
                ACTION_CLOSE -> {
                    AudioPlaybackController.stop(context)
                    cancel()
                }
            }
        }
    }

    init {
        createNotificationChannel()
    }

    companion object {
        private const val CHANNEL_ID = "channel_qw_v2"
        const val ACTION_PLAY = "android.intent.action.music.v2.play"
        const val ACTION_PRE = "android.intent.action.music.v2.pre"
        const val ACTION_NEXT = "android.intent.action.music.v2.next"
        const val ACTION_CLOSE = "android.intent.action.music.v2.close"
    }

    private val notificationFlag = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
}
