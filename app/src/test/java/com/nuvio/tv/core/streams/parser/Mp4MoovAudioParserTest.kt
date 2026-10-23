package com.nuvio.tv.core.streams.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class Mp4MoovAudioParserTest {

    @Test
    fun `parses a faststart mp4 audio track`() {
        val bytes = ftyp() + box("moov", trak(handler = "soun", language = "spa", codec = "ac-3"))

        val tracks = Mp4MoovAudioParser.parse(bytes)

        assertEquals(1, tracks.size)
        assertEquals("spa", tracks[0].language)
        assertEquals("", tracks[0].languageBcp47)
        assertEquals("ac-3", tracks[0].codec)
    }

    @Test
    fun `parses a moov box located at the tail of the window`() {
        val padding = ByteArray(1_024) { 0x55 }
        val bytes = padding + box("moov", trak(handler = "soun", language = "eng", codec = "mp4a"))

        val tracks = Mp4MoovAudioParser.parse(bytes)

        assertEquals(1, tracks.size)
        assertEquals("eng", tracks[0].language)
        assertEquals("mp4a", tracks[0].codec)
    }

    @Test
    fun `reads the elng bcp47 language when present`() {
        val bytes = ftyp() + box(
            "moov",
            trak(handler = "soun", language = "spa", codec = "ec-3", bcp47 = "es-419")
        )

        val tracks = Mp4MoovAudioParser.parse(bytes)

        assertEquals(1, tracks.size)
        assertEquals("spa", tracks[0].language)
        assertEquals("es-419", tracks[0].languageBcp47)
    }

    @Test
    fun `supports version 1 mdhd boxes`() {
        val bytes = ftyp() + box(
            "moov",
            trak(handler = "soun", language = "spa", codec = "ac-3", mdhdVersion = 1)
        )

        val tracks = Mp4MoovAudioParser.parse(bytes)

        assertEquals(1, tracks.size)
        assertEquals("spa", tracks[0].language)
    }

    @Test
    fun `supports 64 bit moov box sizes`() {
        val bytes = ftyp() + box64("moov", trak(handler = "soun", language = "spa", codec = "ac-3"))

        val tracks = Mp4MoovAudioParser.parse(bytes)

        assertEquals(1, tracks.size)
        assertEquals("spa", tracks[0].language)
    }

    @Test
    fun `ignores video tracks`() {
        val bytes = ftyp() + box("moov", trak(handler = "vide", language = "und", codec = "avc1"))

        assertTrue(Mp4MoovAudioParser.parse(bytes).isEmpty())
    }

    @Test
    fun `returns empty when there is no moov`() {
        val bytes = ftyp()
        assertTrue(Mp4MoovAudioParser.parse(bytes).isEmpty())
    }

    @Test
    fun `returns empty for malformed bytes`() {
        assertTrue(Mp4MoovAudioParser.parse(ByteArray(0)).isEmpty())
        assertTrue(Mp4MoovAudioParser.parse(byteArrayOf(1, 2, 3)).isEmpty())
    }

    // ---------------------------------------------------------------------
    // Builders
    // ---------------------------------------------------------------------

    private fun ftyp(): ByteArray =
        box("ftyp", "isom".toByteArray(Charsets.US_ASCII) + u32(0x200) + "isomiso2".toByteArray(Charsets.US_ASCII))

    private fun trak(
        handler: String,
        language: String,
        codec: String,
        bcp47: String? = null,
        mdhdVersion: Int = 0
    ): ByteArray {
        val mdiaContent = ByteArrayOutputStream().apply {
            write(mdhd(language, mdhdVersion))
            if (bcp47 != null) write(fullBox("elng", bcp47.toByteArray(Charsets.UTF_8)))
            write(hdlr(handler))
            write(box("minf", box("stbl", stsd(codec))))
        }.toByteArray()
        return box("trak", box("mdia", mdiaContent))
    }

    private fun mdhd(language: String, version: Int): ByteArray {
        val languageBytes = encodeLanguage(language)
        return if (version == 1) {
            fullBox(
                "mdhd",
                ByteArray(8) + ByteArray(8) + u32(1_000) + ByteArray(8) + languageBytes,
                version = 1
            )
        } else {
            fullBox("mdhd", ByteArray(4) + ByteArray(4) + u32(1_000) + ByteArray(4) + languageBytes)
        }
    }

    private fun hdlr(handler: String): ByteArray =
        fullBox(
            "hdlr",
            ByteArray(4) + handler.toByteArray(Charsets.US_ASCII) + ByteArray(12) + byteArrayOf(0)
        )

    private fun stsd(format: String): ByteArray =
        fullBox("stsd", u32(1) + box(format, ByteArray(8)))

    private fun encodeLanguage(code: String): ByteArray {
        val value = ((code[0].code - 0x60) shl 10) or
            ((code[1].code - 0x60) shl 5) or
            (code[2].code - 0x60)
        return byteArrayOf((value shr 8).toByte(), value.toByte())
    }

    private fun box(type: String, content: ByteArray): ByteArray =
        ByteArrayOutputStream().apply {
            write(u32(8 + content.size))
            write(type.toByteArray(Charsets.US_ASCII))
            write(content)
        }.toByteArray()

    private fun box64(type: String, content: ByteArray): ByteArray =
        ByteArrayOutputStream().apply {
            write(u32(1))
            write(type.toByteArray(Charsets.US_ASCII))
            write(u32(0))
            write(u32(16 + content.size))
            write(content)
        }.toByteArray()

    private fun fullBox(type: String, payload: ByteArray, version: Int = 0): ByteArray =
        box(type, byteArrayOf(version.toByte(), 0, 0, 0) + payload)

    private fun u32(value: Int): ByteArray = byteArrayOf(
        (value ushr 24).toByte(),
        (value ushr 16).toByte(),
        (value ushr 8).toByte(),
        value.toByte()
    )
}
