package com.nuvio.tv.core.streams

import java.util.Locale

enum class SpanishAudioRegion {
    LATINO,
    CASTELLANO,
    SPANISH,
    OTHER
}

/**
 * Single classifier for an audio track's Spanish region. Shared by the header
 * probe and the player so "LA" means the same thing everywhere.
 */
object SpanishAudioRegionClassifier {

    private val latinoCodes = setOf(
        "es-419", "es-005", "es-mx", "es-ar", "es-co", "es-cl", "es-pe", "es-ve",
        "es-ec", "es-gt", "es-cr", "es-pa", "es-do", "es-us", "es-la", "es-latam",
        "lat", "la", "latino", "latin"
    )
    private val castellanoCodes = setOf("es-es", "castellano", "spain")
    private val spanishCodes = setOf("spa", "es", "esl")

    private val latinoNameRegex = Regex(
        """(?i)(^|[\s._-])(la|lat|latam|latin|latino|latina|latinoamerica|latinoamérica|mx|mexico|méxico|arg|es-419|es-005)([\s._-]|$)"""
    )
    private val castellanoNameRegex = Regex(
        """(?i)(^|[\s._-])(eu|esp|españa|espana|spain|castellano|castilian)([\s._-]|$)"""
    )

    fun classify(
        language: String?,
        languageBcp47: String?,
        name: String?
    ): SpanishAudioRegion {
        val lang = language?.trim()?.lowercase(Locale.ROOT).orEmpty()
        val bcp = languageBcp47?.trim()?.lowercase(Locale.ROOT).orEmpty()
        val trackName = name?.lowercase(Locale.ROOT).orEmpty()

        val isSpanish = lang in spanishCodes ||
            bcp.startsWith("es") ||
            trackName.contains("español") ||
            trackName.contains("espanol") ||
            trackName.contains("spanish") ||
            trackName.contains("latino") ||
            trackName.contains("castellano")
        if (!isSpanish) return SpanishAudioRegion.OTHER

        val isLatino = bcp in latinoCodes ||
            lang in latinoCodes ||
            bcp.startsWith("es-419") ||
            bcp.startsWith("es-mx") ||
            bcp.startsWith("es-ar") ||
            bcp.startsWith("es-co") ||
            trackName.contains("latino") ||
            trackName.contains("latam") ||
            trackName.contains("latin america") ||
            trackName.contains("latinoamericano") ||
            trackName.contains("latinoamerica") ||
            trackName.contains("latinoamérica") ||
            trackName.contains("latina") ||
            trackName.contains("es-419") ||
            trackName.contains("audio latino") ||
            latinoNameRegex.containsMatchIn(trackName)
        if (isLatino) return SpanishAudioRegion.LATINO

        val isCastellano = bcp in castellanoCodes ||
            bcp.startsWith("es-es") ||
            trackName.contains("castellano") ||
            trackName.contains("españa") ||
            trackName.contains("espana") ||
            trackName.contains("castilian") ||
            castellanoNameRegex.containsMatchIn(trackName)
        if (isCastellano) return SpanishAudioRegion.CASTELLANO

        return SpanishAudioRegion.SPANISH
    }
}
