package com.qw.player.core

import com.qw.player.core.mode.ListLoopPlayMode
import com.qw.player.core.mode.RandomPlayMode
import com.qw.player.core.mode.SingleLoopPlayMode

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