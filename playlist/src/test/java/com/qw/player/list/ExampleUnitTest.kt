package com.qw.player.list

import com.qw.player.list.mode.IPlayMode
import com.qw.player.list.mode.ListLoopPlayMode
import com.qw.player.list.mode.RandomPlayMode
import com.qw.player.list.mode.SingleLoopPlayMode
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun singLoop() {
        val playMode: IPlayMode = SingleLoopPlayMode()
        var pos = 1
        val max = 10
        assertEquals(playMode.next(true, pos, max), 1)
        assertEquals(playMode.next(false, pos, max), 2)
        assertEquals(playMode.previous(false, pos, max), 0)
        pos = 9
        assertEquals(playMode.next(false, pos, max), 10)
        assertEquals(playMode.previous(false, pos, max), 8)
        pos = 10
        assertEquals(playMode.next(false, pos, max), 0)
        pos = 0
        assertEquals(playMode.previous(false, pos, max), 10)
    }

    @Test
    fun listLoop() {
        val playMode: IPlayMode = ListLoopPlayMode()
        var pos = 1
        val max = 10
        assertEquals(playMode.next(true, pos, max), 2)
        assertEquals(playMode.next(false, pos, max), 2)
        assertEquals(playMode.previous(false, pos, max), 0)
        pos = 9
        assertEquals(playMode.next(false, pos, max), 10)
        assertEquals(playMode.previous(false, pos, max), 8)
        pos = 10
        assertEquals(playMode.next(false, pos, max), 0)
        pos = 0
        assertEquals(playMode.previous(false, pos, max), 10)
    }

    @Test
    fun random() {
        val playMode: IPlayMode = RandomPlayMode()
        val pos = 1
        val max = 10
        for (i in 0..19) {
            System.out.println("random:" + playMode.next(false, pos, max))
        }
    }
}