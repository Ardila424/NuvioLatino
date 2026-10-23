package com.nuvio.tv.core.streams.parser

import com.nuvio.tv.core.streams.ProbeAudioTrack

/**
 * Parses an HLS **master** playlist for declared audio renditions.
 *
 * `#EXT-X-MEDIA:TYPE=AUDIO,LANGUAGE="es-419",NAME="Latino",DEFAULT=YES`
 *
 * Media playlists (no `EXT-X-MEDIA`) yield no tracks: the playlist simply does
 * not declare languages, so the caller must fall back to `Inconclusive` rather
 * than guessing.
 */
internal object HlsPlaylistAudioParser {

    fun isPlaylist(text: String): Boolean {
        val trimmed = text.trimStart('\uFEFF', ' ', '\t', '\r', '\n')
        return trimmed.startsWith("#EXTM3U", ignoreCase = true)
    }

    fun parse(text: String): List<ProbeAudioTrack> {
        if (!isPlaylist(text)) return emptyList()

        val tracks = mutableListOf<ProbeAudioTrack>()
        for (rawLine in text.lineSequence()) {
            val line = rawLine.trim()
            if (!line.startsWith("#EXT-X-MEDIA:", ignoreCase = true)) continue

            val attributes = parseAttributes(line.substringAfter(':'))
            val type = attributes["TYPE"]?.trim()?.uppercase()
            if (type != null && type != "AUDIO") continue

            val language = attributes["LANGUAGE"].orEmpty().trim()
            val name = attributes["NAME"].orEmpty().trim()
            val isDefault = attributes["DEFAULT"]?.equals("YES", ignoreCase = true) == true
            val isForced = attributes["FORCED"]?.equals("YES", ignoreCase = true) == true

            tracks += ProbeAudioTrack(
                language = language.substringBefore('-'),
                languageBcp47 = language,
                name = name,
                codec = "",
                isDefault = isDefault,
                isForced = isForced
            )
        }
        return tracks
    }

    /**
     * Attribute-list parser that respects quoted values (which may contain
     * commas, e.g. `NAME="Español, Latino"`).
     */
    internal fun parseAttributes(input: String): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        var index = 0
        while (index < input.length) {
            val equals = input.indexOf('=', index)
            if (equals == -1) break

            val key = input.substring(index, equals).trim().uppercase()
            var cursor = equals + 1
            if (cursor >= input.length) break

            val value: String
            if (input[cursor] == '"') {
                val closing = input.indexOf('"', cursor + 1)
                if (closing == -1) {
                    value = input.substring(cursor + 1)
                    cursor = input.length
                } else {
                    value = input.substring(cursor + 1, closing)
                    cursor = closing + 1
                }
            } else {
                val comma = input.indexOf(',', cursor).let { if (it == -1) input.length else it }
                value = input.substring(cursor, comma).trim()
                cursor = comma
            }

            if (key.isNotEmpty()) result[key] = value

            val nextComma = input.indexOf(',', cursor)
            if (nextComma == -1) break
            index = nextComma + 1
        }
        return result
    }
}
