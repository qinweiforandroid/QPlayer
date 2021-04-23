package com.qw.player.demo

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.core.content.ContextCompat
import com.qw.player.list.OnPlayListListener
import com.qw.player.list.mode.IPod
import com.qw.player.list.PlayList
import com.qw.player.media.PodMediaPlayer

object PlayManager {
    private var context: Context? = null

    fun init(context: Context) {
        if (this.context != null) {
            return
        }
        this.context = context.applicationContext
        PlayList.injectPlayer(PodMediaPlayer(this.context))
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

    fun skipToNext() {
        execute(AudioPlayService.ACTION_NEXT)
    }

    fun skipToPrevious() {
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

    fun isPlaying(): Boolean {
        return PlayList.isPlaying()
    }

    fun getPos(): Int {
        return PlayList.getPos()
    }

    fun isConnecting(): Boolean {
        return PlayList.isConnecting()
    }

    fun seekTo(toLong: Long) {
        PlayList.seekTo(toLong)
    }

    fun getPlayMode(): Int {
        return PlayList.getPlayMode()
    }

    fun getPod(): IPod? {
        return PlayList.getPod()
    }

    fun addOnPlayListListener(playListListener: OnPlayListListener) {
        PlayList.addOnPlayListListener(playListListener)
    }

    fun removeOnPlayListListener(playListListener: OnPlayListListener) {
        PlayList.removeOnPlayListListener(playListListener)
    }

    fun getDuring(): Int {
        return PlayList.getDuring()
    }

    fun setPlayMode(playMode: Int) {
        PlayList.setPlayMode(playMode)
    }
}