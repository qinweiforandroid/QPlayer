package com.qw.player.demo

import android.content.Context
import android.content.Intent
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.content.ContextCompat

/**
 * when(it.getLongExtra("",0)){
PlaybackStateCompat.ACTION_PLAY->{
play()
}
PlaybackStateCompat.ACTION_PAUSE->{
pause()
}
PlaybackStateCompat.ACTION_STOP->{
stop()
}
PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS->{
skipToPrevious()
}

PlaybackStateCompat.ACTION_SKIP_TO_NEXT->{
skipToNext()
}
}
 */
object PlayManager {
    fun play(context: Context){
        val intent=Intent(context,AudioPlayService::class.java)
        intent.putExtra(AudioPlayService.KEY_ACTION, PlaybackStateCompat.ACTION_PLAY)
        ContextCompat.startForegroundService(context,intent)
    }
    fun pause(context: Context){
        val intent=Intent(context,AudioPlayService::class.java)
        intent.putExtra(AudioPlayService.KEY_ACTION, PlaybackStateCompat.ACTION_PLAY)
        ContextCompat.startForegroundService(context,intent)
    }
    fun reusme(context: Context){
        val intent=Intent(context,AudioPlayService::class.java)
        intent.putExtra(AudioPlayService.KEY_ACTION, PlaybackStateCompat.ACTION_PLAY)
        ContextCompat.startForegroundService(context,intent)
    }
}