package com.qw.player.core

import android.app.Service
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat

interface IPlayNotification {
    fun registerListener()
    fun unRegisterListener()
    fun createNotificationChannel()
    fun createRemoteViews(): RemoteViews
    fun createSmallRemoteViews(): RemoteViews
    fun createNotificationBuilder(): NotificationCompat.Builder
    fun getNotificationId(): Int
    fun notifyNotification(service: Service)
    fun cancel()
}