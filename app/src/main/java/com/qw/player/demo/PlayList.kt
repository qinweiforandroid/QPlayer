package com.qw.player.demo

import android.media.AudioManager
import com.qw.player.core.IAudioFocus
import com.qw.player.core.IPlayMode
import com.qw.player.core.IPodPlayer
import com.qw.player.core.PlayModeFactory
import com.qw.player.core.mode.ListLoopPlayMode

object PlayList {
    private val mPods = ArrayList<IPod>()

    /**
     * 当前音频id
     */
    private var mCurrPodId: String = ""

    /**
     * 播放组件
     */
    private lateinit var mPlayer: IPodPlayer

    /**
     * 焦点处理
     */
    private lateinit var mAudioFocus: IAudioFocus

    private var mPlayMode: IPlayMode = ListLoopPlayMode()
    fun initPlayer(player: IPodPlayer) {
        this.mPlayer = player
        this.mPlayer.registerListener(object : IPodPlayer.OnPlayListener {
            override fun onPlayStart() {
            }

            override fun onPlayResumed() {
            }

            override fun onPlayBufferingUpdate(percent: Int) {

            }

            override fun onPlayError(code: Int, message: String?) {

            }

            override fun onPlayStopped() {
            }

            override fun onPlayPaused() {
            }

            override fun onPlayCompleted() {
            }

            override fun onPlayProgressUpdated(cur: Int, total: Int) {
            }

            override fun onPlayConnect() {

            }

        })
    }

    fun setAudioFocus(audioFocus: IAudioFocus) {
        this.mAudioFocus = audioFocus
    }

    fun setPlayMode(playMode: Int) {
        this.mPlayMode = PlayModeFactory.create(playMode)
    }

    fun play() {
        play(0)
    }

    fun play(position: Int) {
        if (position < 0 || position > mPods.size - 1) {
            return
        }
        play(mPods[position])
    }

    private fun play(iPod: IPod) {
        this.mCurrPodId = iPod.getId()
        if (mAudioFocus.requestAudioFocus() == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            mPlayer.play(iPod.getURL())
        }
    }

    fun skipToNext(auto: Boolean = false) {
        if (hasToNext(auto)) {
            val next = mPlayMode.next(auto, getPos(), mPods.size)
            stop()
            play(next)
        }
    }

    fun skipToPrevious(auto: Boolean = false) {
        if (hasToPrevious(auto)) {
            val previous = mPlayMode.previous(auto, getPos(), mPods.size)
            stop()
            play(previous)
        }
    }

    private fun getPos(): Int {
        for (i in 0 until mPods.size) {
            if (mCurrPodId == mPods[i].getId()) {
                return i
            }
        }
        return 0
    }

    fun hasToNext(auto: Boolean): Boolean {
        return mPlayMode.hasNext(auto, getPos(), mPods.size)
    }

    fun hasToPrevious(auto: Boolean): Boolean {
        return mPlayMode.hasPrevious(auto, getPos(), mPods.size)
    }

    fun stop() {
        mPlayer.stop()
    }

    fun pause() {
        mPlayer.pause()
    }

    fun seekTo(pos: Long) {
        mPlayer.seekTo(pos.toInt())
    }

    fun setPlayList(pods: ArrayList<IPod>) {
        this.mPods.clear()
        this.mPods.addAll(pods)
    }

    fun onDestroy() {
        mPlayer.unregisterListener()
        mPlayer.release()
    }
}