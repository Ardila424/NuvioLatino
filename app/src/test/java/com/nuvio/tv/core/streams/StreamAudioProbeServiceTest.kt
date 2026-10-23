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

    private class FakeAudioProbeCache : AudioProbeCache {
        val entries = mutableMapOf<String, StreamAudioProbeResult>()
        override suspend fun get(cacheKey: String): StreamAudioProbeResult? = entries[cacheKey]
        override suspend fun put(cacheKey: String, result: StreamAudioProbeResult) {
            entries[cacheKey] = result
        }
    }

    private fun newService(
        cache: AudioProbeCache = FakeAudioProbeCache()
    ) = StreamAudioProbeService(
        directDebridResolver = mockk(relaxed = true),
        okHttpClient = OkHttpClient(),
        audioProbeCache = cache
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
    fun `evaluateAudioTracks recognizes LA and EU region abbreviations on Spanish tracks`() {
        val tracks = listOf(
            ProbeAudioTrack(language = "spa", languageBcp47 = "", name = "BTM DDP5.1 LA", codec = "A_EAC3"),
            ProbeAudioTrack(language = "spa", languageBcp47 = "", name = "BTM DDP5.1 EU", codec = "A_EAC3")
        )
        val result = newService().evaluateAudioTracks(tracks)
        assertTrue(result is StreamAudioProbeResult.Success)
        val success = result as StreamAudioProbeResult.Success
        assertTrue(success.hasLatino)
        assertTrue(success.hasCastellano)
    }

    @Test
    fun `evaluateAudioTracks does not read LA or EU from non-Spanish tracks`() {
        val tracks = listOf(
            ProbeAudioTrack(language = "eng", languageBcp47 = "en", name = "LA Commentary", codec = "A_AAC"),
            ProbeAudioTrack(language = "fra", languageBcp47 = "fr", name = "EU Version", codec = "A_AAC")
        )
        val result = newService().evaluateAudioTracks(tracks)
        assertTrue(result is StreamAudioProbeResult.Success)
        val success = result as StreamAudioProbeResult.Success
        assertFalse(success.hasLatino)
        assertFalse(success.hasCastellano)
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

    @Test
    fun `second service instance reuses the persistent cache`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setResponseCode(206)
                    .setBody(Buffer().write(ebmlHeader + buildMkvTracksElement()))
            )
            // Fallback so a wrong second request fails fast instead of hanging.
            server.enqueue(MockResponse().setResponseCode(200).setBody("nope"))

            val cache = FakeAudioProbeCache()
            val stream = httpStream(server.url("/persisted.mkv").toString())

            val first = newService(cache).probeStreamAudio(stream)
            val second = newService(cache).probeStreamAudio(stream)

            assertTrue(first is StreamAudioProbeResult.Success)
            assertTrue(second is StreamAudioProbeResult.Success)
            assertEquals(1, server.requestCount)
            assertEquals(1, cache.entries.size)
        }
    }

    @Test
    fun `inconclusive results are persisted so they are not refetched`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(200).setBody("not a container"))

            val cache = FakeAudioProbeCache()
            val service = newService(cache)
            val stream = httpStream(server.url("/plain.bin").toString())

            val first = service.probeStreamAudio(stream)
            val second = service.probeStreamAudio(stream)

            assertEquals(StreamAudioProbeResult.Inconclusive, first)
            assertEquals(StreamAudioProbeResult.Inconclusive, second)
            assertEquals(1, server.requestCount)
        }
    }

    @Test
    fun `probe verifies HLS master playlist languages`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(
                        """
                        #EXTM3U
                        #EXT-X-MEDIA:TYPE=AUDIO,NAME="Latino",LANGUAGE="es-419",DEFAULT=YES
                        """.trimIndent()
                    )
            )

            val result = newService().probeStreamAudio(
                httpStream(server.url("/master.m3u8").toString())
            )

            assertTrue(result is StreamAudioProbeResult.Success)
            val success = result as StreamAudioProbeResult.Success
            assertTrue(success.hasLatino)
            assertEquals(ProbeProvenance.HLS_PLAYLIST, success.provenance)
        }
    }

    @Test
    fun `probe verifies faststart MP4 audio track`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setResponseCode(206)
                    .setBody(Buffer().write(mp4WithAudio(language = "spa", codec = "ac-3")))
            )

            val result = newService().probeStreamAudio(
                httpStream(server.url("/movie.mp4").toString())
            )

            assertTrue(result is StreamAudioProbeResult.Success)
            val success = result as StreamAudioProbeResult.Success
            assertTrue(success.hasCastellano)
            assertEquals(ProbeProvenance.MP4_MOOV, success.provenance)
            assertEquals("ac-3", success.tracks.single().codec)
        }
    }

    private fun mp4WithAudio(language: String, codec: String): ByteArray {
        fun u32(value: Int) = byteArrayOf(
            (value ushr 24).toByte(),
            (value ushr 16).toByte(),
            (value ushr 8).toByte(),
            value.toByte()
        )

        fun box(type: String, content: ByteArray): ByteArray =
            ByteArrayOutputStream().apply {
                write(u32(8 + content.size))
                write(type.toByteArray(Charsets.US_ASCII))
                write(content)
            }.toByteArray()

        fun fullBox(type: String, payload: ByteArray) =
            box(type, byteArrayOf(0, 0, 0, 0) + payload)

        val lang = ((language[0].code - 0x60) shl 10) or
            ((language[1].code - 0x60) shl 5) or
            (language[2].code - 0x60)
        val mdhd = fullBox(
            "mdhd",
            ByteArray(4) + ByteArray(4) + u32(1_000) + ByteArray(4) +
                byteArrayOf((lang shr 8).toByte(), lang.toByte())
        )
        val hdlr = fullBox(
            "hdlr",
            ByteArray(4) + "soun".toByteArray(Charsets.US_ASCII) + ByteArray(12) + byteArrayOf(0)
        )
        val stsd = fullBox("stsd", u32(1) + box(codec, ByteArray(8)))
        val mdia = box("mdia", mdhd + hdlr + box("minf", box("stbl", stsd)))

        return box(
            "ftyp",
            "isom".toByteArray(Charsets.US_ASCII) + u32(0x200) +
                "isomiso2".toByteArray(Charsets.US_ASCII)
        ) + box("moov", box("trak", mdia))
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
