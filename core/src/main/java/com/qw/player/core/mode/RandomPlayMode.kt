package com.qw.player.core.mode

import com.qw.player.core.IPlayMode

class RandomPlayMode : IPlayMode {
    override fun previous(auto: Boolean, pos: Int, max: Int): Int {
        return next(auto, pos, max)
    }

    override fun next(auto: Boolean, pos: Int, max: Int): Int {
        return (0..max).random()
    }

    override fun hasPrevious(auto: Boolean, pos: Int, max: Int): Boolean {
        return true
    }

    override fun hasNext(auto: Boolean, pos: Int, max: Int): Boolean {
        return true
    }

}