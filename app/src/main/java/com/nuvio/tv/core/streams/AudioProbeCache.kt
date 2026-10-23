package com.nuvio.tv.core.streams

import com.nuvio.tv.core.debrid.StreamTextSizeParser
import com.nuvio.tv.domain.model.Stream
import java.security.MessageDigest

/**
 * Persistent cache for verified/terminal audio probes.
 *
 * Implementations must never store [StreamAudioProbeResult.RateLimited]: that
 * outcome is host-wide and expires on its own.
 */
interface AudioProbeCache {
    suspend fun get(cacheKey: String): StreamAudioProbeResult?
    suspend fun put(cacheKey: String, result: StreamAudioProbeResult)
}

/**
 * Pure cache policy (key, TTL, freshness). Kept free of Android types so it can
 * be unit-tested on the JVM.
 */
internal object AudioProbeCachePolicy {

    /** Bump whenever a parser changes semantics; invalidates every entry. */
    const val PARSER_VERSION = 1

    /** A header-verified verdict is stable for a month. */
    const val VERIFIED_TTL_MS = 30L * 24L * 60L * 60L * 1000L

    /** Inconclusive/failed results are retried sooner in case the source recovers. */
    const val INCONCLUSIVE_TTL_MS = 6L * 60L * 60L * 1000L

    const val MAX_ENTRIES = 1_000

    fun cacheKey(stream: Stream): String {
        val identity = stream.getEffectiveInfoHash()?.lowercase()
            ?: stream.getStreamUrl()
            ?: stream.badgeMergeKey()
        return cacheKeyFor(identity, StreamTextSizeParser.effectiveSizeBytes(stream))
    }

    fun cacheKeyFor(identity: String, sizeBytes: Long?): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(identity.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return "v$PARSER_VERSION:$digest:${sizeBytes ?: -1L}"
    }

    /** Tolerate small device clock skew when comparing timestamps. */
    const val MAX_CLOCK_SKEW_MS = 24L * 60L * 60L * 1000L

    fun ttlMs(inconclusive: Boolean): Long =
        if (inconclusive) INCONCLUSIVE_TTL_MS else VERIFIED_TTL_MS

    fun isFresh(savedAtMs: Long, inconclusive: Boolean, nowMs: Long): Boolean {
        if (savedAtMs <= 0L) return false
        val age = nowMs - savedAtMs
        return age in -MAX_CLOCK_SKEW_MS..ttlMs(inconclusive)
    }
}
