package com.qw.player.core.session

import com.qw.player.core.media.MediaType
import com.qw.player.core.media.PlaySource
import com.qw.player.core.media.PlayableMedia
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DefaultSourceSelectorTest {

    private fun media(defaultSourceId: String? = null, vararg sources: PlaySource) =
        PlayableMedia(
            mediaId = "m1",
            mediaType = MediaType.AUDIO,
            playSources = sources.toList(),
            defaultSourceId = defaultSourceId
        )

    private fun source(id: String, qualityId: String? = null) =
        PlaySource(sourceId = id, url = "https://example.com/$id", qualityId = qualityId)

    @Test
    fun `selectInitial returns source matching defaultSourceId`() {
        val selector = DefaultSourceSelector()
        val m = media("b", source("a"), source("b"))

        assertEquals("b", selector.selectInitial(m)?.sourceId)
    }

    @Test
    fun `selectInitial falls back to first source when no default set`() {
        val selector = DefaultSourceSelector()
        val m = media(null, source("a"), source("b"))

        assertEquals("a", selector.selectInitial(m)?.sourceId)
    }

    @Test
    fun `selectFallback prefers untried source in same quality group`() {
        val selector = DefaultSourceSelector()
        val a = source("a", qualityId = "1080p")
        val b = source("b", qualityId = "1080p")
        val c = source("c", qualityId = "720p")
        val m = media(null, a, b, c)

        val next = selector.selectFallback(m, currentSource = a, failedSourceIds = setOf("a"))

        assertEquals("b", next?.sourceId)
    }

    @Test
    fun `selectFallback crosses quality group only after same group exhausted`() {
        val selector = DefaultSourceSelector()
        val a = source("a", qualityId = "1080p")
        val b = source("b", qualityId = "1080p")
        val c = source("c", qualityId = "720p")
        val m = media(null, a, b, c)

        val next = selector.selectFallback(m, currentSource = a, failedSourceIds = setOf("a", "b"))

        assertEquals("c", next?.sourceId)
    }

    @Test
    fun `selectFallback returns null when all sources exhausted`() {
        val selector = DefaultSourceSelector()
        val a = source("a", qualityId = "1080p")
        val m = media(null, a)

        val next = selector.selectFallback(m, currentSource = a, failedSourceIds = setOf("a"))

        assertNull(next)
    }
}
