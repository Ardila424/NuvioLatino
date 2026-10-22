package com.nuvio.tv.ui.screens.player

import com.nuvio.tv.data.local.SUBTITLE_LANGUAGE_FORCED

/**
 * Central policy for deciding which subtitle languages remain visible when the
 * user enables "Show Only Preferred Languages".
 *
 * Filtering is intentionally narrow: it only kicks in for the
 * Spanish (Latin America) preference with a secondary language set. Every other
 * configuration returns `null` ("do not filter") so that English, generic
 * Spanish, and all other setups keep their current behaviour untouched.
 */
internal object SubtitleVisibilityPolicy {

    const val SPANISH_FAMILY = "es"
    const val SPANISH_LATIN_AMERICA = "es-419"

    /**
     * Returns the set of language targets that should be shown, or `null` to
     * show every language (no filtering).
     */
    fun visibleSubtitleTargets(
        showOnly: Boolean,
        preferred: String,
        secondary: String?
    ): List<String>? {
        if (!showOnly) return null
        if (PlayerSubtitleUtils.normalizeLanguageCode(preferred) != SPANISH_LATIN_AMERICA) return null
        val secondaryTarget = secondary
            ?.takeIf { it.isNotBlank() }
            ?.let { PlayerSubtitleUtils.normalizeLanguageCode(it) }
            ?.takeUnless { it.isBlank() || it == "none" || it == SUBTITLE_LANGUAGE_FORCED }
            ?: return null
        return listOf(SPANISH_FAMILY, secondaryTarget).distinct()
    }

    /** Whether the Spanish family merge is active for the given visibility targets. */
    fun mergesSpanishFamily(targets: List<String>?): Boolean =
        targets?.any { PlayerSubtitleUtils.languageFamilyKey(it) == SPANISH_FAMILY } == true
}
