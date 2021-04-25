package com.qw.player.list

import android.media.AudioManager
import com.qw.player.core.IAudioFocus
import com.qw.player.core.IPodPlayer
import com.qw.player.list.mode.IPlayMode
import com.qw.player.list.mode.PlayModeFactory

object PlayList {
    private val mPods = ArrayList<IPod>()

    /**
     * 当前音频id
     */
    private var mCurrPodId: String = ""
    private var mCurrPosition = -1

    /**
     * url load接口
     */
    private lateinit var mUrlLoad: IUrlLoad

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

    fun injectUrlLoad(iUrlLoad: IUrlLoad) {
        this.mUrlLoad = iUrlLoad
    }

    fun injectPlayer(player: IPodPlayer) {
        mPlayer = player
        setPlayMode(IPlayMode.PLAY_MODEL_LIST_LOOP)
        mPlayer.registerListener(object : IPodPlayer.OnPlayListener {
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

    fun injectAudioFocus(audioFocus: IAudioFocus) {
        mAudioFocus = audioFocus
    }

    fun setPlayMode(playMode: Int) {
        mPlayModeImpl = PlayModeFactory.create(playMode)
        mPlayMode = playMode
    }

    fun getPlayMode(): Int {
        return mPlayMode
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
            listener.onPlaySwitched(pod.getPodId(), mCurrPodId)
        }
        mCurrPosition = position
        play(pod)
    }

    private fun play(pod: IPod) {
        mCurrPodId = pod.getPodId()
        if (mAudioFocus.requestAudioFocus() == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            //check url is exist
            if (pod.getPodUrl().isEmpty()) {
                tryLoadUrlAndPlay(pod)
            } else {
                //fixme check url is expire
                mPlayer.play(pod.getPodUrl())
            }
        }
    }

    private fun tryLoadUrlAndPlay(pod: IPod) {
        // try load url by pod id
        if (this::mUrlLoad.isInitialized) {
            mPlayer.notifyPlayConnecting()
            mUrlLoad.load(pod.getPodId(), object : UrlLoadCallback {
                override fun onLoadSuccess(url: String) {
                    pod.setPodUrl(url)
                    play(pod)
                }

                override fun onLoadFailure(code: Int, msg: String) {
                    mPlayer.notifyPlayError(code, msg)
                }
            })
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
            if (id == mPods[i].getPodId()) {
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
        mPods.clear()
        mPods.addAll(pods)
        stop()
        mCurrPosition=-1
        mCurrPodId=""
    }

    fun addPlayListHeader(pod: IPod) {
        mPods.add(0, pod)
        if (mCurrPodId.isNotEmpty()) {
            //重置当前的播放位置
            mCurrPosition = getPosById(mCurrPodId)
        }
    }

    fun addPlayListFooter(pod: IPod) {
        mPods.add(pod)
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

    fun getDuring(): Int {
        return mPlayer.during
    }
}