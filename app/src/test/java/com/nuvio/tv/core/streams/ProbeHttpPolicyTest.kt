package com.nuvio.tv.core.streams

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProbeHttpPolicyTest {

    @Test
    fun `classifies success codes`() {
        assertEquals(ProbeHttpOutcome.OK, ProbeHttpPolicy.classify(200))
        assertEquals(ProbeHttpOutcome.OK, ProbeHttpPolicy.classify(206))
        assertEquals(ProbeHttpOutcome.OK, ProbeHttpPolicy.classify(204))
    }

    @Test
    fun `classifies rate limit and auth failures`() {
        assertEquals(ProbeHttpOutcome.RATE_LIMITED, ProbeHttpPolicy.classify(429))
        assertEquals(ProbeHttpOutcome.FORBIDDEN, ProbeHttpPolicy.classify(401))
        assertEquals(ProbeHttpOutcome.FORBIDDEN, ProbeHttpPolicy.classify(403))
    }

    @Test
    fun `classifies transient server failures`() {
        assertEquals(ProbeHttpOutcome.TRANSIENT, ProbeHttpPolicy.classify(408))
        assertEquals(ProbeHttpOutcome.TRANSIENT, ProbeHttpPolicy.classify(425))
        assertEquals(ProbeHttpOutcome.TRANSIENT, ProbeHttpPolicy.classify(500))
        assertEquals(ProbeHttpOutcome.TRANSIENT, ProbeHttpPolicy.classify(503))
    }

    @Test
    fun `classifies other client errors as other`() {
        assertEquals(ProbeHttpOutcome.OTHER, ProbeHttpPolicy.classify(404))
        assertEquals(ProbeHttpOutcome.OTHER, ProbeHttpPolicy.classify(410))
        assertEquals(ProbeHttpOutcome.OTHER, ProbeHttpPolicy.classify(451))
    }

    @Test
    fun `stops the batch on rate limit, forbidden and transient errors only`() {
        assertTrue(ProbeHttpPolicy.stopsBatch(ProbeHttpOutcome.RATE_LIMITED))
        assertTrue(ProbeHttpPolicy.stopsBatch(ProbeHttpOutcome.FORBIDDEN))
        assertTrue(ProbeHttpPolicy.stopsBatch(ProbeHttpOutcome.TRANSIENT))
        assertFalse(ProbeHttpPolicy.stopsBatch(ProbeHttpOutcome.OK))
        assertFalse(ProbeHttpPolicy.stopsBatch(ProbeHttpOutcome.OTHER))
    }

    @Test
    fun `parses retry-after in seconds`() {
        assertEquals(30_000L, ProbeHttpPolicy.parseRetryAfterMs("30"))
        assertEquals(0L, ProbeHttpPolicy.parseRetryAfterMs("0"))
        assertEquals(0L, ProbeHttpPolicy.parseRetryAfterMs("-5"))
    }

    @Test
    fun `ignores missing or non numeric retry-after`() {
        assertNull(ProbeHttpPolicy.parseRetryAfterMs(null))
        assertNull(ProbeHttpPolicy.parseRetryAfterMs(""))
        assertNull(ProbeHttpPolicy.parseRetryAfterMs("Wed, 21 Oct 2015 07:28:00 GMT"))
    }

    @Test
    fun `caps retry-after to the maximum cooldown`() {
        assertEquals(ProbeHttpPolicy.MAX_COOLDOWN_MS, ProbeHttpPolicy.parseRetryAfterMs("999999"))
    }

    @Test
    fun `cooldown prefers retry-after header over the default`() {
        assertEquals(
            12_000L,
            ProbeHttpPolicy.cooldownMs(ProbeHttpOutcome.RATE_LIMITED, "12")
        )
        assertEquals(
            ProbeHttpPolicy.defaultCooldownMs(ProbeHttpOutcome.RATE_LIMITED),
            ProbeHttpPolicy.cooldownMs(ProbeHttpOutcome.RATE_LIMITED, null)
        )
    }
}
