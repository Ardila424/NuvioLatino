package com.nuvio.tv.ui.screens.player

import com.nuvio.tv.domain.model.Subtitle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleSelectionOverlayVisibilityTest {

    private fun subtitle(id: String, lang: String) = Subtitle(
        id = id,
        url = "https://example.com/$id",
        lang = lang,
        addonName = "OpenSubtitles v3",
        addonLogo = null
    )

    private fun track(index: Int, language: String, name: String = "") = TrackInfo(
        index = index,
        name = name,
        language = language
    )

    @Test
    fun `latin american preference shows merged spanish and english only`() {
        val items = buildSubtitleLanguageRailItems(
            internalTracks = listOf(track(0, "spa", "Spanish (Latinoamérica)")),
            addonSubtitles = listOf(
                subtitle("1", "spa"),
                subtitle("2", "eng"),
                subtitle("3", "fre")
            ),
            preferredLanguage = "es-419",
            secondaryPreferredLanguage = "en",
            showOnlyPreferredLanguages = true,
            currentLanguageKey = "__off__",
            mergeSpanish = true,
            noneLabel = "None",
            unknownLabel = "Unknown"
        )

        assertEquals(listOf("__off__", "es", "en"), items.map { it.key })
        assertEquals(2, items.first { it.key == "es" }.count)
    }

    @Test
    fun `english preference is unaffected and lists all languages`() {
        val items = buildSubtitleLanguageRailItems(
            internalTracks = emptyList(),
            addonSubtitles = listOf(
                subtitle("1", "spa"),
                subtitle("2", "eng"),
                subtitle("3", "fre")
            ),
            preferredLanguage = "en",
            secondaryPreferredLanguage = null,
            showOnlyPreferredLanguages = true,
            currentLanguageKey = "__off__",
            mergeSpanish = false,
            noneLabel = "None",
            unknownLabel = "Unknown"
        )

        val keys = items.map { it.key }
        assertTrue("es" in keys)
        assertTrue("en" in keys)
        assertTrue("fr" in keys)
    }

    @Test
    fun `latin american preference without secondary shows all languages`() {
        val items = buildSubtitleLanguageRailItems(
            internalTracks = emptyList(),
            addonSubtitles = listOf(
                subtitle("1", "spa"),
                subtitle("2", "eng"),
                subtitle("3", "fre")
            ),
            preferredLanguage = "es-419",
            secondaryPreferredLanguage = null,
            showOnlyPreferredLanguages = true,
            currentLanguageKey = "__off__",
            mergeSpanish = false,
            noneLabel = "None",
            unknownLabel = "Unknown"
        )

        val keys = items.map { it.key }
        assertTrue("es" in keys)
        assertTrue("en" in keys)
        assertTrue("fr" in keys)
    }
}
