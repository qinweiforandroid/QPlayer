package com.qw.player.demo.service

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
import com.qw.player.core.IPodPlayer
import com.qw.player.core.PlayLog
import com.qw.player.demo.MainActivity
import com.qw.player.demo.PlayManager
import com.qw.player.list.PlayList.getState
import com.qw.player.list.PlayList.isConnecting
import com.qw.player.list.PlayList.isPlaying
import com.qw.player.demo.PlayManager.skipToNext
import com.qw.player.demo.PlayManager.pause
import com.qw.player.demo.PlayManager.skipToPrevious
import com.qw.player.demo.PlayManager.resume
import com.qw.player.demo.R

class PlayNotification constructor(private val context: Context) : IPlayNotification {
    private var play: IPlayNotification.PlayEntity? = null
    private lateinit var receiver: NotificationReceiver

    override fun createNotificationChannel() {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name: CharSequence = "channel_name"
            val description = "channel_description"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance)
            channel.description = description
            channel.setSound(null, null)
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            manager.createNotificationChannel(channel)
        }
    }

    override fun createRemoteViews(): RemoteViews {
        val remoteViews = RemoteViews(
                context.packageName,
                R.layout.music_play_notification
        )
        remoteViews.setTextViewText(R.id.mNMusicPlayTitleLabel, "")
        if (isPlaying() || isConnecting()) {
            remoteViews.setImageViewResource(
                    R.id.mNMusicPlayPlayImg,
                    R.drawable.ic_baseline_pause_24
            )
        } else {
            remoteViews.setImageViewResource(
                    R.id.mNMusicPlayPlayImg,
                    R.drawable.ic_baseline_play_arrow_24
            )
        }

        play?.let {
            remoteViews.setTextViewText(R.id.mNMusicPlayTitleLabel, it.getTitle())
            remoteViews.setTextViewText(R.id.mNMusicPlaySubTitleLabel, it.getSubTitle())
            if (it.getIcon() != null) {
                remoteViews.setImageViewBitmap(R.id.mNMusicPlayIconImg, it.getIcon())
            } else {
                remoteViews.setImageViewResource(R.id.mNMusicPlayIconImg, it.getDefaultIcon())
            }
        }

        val intent = Intent(context, MainActivity::class.java)
        // 点击跳转到主界面
        val intentGo =
                PendingIntent.getActivity(context, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT)
        remoteViews.setOnClickPendingIntent(R.id.mNMusicPlayIconImg, intentGo)
        //关闭通知栏
        remoteViews.setOnClickPendingIntent(
                R.id.mNMusicPlayCloseImg, getBroadcastIntent(
                ACTION_CLOSE, 2
        )
        )
        //设置上一曲
        remoteViews.setOnClickPendingIntent(
                R.id.mNMusicPlayPreImg, getBroadcastIntent(
                ACTION_PRE, 3
        )
        )
        //播放/暂停
        remoteViews.setOnClickPendingIntent(
                R.id.mNMusicPlayPlayImg, getBroadcastIntent(
                ACTION_PLAY, 4
        )
        )
        //下一曲
        remoteViews.setOnClickPendingIntent(
                R.id.mNMusicPlayNextImg, getBroadcastIntent(
                ACTION_NEXT, 5
        )
        )
        return remoteViews
    }

    override fun createSmallRemoteViews(): RemoteViews {
        val remoteViewsSmall = RemoteViews(
                context.packageName,
                R.layout.music_play_small_notification
        )
        remoteViewsSmall.setTextViewText(R.id.mNMusicPlayTitleLabel, "")
        if (isPlaying() || isConnecting()) {
            remoteViewsSmall.setImageViewResource(
                    R.id.mNMusicPlayPlayImg,
                    R.drawable.ic_baseline_pause_24
            )
        } else {
            remoteViewsSmall.setImageViewResource(
                    R.id.mNMusicPlayPlayImg,
                    R.drawable.ic_baseline_play_arrow_24
            )
        }

        play?.let {
            remoteViewsSmall.setTextViewText(R.id.mNMusicPlayTitleLabel, it.getTitle())
            remoteViewsSmall.setTextViewText(R.id.mNMusicPlaySubTitleLabel, it.getSubTitle())
            if (it.getIcon() != null) {
                remoteViewsSmall.setImageViewBitmap(R.id.mNMusicPlayIconImg, it.getIcon())
            } else {
                remoteViewsSmall.setImageViewResource(R.id.mNMusicPlayIconImg, it.getDefaultIcon())
            }
        }

        val intent = Intent(context, MainActivity::class.java)
        // 点击跳转到主界面
        val intentGo =
                PendingIntent.getActivity(context, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT)
        remoteViewsSmall.setOnClickPendingIntent(R.id.mNMusicPlayIconImg, intentGo)
        //关闭通知栏
        remoteViewsSmall.setOnClickPendingIntent(
                R.id.mNMusicPlayCloseImg, getBroadcastIntent(
                ACTION_CLOSE, 2
        )
        )
        //设置上一曲
        remoteViewsSmall.setOnClickPendingIntent(
                R.id.mNMusicPlayPreImg, getBroadcastIntent(
                ACTION_PRE, 3
        )
        )
        //播放/暂停
        remoteViewsSmall.setOnClickPendingIntent(
                R.id.mNMusicPlayPlayImg, getBroadcastIntent(
                ACTION_PLAY, 4
        )
        )
        //下一曲
        remoteViewsSmall.setOnClickPendingIntent(
                R.id.mNMusicPlayNextImg, getBroadcastIntent(
                ACTION_NEXT, 5
        )
        )
        return remoteViewsSmall
    }

    /**
     * 设置通知
     */
    override fun createNotificationBuilder(): NotificationCompat.Builder {
        val notificationBuilder = NotificationCompat.Builder(
                context,
                CHANNEL_ID
        )
//        notificationBuilder.setStyle(NotificationCompat.DecoratedCustomViewStyle())
        notificationBuilder.setContent(createSmallRemoteViews())
        notificationBuilder.setCustomBigContentView(createRemoteViews())
        notificationBuilder.priority = NotificationCompat.PRIORITY_HIGH
        notificationBuilder.setSound(null)
        notificationBuilder.setDefaults(NotificationCompat.FLAG_ONLY_ALERT_ONCE)
        notificationBuilder.setVibrate(longArrayOf(0))
        notificationBuilder.setSmallIcon(R.drawable.ic_launcher_background)
        notificationBuilder.setOngoing(true)
        return notificationBuilder
    }

    override fun getNotificationId(): Int {
        return 10000
    }

    override fun notifyNotification(play: IPlayNotification.PlayEntity, service: Service) {
        log("notifyNotification $play")
        this.play = play
        val notificationBuilder = createNotificationBuilder()
        service.startForeground(getNotificationId(), notificationBuilder.build())
    }

    override fun cancel() {
//        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
//        manager.cancel(getNotificationId())
        if (context is Service) {
            context.stopSelf()
        }
    }

    override fun registerListener() {
        receiver = NotificationReceiver()
        val filter = IntentFilter()
        filter.addAction(ACTION_PLAY)
        filter.addAction(ACTION_PRE)
        filter.addAction(ACTION_NEXT)
        filter.addAction(ACTION_CLOSE)
        context.registerReceiver(receiver, filter)
    }

    override fun unRegisterListener() {
        context.unregisterReceiver(receiver)
    }

    inner class NotificationReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            PlayLog.d("PlayNotification $intent.action")
            when (intent.action) {
                ACTION_PLAY ->
                    when {
                        PlayManager.isPaused() -> resume()
                        isPlaying() -> pause()
                        else -> {
                            PlayManager.play()
                        }
                    }
                ACTION_PRE -> skipToPrevious()
                ACTION_NEXT -> skipToNext()
                ACTION_CLOSE -> {
                    pause()
                    cancel()
                }
            }
        }
    }

    private fun getBroadcastIntent(action: String, requestCode: Int): PendingIntent {
        val close = Intent()
        close.action = action
        return PendingIntent.getBroadcast(
                context,
                requestCode,
                close,
                PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    init {
        createNotificationChannel()
    }

    companion object {
        private const val CHANNEL_ID = "channel_qw"
        const val ACTION_PLAY = "android.intent.action.music.play"
        const val ACTION_PRE = "android.intent.action.music.pre"
        const val ACTION_NEXT = "android.intent.action.music.next"
        const val ACTION_CLOSE = "android.intent.action.music.close"
    }

    private fun log(msg: String) {
        PlayLog.d("notification $msg")
    }
}