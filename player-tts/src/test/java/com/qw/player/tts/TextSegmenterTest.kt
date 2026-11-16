package com.qw.player.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextSegmenterTest {

    private val segmenter = TextSegmenter(maxSegmentChars = 20)

    @Test
    fun `splits by sentence end punctuation`() {
        val segments = segmenter.split("第一句。第二句！第三句？")

        assertEquals(listOf("第一句。", "第二句！", "第三句？"), segments.map { it.text })
        assertEquals(listOf(0, 4, 8), segments.map { it.startOffset })
    }

    @Test
    fun `offsets map back into original text`() {
        val text = "第一句。第二句！"
        segmenter.split(text).forEach { segment ->
            assertEquals(segment.text, text.substring(segment.startOffset, segment.endOffset).trim())
        }
    }

    @Test
    fun `splits long text without punctuation by max length`() {
        val text = "甲".repeat(45)
        val segments = segmenter.split(text)

        assertEquals(3, segments.size)
        assertTrue("单段不能超过配置上限", segments.all { it.endOffset - it.startOffset <= 20 })
        assertEquals(text.length, segments.last().endOffset)
    }

    @Test
    fun `blank input produces no segments`() {
        assertTrue(segmenter.split("").isEmpty())
        assertTrue(segmenter.split("   \n\n  ").isEmpty())
    }

    @Test
    fun `newlines act as sentence boundaries`() {
        val segments = segmenter.split("标题\n正文内容。")

        assertEquals(2, segments.size)
        assertFalse(segments.any { it.text.isBlank() })
    }
}
