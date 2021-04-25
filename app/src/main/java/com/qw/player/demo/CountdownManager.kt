package com.qw.player.demo

import com.qw.player.core.PodPlayerTimer

object CountdownManager {
    private var mListenerTime: Long = 0
    private val timer = PodPlayerTimer()
    private var mCurrentTime = 0L
    private val listeners = ArrayList<OnCountdownListener>()
    fun startCountdown(time: Long) {
        stopCountdown()
        this.mListenerTime = time
        timer.setOnPodPlayerTimerListener {
            mCurrentTime += 1000
            if (mListenerTime == mCurrentTime) {
                stopCountdown()
                for (listener in listeners) {
                    listener.onCountdownCompleted()
                }
            } else {
                for (listener in listeners) {
                    listener.onExecute(mCurrentTime)
                }
            }
        }
        timer.start(1000)
    }

    fun stopCountdown() {
        timer.stop()
        mListenerTime = 0
        mCurrentTime = 0
    }

    fun addOnCountdownListener(listener: OnCountdownListener) {
        listeners.add(listener)
    }

    fun removeOnCountdownListener(listener: OnCountdownListener) {
        listeners.remove(listener)
    }

    interface OnCountdownListener {
        fun onCountdownCompleted()
        fun onExecute(time: Long) {}
    }
}