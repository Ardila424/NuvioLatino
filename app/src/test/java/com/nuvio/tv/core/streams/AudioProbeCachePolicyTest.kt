package com.nuvio.tv.core.streams

import com.nuvio.tv.domain.model.Stream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioProbeCachePolicyTest {

    private fun stream(
        infoHash: String? = null,
        url: String? = null
    ) = Stream(
        name = "Stream",
        title = null,
        description = null,
        url = url,
        ytId = null,
        infoHash = infoHash,
        fileIdx = null,
        externalUrl = null,
        behaviorHints = null,
        addonName = "addon",
        addonLogo = null
    )

    @Test
    fun `cache key is stable for the same hash and size`() {
        val a = AudioProbeCachePolicy.cacheKeyFor("hash", 1_000L)
        val b = AudioProbeCachePolicy.cacheKeyFor("hash", 1_000L)
        assertEquals(a, b)
    }

    @Test
    fun `cache key ignores the debrid host when an info hash exists`() {
        val a = AudioProbeCachePolicy.cacheKey(
            stream(infoHash = "ABC", url = "http://host1.example/a.mkv")
        )
        val b = AudioProbeCachePolicy.cacheKey(
            stream(infoHash = "abc", url = "http://host2.example/b.mkv")
        )
        assertEquals(a, b)
    }

    @Test
    fun `cache key changes with size`() {
        assertNotEquals(
            AudioProbeCachePolicy.cacheKeyFor("hash", 1_000L),
            AudioProbeCachePolicy.cacheKeyFor("hash", 2_000L)
        )
    }

    @Test
    fun `cache key falls back to the url when there is no hash`() {
        assertNotEquals(
            AudioProbeCachePolicy.cacheKey(stream(url = "http://host/a.mkv")),
            AudioProbeCachePolicy.cacheKey(stream(url = "http://host/b.mkv"))
        )
    }

    @Test
    fun `cache key encodes the parser version`() {
        assertTrue(
            AudioProbeCachePolicy.cacheKeyFor("x", 1L)
                .startsWith("v${AudioProbeCachePolicy.PARSER_VERSION}:")
        )
    }

    @Test
    fun `verified entries live for 7 days`() {
        val now = 10_000_000_000L
        assertTrue(
            AudioProbeCachePolicy.isFresh(
                now - AudioProbeCachePolicy.VERIFIED_TTL_MS + 1,
                inconclusive = false,
                nowMs = now
            )
        )
        assertFalse(
            AudioProbeCachePolicy.isFresh(
                now - AudioProbeCachePolicy.VERIFIED_TTL_MS - 1,
                inconclusive = false,
                nowMs = now
            )
        )
    }

    @Test
    fun `inconclusive entries live for 6 hours`() {
        val now = 10_000_000_000L
        assertTrue(
            AudioProbeCachePolicy.isFresh(
                now - AudioProbeCachePolicy.INCONCLUSIVE_TTL_MS + 1,
                inconclusive = true,
                nowMs = now
            )
        )
        assertFalse(
            AudioProbeCachePolicy.isFresh(
                now - AudioProbeCachePolicy.INCONCLUSIVE_TTL_MS - 1,
                inconclusive = true,
                nowMs = now
            )
        )
    }

    @Test
    fun `missing timestamps are stale and small clock skew is tolerated`() {
        assertFalse(AudioProbeCachePolicy.isFresh(0L, inconclusive = false, nowMs = 1_000L))
        assertTrue(AudioProbeCachePolicy.isFresh(2_000L, inconclusive = false, nowMs = 1_000L))
        assertFalse(
            AudioProbeCachePolicy.isFresh(
                1_000L + AudioProbeCachePolicy.MAX_CLOCK_SKEW_MS + 1,
                inconclusive = false,
                nowMs = 1_000L
            )
        )
    }
}
