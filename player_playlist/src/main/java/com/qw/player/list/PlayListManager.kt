package com.qw.player.list

import com.qw.player.core.IAudioFocus
import com.qw.player.core.IPodPlayer

object PlayListManager {
    private val mPlayList = PlayList()

    fun injectUrlLoad(iUrlLoad: IUrlLoad) {
        mPlayList.injectUrlLoad(iUrlLoad)
    }

    fun injectPlayer(player: IPodPlayer) {
        mPlayList.injectPlayer(player)
    }

    fun injectAudioFocus(audioFocus: IAudioFocus) {
        mPlayList.injectAudioFocus(audioFocus)
    }

    fun setPlayMode(playMode: Int) {
        mPlayList.setPlayMode(playMode)
    }

    fun getPlayMode(): Int {
        return mPlayList.getPlayMode()
    }

    fun play(position: Int) {
        mPlayList.play(position)
    }


    fun skipToNext(auto: Boolean = false) {
        mPlayList.skipToNext(auto)
    }

    fun skipToPrevious(auto: Boolean = false) {
        mPlayList.skipToPrevious(auto)
    }

    fun getPosById(id: String): Int {
        return mPlayList.getPosById(id)
    }

    fun getPos(): Int {
        return mPlayList.getPos()
    }

    fun getPod(): IPod? {
        return mPlayList.getPod()
    }

    fun hasToNext(auto: Boolean): Boolean {
        return mPlayList.hasToNext(auto)
    }

    fun hasToPrevious(auto: Boolean): Boolean {
        return mPlayList.hasToPrevious(auto)
    }

    fun stop() {
        return mPlayList.stop()
    }

    fun pause() {
        mPlayList.pause()
    }

    fun seekTo(pos: Long) {
        mPlayList.seekTo(pos)
    }

    fun setPlayList(pods: ArrayList<IPod>, checkedPos: Int = -1) {
        mPlayList.setPlayList(pods, checkedPos)
    }

    fun getPlayList(): ArrayList<IPod> {
        return mPlayList.getPlayList()
    }

    fun addPlayListHeader(pod: IPod) {
        mPlayList.addPlayListHeader(pod)
    }

    fun addPlayListFooter(pod: IPod) {
        mPlayList.addPlayListFooter(pod)
    }

    fun isPaused(): Boolean {
        return mPlayList.isPaused()
    }

    fun isPlaying(): Boolean {
        return mPlayList.isPlaying()
    }

    fun isConnecting(): Boolean {
        return mPlayList.isConnecting()
    }


    fun getState(): Int {
        return mPlayList.getState()
    }

    fun onDestroy() {
        mPlayList.onDestroy()
    }

    fun addOnPlayListListener(playListListener: OnPlayListListener) {
        mPlayList.addOnPlayListListener(playListListener)
    }

    fun removeOnPlayListListener(playListListener: OnPlayListListener) {
        mPlayList.removeOnPlayListListener(playListListener)
    }

    fun getDuring(): Int {
        return mPlayList.getDuring()
    }

    fun setSpeed(speed: Float) {
        mPlayList.setSpeed(speed)
    }
}