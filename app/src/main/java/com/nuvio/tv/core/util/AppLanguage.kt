package com.nuvio.tv.core.util

import android.content.res.Configuration
import com.nuvio.tv.LocaleCache
import java.util.Locale

/**
 * Single source of truth for the app's current language.
 *
 * The in-app Appearance language wins; otherwise we use the actual app
 * configuration (which also covers Android's per-app language), and only then
 * fall back to the device locale.
 */
object AppLanguage {

    fun effectiveTag(configuration: Configuration? = null): String {
        val override = LocaleCache.localeTag
            .takeIf { it.isNotBlank() && it != LocaleCache.UNSET }
        if (override != null) return override

        val configurationTag = configuration
            ?.locales
            ?.get(0)
            ?.toLanguageTag()
            ?.takeIf { it.isNotBlank() && it != "und" }
        if (configurationTag != null) return configurationTag

        return Locale.getDefault().toLanguageTag().ifBlank { "en" }
    }

    fun isSpanish(configuration: Configuration? = null): Boolean =
        effectiveTag(configuration).startsWith("es", ignoreCase = true)

    /**
     * Fork-specific filtering, verification and provenance badges are only
     * shown when the UI is Spanish. English keeps the normal Nuvio experience
     * plus the technical quality/audio badges.
     */
    fun latinoFeaturesEnabled(configuration: Configuration? = null): Boolean =
        isSpanish(configuration)
}
