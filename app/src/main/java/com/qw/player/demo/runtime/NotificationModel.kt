package com.qw.player.demo.runtime

import android.graphics.Bitmap

data class NotificationModel(
    val defaultIconRes: Int,
    val icon: Bitmap? = null,
    val title: String = "",
    val subTitle: String = "",
    val isPlaying: Boolean = false
)
