package com.nuvio.tv.ui.components

import com.nuvio.tv.core.streams.NativeStreamBadgeDetector
import com.nuvio.tv.domain.model.Stream
import com.nuvio.tv.domain.model.StreamBadge

/**
 * Badges exactly as the card should render them: probe/imported badges first,
 * then anything the filename/description detector can add. Keeps the stream
 * list and the in-player source panel in sync.
 */
fun Stream.displayBadges(showLatinoBadges: Boolean): List<StreamBadge> {
    val native = NativeStreamBadgeDetector.detectBadges(this)
    val allBadges = if (native.isEmpty()) {
        badges
    } else {
        val merged = LinkedHashMap<String, StreamBadge>(badges.size + native.size)
        badges.forEach { merged[it.name] = it }
        native.forEach { merged.putIfAbsent(it.name, it) }
        merged.values.toList()
    }

    val hasVerifiedLatino = allBadges.any { it.name.contains("LATINO (Verificado)") }
    val hasNoLatino = allBadges.any { it.name.contains("Sin Latino", ignoreCase = true) }
    val cleaned = allBadges.filterNot { badge ->
        (hasNoLatino &&
            badge.name.contains("LATINO", ignoreCase = true) &&
            !badge.name.contains("Sin Latino", ignoreCase = true)) ||
            (hasVerifiedLatino &&
                badge.name.contains("LATINO", ignoreCase = true) &&
                !badge.name.contains("(Verificado)") &&
                !badge.name.contains("Sin Latino", ignoreCase = true))
    }

    return if (showLatinoBadges) cleaned else cleaned.filterNot { it.isLatinoProvenanceBadge() }
}

fun Stream.hasVerifiedLatinoBadge(): Boolean =
    displayBadges(showLatinoBadges = true).any { it.name.contains("LATINO (Verificado)") }

private fun StreamBadge.isLatinoProvenanceBadge(): Boolean =
    name.contains("LATINO", ignoreCase = true) ||
        name.contains("CASTELLANO", ignoreCase = true) ||
        name.contains("ESPAÑOL", ignoreCase = true) ||
        name.contains("ESPANOL", ignoreCase = true) ||
        name.contains("SPANISH", ignoreCase = true)
