package com.qw.player.list

interface OnPlayListListener {
    fun onPlaySwitched(newId: String, oldId: String) {
    }

    fun onPlayStateChanged(id: String, state: Int) {}
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