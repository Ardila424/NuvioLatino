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

    /**
     * Markers strong enough to promote a card to LATINO ✓ without reading the
     * file header. Generic "Spanish"/"Español"/"Multi" are deliberately absent:
     * those stay LATINO ? because they cannot distinguish Latino from
     * Castellano. The 1080p card itself usually carries these tokens.
     */
    private val EXPLICIT_LATINO_REGEX = Regex(
        """(?i)(^|[^a-z0-9])(latino|latinos|latam|latin|lat|es-419|es419|es-mx|es-ar|es-co|es-cl|es-pe|es-ve|es-ec|mx|mexico|méxico|arg|argentina|spa-lat|spalat|esp-lat|dual[ ._-]?latino|audio[ ._-]?latino|multi[ ._-]?lat)([^a-z0-9]|$)"""
    )

    private val EXPLICIT_CASTELLANO_REGEX = Regex(
        """(?i)(^|[^a-z0-9])(castellano|castilian|españa|espana|spain|es-es)([^a-z0-9]|$)"""
    )

    /** Removes "Subtitles: ..." segments so subtitle text can never decide audio language. */
    private val SUBTITLE_SECTION_REGEX = Regex(
        """(?i)(\|\s*)?(subtitles?|subs?)\s*:[^|\n\r]*"""
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

    fun detectBadges(stream: Stream, latinTitles: List<String> = emptyList()): List<StreamBadge> {
        val badges = mutableListOf<StreamBadge>()

        val remux = isRemux(stream)
        val latino = isLatino(stream, latinTitles)
        val explicitLatino = hasExplicitLatinoMarker(stream)
        val explicitCastellano = hasExplicitCastellanoMarker(stream)
        val castellano = isCastellano(stream)
        val multi = isMulti(stream)

        val hasVerifiedLatino = stream.badges.any { it.name.contains("LATINO (Verificado)") }
        val hasNoLatino = stream.badges.any { it.name.startsWith("🚫 Sin Latino") }

        if (remux) {
            badges += StreamBadge(
                name = "💎 REMUX",
                tagColor = "#6D28D9",
                textColor = "#FFFFFF",
                borderColor = "#8B5CF6"
            )
        }

        if (hasVerifiedLatino) {
            badges += StreamBadge(
                name = "🇲🇽 LATINO (Verificado)",
                tagColor = "#059669",
                textColor = "#FFFFFF",
                borderColor = "#10B981"
            )
        } else if (hasNoLatino) {
            stream.badges.firstOrNull { it.name.startsWith("🚫 Sin Latino") }?.let {
                badges += it
            }
        } else if (explicitLatino) {
            // The release/card explicitly says Latino/LAT/MX. Show the green
            // check immediately; tagStyle distinguishes it from a probe.
            badges += StreamBadge(
                name = "🇲🇽 LATINO (Verificado)",
                tagColor = "#059669",
                textColor = "#FFFFFF",
                borderColor = "#10B981",
                tagStyle = "latino-name"
            )
        } else if (latino) {
            badges += StreamBadge(
                name = "🇲🇽 LATINO",
                tagColor = "#059669",
                textColor = "#FFFFFF",
                borderColor = "#10B981"
            )
        }

        if (castellano && (!explicitLatino || explicitCastellano)) {
            badges += StreamBadge(
                name = "🇪🇸 CASTELLANO",
                tagColor = "#B45309",
                textColor = "#FFFFFF",
                borderColor = "#F59E0B"
            )
        }

        if (multi && !latino && !castellano && !hasVerifiedLatino) {
            badges += StreamBadge(
                name = "🌐 MULTI",
                tagColor = "#1D4ED8",
                textColor = "#FFFFFF",
                borderColor = "#3B82F6"
            )
        }

        badges += detectTechnicalBadges(stream, badges)
        return badges
    }

    /**
     * Technical marks parsed from the title/description/filename plus the
     * addon's parsed metadata. These are *estimates* taken from the release
     * name, not facts read from the file; the compact renderer draws them with
     * a hollow border so they never look like a probe-verified result.
     */
    private fun detectTechnicalBadges(stream: Stream, existing: List<StreamBadge>): List<StreamBadge> {
        val parsed = stream.clientResolve?.stream?.raw?.parsed
        val text = buildString {
            append(candidateTexts(stream).joinToString(" "))
            append(' ')
            append(
                listOfNotNull(
                    parsed?.resolution,
                    parsed?.quality,
                    parsed?.codec,
                    parsed?.hdr?.joinToString(" "),
                    parsed?.audio?.joinToString(" "),
                    parsed?.channels?.joinToString(" ")
                ).joinToString(" ")
            )
        }.lowercase()

        val technical = mutableListOf<StreamBadge>()
        fun has(pattern: Regex) = pattern.containsMatchIn(text)
        fun add(name: String, style: String, background: String = "", foreground: String = "#FFFFFF", border: String = "") {
            val alreadyThere = existing.any {
                it.name == name || it.name.removePrefix("💎 ").equals(name, ignoreCase = true)
            } || technical.any { it.name == name }
            if (alreadyThere) return
            technical += StreamBadge(
                name = name,
                tagStyle = style,
                tagColor = background,
                textColor = foreground,
                borderColor = border
            )
        }

        if (has(FOUR_K_REGEX)) {
            add("4K", "tech-4k", "#0B0B0C", "#FFFFFF", "#FFFFFF")
        } else if (has(FULL_HD_REGEX)) {
            add("1080p", "tech-meta", foreground = "#D1D5DB", border = "#4B5563")
        }

        if (has(DOLBY_VISION_REGEX)) add("DV", "tech-dv", "#000000", "#FFFFFF", "#FFFFFF")

        when {
            has(HDR10_PLUS_REGEX) -> add("HDR10+", "tech-hdr", "#0B0B0C", "#FFFFFF", "#FFFFFF")
            has(HDR10_REGEX) -> add("HDR10", "tech-hdr", "#0B0B0C", "#FFFFFF", "#FFFFFF")
            has(HDR_REGEX) -> add("HDR", "tech-hdr", "#0B0B0C", "#FFFFFF", "#6B7280")
        }
        if (has(HLG_REGEX)) add("HLG", "tech-hdr", "#0B0B0C", "#FFFFFF", "#6B7280")

        val isAtmos = has(ATMOS_REGEX)
        if (isAtmos) add("ATMOS", "tech-atmos", "#0B0B0C", "#FFFFFF", "#FFFFFF")

        when {
            has(DTS_X_REGEX) -> add("DTS:X", "tech-dts", "#0B0B0C", "#FFFFFF", "#6B7280")
            has(DTS_HD_MA_REGEX) -> add("DTS-HD MA", "tech-dts", "#0B0B0C", "#FFFFFF", "#6B7280")
            has(DTS_HD_REGEX) -> add("DTS-HD", "tech-dts", "#0B0B0C", "#FFFFFF", "#6B7280")
            has(DTS_REGEX) -> add("DTS", "tech-dts", "#0B0B0C", "#FFFFFF", "#6B7280")
        }

        if (!isAtmos) {
            when {
                has(TRUEHD_REGEX) -> add("TrueHD", "tech-audio", foreground = "#D1D5DB", border = "#4B5563")
                has(DD_PLUS_REGEX) -> add("DD+", "tech-audio", foreground = "#D1D5DB", border = "#4B5563")
                has(DD_REGEX) -> add("DD", "tech-audio", foreground = "#D1D5DB", border = "#4B5563")
                has(AAC_REGEX) -> add("AAC", "tech-audio", foreground = "#D1D5DB", border = "#4B5563")
            }
        }

        when {
            has(SURROUND_7_1_REGEX) -> add("7.1", "tech-meta", foreground = "#D1D5DB", border = "#4B5563")
            has(SURROUND_5_1_REGEX) -> add("5.1", "tech-meta", foreground = "#D1D5DB", border = "#4B5563")
        }

        when {
            has(HEVC_REGEX) -> add("HEVC", "tech-meta", foreground = "#D1D5DB", border = "#4B5563")
            has(AV1_REGEX) -> add("AV1", "tech-meta", foreground = "#D1D5DB", border = "#4B5563")
        }

        if (has(TEN_BIT_REGEX)) {
            add("10-bit", "tech-meta", foreground = "#D1D5DB", border = "#4B5563")
        }

        return technical.take(8)
    }

    private val FOUR_K_REGEX = Regex("""(?i)(^|[^a-z0-9])(2160p?|4k|uhd)([^a-z0-9]|$)""")
    private val FULL_HD_REGEX = Regex("""(?i)(^|[^a-z0-9])(1080p?|fhd)([^a-z0-9]|$)""")
    private val DOLBY_VISION_REGEX = Regex("""(?i)(dolby[ ._-]?vision|\bdovi\b|\bdv\b)""")
    private val HDR10_PLUS_REGEX = Regex("""(?i)(hdr10\+|hdr10plus|hdr10 plus)""")
    private val HDR10_REGEX = Regex("""(?i)hdr10""")
    private val HDR_REGEX = Regex("""(?i)(^|[^a-z0-9])hdr([^a-z0-9]|$)""")
    private val HLG_REGEX = Regex("""(?i)(^|[^a-z0-9])hlg([^a-z0-9]|$)""")
    private val ATMOS_REGEX = Regex("""(?i)(^|[^a-z0-9])(atmos|dolby[ ._-]?atmos)([^a-z0-9]|$)""")
    private val DTS_X_REGEX = Regex("""(?i)(dts[ .:_-]?x|dtsx)([^a-z0-9]|$)""")
    private val DTS_HD_MA_REGEX = Regex("""(?i)(dts[ ._-]?hd[ ._-]?ma|dtshd[ ._-]?ma)""")
    private val DTS_HD_REGEX = Regex("""(?i)(dts[ ._-]?hd|dtshd)([^a-z0-9]|$)""")
    private val DTS_REGEX = Regex("""(?i)(^|[^a-z0-9])dts([^a-z0-9]|$)""")
    private val TRUEHD_REGEX = Regex("""(?i)(true[ ._-]?hd)([^a-z0-9]|$)""")
    private val DD_PLUS_REGEX = Regex("""(?i)(\bddp\b|dd\+|e[- .]?ac[- .]?3|dolby digital plus)""")
    private val DD_REGEX = Regex("""(?i)(\bac[- .]?3\b|\bdd\b)""")
    private val AAC_REGEX = Regex("""(?i)(^|[^a-z0-9])aac([^a-z0-9]|$)""")
    private val SURROUND_7_1_REGEX = Regex("""(?i)(^|[^a-z0-9])7[ .]1([^a-z0-9]|$)""")
    private val SURROUND_5_1_REGEX = Regex("""(?i)(^|[^a-z0-9])5[ .]1([^a-z0-9]|$)|6ch""")
    private val HEVC_REGEX = Regex("""(?i)(^|[^a-z0-9])(hevc|h[ .]?265|x265)([^a-z0-9]|$)""")
    private val AV1_REGEX = Regex("""(?i)(^|[^a-z0-9])av1([^a-z0-9]|$)""")
    private val TEN_BIT_REGEX = Regex("""(?i)(^|[^a-z0-9])10[ ._-]?bit([^a-z0-9]|$)""")

    fun isRemux(stream: Stream): Boolean {
        if (stream.quality?.contains("remux", ignoreCase = true) == true) return true
        val parsedQuality = stream.clientResolve?.stream?.raw?.parsed?.quality
        if (parsedQuality?.contains("remux", ignoreCase = true) == true) return true
        return candidateTexts(stream).any { REMUX_REGEX.containsMatchIn(it) }
    }

    fun isLatino(stream: Stream, latinTitles: List<String> = emptyList()): Boolean {
        if (stream.badges.any { it.name.startsWith("🚫 Sin Latino") }) return false
        if (stream.badges.any { it.name.contains("LATINO (Verificado)") }) return true
        if (hasExplicitLanguagesWithoutSpanish(stream)) return false
        if (hasExplicitFlagsWithoutSpanish(stream)) return false

        val text = audioCandidateTexts(stream).joinToString(" ")
        if (text.contains("🇲🇽")) return true

        if (matchesLatinTitle(text, latinTitles)) return true

        val parsedLangs = stream.clientResolve?.stream?.raw?.parsed?.languages.orEmpty()
        if (parsedLangs.any { it.equals("la", ignoreCase = true) || it.equals("lat", ignoreCase = true) || it.equals("latino", ignoreCase = true) || it.equals("es-419", ignoreCase = true) || it.equals("latin", ignoreCase = true) }) {
            return true
        }
        return audioCandidateTexts(stream).any { LATINO_REGEX.containsMatchIn(it) }
    }

    /**
     * True only when the release/card itself names a Latin American variant
     * (Latino, LAT, LATAM, MX, Mexico, ARG, es-419, ...). This never looks at
     * subtitles and never treats generic Spanish as Latino.
     */
    fun hasExplicitLatinoMarker(stream: Stream): Boolean {
        if (stream.badges.any { it.name.startsWith("🚫 Sin Latino") }) return false
        if (hasExplicitLanguagesWithoutSpanish(stream)) return false
        if (hasExplicitFlagsWithoutSpanish(stream)) return false

        val text = audioCandidateTexts(stream).joinToString(" ")

        val explicitRegions = setOf(
            "la", "lat", "latam", "latin", "latino", "es-419", "es419",
            "es-mx", "es-ar", "es-co", "es-cl", "es-pe", "es-ve", "es-ec",
            "es-us", "es-la", "es-005", "mx", "mexico", "méxico", "arg"
        )
        val parsedLangs = stream.clientResolve?.stream?.raw?.parsed?.languages.orEmpty()
        if (parsedLangs.any { it.lowercase() in explicitRegions }) return true

        return audioCandidateTexts(stream).any { EXPLICIT_LATINO_REGEX.containsMatchIn(it) }
    }

    /**
     * True only when the audio metadata explicitly names Spain/Castilian.
     * Generic "spa"/"es" does not count: if the same release also says Latino,
     * showing ESPAÑOL next to LATINO is misleading.
     */
    fun hasExplicitCastellanoMarker(stream: Stream): Boolean {
        val text = audioCandidateTexts(stream).joinToString(" ")
        if (text.contains("🇪🇸")) return true

        val parsedLangs = stream.clientResolve?.stream?.raw?.parsed?.languages.orEmpty()
        if (parsedLangs.any {
                it.equals("es-es", ignoreCase = true) ||
                    it.equals("castellano", ignoreCase = true) ||
                    it.equals("spain", ignoreCase = true)
            }
        ) {
            return true
        }

        return audioCandidateTexts(stream).any { EXPLICIT_CASTELLANO_REGEX.containsMatchIn(it) }
    }

    fun isCastellano(stream: Stream): Boolean {
        if (hasExplicitLanguagesWithoutSpanish(stream)) return false
        if (hasExplicitFlagsWithoutSpanish(stream)) return false

        val text = audioCandidateTexts(stream).joinToString(" ")
        if (text.contains("🇪🇸")) return true

        val parsedLangs = stream.clientResolve?.stream?.raw?.parsed?.languages.orEmpty()
        if (parsedLangs.any { it.equals("es", ignoreCase = true) || it.equals("spa", ignoreCase = true) || it.equals("spanish", ignoreCase = true) || it.equals("castellano", ignoreCase = true) }) {
            return true
        }
        return audioCandidateTexts(stream).any { CASTELLANO_REGEX.containsMatchIn(it) }
    }

    fun isMulti(stream: Stream): Boolean {
        val parsedLangs = stream.clientResolve?.stream?.raw?.parsed?.languages.orEmpty()
        if (parsedLangs.size > 1 || parsedLangs.any { it.equals("multi", ignoreCase = true) }) {
            return true
        }
        return audioCandidateTexts(stream).any { MULTI_AUDIO_REGEX.containsMatchIn(it) }
    }

    fun is4K(stream: Stream): Boolean {
        val parsedRes = stream.clientResolve?.stream?.raw?.parsed?.resolution
        if (parsedRes?.contains("2160", ignoreCase = true) == true || parsedRes?.contains("4k", ignoreCase = true) == true) return true
        if (stream.quality?.contains("4k", ignoreCase = true) == true || stream.quality?.contains("2160", ignoreCase = true) == true) return true
        return candidateTexts(stream).any { UHD_4K_REGEX.containsMatchIn(it) }
    }

    fun isSpanishOrLatino(stream: Stream, latinTitles: List<String> = emptyList()): Boolean {
        return isLatino(stream, latinTitles) || isCastellano(stream)
    }

    private fun matchesLatinTitle(text: String, latinTitles: List<String>): Boolean {
        if (latinTitles.isEmpty()) return false
        val normalizedText = normalizeForMatching(text)
        return latinTitles.any { title ->
            val normalizedTitle = normalizeForMatching(title)
            normalizedTitle.length >= 4 && normalizedText.contains(normalizedTitle)
        }
    }

    private fun normalizeForMatching(s: String): String =
        s.lowercase().replace(Regex("[^a-z0-9]"), " ").replace(Regex("\\s+"), " ").trim()

    private val EXPLICIT_LANGUAGES_REGEX = Regex(
        """(?i)languages?:\s*([^|\n\r]+)"""
    )

    private val OTHER_AUDIO_FLAGS = listOf(
        "🇬🇧", "🇺🇸", "🇨🇿", "🇷🇺", "🇩🇪", "🇫🇷", "🇮🇹", "🇯🇵", "🇰🇷", "🇵🇹", "🇧🇷", "🇵🇱", "🇹🇷", "🇮🇳"
    )

    private fun hasExplicitLanguagesWithoutSpanish(stream: Stream): Boolean {
        val desc = stream.description?.let(::stripSubtitleSections) ?: return false
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
        val text = audioCandidateTexts(stream).joinToString(" ")
        if (text.contains("🇲🇽") || text.contains("🇪🇸")) return false
        return OTHER_AUDIO_FLAGS.any { text.contains(it) }
    }

    private fun stripSubtitleSections(value: String): String =
        SUBTITLE_SECTION_REGEX.replace(value, " ")

    private fun audioCandidateTexts(stream: Stream): List<String> =
        candidateTexts(stream)
            .map(::stripSubtitleSections)
            .filter { it.isNotBlank() }

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
