package com.qw.player.core

/**
 * 设置播放器模式，mode取值为PlayMode中的下列之一：
PLAY_MODEL_SINGLE单曲播放
PLAY_MODEL_SINGLE_LOOP 单曲循环播放
PLAY_MODEL_LIST列表播放
PLAY_MODEL_LIST_LOOP列表循环
PLAY_MODEL_RANDOM 随机播放
 */
interface IPlayMode {
    companion object {
        const val PLAY_MODEL_SINGLE_LOOP = 1
        const val PLAY_MODEL_LIST_LOOP = 2
        const val PLAY_MODEL_RANDOM = 3
    }

    fun previous(auto: Boolean, pos: Int, max: Int): Int

    fun next(auto: Boolean, pos: Int, max: Int): Int

    fun hasPrevious(auto: Boolean, pos: Int, max: Int): Boolean

    fun hasNext(auto: Boolean, pos: Int, max: Int): Boolean
}