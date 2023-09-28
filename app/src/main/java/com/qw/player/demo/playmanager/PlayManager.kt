package com.qw.player.demo.playmanager

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.core.content.ContextCompat
import com.qw.player.demo.service.AudioPlayService
import com.qw.player.list.OnPlayListListener
import com.qw.player.list.IPod
import com.qw.player.list.IUrlLoad
import com.qw.player.list.PlayListManager
import com.qw.exoplayer.PodExoplayer

object PlayManager {
    private var context: Context? = null
    fun init(context: Context) {
        if (PlayManager.context != null) {
            return
        }
        PlayManager.context = context.applicationContext
//        PlayList.injectPlayer(PodMediaPlayer(PlayManager.context))
        PlayListManager.injectPlayer(PodExoplayer(PlayManager.context))
    }

    fun injectUrlLoad(urlLoad: IUrlLoad) {
        PlayListManager.injectUrlLoad(urlLoad)
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

    fun setPlayList(pods: ArrayList<IPod>, checkedPos: Int = 0) {
        PlayListManager.setPlayList(pods, checkedPos)
    }

    fun getPlayList(): ArrayList<IPod> {
        return PlayListManager.getPlayList()
    }

    fun isPlaying(): Boolean {
        return PlayListManager.isPlaying()
    }

    fun getPos(): Int {
        return PlayListManager.getPos()
    }

    fun isConnecting(): Boolean {
        return PlayListManager.isConnecting()
    }

    fun seekTo(toLong: Long) {
        PlayListManager.seekTo(toLong)
    }

    fun getPlayMode(): Int {
        return PlayListManager.getPlayMode()
    }

    fun getPod(): IPod? {
        return PlayListManager.getPod()
    }

    fun addPlayListHeader(pod: IPod) {
        PlayListManager.addPlayListHeader(pod)
    }

    fun addPlayListFooter(pod: IPod) {
        PlayListManager.addPlayListFooter(pod)
    }

    fun addOnPlayListListener(playListListener: OnPlayListListener) {
        PlayListManager.addOnPlayListListener(playListListener)
    }

    fun removeOnPlayListListener(playListListener: OnPlayListListener) {
        PlayListManager.removeOnPlayListListener(playListListener)
    }

    fun getDuring(): Int {
        return PlayListManager.getDuring()

    }

    fun setPlayMode(playMode: Int) {
        PlayListManager.setPlayMode(playMode)
    }

    fun setSpeed(speed: Float) {
        PlayListManager.setSpeed(speed)
    }

    fun startCountdown(time: Long) {
        PlayCountdownManager.startCountdown(time)
    }

    fun stopCountDown() {
        PlayCountdownManager.stopCountdown()
    }

    fun isPaused(): Boolean {
        return PlayListManager.isPaused()
    }
}