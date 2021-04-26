package com.qw.player.demo.service

import android.app.Service
import android.graphics.Bitmap
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
    fun notifyNotification(play: PlayEntity, service: Service)
    fun cancel()

    class PlayEntity {
        private constructor()

        private var icon: Bitmap? = null
        private var defaultIcon: Int = 0
        private var title = ""
        private var subTitle = ""
        private var isPlaying = false

        fun getDefaultIcon(): Int {
            return defaultIcon
        }

        fun getIcon(): Bitmap? {
            return icon
        }

        fun getTitle(): String {
            return title
        }

        fun getSubTitle(): String {
            return subTitle
        }

        fun isPlaying(): Boolean {
            return isPlaying
        }

        class Builder {
            private var icon: Bitmap? = null
            private var defaultIcon: Int = 0
            private var title = ""
            private var subTitle = ""
            private var isPlaying = false

            fun setIcon(icon: Bitmap?): Builder {
                this.icon = icon
                return this
            }

            fun setDefaultIcon(defaultIcon: Int): Builder {
                this.defaultIcon = defaultIcon
                return this
            }

            fun setPlaying(isPlaying: Boolean): Builder {
                this.isPlaying = isPlaying
                return this
            }

            fun setTitle(title: String): Builder {
                this.title = title
                return this
            }

            fun setSubTitle(subTitle: String): Builder {
                this.subTitle = subTitle
                return this
            }

            fun builder(): PlayEntity {
                return PlayEntity().apply {
                    this.icon = this@Builder.icon
                    this.defaultIcon = this@Builder.defaultIcon
                    this.title = this@Builder.title
                    this.subTitle = this@Builder.subTitle
                    this.isPlaying = this@Builder.isPlaying
                }
            }
        }
    }
}