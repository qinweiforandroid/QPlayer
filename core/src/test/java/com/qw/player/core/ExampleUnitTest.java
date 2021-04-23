package com.qw.player.core;

import com.qw.player.list.mode.ListLoopPlayMode;
import com.qw.player.list.mode.RandomPlayMode;
import com.qw.player.list.mode.SingleLoopPlayMode;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
public class ExampleUnitTest {
    @Test
    public void addition_isCorrect() {
        assertEquals(4, 2 + 2);
    }

    @Test
    public void singLoop() {
        IPlayMode playMode = new SingleLoopPlayMode();
        int pos = 1;
        int max = 10;
        assertEquals(playMode.next(true, pos, max), 1);

        assertEquals(playMode.next(false, pos, max), 2);
        assertEquals(playMode.previous(false, pos, max), 0);

        pos = 9;
        assertEquals(playMode.next(false, pos, max), 10);
        assertEquals(playMode.previous(false, pos, max), 8);


        pos = 10;
        assertEquals(playMode.next(false, pos, max), 0);
        pos = 0;
        assertEquals(playMode.previous(false, pos, max), 10);
    }

    @Test
    public void listLoop() {
        IPlayMode playMode = new ListLoopPlayMode();
        int pos = 1;
        int max = 10;
        assertEquals(playMode.next(true, pos, max), 2);

        assertEquals(playMode.next(false, pos, max), 2);
        assertEquals(playMode.previous(false, pos, max), 0);

        pos = 9;
        assertEquals(playMode.next(false, pos, max), 10);
        assertEquals(playMode.previous(false, pos, max), 8);

        pos = 10;
        assertEquals(playMode.next(false, pos, max), 0);
        pos = 0;
        assertEquals(playMode.previous(false, pos, max), 10);
    }

    @Test
    public void random() {
        IPlayMode playMode = new RandomPlayMode();
        int pos = 1;
        int max = 10;
        for (int i = 0; i < 20; i++) {
            System.out.println("random:"+playMode.next(false,pos,max));
        }
    }
}