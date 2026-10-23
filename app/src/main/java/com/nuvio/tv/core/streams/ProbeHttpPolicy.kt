package com.nuvio.tv.core.streams

/**
 * Pure HTTP classification for the remote audio probe. Kept free of Android and
 * OkHttp types so it can be unit-tested on the JVM.
 */
internal enum class ProbeHttpOutcome {
    /** 2xx — a usable (partial) response. */
    OK,
    /** 429 — the host is explicitly asking us to back off. */
    RATE_LIMITED,
    /** 401 / 403 — expired or unauthorised debrid/CDN link. */
    FORBIDDEN,
    /** 408 / 425 / 5xx — transient server-side problem. */
    TRANSIENT,
    /** Anything else (404, 410, 451…) — this specific stream is unusable. */
    OTHER
}

internal object ProbeHttpPolicy {

    /** Upper bound for a cooldown, even if the server asks for longer. */
    const val MAX_COOLDOWN_MS = 15 * 60 * 1000L

    fun classify(code: Int): ProbeHttpOutcome = when {
        code in 200..299 -> ProbeHttpOutcome.OK
        code == 429 -> ProbeHttpOutcome.RATE_LIMITED
        code == 401 || code == 403 -> ProbeHttpOutcome.FORBIDDEN
        code == 408 || code == 425 || code in 500..599 -> ProbeHttpOutcome.TRANSIENT
        else -> ProbeHttpOutcome.OTHER
    }

    /**
     * True when the whole batch should stop: the host is telling us to slow down
     * or the account/link is not usable, so probing more streams would only make
     * things worse (and can burn debrid quota).
     */
    fun stopsBatch(outcome: ProbeHttpOutcome): Boolean = when (outcome) {
        ProbeHttpOutcome.RATE_LIMITED,
        ProbeHttpOutcome.FORBIDDEN,
        ProbeHttpOutcome.TRANSIENT -> true
        ProbeHttpOutcome.OK,
        ProbeHttpOutcome.OTHER -> false
    }

    fun defaultCooldownMs(outcome: ProbeHttpOutcome): Long = when (outcome) {
        ProbeHttpOutcome.RATE_LIMITED -> 60_000L
        ProbeHttpOutcome.FORBIDDEN -> 120_000L
        ProbeHttpOutcome.TRANSIENT -> 30_000L
        ProbeHttpOutcome.OK,
        ProbeHttpOutcome.OTHER -> 0L
    }

    /**
     * Parses a `Retry-After` header expressed in seconds (the common form).
     * HTTP-date values are ignored because java.time needs extra desugaring on
     * this minSdk; falling back to the default cooldown is safe.
     */
    fun parseRetryAfterMs(value: String?): Long? {
        val raw = value?.trim().orEmpty()
        if (raw.isEmpty()) return null
        val seconds = raw.toLongOrNull() ?: return null
        if (seconds <= 0L) return 0L
        return (seconds * 1_000L).coerceAtMost(MAX_COOLDOWN_MS)
    }

    fun cooldownMs(outcome: ProbeHttpOutcome, retryAfterHeader: String?): Long {
        val fromHeader = parseRetryAfterMs(retryAfterHeader)
        return (fromHeader ?: defaultCooldownMs(outcome)).coerceAtMost(MAX_COOLDOWN_MS)
    }
}
