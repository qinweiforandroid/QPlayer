package com.qw.player.tts

import com.qw.player.core.engine.PlaybackError
import com.qw.player.core.engine.PlaybackState
import com.qw.player.core.engine.PlayerEventListener
import com.qw.player.core.media.MediaType
import com.qw.player.core.media.PlayableMedia
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TextPlayerEngineTest {

    private class FakeSynthesizer : SpeechSynthesizer {
        var available = true
        var speedCalls = mutableListOf<Float>()
        var spokenSegments: List<TextSegment> = emptyList()
        var stopCount = 0
        var released = false
        private var listener: SpeechSynthesizer.Listener? = null

        override fun isAvailable() = available
        override fun speak(segments: List<TextSegment>) { spokenSegments = segments }
        override fun stop() { stopCount++ }
        override fun setSpeed(speed: Float) { speedCalls.add(speed) }
        override fun setListener(listener: SpeechSynthesizer.Listener?) { this.listener = listener }
        override fun release() { released = true }

        fun start(segment: TextSegment) { listener?.onSegmentStarted(segment) }
        fun complete(segment: TextSegment) { listener?.onSegmentCompleted(segment) }
        fun error(segment: TextSegment, message: String = "boom") {
            listener?.onSegmentError(segment, message)
        }
    }

    private class FakeTicker : ProgressTicker {
        var started = false
        override fun setOnTick(listener: (() -> Unit)?) {}
        override fun start(intervalMs: Long) { started = true }
        override fun stop() { started = false }
    }

    private fun engine(
        synthesizer: FakeSynthesizer = FakeSynthesizer(),
        config: TextSpeechConfig = TextSpeechConfig(charsPerSecond = 10f)
    ) = TextPlayerEngine(synthesizer, config, FakeTicker())

    private fun textMedia(text: String) = PlayableMedia.fromText(text, mediaId = "t1", title = "第一章")

    private fun load(text: String, engine: TextPlayerEngine): PlayableMedia {
        val media = textMedia(text)
        engine.setMedia(media, media.playSources.first())
        return media
    }

    @Test
    fun `setMedia splits text and reports preparing`() {
        val engine = engine()
        val states = mutableListOf<PlaybackState>()
        engine.setListener(object : PlayerEventListener {
            override fun onStateChanged(state: PlaybackState) { states.add(state) }
        })

        load("第一句。第二句。", engine)

        assertEquals(PlaybackState.PREPARING, engine.getSnapshot().state)
        assertTrue(states.contains(PlaybackState.PREPARING))
    }

    @Test
    fun `play sends pending segments to synthesizer`() {
        val synthesizer = FakeSynthesizer()
        val engine = engine(synthesizer)
        load("第一句。第二句。", engine)

        engine.prepare()
        engine.play()

        assertEquals(PlaybackState.PLAYING, engine.getSnapshot().state)
        assertEquals(listOf("第一句。", "第二句。"), synthesizer.spokenSegments.map { it.text })
    }

    @Test
    fun `empty text reports an error instead of playing`() {
        val engine = engine()
        val errors = mutableListOf<PlaybackError>()
        engine.setListener(object : PlayerEventListener {
            override fun onError(error: PlaybackError) { errors.add(error) }
        })

        val media = PlayableMedia.fromText("   ", mediaId = "t-empty")
        engine.setMedia(media, media.playSources.first())

        assertEquals(1, errors.size)
        assertEquals(TextPlayerEngine.ERROR_EMPTY_TEXT, errors.first().code)
        assertEquals(PlaybackState.ERROR, engine.getSnapshot().state)
    }

    @Test
    fun `unavailable synthesizer reports error on play`() {
        val synthesizer = FakeSynthesizer().apply { available = false }
        val engine = engine(synthesizer)
        val errors = mutableListOf<PlaybackError>()
        engine.setListener(object : PlayerEventListener {
            override fun onError(error: PlaybackError) { errors.add(error) }
        })
        load("正文。", engine)

        engine.prepare()
        engine.play()

        assertEquals(TextPlayerEngine.ERROR_SYNTHESIZER_UNAVAILABLE, errors.first().code)
        assertEquals(PlaybackState.ERROR, engine.getSnapshot().state)
    }

    @Test
    fun `progress advances as segments complete`() {
        val synthesizer = FakeSynthesizer()
        val engine = engine(synthesizer)
        load("一二三四。五六七八。", engine)
        engine.prepare()
        engine.play()
        val first = synthesizer.spokenSegments.first()

        synthesizer.complete(first)

        // 10 字/秒，第一句 5 个字符 => 500ms；全文 10 个字符 => 1000ms
        assertEquals(500L, engine.getSnapshot().currentPositionMs)
        assertEquals(1000L, engine.getSnapshot().durationMs)
    }

    @Test
    fun `completing last segment ends playback`() {
        val synthesizer = FakeSynthesizer()
        val engine = engine(synthesizer)
        var completed = false
        engine.setListener(object : PlayerEventListener {
            override fun onCompleted() { completed = true }
        })
        load("第一句。", engine)
        engine.prepare()
        engine.play()

        synthesizer.complete(synthesizer.spokenSegments.single())

        assertTrue(completed)
        assertEquals(PlaybackState.ENDED, engine.getSnapshot().state)
    }

    @Test
    fun `segment listener reports start and finish for highlight`() {
        val synthesizer = FakeSynthesizer()
        val engine = engine(synthesizer)
        val started = mutableListOf<Int>()
        val finished = mutableListOf<Int>()
        engine.setOnSegmentListener(object : TextPlayerEngine.OnSegmentListener {
            override fun onSegmentStarted(segment: TextSegment) { started.add(segment.index) }
            override fun onSegmentFinished(segment: TextSegment) { finished.add(segment.index) }
        })
        load("第一句。第二句。", engine)
        engine.prepare()
        engine.play()
        synthesizer.start(synthesizer.spokenSegments.first())
        synthesizer.complete(synthesizer.spokenSegments.first())

        assertEquals(listOf(0), started)
        assertEquals(listOf(0), finished)
    }

    @Test
    fun `seekTo jumps to the segment containing target time`() {
        val synthesizer = FakeSynthesizer()
        val engine = engine(synthesizer)
        load("一二三四。五六七八。", engine)
        engine.prepare()
        engine.play()

        engine.seekTo(600L)

        assertEquals(600L, engine.getSnapshot().currentPositionMs)
        assertEquals(
            "句内不能精确定位，应从第二句开始重念",
            listOf("五六七八。"),
            synthesizer.spokenSegments.map { it.text }
        )
    }

    @Test
    fun `seek while paused does not restart speaking`() {
        val synthesizer = FakeSynthesizer()
        val engine = engine(synthesizer)
        load("一二三四。五六七八。", engine)
        engine.prepare()
        engine.play()
        engine.pause()
        val stopCountAfterPause = synthesizer.stopCount

        engine.seekTo(600L)

        assertEquals(stopCountAfterPause, synthesizer.stopCount)
        assertEquals(600L, engine.getSnapshot().currentPositionMs)
    }

    @Test
    fun `setSpeed recalculates duration and forwards to synthesizer`() {
        val synthesizer = FakeSynthesizer()
        val engine = engine(synthesizer)
        load("一二三四。五六七八。", engine)
        engine.prepare()
        engine.play()

        engine.setSpeed(2f)

        assertEquals("10 字按 20 字/秒折算", 500L, engine.getSnapshot().durationMs)
        assertTrue(synthesizer.speedCalls.contains(2f))
    }

    @Test
    fun `segment error reports tts error`() {
        val synthesizer = FakeSynthesizer()
        val engine = engine(synthesizer)
        val errors = mutableListOf<PlaybackError>()
        engine.setListener(object : PlayerEventListener {
            override fun onError(error: PlaybackError) { errors.add(error) }
        })
        load("第一句。", engine)
        engine.prepare()
        engine.play()

        synthesizer.error(synthesizer.spokenSegments.single())

        assertEquals(TextPlayerEngine.ERROR_SYNTHESIS_FAILED, errors.first().code)
        assertEquals("tts", errors.first().domain)
        assertEquals(PlaybackState.ERROR, engine.getSnapshot().state)
    }

    @Test
    fun `capabilities describe text engine`() {
        val capabilities = engine().getCapabilities()

        assertFalse(capabilities.supportsVideoOutput)
        assertTrue(capabilities.supportsSpeedControl)
        assertTrue(capabilities.supportsTextHighlight)
        assertFalse(capabilities.supportsSubtitles)
    }

    @Test
    fun `stop with reset clears current media`() {
        val engine = engine()
        load("第一句。", engine)
        engine.prepare()
        engine.play()

        engine.stop(reset = true)

        assertEquals(PlaybackState.IDLE, engine.getSnapshot().state)
        assertEquals(null, engine.getSnapshot().currentMediaId)
    }

    @Test
    fun `media type is text`() {
        val media = textMedia("正文")
        assertEquals(MediaType.TEXT, media.mediaType)
        assertNotNull(media.resolveDefaultSource()?.inlineText)
    }
}
