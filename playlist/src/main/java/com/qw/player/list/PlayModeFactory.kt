package com.qw.player.list

import com.qw.player.list.mode.ListLoopPlayMode
import com.qw.player.list.mode.RandomPlayMode
import com.qw.player.list.mode.SingleLoopPlayMode

object PlayModeFactory {
    fun create(mode: Int): IPlayMode {
        return when (mode) {
            IPlayMode.PLAY_MODEL_SINGLE_LOOP -> {
                SingleLoopPlayMode()
            }
            IPlayMode.PLAY_MODEL_LIST_LOOP -> {
                ListLoopPlayMode()
            }
            IPlayMode.PLAY_MODEL_RANDOM -> {
                RandomPlayMode()
            }
            else -> {
                SingleLoopPlayMode()
            }
        }
    }
}