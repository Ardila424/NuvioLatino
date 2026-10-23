package com.nuvio.tv.core.latino

import com.nuvio.tv.core.debrid.StreamTextSizeParser
import com.nuvio.tv.core.streams.NativeStreamBadgeDetector
import com.nuvio.tv.domain.model.Stream

/**
 * Pure ranking policy for the stream list. Kept free of Compose/ViewModel state
 * so the ordering rules can be unit-tested.
 *
 * Language tiers (higher wins):
 * 600 verified Latino
 * 500 verified Castellano (probe said "Sin Latino" but a Spanish track exists)
 * 450 filename/title says Latino (unverified)
 * 400 filename/title says Spanish/Castellano (unverified)
 * 300 unknown (not probed yet — never bury these below confirmed misses)
 * 100 verified: no Spanish at all
 */
internal object LatinoStreamRanker {

    private const val TIER_VERIFIED_LATINO = 600
    private const val TIER_VERIFIED_CASTELLANO = 500
    private const val TIER_LIKELY_LATINO = 450
    private const val TIER_LIKELY_SPANISH = 400
    private const val TIER_UNKNOWN = 300
    private const val TIER_NO_SPANISH = 100

    data class Attributes(
        val latinoScore: Int,
        val qualityScore: Int,
        val resolutionScore: Int,
        val hdrScore: Int,
        val sizeBytes: Long
    )

    fun isVerifiedLatino(stream: Stream): Boolean =
        stream.badges.any { it.name.contains("LATINO (Verificado)") }

    fun isVerifiedNoLatino(stream: Stream): Boolean =
        stream.badges.any { it.name.startsWith("🚫 Sin Latino") }

    /** A "🚫 Sin Latino (SPA, …)" probe result still declares Spanish audio. */
    fun isVerifiedNoLatinoWithSpanish(stream: Stream): Boolean {
        val badge = stream.badges.firstOrNull { it.name.startsWith("🚫 Sin Latino") } ?: return false
        return badge.name.contains("SPA") ||
            badge.name.contains("ES-") ||
            badge.name.contains("ES,") ||
            badge.name.contains("ES)")
    }

    fun languageTier(stream: Stream, latinTitles: List<String> = emptyList()): Int {
        val verifiedLatino = isVerifiedLatino(stream)
        val verifiedNoLatino = isVerifiedNoLatino(stream)
        val verifiedCastellano = isVerifiedNoLatinoWithSpanish(stream)
        val likelyLatino = !verifiedLatino && !verifiedNoLatino &&
            NativeStreamBadgeDetector.isLatino(stream, latinTitles)
        val likelySpanish = !verifiedNoLatino &&
            NativeStreamBadgeDetector.isCastellano(stream)

        return when {
            verifiedLatino -> TIER_VERIFIED_LATINO
            verifiedCastellano -> TIER_VERIFIED_CASTELLANO
            likelyLatino -> TIER_LIKELY_LATINO
            likelySpanish -> TIER_LIKELY_SPANISH
            verifiedNoLatino -> TIER_NO_SPANISH
            else -> TIER_UNKNOWN
        }
    }

    fun attributes(stream: Stream, latinTitles: List<String> = emptyList()): Attributes {
        val quality = qualityScore(stream)
        val resolution = resolutionScore(stream)
        val hdr = hdrScore(stream)
        val size = StreamTextSizeParser.effectiveSizeBytes(stream) ?: 0L
        val sizeTieBreak = (size / 1_000_000_000L).coerceAtMost(50L).toInt()

        return Attributes(
            latinoScore = languageTier(stream, latinTitles) * 1_000 +
                quality * 10 +
                resolution +
                hdr +
                sizeTieBreak,
            qualityScore = quality * 100 + resolution * 10 + hdr,
            resolutionScore = resolution,
            hdrScore = hdr,
            sizeBytes = size
        )
    }

    fun qualityScore(stream: Stream): Int {
        if (NativeStreamBadgeDetector.isRemux(stream)) return 60
        val text = contentText(stream)
        return when {
            text.contains("bluray") || text.contains("blu-ray") || text.contains("bdrip") -> 50
            text.contains("web-dl") || text.contains("webdl") || text.contains("web dl") -> 40
            text.contains("webrip") || text.contains("web-rip") -> 30
            text.contains("hdrip") || text.contains("brrip") || text.contains("dvdrip") -> 20
            else -> 10
        }
    }

    fun resolutionScore(stream: Stream): Int {
        if (NativeStreamBadgeDetector.is4K(stream)) return 40
        val text = contentText(stream)
        return when {
            text.contains("1080") -> 30
            text.contains("720") -> 10
            else -> 0
        }
    }

    fun hdrScore(stream: Stream): Int {
        val text = contentText(stream)
        return when {
            text.contains("dolby vision") || text.contains("dovi") || text.contains(" dv ") -> 10
            text.contains("hdr10+") -> 9
            text.contains("hdr") -> 8
            else -> 0
        }
    }

    private fun contentText(stream: Stream): String = buildString {
        append(stream.name.orEmpty()).append(' ')
        append(stream.title.orEmpty()).append(' ')
        append(stream.description.orEmpty()).append(' ')
        append(stream.quality.orEmpty())
    }.lowercase()
}
