package com.qw.player.demo.runtime

import android.app.Service
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat

interface NotificationRenderer {
    fun registerListener()
    fun unRegisterListener()
    fun createNotificationChannel()
    fun createRemoteViews(): RemoteViews
    fun createSmallRemoteViews(): RemoteViews
    fun createNotificationBuilder(): NotificationCompat.Builder
    fun getNotificationId(): Int
    fun notifyNotification(model: NotificationModel, service: Service)
    fun cancel()
}
