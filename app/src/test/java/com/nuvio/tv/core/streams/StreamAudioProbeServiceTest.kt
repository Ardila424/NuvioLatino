package com.nuvio.tv.core.streams

import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class StreamAudioProbeServiceTest {

    @Test
    fun `evaluateAudioTracks with es-419 BCP47 identifies latino and not castellano`() {
        val tracks = listOf(
            ProbeAudioTrack(language = "eng", languageBcp47 = "en", name = "Original", codec = "A_TRUEHD"),
            ProbeAudioTrack(language = "spa", languageBcp47 = "es-419", name = "BTM DD 5.1", codec = "A_AC3")
        )
        val service = StreamAudioProbeService(
            directDebridResolver = mockk(relaxed = true),
            okHttpClient = okhttp3.OkHttpClient()
        )
        val result = service.evaluateAudioTracks(tracks)
        assertTrue(result is StreamAudioProbeResult.Success)
        val success = result as StreamAudioProbeResult.Success
        assertTrue(success.hasLatino)
        assertFalse(success.hasCastellano)
        assertTrue(success.hasSpanish)
        assertTrue(success.detectedLanguages.contains("es-419"))
        assertTrue(success.detectedLanguages.contains("en"))
    }

    @Test
    fun `evaluateAudioTracks with es-es BCP47 identifies castellano and not latino`() {
        val tracks = listOf(
            ProbeAudioTrack(language = "eng", languageBcp47 = "en", name = "Original", codec = "A_TRUEHD"),
            ProbeAudioTrack(language = "spa", languageBcp47 = "es-es", name = "Castellano 5.1", codec = "A_AC3")
        )
        val service = StreamAudioProbeService(
            directDebridResolver = mockk(relaxed = true),
            okHttpClient = okhttp3.OkHttpClient()
        )
        val result = service.evaluateAudioTracks(tracks)
        assertTrue(result is StreamAudioProbeResult.Success)
        val success = result as StreamAudioProbeResult.Success
        assertFalse(success.hasLatino)
        assertTrue(success.hasCastellano)
        assertTrue(success.hasSpanish)
    }

    @Test
    fun `evaluateAudioTracks with non-spanish languages identifies no spanish`() {
        val tracks = listOf(
            ProbeAudioTrack(language = "eng", languageBcp47 = "en", name = "English", codec = "A_TRUEHD"),
            ProbeAudioTrack(language = "fra", languageBcp47 = "fr", name = "French", codec = "A_AC3"),
            ProbeAudioTrack(language = "deu", languageBcp47 = "de", name = "German", codec = "A_AC3")
        )
        val service = StreamAudioProbeService(
            directDebridResolver = mockk(relaxed = true),
            okHttpClient = okhttp3.OkHttpClient()
        )
        val result = service.evaluateAudioTracks(tracks)
        assertTrue(result is StreamAudioProbeResult.Success)
        val success = result as StreamAudioProbeResult.Success
        assertFalse(success.hasLatino)
        assertFalse(success.hasCastellano)
        assertFalse(success.hasSpanish)
        assertEquals(listOf("en", "fr", "de"), success.detectedLanguages)
    }

    @Test
    fun `evaluateAudioTracks with empty tracks returns Inconclusive`() {
        val service = StreamAudioProbeService(
            directDebridResolver = mockk(relaxed = true),
            okHttpClient = okhttp3.OkHttpClient()
        )
        val result = service.evaluateAudioTracks(emptyList())
        assertEquals(StreamAudioProbeResult.Inconclusive, result)
    }

    @Test
    fun `parseMkvAudioTracks parses synthesized EBML Tracks element`() {
        val service = StreamAudioProbeService(
            directDebridResolver = mockk(relaxed = true),
            okHttpClient = okhttp3.OkHttpClient()
        )

        // Build a minimal TrackEntry:
        // TrackType = 2 (audio): 0x83 0x01 0x02
        // Language = "spa": 0x22 0xB5 0x9C 0x03 's' 'p' 'a'
        // LanguageBCP47 = "es-419": 0x22 0xB5 0x9D 0x06 'e' 's' '-' '4' '1' '9'
        // Name = "Latino": 0x53 0x6E 0x06 'L' 'a' 't' 'i' 'n' 'o'
        val trackEntryContent = ByteArrayOutputStream().apply {
            write(byteArrayOf(0x83.toByte(), 0x81.toByte(), 0x02.toByte()))
            write(byteArrayOf(0x22.toByte(), 0xB5.toByte(), 0x9C.toByte(), 0x83.toByte()))
            write("spa".toByteArray(Charsets.ISO_8859_1))
            write(byteArrayOf(0x22.toByte(), 0xB5.toByte(), 0x9D.toByte(), 0x86.toByte()))
            write("es-419".toByteArray(Charsets.UTF_8))
            write(byteArrayOf(0x53.toByte(), 0x6E.toByte(), 0x86.toByte()))
            write("Latino".toByteArray(Charsets.UTF_8))
        }.toByteArray()

        val trackEntryBytes = ByteArrayOutputStream().apply {
            write(0xAE.toByte().toInt())
            write(0x80 or trackEntryContent.size) // 1-byte vint
            write(trackEntryContent)
        }.toByteArray()

        val tracksMaster = ByteArrayOutputStream().apply {
            write(byteArrayOf(0x16.toByte(), 0x54.toByte(), 0xAE.toByte(), 0x6B.toByte()))
            write(0x80 or trackEntryBytes.size) // 1-byte vint
            write(trackEntryBytes)
        }.toByteArray()

        val parsed = service.parseMkvAudioTracks(tracksMaster)
        assertEquals(1, parsed.size)
        val track = parsed[0]
        assertEquals("spa", track.language)
        assertEquals("es-419", track.languageBcp47)
        assertEquals("Latino", track.name)

        val eval = service.evaluateAudioTracks(parsed)
        assertTrue(eval is StreamAudioProbeResult.Success)
        val success = eval as StreamAudioProbeResult.Success
        assertTrue(success.hasLatino)
        assertFalse(success.hasCastellano)
    }
}
