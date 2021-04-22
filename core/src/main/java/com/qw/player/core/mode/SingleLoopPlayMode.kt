package com.qw.player.core.mode

import com.qw.player.core.IPlayMode

class SingleLoopPlayMode : IPlayMode {
    override fun previous(auto: Boolean, pos: Int, max: Int): Int {
        if (auto) {
            return pos
        }
        if (pos == 0) {
            return max
        }
        return pos - 1
    }

    override fun next(auto: Boolean, pos: Int, max: Int): Int {
        if (auto) {
            return pos
        }
        if (pos == max) {
            return 0
        }
        return pos + 1
    }

    override fun hasPrevious(auto: Boolean, pos: Int, max: Int): Boolean {
        return true
    }

    override fun hasNext(auto: Boolean, pos: Int, max: Int): Boolean {
        return true
    }
}