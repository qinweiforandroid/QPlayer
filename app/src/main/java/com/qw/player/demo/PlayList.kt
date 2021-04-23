package com.qw.player.demo

import android.media.AudioManager
import com.qw.player.core.IAudioFocus
import com.qw.player.core.IPlayMode
import com.qw.player.core.IPodPlayer
import com.qw.player.core.PlayModeFactory
import com.qw.player.core.mode.IPod

object PlayList {
    private val mPods = ArrayList<IPod>()

    /**
     * 当前音频id
     */
    private var mCurrPodId: String = ""
    private var mCurrPosition = -1

    /**
     * 播放组件
     */
    private lateinit var mPlayer: IPodPlayer

    /**
     * 焦点处理
     */
    private lateinit var mAudioFocus: IAudioFocus

    private lateinit var mPlayModeImpl: IPlayMode
    private var mPlayMode: Int = 0
    private var listeners = ArrayList<OnPlayListListener>()
    fun initPlayer(player: IPodPlayer) {
        this.mPlayer = player
        setPlayMode(IPlayMode.PLAY_MODEL_LIST_LOOP)
        this.mPlayer.registerListener(object : IPodPlayer.OnPlayListener {
            override fun onPlayStart() {
                for (listener in listeners) {
                    listener.onPlayStart(mCurrPodId)
                }
            }

            override fun onPlayResumed() {
                for (listener in listeners) {
                    listener.onPlayResumed(mCurrPodId)
                }
            }

            override fun onPlayBufferingUpdated(percent: Int) {
                for (listener in listeners) {
                    listener.onPlayBufferingUpdated(mCurrPodId, percent)
                }
            }

            override fun onPlayError(code: Int, message: String?) {
                for (listener in listeners) {
                    listener.onPlayError(mCurrPodId, code, message ?: "")
                }
            }

            override fun onPlayStopped() {
                for (listener in listeners) {
                    listener.onPlayStopped(mCurrPodId)
                }
            }

            override fun onPlayPaused() {
                for (listener in listeners) {
                    listener.onPlayPaused(mCurrPodId)
                }
            }

            override fun onPlayCompleted() {
                for (listener in listeners) {
                    listener.onPlayCompleted(mCurrPodId)
                }
            }

            override fun onPlayProgressUpdated(cur: Int, total: Int) {
                for (listener in listeners) {
                    listener.onPlayProgressUpdated(mCurrPodId, cur, total)
                }
            }

            override fun onPlayConnect() {
                for (listener in listeners) {
                    listener.onPlayConnecting(mCurrPodId)
                }
            }
        })
    }

    fun setAudioFocus(audioFocus: IAudioFocus) {
        this.mAudioFocus = audioFocus
    }

    fun setPlayMode(playMode: Int) {
        this.mPlayModeImpl = PlayModeFactory.create(playMode)
        this.mPlayMode=playMode
    }
    fun getPlayMode(): Int {
        return this.mPlayMode
    }

    fun play() {
        if (mCurrPosition >= 0) {
            play(mCurrPosition)
        } else {
            play(0)
        }
    }

    fun play(position: Int) {
        if (mPods.size == 0) {
            return
        }
        if (position < 0 || position > mPods.size - 1) {
            return
        }
        if (mCurrPosition == position) {
            when {
                mPlayer.isPlaying -> {
                    mPlayer.pause()
                }
                mPlayer.isPaused -> {
                    mPlayer.resume()
                }
                else -> {
                    play(mPods[position])
                }
            }
            return
        }
        if (mPlayer.isPlaying) {
            mPlayer.stop()
        }
        val pod = mPods[position]
        for (listener in listeners) {
            listener.onPlaySwitched(pod.getId(), mCurrPodId)
        }
        mCurrPosition = position
        play(pod)
    }

    private fun play(iPod: IPod) {
        this.mCurrPodId = iPod.getId()
        if (mAudioFocus.requestAudioFocus() == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            mPlayer.play(iPod.getURL())
        }
    }

    fun skipToNext(auto: Boolean = false) {
        if (hasToNext(auto)) {
            val next = mPlayModeImpl.next(auto, getPos(), mPods.size - 1)
            stop()
            play(next)
        }
    }

    fun skipToPrevious(auto: Boolean = false) {
        if (hasToPrevious(auto)) {
            val previous = mPlayModeImpl.previous(auto, getPos(), mPods.size - 1)
            stop()
            play(previous)
        }
    }

    fun getPosById(id: String): Int {
        for (i in 0 until mPods.size) {
            if (id == mPods[i].getId()) {
                return i
            }
        }
        return -1
    }

    fun getPos(): Int {
        return mCurrPosition
    }

    fun getPod(): IPod? {
        if (mCurrPodId == "") {
            return null
        }
        return mPods[mCurrPosition]
    }

    fun hasToNext(auto: Boolean): Boolean {
        return mPlayModeImpl.hasNext(auto, getPos(), mPods.size)
    }

    fun hasToPrevious(auto: Boolean): Boolean {
        return mPlayModeImpl.hasPrevious(auto, getPos(), mPods.size)
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

    fun addPlayListHeader(pod: IPod) {
        this.mPods.add(0, pod)
    }

    fun addPlayListFooter(pod: IPod) {
        this.mPods.add(pod)
    }

    fun isPaused(): Boolean {
        return mPlayer.isPaused
    }

    fun isPlaying(): Boolean {
        return mPlayer.isPlaying
    }

    fun isConnecting(): Boolean {
        return mPlayer.isConnecting
    }

    fun getState(): Int {
        return mPlayer.state
    }

    fun onDestroy() {
        mPlayer.unregisterListener()
        mPlayer.release()
    }

    fun addOnPlayListListener(playListListener: OnPlayListListener) {
        listeners.add(playListListener)
    }

    fun removeOnPlayListListener(playListListener: OnPlayListListener) {
        listeners.remove(playListListener)
    }

    interface OnPlayListListener {
        fun onPlaySwitched(newId: String, oldId: String) {
        }

        fun onPlayConnecting(mCurrPodId: String) {
        }

        fun onPlayProgressUpdated(mCurrPodId: String, cur: Int, total: Int) {
        }

        fun onPlayCompleted(mCurrPodId: String) {
        }

        fun onPlayPaused(mCurrPodId: String) {
        }

        fun onPlayStopped(mCurrPodId: String) {
        }

        fun onPlayError(mCurrPodId: String, code: Int, msg: String) {
        }

        fun onPlayBufferingUpdated(mCurrPodId: String, percent: Int) {
        }

        fun onPlayResumed(mCurrPodId: String) {
        }

        fun onPlayStart(mCurrPodId: String) {
        }
    }
}