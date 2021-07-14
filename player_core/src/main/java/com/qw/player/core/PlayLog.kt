package com.qw.player.core

import android.util.Log

/**
 * Created by qinwei on 4/23/21 11:25 AM
 * email: qinwei_it@163.com
 */
class PlayLog {
    companion object {
        var isDebug = true
        var TAG = "PlayLog"
        fun d(msg: String) {
            if (isDebug) {
                Log.d(TAG, msg)
            }
        }
    }
}