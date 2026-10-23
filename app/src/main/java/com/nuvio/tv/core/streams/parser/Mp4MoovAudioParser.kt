package com.nuvio.tv.core.streams.parser

import com.nuvio.tv.core.streams.ProbeAudioTrack

/**
 * Minimal ISO-BMFF (MP4/MOV/M4V) audio-track parser.
 *
 * Works on a byte window (head or tail of the file): it locates the `moov`
 * box by fourcc rather than assuming it starts at offset 0, so it also handles
 * non-faststart files where `moov` lives at the end.
 *
 * Per audio `trak` it reads:
 * - `mdia/hdlr`      → handler type must be `soun`
 * - `mdia/mdhd`      → ISO-639-2 language (e.g. `spa`)
 * - `mdia/elng`      → BCP-47 language when present (e.g. `es-419`)
 * - `mdia/minf/stbl/stsd` → first sample entry fourcc as the codec
 */
internal object Mp4MoovAudioParser {

    fun parse(bytes: ByteArray): List<ProbeAudioTrack> {
        val moov = findBox(bytes, "moov") ?: return emptyList()
        val tracks = mutableListOf<ProbeAudioTrack>()

        for (trak in children(bytes, moov)) {
            if (trak.type != "trak") continue
            val mdia = children(bytes, trak).firstOrNull { it.type == "mdia" } ?: continue
            val mdiaChildren = children(bytes, mdia)

            val hdlr = mdiaChildren.firstOrNull { it.type == "hdlr" }
            val handler = hdlr?.let { handlerType(bytes, it) }
            if (handler != "soun") continue

            val language = mdiaChildren.firstOrNull { it.type == "mdhd" }
                ?.let { languageCode(bytes, it) }
                .orEmpty()
            val bcp47 = mdiaChildren.firstOrNull { it.type == "elng" }
                ?.let { extendedLanguage(bytes, it) }
                .orEmpty()

            val minf = mdiaChildren.firstOrNull { it.type == "minf" }
            val codec = minf
                ?.let { children(bytes, it).firstOrNull { child -> child.type == "stbl" } }
                ?.let { stbl -> children(bytes, stbl).firstOrNull { it.type == "stsd" } }
                ?.let { stsd -> firstSampleEntryFormat(bytes, stsd) }
                .orEmpty()

            tracks += ProbeAudioTrack(
                language = language,
                languageBcp47 = bcp47,
                name = "",
                codec = codec
            )
        }
        return tracks
    }

    // ---------------------------------------------------------------------
    // Box model
    // ---------------------------------------------------------------------

    private data class Box(
        val type: String,
        val start: Int,
        val contentStart: Int,
        val contentEnd: Int
    )

    private fun findBox(bytes: ByteArray, type: String): Box? {
        val needle = type.toByteArray(Charsets.US_ASCII)
        var i = 4
        while (i + needle.size <= bytes.size) {
            var matches = true
            for (j in needle.indices) {
                if (bytes[i + j] != needle[j]) {
                    matches = false
                    break
                }
            }
            if (matches) {
                val boxStart = i - 4
                val box = boxAt(bytes, boxStart)
                if (box != null && box.type == type) return box
            }
            i++
        }
        return null
    }

    private fun boxAt(bytes: ByteArray, start: Int): Box? {
        if (start < 0 || start + 8 > bytes.size) return null
        var size = readUInt32(bytes, start)
        var headerSize = 8
        if (size == 1L) {
            if (start + 16 > bytes.size) return null
            size = readUInt64(bytes, start + 8)
            headerSize = 16
        } else if (size == 0L) {
            size = (bytes.size - start).toLong()
        }
        if (size < headerSize || size > Int.MAX_VALUE) return null
        val contentStart = start + headerSize
        if (contentStart > bytes.size) return null
        val declaredEnd = start + size
        val contentEnd = if (declaredEnd > bytes.size || declaredEnd < start) bytes.size
        else declaredEnd.toInt()
        val type = fourcc(bytes, start + 4) ?: return null
        return Box(type, start, contentStart, contentEnd)
    }

