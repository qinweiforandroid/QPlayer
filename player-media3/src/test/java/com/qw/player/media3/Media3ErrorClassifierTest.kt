package com.qw.player.media3

import androidx.media3.common.PlaybackException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Media3ErrorClassifierTest {

    @Test
    fun `network connection failures are classified as network`() {
        assertEquals(
            Media3ErrorClassifier.ERROR_KIND_NETWORK,
            Media3ErrorClassifier.classify(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED)
        )
        assertEquals(
            Media3ErrorClassifier.ERROR_KIND_NETWORK,
            Media3ErrorClassifier.classify(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT)
        )
        assertEquals(
            Media3ErrorClassifier.ERROR_KIND_NETWORK,
            Media3ErrorClassifier.classify(PlaybackException.ERROR_CODE_IO_NO_PERMISSION)
        )
        assertEquals(
            Media3ErrorClassifier.ERROR_KIND_NETWORK,
            Media3ErrorClassifier.classify(PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED)
        )
    }

    @Test
    fun `2xxx and 3xxx ranges are classified as source`() {
        assertEquals(Media3ErrorClassifier.ERROR_KIND_SOURCE, Media3ErrorClassifier.classify(2000))
        assertEquals(Media3ErrorClassifier.ERROR_KIND_SOURCE, Media3ErrorClassifier.classify(2999))
        assertEquals(Media3ErrorClassifier.ERROR_KIND_SOURCE, Media3ErrorClassifier.classify(3000))
        assertEquals(Media3ErrorClassifier.ERROR_KIND_SOURCE, Media3ErrorClassifier.classify(3999))
    }

    @Test
    fun `4xxx range is classified as decode`() {
        assertEquals(Media3ErrorClassifier.ERROR_KIND_DECODE, Media3ErrorClassifier.classify(4000))
        assertEquals(Media3ErrorClassifier.ERROR_KIND_DECODE, Media3ErrorClassifier.classify(4999))
    }

    @Test
    fun `unmapped codes fall back to unknown`() {
        assertEquals(Media3ErrorClassifier.ERROR_KIND_UNKNOWN, Media3ErrorClassifier.classify(1000))
        assertEquals(Media3ErrorClassifier.ERROR_KIND_UNKNOWN, Media3ErrorClassifier.classify(5999))
        assertEquals(Media3ErrorClassifier.ERROR_KIND_UNKNOWN, Media3ErrorClassifier.classify(0))
    }

    @Test
    fun `only network and source errors are recoverable`() {
        assertTrue(Media3ErrorClassifier.isRecoverable(Media3ErrorClassifier.ERROR_KIND_NETWORK))
        assertTrue(Media3ErrorClassifier.isRecoverable(Media3ErrorClassifier.ERROR_KIND_SOURCE))
        assertFalse(Media3ErrorClassifier.isRecoverable(Media3ErrorClassifier.ERROR_KIND_DECODE))
        assertFalse(Media3ErrorClassifier.isRecoverable(Media3ErrorClassifier.ERROR_KIND_UNKNOWN))
    }

    @Test
    fun `retry delay doubles per attempt starting at base`() {
        val base = Media3ErrorClassifier.RETRY_BASE_DELAY_MS
        assertEquals(base, Media3ErrorClassifier.retryDelayMs(1))
        assertEquals(base * 2, Media3ErrorClassifier.retryDelayMs(2))
        assertEquals(base * 4, Media3ErrorClassifier.retryDelayMs(3))
    }

    @Test
    fun `retry delay treats non-positive attempt as first attempt`() {
        val base = Media3ErrorClassifier.RETRY_BASE_DELAY_MS
        assertEquals(base, Media3ErrorClassifier.retryDelayMs(0))
        assertEquals(base, Media3ErrorClassifier.retryDelayMs(-3))
    }
}
