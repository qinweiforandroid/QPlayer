package com.qw.player.core.session

import com.qw.player.core.engine.PlaybackError
import com.qw.player.core.engine.PlaybackSnapshot
import com.qw.player.core.engine.PlaybackState
import com.qw.player.core.engine.PlayerCapabilities
import com.qw.player.core.engine.PlayerConfig
import com.qw.player.core.engine.PlayerEngine
import com.qw.player.core.engine.PlayerEventListener
import com.qw.player.core.engine.PlayerVideoOutput
import com.qw.player.core.media.MediaType
import com.qw.player.core.media.PlaySource
import com.qw.player.core.media.PlayableMedia
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultPlaybackSessionTest {

    private class FakeEngine : PlayerEngine {
        private var eventListener: PlayerEventListener? = null
        val setMediaCalls = mutableListOf<Pair<String, String>>()
        var prepareCount = 0
        var playCount = 0
        private var currentSnapshot = PlaybackSnapshot()

        override fun initialize(config: PlayerConfig) {}
        override fun setMedia(media: PlayableMedia, source: PlaySource, startPositionMs: Long) {
            setMediaCalls.add(media.mediaId to source.sourceId)
            currentSnapshot = currentSnapshot.copy(
                currentMediaId = media.mediaId,
                currentSourceId = source.sourceId
            )
        }
        override fun preload(media: PlayableMedia, source: PlaySource) {}
        override fun prepare() { prepareCount++ }
        override fun play() { playCount++ }
        override fun pause() {}
        override fun stop(reset: Boolean) {}
        override fun seekTo(positionMs: Long) {}
        override fun setSpeed(speed: Float) {}
        override fun setLooping(looping: Boolean) {}
        override fun setVolume(volume: Float) {}
        override fun setMuted(muted: Boolean) {}
        override fun attachVideoOutput(output: PlayerVideoOutput?) {}
        override fun setListener(listener: PlayerEventListener?) { eventListener = listener }
        override fun getSnapshot(): PlaybackSnapshot = currentSnapshot
        override fun getCapabilities() = PlayerCapabilities()
        override fun release() {}

        fun emitError(error: PlaybackError) {
            currentSnapshot = currentSnapshot.copy(state = PlaybackState.ERROR, error = error)
            eventListener?.onError(error)
        }
    }

    private fun media(
        id: String,
        vararg sources: PlaySource
    ) = PlayableMedia(
        mediaId = id,
        mediaType = MediaType.AUDIO,
        playSources = sources.toList()
    )

    private fun source(id: String, expiresAtMs: Long? = null) =
        PlaySource(sourceId = id, url = "https://example.com/$id.mp3", expiresAtMs = expiresAtMs)

    @Test
    fun `plays default source from media`() {
        val engine = FakeEngine()
        val session = DefaultPlaybackSession()
        session.bindEngine(engine)
        session.setMediaList(listOf(media("m1", source("a"), source("b"))), 0)

        session.play(0)

        assertEquals(listOf("m1" to "a"), engine.setMediaCalls)
        assertEquals(1, engine.prepareCount)
        assertEquals(1, engine.playCount)
    }

    @Test
    fun `falls back to next source on non-recoverable error`() {
        val engine = FakeEngine()
        val session = DefaultPlaybackSession()
        session.bindEngine(engine)
        session.setMediaList(listOf(media("m1", source("a"), source("b"))), 0)
        session.play(0)

        val errors = mutableListOf<PlaybackError>()
        session.addListener(object : PlaybackSessionListener {
            override fun onPlaybackError(media: PlayableMedia?, error: PlaybackError) {
                errors.add(error)
            }
        })

        engine.emitError(PlaybackError(code = 2001, message = "boom", recoverable = false))

        assertEquals(listOf("m1" to "a", "m1" to "b"), engine.setMediaCalls)
        assertTrue("降级成功时不应把错误上抛", errors.isEmpty())
    }

    @Test
    fun `reports error after all sources exhausted`() {
        val engine = FakeEngine()
        val session = DefaultPlaybackSession()
        session.bindEngine(engine)
        session.setMediaList(listOf(media("m1", source("a"))), 0)
        session.play(0)

        val errors = mutableListOf<PlaybackError>()
        session.addListener(object : PlaybackSessionListener {
            override fun onPlaybackError(media: PlayableMedia?, error: PlaybackError) {
                errors.add(error)
            }
        })

        engine.emitError(PlaybackError(code = 2001, message = "boom", recoverable = false))

        assertEquals(1, engine.setMediaCalls.size)
        assertEquals(1, errors.size)
    }

    @Test
    fun `recoverable error is not downgraded by session`() {
        val engine = FakeEngine()
        val session = DefaultPlaybackSession()
        session.bindEngine(engine)
        session.setMediaList(listOf(media("m1", source("a"), source("b"))), 0)
        session.play(0)

        val errors = mutableListOf<PlaybackError>()
        session.addListener(object : PlaybackSessionListener {
            override fun onPlaybackError(media: PlayableMedia?, error: PlaybackError) {
                errors.add(error)
            }
        })

        engine.emitError(PlaybackError(code = 2001, message = "boom", recoverable = true))

        assertEquals("可恢复错误由 engine 自己重试，session 不应切换 source", 1, engine.setMediaCalls.size)
        assertEquals(1, errors.size)
    }

    @Test
    fun `expired source is rejected before reaching engine`() {
        val engine = FakeEngine()
        val session = DefaultPlaybackSession()
        session.bindEngine(engine)
        session.setMediaList(
            listOf(media("m1", source("a", expiresAtMs = System.currentTimeMillis() - 1000))),
            0
        )

        val errors = mutableListOf<PlaybackError>()
        session.addListener(object : PlaybackSessionListener {
            override fun onPlaybackError(media: PlayableMedia?, error: PlaybackError) {
                errors.add(error)
            }
        })

        session.play(0)

        assertTrue("过期 source 不应交给 engine", engine.setMediaCalls.isEmpty())
        assertEquals(1, errors.size)
        assertTrue(errors.first().message.contains("过期"))
    }

    @Test
    fun `skip to next stops at end when repeat is off`() {
        val engine = FakeEngine()
        val session = DefaultPlaybackSession()
        session.bindEngine(engine)
        session.updatePlaybackMode(PlaybackMode(repeatMode = RepeatMode.OFF))
        session.setMediaList(listOf(media("m1", source("a")), media("m2", source("b"))), 1)

        session.skipToNext()
        assertEquals("最后一项且不循环时不应切歌", 0, engine.setMediaCalls.size)

        session.skipToPrevious()
        assertEquals(listOf("m1" to "a"), engine.setMediaCalls)
    }

    @Test
    fun `repeat all wraps to first item`() {
        val engine = FakeEngine()
        val session = DefaultPlaybackSession()
        session.bindEngine(engine)
        session.updatePlaybackMode(PlaybackMode(repeatMode = RepeatMode.ALL))
        session.setMediaList(listOf(media("m1", source("a")), media("m2", source("b"))), 1)

        session.skipToNext()

        assertEquals(listOf("m1" to "a"), engine.setMediaCalls)
    }

    @Test
    fun `auto next with repeat one stays on same item`() {
        val engine = FakeEngine()
        val session = DefaultPlaybackSession()
        session.bindEngine(engine)
        session.updatePlaybackMode(PlaybackMode(repeatMode = RepeatMode.ONE))
        session.setMediaList(listOf(media("m1", source("a")), media("m2", source("b"))), 0)

        session.skipToNext(auto = true)

        assertEquals(listOf("m1" to "a"), engine.setMediaCalls)
    }

    @Test
    fun `pending start position is consumed once`() {
        val engine = FakeEngine()
        val session = DefaultPlaybackSession()
        session.bindEngine(engine)
        session.setMediaList(listOf(media("m1", source("a")), media("m2", source("b"))), 0)

        session.setPendingStartPosition(42_000L)
        session.play(0)
        session.play(1)

        assertEquals(2, engine.setMediaCalls.size)
    }

    @Test
    fun `empty queue play reports error instead of crashing`() {
        val engine = FakeEngine()
        val session = DefaultPlaybackSession()
        session.bindEngine(engine)

        val errors = mutableListOf<PlaybackError>()
        session.addListener(object : PlaybackSessionListener {
            override fun onPlaybackError(media: PlayableMedia?, error: PlaybackError) {
                errors.add(error)
            }
        })

        session.play(3)

        assertTrue(engine.setMediaCalls.isEmpty())
        assertEquals(1, errors.size)
        assertNull(session.getCurrentMedia())
    }

    @Test
    fun `fallback stays within same quality group before switching quality`() {
        val engine = FakeEngine()
        val session = DefaultPlaybackSession()
        session.bindEngine(engine)
        val hd1 = PlaySource(sourceId = "hd1", url = "https://example.com/hd1.mp3", qualityId = "1080p")
        val hd2 = PlaySource(sourceId = "hd2", url = "https://example.com/hd2.mp3", qualityId = "1080p")
        val sd = PlaySource(sourceId = "sd", url = "https://example.com/sd.mp3", qualityId = "480p")
        session.setMediaList(
            listOf(media("m1", hd1, hd2, sd)),
            0
        )
        session.play(0)

        engine.emitError(PlaybackError(code = 2001, message = "boom", recoverable = false))

        assertEquals(
            "同清晰度下的线路挂了，应该先换同清晰度的另一条线路，而不是直接降级清晰度",
            listOf("m1" to "hd1", "m1" to "hd2"),
            engine.setMediaCalls
        )
    }

    @Test
    fun `switching media resets failed source tracking`() {
        val engine = FakeEngine()
        val session = DefaultPlaybackSession()
        session.bindEngine(engine)
        session.setMediaList(
            listOf(media("m1", source("a"), source("b")), media("m2", source("a"), source("b"))),
            0
        )
        session.play(0)
        engine.emitError(PlaybackError(code = 2001, message = "boom", recoverable = false))

        session.play(1)
        assertFalse(engine.setMediaCalls.isEmpty())
        assertEquals("m2" to "a", engine.setMediaCalls.last())
    }
}
