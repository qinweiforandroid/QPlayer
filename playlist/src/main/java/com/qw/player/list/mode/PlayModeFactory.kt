package com.qw.player.list.mode

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