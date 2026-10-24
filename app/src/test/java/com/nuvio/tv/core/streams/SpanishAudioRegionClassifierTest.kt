package com.nuvio.tv.core.streams

import org.junit.Assert.assertEquals
import org.junit.Test

class SpanishAudioRegionClassifierTest {

    @Test
    fun `spanish track named LA is latino`() {
        assertEquals(
            SpanishAudioRegion.LATINO,
            SpanishAudioRegionClassifier.classify("spa", null, "BTM DDP5.1 LA")
        )
    }

    @Test
    fun `spanish track named EU is castellano`() {
        assertEquals(
            SpanishAudioRegion.CASTELLANO,
            SpanishAudioRegionClassifier.classify("spa", null, "BTM DDP5.1 EU")
        )
    }

    @Test
    fun `latin american bcp47 is latino`() {
        assertEquals(
            SpanishAudioRegion.LATINO,
            SpanishAudioRegionClassifier.classify("spa", "es-419", "Spanish")
        )
    }

    @Test
    fun `spain bcp47 is castellano`() {
        assertEquals(
            SpanishAudioRegion.CASTELLANO,
            SpanishAudioRegionClassifier.classify("spa", "es-ES", "Spanish")
        )
    }

    @Test
    fun `generic spanish stays unspecified`() {
        assertEquals(
            SpanishAudioRegion.SPANISH,
            SpanishAudioRegionClassifier.classify("spa", null, "BTM DD 5.1")
        )
    }

    @Test
    fun `english track named LA is not spanish`() {
        assertEquals(
            SpanishAudioRegion.OTHER,
            SpanishAudioRegionClassifier.classify("eng", null, "LA Commentary")
        )
    }

    @Test
    fun `name can identify latino without a language tag`() {
        assertEquals(
            SpanishAudioRegion.LATINO,
            SpanishAudioRegionClassifier.classify(null, null, "Español Latino")
        )
    }

    @Test
    fun `name can identify spain without a language tag`() {
        assertEquals(
            SpanishAudioRegion.CASTELLANO,
            SpanishAudioRegionClassifier.classify(null, null, "Español (España)")
        )
    }
}
