package com.nuvio.tv.core.streams

import com.nuvio.tv.core.debrid.StreamTextSizeParser
import com.nuvio.tv.domain.model.Stream
import com.nuvio.tv.domain.model.StreamBadge

/**
 * Detects native badges (Remux, Spanish Latino, Castellano, Multi-audio) from
 * stream names, descriptions, filenames, and parsed technical facts.
 *
 * This provides immediate out-of-the-box visual indicators without requiring
 * external badge rules to be imported.
 */
object NativeStreamBadgeDetector {

    private val REMUX_REGEX = Regex(
        """(?i)(^|[^a-z0-9])(remux|bdremux|uhd[ ._-]?remux)([^a-z0-9]|$)"""
    )

    private val LATINO_REGEX = Regex(
        """(?i)(^|[^a-z0-9])(latino|lat|es-419|dual[ ._-]?latino|audio[ ._-]?latino)([^a-z0-9]|$)"""
    )

    private val CASTELLANO_REGEX = Regex(
        """(?i)(^|[^a-z0-9])(castellano|español|espanol|spa)([^a-z0-9]|$)"""
    )

    private val MULTI_AUDIO_REGEX = Regex(
        """(?i)(^|[^a-z0-9])(multi|dual|multi[ ._-]?audio|multiaudio|multi[ ._-]?lang|multilang)([^a-z0-9]|$)"""
    )

    private val UHD_4K_REGEX = Regex(
        """(?i)(^|[^a-z0-9])(2160p?|4k|uhd)([^a-z0-9]|$)"""
    )

    // 25 GB minimum for a 4K UHD Blu-ray Remux to reliably contain disc tracks
    private const val UHD_REMUX_MIN_SIZE_BYTES = 25L * 1024L * 1024L * 1024L

    fun detectBadges(stream: Stream): List<StreamBadge> {
        val badges = mutableListOf<StreamBadge>()

        val remux = isRemux(stream)
        val latino = isLatino(stream)
        val castellano = isCastellano(stream)
        val probableLatino = if (!latino && !castellano) isProbableUhdRemuxLatino(stream) else false
        val multi = isMulti(stream)

        if (remux) {
            badges += StreamBadge(
                name = "💎 REMUX",
                tagColor = "#6D28D9",
                textColor = "#FFFFFF",
                borderColor = "#8B5CF6"
            )
        }

        if (latino) {
            badges += StreamBadge(
                name = "🇲🇽 LATINO",
                tagColor = "#059669",
                textColor = "#FFFFFF",
                borderColor = "#10B981"
            )
        } else if (probableLatino) {
            badges += StreamBadge(
                name = "🇲🇽 Latino en disco",
                tagColor = "#0F766E",
                textColor = "#FFFFFF",
                borderColor = "#14B8A6"
            )
        }

        if (castellano) {
            badges += StreamBadge(
                name = "🇪🇸 CASTELLANO",
                tagColor = "#B45309",
                textColor = "#FFFFFF",
                borderColor = "#F59E0B"
            )
        }

        if (multi && !latino && !castellano && !probableLatino) {
            badges += StreamBadge(
                name = "🌐 MULTI",
                tagColor = "#1D4ED8",
                textColor = "#FFFFFF",
                borderColor = "#3B82F6"
            )
        }

        return badges
    }

    fun isRemux(stream: Stream): Boolean {
        if (stream.quality?.contains("remux", ignoreCase = true) == true) return true
        val parsedQuality = stream.clientResolve?.stream?.raw?.parsed?.quality
        if (parsedQuality?.contains("remux", ignoreCase = true) == true) return true
        return candidateTexts(stream).any { REMUX_REGEX.containsMatchIn(it) }
    }

    fun isLatino(stream: Stream): Boolean {
        val parsedLangs = stream.clientResolve?.stream?.raw?.parsed?.languages.orEmpty()
        if (parsedLangs.any { it.equals("la", ignoreCase = true) || it.equals("lat", ignoreCase = true) || it.equals("latino", ignoreCase = true) || it.equals("es-419", ignoreCase = true) }) {
            return true
        }
        return candidateTexts(stream).any { LATINO_REGEX.containsMatchIn(it) }
    }

    fun isCastellano(stream: Stream): Boolean {
        val parsedLangs = stream.clientResolve?.stream?.raw?.parsed?.languages.orEmpty()
        if (parsedLangs.any { it.equals("es", ignoreCase = true) || it.equals("spa", ignoreCase = true) || it.equals("spanish", ignoreCase = true) }) {
            return true
        }
        return candidateTexts(stream).any { CASTELLANO_REGEX.containsMatchIn(it) }
    }

    fun isMulti(stream: Stream): Boolean {
        val parsedLangs = stream.clientResolve?.stream?.raw?.parsed?.languages.orEmpty()
        if (parsedLangs.size > 1 || parsedLangs.any { it.equals("multi", ignoreCase = true) }) {
            return true
        }
        return candidateTexts(stream).any { MULTI_AUDIO_REGEX.containsMatchIn(it) }
    }

    fun is4K(stream: Stream): Boolean {
        val parsedRes = stream.clientResolve?.stream?.raw?.parsed?.resolution
        if (parsedRes?.contains("2160", ignoreCase = true) == true || parsedRes?.contains("4k", ignoreCase = true) == true) return true
        if (stream.quality?.contains("4k", ignoreCase = true) == true || stream.quality?.contains("2160", ignoreCase = true) == true) return true
        return candidateTexts(stream).any { UHD_4K_REGEX.containsMatchIn(it) }
    }

    fun isProbableUhdRemuxLatino(stream: Stream): Boolean {
        if (!isRemux(stream) || !is4K(stream)) return false
        val size = StreamTextSizeParser.effectiveSizeBytes(stream) ?: return false
        return size >= UHD_REMUX_MIN_SIZE_BYTES
    }

    fun isSpanishOrLatino(stream: Stream): Boolean {
        return isLatino(stream) || isCastellano(stream) || isProbableUhdRemuxLatino(stream)
    }

    private fun candidateTexts(stream: Stream): List<String> {
        val resolve = stream.clientResolve
        val raw = resolve?.stream?.raw
        val parsed = raw?.parsed
        return listOfNotNull(
            stream.name,
            stream.title,
            stream.description,
            stream.quality,
            resolve?.torrentName,
            resolve?.filename,
            raw?.torrentName,
            raw?.filename,
            stream.behaviorHints?.filename,
            stream.debridCacheStatus?.cachedName,
            parsed?.rawTitle,
            parsed?.parsedTitle,
            parsed?.group,
            parsed?.edition
        ).filter { it.isNotBlank() }
    }
}
