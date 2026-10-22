package com.nuvio.tv.ui.screens.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleVisibilityPolicyTest {

    @Test
    fun `show only disabled returns null`() {
        assertNull(SubtitleVisibilityPolicy.visibleSubtitleTargets(false, "es-419", "en"))
    }

    @Test
    fun `english preferred returns null`() {
        assertNull(SubtitleVisibilityPolicy.visibleSubtitleTargets(true, "en", null))
        assertNull(SubtitleVisibilityPolicy.visibleSubtitleTargets(true, "en", "es"))
    }

    @Test
    fun `generic spanish preferred returns null`() {
        assertNull(SubtitleVisibilityPolicy.visibleSubtitleTargets(true, "es", "en"))
    }

    @Test
    fun `latin american spanish with english secondary filters to spanish and english`() {
        assertEquals(
            listOf("es", "en"),
            SubtitleVisibilityPolicy.visibleSubtitleTargets(true, "es-419", "en")
        )
    }

    @Test
    fun `latin american spanish without secondary returns null`() {
        assertNull(SubtitleVisibilityPolicy.visibleSubtitleTargets(true, "es-419", null))
        assertNull(SubtitleVisibilityPolicy.visibleSubtitleTargets(true, "es-419", ""))
        assertNull(SubtitleVisibilityPolicy.visibleSubtitleTargets(true, "es-419", "none"))
    }

    @Test
    fun `latin american spanish with french secondary filters to spanish and french`() {
        assertEquals(
            listOf("es", "fr"),
            SubtitleVisibilityPolicy.visibleSubtitleTargets(true, "es-419", "fr")
        )
    }

    @Test
    fun `human readable latin american preference is recognized`() {
        assertEquals(
            listOf("es", "en"),
            SubtitleVisibilityPolicy.visibleSubtitleTargets(true, "Spanish (Latin America)", "en")
        )
    }

    @Test
    fun `mergesSpanishFamily flags only the spanish family`() {
        assertTrue(SubtitleVisibilityPolicy.mergesSpanishFamily(listOf("es", "en")))
        assertFalse(SubtitleVisibilityPolicy.mergesSpanishFamily(null))
        assertFalse(SubtitleVisibilityPolicy.mergesSpanishFamily(listOf("en", "fr")))
    }
}
