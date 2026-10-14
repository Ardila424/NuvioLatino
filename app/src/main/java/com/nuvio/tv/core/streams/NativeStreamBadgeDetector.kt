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
        """(?i)(^|[^a-z0-9])(latino|latinos|latin|lat|es-419|es419|spa-lat|spalat|esp-lat|dual[ ._-]?latino|audio[ ._-]?latino|multi[ ._-]?lat)([^a-z0-9]|$)"""
    )

    private val CASTELLANO_REGEX = Regex(
        """(?i)(^|[^a-z0-9])(castellano|español|espanol|spanish|spa)([^a-z0-9]|$)"""
    )

    private val MULTI_AUDIO_REGEX = Regex(
        """(?i)(^|[^a-z0-9])(multi|dual|multi[ ._-]?audio|multiaudio|multi[ ._-]?lang|multilang)([^a-z0-9]|$)"""
    )

    private val UHD_4K_REGEX = Regex(
        """(?i)(^|[^a-z0-9])(2160p?|4k|uhd)([^a-z0-9]|$)"""
    )

    fun detectBadges(stream: Stream): List<StreamBadge> {
        val badges = mutableListOf<StreamBadge>()

        val remux = isRemux(stream)
        val latino = isLatino(stream)
        val castellano = isCastellano(stream)
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
        }

        if (castellano) {
            badges += StreamBadge(
                name = "🇪🇸 CASTELLANO",
                tagColor = "#B45309",
                textColor = "#FFFFFF",
                borderColor = "#F59E0B"
            )
        }

        if (multi && !latino && !castellano) {
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
        if (hasExplicitLanguagesWithoutSpanish(stream)) return false
        if (hasExplicitFlagsWithoutSpanish(stream)) return false

        val text = candidateTexts(stream).joinToString(" ")
        if (text.contains("🇲🇽")) return true

        val parsedLangs = stream.clientResolve?.stream?.raw?.parsed?.languages.orEmpty()
        if (parsedLangs.any { it.equals("la", ignoreCase = true) || it.equals("lat", ignoreCase = true) || it.equals("latino", ignoreCase = true) || it.equals("es-419", ignoreCase = true) || it.equals("latin", ignoreCase = true) }) {
            return true
        }
        return candidateTexts(stream).any { LATINO_REGEX.containsMatchIn(it) }
    }

    fun isCastellano(stream: Stream): Boolean {
        if (hasExplicitLanguagesWithoutSpanish(stream)) return false
        if (hasExplicitFlagsWithoutSpanish(stream)) return false

        val text = candidateTexts(stream).joinToString(" ")
        if (text.contains("🇪🇸")) return true

        val parsedLangs = stream.clientResolve?.stream?.raw?.parsed?.languages.orEmpty()
        if (parsedLangs.any { it.equals("es", ignoreCase = true) || it.equals("spa", ignoreCase = true) || it.equals("spanish", ignoreCase = true) || it.equals("castellano", ignoreCase = true) }) {
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

    fun isSpanishOrLatino(stream: Stream): Boolean {
        return isLatino(stream) || isCastellano(stream)
    }

    private val EXPLICIT_LANGUAGES_REGEX = Regex(
        """(?i)languages?:\s*([^|\n\r]+)"""
    )

    private val OTHER_AUDIO_FLAGS = listOf(
        "🇬🇧", "🇺🇸", "🇨🇿", "🇷🇺", "🇩🇪", "🇫🇷", "🇮🇹", "🇯🇵", "🇰🇷", "🇵🇹", "🇧🇷", "🇵🇱", "🇹🇷", "🇮🇳"
    )

    private fun hasExplicitLanguagesWithoutSpanish(stream: Stream): Boolean {
        val desc = stream.description ?: return false
        val match = EXPLICIT_LANGUAGES_REGEX.find(desc) ?: return false
        val languagesText = match.groupValues[1].lowercase()
        val hasSpanish = languagesText.contains("spanish") ||
            languagesText.contains("latino") ||
            languagesText.contains("latin") ||
            languagesText.contains("español") ||
            languagesText.contains("espanol") ||
            languagesText.contains("castellano") ||
            languagesText.contains("es-419") ||
            languagesText.contains("spa")
        return !hasSpanish
    }

    private fun hasExplicitFlagsWithoutSpanish(stream: Stream): Boolean {
        val text = listOfNotNull(stream.description, stream.title, stream.name).joinToString(" ")
        if (text.contains("🇲🇽") || text.contains("🇪🇸")) return false
        return OTHER_AUDIO_FLAGS.any { text.contains(it) }
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