    private fun children(bytes: ByteArray, parent: Box): List<Box> {
        val result = mutableListOf<Box>()
        var pos = parent.contentStart
        while (pos + 8 <= parent.contentEnd) {
            val box = boxAt(bytes, pos) ?: break
            if (box.start != pos || box.contentEnd <= pos) break
            result += box
            // Stop when the declared size runs past the window (truncated box).
            if (box.contentEnd >= parent.contentEnd) break
            pos = box.contentEnd
        }
        return result
    }

    // ---------------------------------------------------------------------
    // Field readers
    // ---------------------------------------------------------------------

    private fun handlerType(bytes: ByteArray, hdlr: Box): String? {
        // full box: version/flags (4) + pre_defined (4) + handler_type (4)
        val handlerOffset = hdlr.contentStart + 8
        if (handlerOffset + 4 > hdlr.contentEnd) return null
        return fourcc(bytes, handlerOffset)
    }

    private fun languageCode(bytes: ByteArray, mdhd: Box): String {
        if (mdhd.contentStart + 4 > mdhd.contentEnd) return ""
        val version = bytes[mdhd.contentStart].toInt() and 0xFF
        val languageOffset = if (version == 1) {
            mdhd.contentStart + 4 + 8 + 8 + 4 + 8
        } else {
            mdhd.contentStart + 4 + 4 + 4 + 4 + 4
        }
        if (languageOffset + 2 > mdhd.contentEnd) return ""
        val raw = ((bytes[languageOffset].toInt() and 0xFF) shl 8) or
            (bytes[languageOffset + 1].toInt() and 0xFF)
        val decoded = decodeIso639(raw)
        return if (decoded == "und") "" else decoded
    }

    private fun extendedLanguage(bytes: ByteArray, elng: Box): String {
        // full box: version/flags (4) + language string (NUL or box-end terminated)
        val start = elng.contentStart + 4
        if (start >= elng.contentEnd) return ""
        var end = start
        while (end < elng.contentEnd && bytes[end] != 0.toByte()) end++
        return String(bytes, start, end - start, Charsets.UTF_8).trim()
    }

    private fun firstSampleEntryFormat(bytes: ByteArray, stsd: Box): String? {
        // full box: version/flags (4) + entry_count (4) + sample entries
        val entriesStart = stsd.contentStart + 8
        if (entriesStart + 8 > stsd.contentEnd) return null
        // First sample entry: size (4) + format (4)
        return fourcc(bytes, entriesStart + 4)
    }

    private fun decodeIso639(raw: Int): String {
        if (raw == 0) return ""
        val c1 = ((raw shr 10) and 0x1F) + 0x60
        val c2 = ((raw shr 5) and 0x1F) + 0x60
        val c3 = (raw and 0x1F) + 0x60
        val code = String(charArrayOf(c1.toChar(), c2.toChar(), c3.toChar()))
        return if (code.all { it in 'a'..'z' }) code else ""
    }

    private fun fourcc(bytes: ByteArray, offset: Int): String? {
        if (offset + 4 > bytes.size) return null
        return String(bytes, offset, 4, Charsets.US_ASCII)
    }

    private fun readUInt32(bytes: ByteArray, offset: Int): Long {
        if (offset + 4 > bytes.size) return -1L
        return ((bytes[offset].toLong() and 0xFF) shl 24) or
            ((bytes[offset + 1].toLong() and 0xFF) shl 16) or
            ((bytes[offset + 2].toLong() and 0xFF) shl 8) or
            (bytes[offset + 3].toLong() and 0xFF)
    }

    private fun readUInt64(bytes: ByteArray, offset: Int): Long {
        if (offset + 8 > bytes.size) return -1L
        var value = 0L
        for (i in 0 until 8) {
            // Guard against overflow for absurd sizes; 2^56 bytes is plenty.
            if (value > (Long.MAX_VALUE shr 8)) return Long.MAX_VALUE
            value = (value shl 8) or (bytes[offset + i].toLong() and 0xFF)
        }
        return value
    }
}
