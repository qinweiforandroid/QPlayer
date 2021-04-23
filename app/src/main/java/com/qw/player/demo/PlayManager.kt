package com.qw.player.demo

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.core.content.ContextCompat
import com.qw.player.core.mode.IPod
import com.qw.player.media.PodMediaPlayer

object PlayManager {
    private var context: Context? = null

    fun init(context: Context) {
        if (this.context != null) {
            return
        }
        this.context = context.applicationContext
        PlayList.initPlayer(PodMediaPlayer(this.context))
    }

    fun play(position: Int = 0) {
        execute(AudioPlayService.ACTION_PLAY, Bundle().apply {
            this.putInt(AudioPlayService.KEY_POSITION, position)
        })
    }

    fun resume() {
        execute(AudioPlayService.ACTION_RESUME)
    }

    fun pause() {
        execute(AudioPlayService.ACTION_PAUSE)
    }

    fun next() {
        execute(AudioPlayService.ACTION_NEXT)
    }

    fun previous() {
        execute(AudioPlayService.ACTION_PREVIOUS)
    }

    private fun execute(action: String, bundle: Bundle = Bundle()) {
        val intent = Intent(context, AudioPlayService::class.java)
        intent.putExtra(AudioPlayService.KEY_ACTION, action)
        intent.putExtras(bundle)
        ContextCompat.startForegroundService(context!!, intent)
    }

    fun setPlayList(pods: ArrayList<IPod>) {
        PlayList.setPlayList(pods)
    }
}