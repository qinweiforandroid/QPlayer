package com.qw.player.core.engine

import com.qw.player.core.media.MediaType
import com.qw.player.core.media.PlaySource
import com.qw.player.core.media.PlayableMedia
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutingPlayerEngineTest {

    private class FakeEngine : PlayerEngine {
        private var eventListener: PlayerEventListener? = null
        val setMediaCalls = mutableListOf<Pair<String, String>>()
        var stopResetCalls = 0
        var stopWithoutResetCalls = 0
        var volumeCalls = mutableListOf<Float>()
        var mutedCalls = mutableListOf<Boolean>()
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
        override fun prepare() {}
        override fun play() {}
        override fun pause() {}
        override fun stop(reset: Boolean) {
            if (reset) stopResetCalls++ else stopWithoutResetCalls++
        }
        override fun seekTo(positionMs: Long) {}
        override fun setSpeed(speed: Float) {}
        override fun setLooping(looping: Boolean) {}
        override fun setVolume(volume: Float) { volumeCalls.add(volume) }
        override fun setMuted(muted: Boolean) { mutedCalls.add(muted) }
        override fun attachVideoOutput(output: PlayerVideoOutput?) {}
        override fun setListener(listener: PlayerEventListener?) { eventListener = listener }
        override fun getSnapshot(): PlaybackSnapshot = currentSnapshot
        override fun getCapabilities() = PlayerCapabilities()
        override fun release() {}

        fun emitCompleted() { eventListener?.onCompleted() }
        fun emitState(state: PlaybackState) { eventListener?.onStateChanged(state) }
    }

    private fun media(id: String, type: MediaType = MediaType.AUDIO) = PlayableMedia(
        mediaId = id,
        mediaType = type,
        playSources = listOf(PlaySource(sourceId = "default", url = "https://example.com/$id"))
    )

    private fun router(audio: FakeEngine, video: FakeEngine, text: FakeEngine): RoutingPlayerEngine {
        return RoutingPlayerEngine(
            engines = mapOf(
                MediaType.AUDIO to audio,
                MediaType.VIDEO to video,
                MediaType.TEXT to text
            ),
            defaultType = MediaType.VIDEO
        )
    }

    @Test
    fun `routes media to engine matching its media type`() {
        val audio = FakeEngine()
        val video = FakeEngine()
        val text = FakeEngine()
        val engine = router(audio, video, text)

        engine.setMedia(media("a1", MediaType.AUDIO), media("a1", MediaType.AUDIO).playSources.first())
        engine.setMedia(media("t1", MediaType.TEXT), media("t1", MediaType.TEXT).playSources.first())

        assertEquals(listOf("a1" to "default"), audio.setMediaCalls)
        assertEquals(listOf("t1" to "default"), text.setMediaCalls)
        assertTrue("视频 engine 不应收到任何指令", video.setMediaCalls.isEmpty())
    }

    @Test
    fun `switching media type stops previous active engine with reset`() {
        val audio = FakeEngine()
        val video = FakeEngine()
        val text = FakeEngine()
        val engine = router(audio, video, text)
        engine.setMedia(media("a1", MediaType.AUDIO), media("a1").playSources.first())

        engine.setMedia(media("t1", MediaType.TEXT), media("t1", MediaType.TEXT).playSources.first())

        assertEquals(1, audio.stopResetCalls)
        assertEquals(MediaType.TEXT, engine.getActiveType())
    }

    @Test
    fun `staying on same media type does not stop the engine`() {
        val audio = FakeEngine()
        val engine = router(audio, FakeEngine(), FakeEngine())

        engine.setMedia(media("a1", MediaType.AUDIO), media("a1").playSources.first())
        engine.setMedia(media("a2", MediaType.AUDIO), media("a2").playSources.first())

        assertEquals(0, audio.stopResetCalls)
    }

    @Test
    fun `unregistered media type falls back to default engine`() {
        val audio = FakeEngine()
        val video = FakeEngine()
        val engine = RoutingPlayerEngine(
            engines = mapOf(MediaType.AUDIO to audio, MediaType.VIDEO to video),
            defaultType = MediaType.VIDEO
        )

        engine.setMedia(media("t1", MediaType.TEXT), media("t1").playSources.first())

        assertEquals(listOf("t1" to "default"), video.setMediaCalls)
    }

    @Test
    fun `only active engine events reach external listener`() {
        val audio = FakeEngine()
        val video = FakeEngine()
        val engine = router(audio, video, FakeEngine())
        val completed = mutableListOf<String>()
        engine.setListener(object : PlayerEventListener {
            override fun onCompleted() { completed.add("completed") }
        })
        engine.setMedia(media("a1", MediaType.AUDIO), media("a1").playSources.first())

        video.emitCompleted()
        assertEquals("非活跃 engine 的完成事件必须丢弃", emptyList<String>(), completed)

        audio.emitCompleted()
        assertEquals(listOf("completed"), completed)
    }

    @Test
    fun `volume and mute are broadcast to every engine`() {
        val audio = FakeEngine()
        val video = FakeEngine()
        val text = FakeEngine()
        val engine = router(audio, video, text)

        engine.setVolume(0.4f)
        engine.setMuted(true)

        listOf(audio, video, text).forEach { fake ->
            assertEquals(listOf(0.4f), fake.volumeCalls)
            assertEquals(listOf(true), fake.mutedCalls)
        }
    }

    @Test
    fun `snapshot and capabilities come from active engine`() {
        val text = FakeEngine()
        val engine = router(FakeEngine(), FakeEngine(), text)
        engine.setMedia(media("t1", MediaType.TEXT), media("t1", MediaType.TEXT).playSources.first())

        assertEquals("t1", engine.getSnapshot().currentMediaId)
        assertEquals(MediaType.TEXT, engine.getActiveType())
    }

    @Test
    fun `requires default type to be registered`() {
        var thrown = false
        try {
            RoutingPlayerEngine(engines = mapOf(MediaType.AUDIO to FakeEngine()), defaultType = MediaType.VIDEO)
        } catch (e: IllegalArgumentException) {
            thrown = true
        }
        assertTrue(thrown)
    }

    @Test
    fun `release releases every engine`() {
        val audio = FakeEngine()
        val video = FakeEngine()
        val engine = router(audio, video, FakeEngine())

        engine.release()

        var thrown = false
        try {
            engine.play()
        } catch (e: IllegalStateException) {
            thrown = true
        }
        assertTrue("释放后不应再接受指令", thrown)
        assertFalse(audio.setMediaCalls.isNotEmpty())
    }

    @Test
    fun `shared engine across audio and video types still forwards events while either type is active`() {
        // Media3 这种一个 engine 同时服务 AUDIO 和 VIDEO 的真实场景：
        // setListener 只能调一次，不能让后注册的类型把先注册的覆盖掉。
        val shared = FakeEngine()
        val text = FakeEngine()
        val engine = RoutingPlayerEngine(
            engines = mapOf(MediaType.AUDIO to shared, MediaType.VIDEO to shared, MediaType.TEXT to text),
            defaultType = MediaType.VIDEO
        )
        val states = mutableListOf<PlaybackState>()
        engine.setListener(object : PlayerEventListener {
            override fun onStateChanged(state: PlaybackState) { states.add(state) }
        })

        engine.setMedia(media("a1", MediaType.AUDIO), media("a1").playSources.first())
        shared.emitState(PlaybackState.PLAYING)

        assertEquals(
            "AUDIO 和 VIDEO 共用同一个 engine 实例时，播 AUDIO 也必须能收到事件",
            listOf(PlaybackState.PLAYING),
            states
        )
    }

    @Test
    fun `switching between types sharing the same engine does not stop it`() {
        val shared = FakeEngine()
        val engine = RoutingPlayerEngine(
            engines = mapOf(MediaType.AUDIO to shared, MediaType.VIDEO to shared),
            defaultType = MediaType.VIDEO
        )

        engine.setMedia(media("v1", MediaType.VIDEO), media("v1").playSources.first())
        engine.setMedia(media("a1", MediaType.AUDIO), media("a1").playSources.first())

        assertEquals("共用同一个实例时不应该被 stop", 0, shared.stopResetCalls)
        assertEquals(MediaType.AUDIO, engine.getActiveType())
    }
}
