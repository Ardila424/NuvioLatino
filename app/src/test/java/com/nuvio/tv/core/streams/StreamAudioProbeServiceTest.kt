package com.nuvio.tv.core.streams

import com.nuvio.tv.domain.model.Stream
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class StreamAudioProbeServiceTest {

    private fun newService() = StreamAudioProbeService(
        directDebridResolver = mockk(relaxed = true),
        okHttpClient = OkHttpClient()
    )

    private fun httpStream(url: String) = Stream(
        name = "Test stream",
        title = null,
        description = null,
        url = url,
        ytId = null,
        infoHash = null,
        fileIdx = null,
        externalUrl = null,
        behaviorHints = null,
        addonName = "test-addon",
        addonLogo = null
    )

    @Test
    fun `evaluateAudioTracks with es-419 BCP47 identifies latino and not castellano`() {
        val tracks = listOf(
            ProbeAudioTrack(language = "eng", languageBcp47 = "en", name = "Original", codec = "A_TRUEHD"),
            ProbeAudioTrack(language = "spa", languageBcp47 = "es-419", name = "BTM DD 5.1", codec = "A_AC3")
        )
        val result = newService().evaluateAudioTracks(tracks)
        assertTrue(result is StreamAudioProbeResult.Success)
        val success = result as StreamAudioProbeResult.Success
        assertTrue(success.hasLatino)
        assertFalse(success.hasCastellano)
        assertTrue(success.hasSpanish)
        assertTrue(success.detectedLanguages.contains("es-419"))
        assertTrue(success.detectedLanguages.contains("en"))
        assertEquals(tracks, success.tracks)
        assertEquals(ProbeProvenance.MKV_EBML, success.provenance)
    }

    @Test
    fun `evaluateAudioTracks with es-es BCP47 identifies castellano and not latino`() {
        val tracks = listOf(
            ProbeAudioTrack(language = "eng", languageBcp47 = "en", name = "Original", codec = "A_TRUEHD"),
            ProbeAudioTrack(language = "spa", languageBcp47 = "es-es", name = "Castellano 5.1", codec = "A_AC3")
        )
        val result = newService().evaluateAudioTracks(tracks)
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
        val result = newService().evaluateAudioTracks(tracks)
        assertTrue(result is StreamAudioProbeResult.Success)
        val success = result as StreamAudioProbeResult.Success
        assertFalse(success.hasLatino)
        assertFalse(success.hasCastellano)
        assertFalse(success.hasSpanish)
        assertEquals(listOf("en", "fr", "de"), success.detectedLanguages)
    }

    @Test
    fun `evaluateAudioTracks with empty tracks returns Inconclusive`() {
        assertEquals(StreamAudioProbeResult.Inconclusive, newService().evaluateAudioTracks(emptyList()))
    }

    @Test
    fun `parseMkvAudioTracks parses synthesized EBML Tracks element`() {
        val service = newService()
        val parsed = service.parseMkvAudioTracks(buildMkvTracksElement())
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

    @Test
    fun `probe returns verified MKV verdict from a header-only range request`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setResponseCode(206)
                    .setBody(Buffer().write(ebmlHeader + buildMkvTracksElement()))
            )

            val result = newService().probeStreamAudio(
                httpStream(server.url("/movie.mkv").toString())
            )

            assertTrue(result is StreamAudioProbeResult.Success)
            val success = result as StreamAudioProbeResult.Success
            assertTrue(success.hasLatino)
            assertEquals(ProbeProvenance.MKV_EBML, success.provenance)
            assertEquals(1, success.tracks.size)

            val request = server.takeRequest()
            assertEquals("bytes=0-262143", request.getHeader("Range"))
        }
    }

    @Test
    fun `probe returns Inconclusive for a non-container body`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody("this is not a media container")
            )

            val result = newService().probeStreamAudio(
                httpStream(server.url("/unknown.bin").toString())
            )

            assertEquals(StreamAudioProbeResult.Inconclusive, result)
        }
    }

    @Test
    fun `probe returns RateLimited on 429 and stops further requests during cooldown`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setResponseCode(429)
                    .setHeader("Retry-After", "30")
            )
            // Fallback in case the implementation wrongly retries instead of
            // respecting the cooldown (would otherwise block the test).
            server.enqueue(MockResponse().setResponseCode(200).setBody("nope"))

            val service = newService()
            val first = service.probeStreamAudio(httpStream(server.url("/a.mkv").toString()))

            assertTrue(first is StreamAudioProbeResult.RateLimited)
            val limited = first as StreamAudioProbeResult.RateLimited
            assertEquals(429, limited.httpCode)
            assertEquals(30_000L, limited.retryAfterMs)

            val second = service.probeStreamAudio(httpStream(server.url("/b.mkv").toString()))
            assertTrue(second is StreamAudioProbeResult.RateLimited)
            assertEquals(1, server.requestCount)
        }
    }

    @Test
    fun `probe returns RateLimited on 403 forbidden`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(403))

            val result = newService().probeStreamAudio(
                httpStream(server.url("/expired.mkv").toString())
            )

            assertTrue(result is StreamAudioProbeResult.RateLimited)
            assertEquals(403, (result as StreamAudioProbeResult.RateLimited).httpCode)
        }
    }

    @Test
    fun `probe caches terminal results and does not refetch`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setResponseCode(206)
                    .setBody(Buffer().write(ebmlHeader + buildMkvTracksElement()))
            )

            val service = newService()
            val stream = httpStream(server.url("/cached.mkv").toString())
            val first = service.probeStreamAudio(stream)
            val second = service.probeStreamAudio(stream)

            assertTrue(first is StreamAudioProbeResult.Success)
            assertTrue(second is StreamAudioProbeResult.Success)
            assertEquals(1, server.requestCount)
        }
    }

    private companion object {
        val ebmlHeader = byteArrayOf(0x1A, 0x45, 0xDF.toByte(), 0xA3.toByte())

        /**
         * Minimal Tracks element containing one audio TrackEntry:
         * TrackType=2, Language="spa", LanguageBCP47="es-419", Name="Latino".
         */
        fun buildMkvTracksElement(): ByteArray {
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
                write(0xAE)
                write(0x80 or trackEntryContent.size)
                write(trackEntryContent)
            }.toByteArray()

            return ByteArrayOutputStream().apply {
                write(byteArrayOf(0x16, 0x54, 0xAE.toByte(), 0x6B))
                write(0x80 or trackEntryBytes.size)
                write(trackEntryBytes)
            }.toByteArray()
        }
    }
}
