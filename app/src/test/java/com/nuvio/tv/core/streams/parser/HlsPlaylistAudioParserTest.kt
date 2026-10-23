package com.nuvio.tv.core.streams.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HlsPlaylistAudioParserTest {

    @Test
    fun `parses audio renditions from a master playlist`() {
        val playlist = """
            #EXTM3U
            #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="aud",NAME="Latino",LANGUAGE="es-419",DEFAULT=YES,AUTOSELECT=YES
            #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="aud",NAME="Castellano",LANGUAGE="es-ES",DEFAULT=NO
            #EXT-X-STREAM-INF:BANDWIDTH=5000000,RESOLUTION=1920x1080,AUDIO="aud"
            index.m3u8
        """.trimIndent()

        val tracks = HlsPlaylistAudioParser.parse(playlist)

        assertEquals(2, tracks.size)
        assertEquals("es-419", tracks[0].languageBcp47)
        assertEquals("es", tracks[0].language)
        assertEquals("Latino", tracks[0].name)
        assertTrue(tracks[0].isDefault)
        assertEquals("es-ES", tracks[1].languageBcp47)
        assertEquals("Castellano", tracks[1].name)
        assertTrue(!tracks[1].isDefault)
    }

    @Test
    fun `keeps commas inside quoted attribute values`() {
        val playlist = """
            #EXTM3U
            #EXT-X-MEDIA:TYPE=AUDIO,NAME="Español, Latino",LANGUAGE="es-419"
        """.trimIndent()

        val tracks = HlsPlaylistAudioParser.parse(playlist)

        assertEquals(1, tracks.size)
        assertEquals("Español, Latino", tracks[0].name)
    }

    @Test
    fun `skips non audio media entries`() {
        val playlist = """
            #EXTM3U
            #EXT-X-MEDIA:TYPE=SUBTITLES,GROUP-ID="subs",NAME="Spanish",LANGUAGE="es"
            #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="aud",NAME="English",LANGUAGE="en"
        """.trimIndent()

        val tracks = HlsPlaylistAudioParser.parse(playlist)

        assertEquals(1, tracks.size)
        assertEquals("en", tracks[0].language)
    }

    @Test
    fun `media playlists without EXT-X-MEDIA yield no tracks`() {
        val playlist = """
            #EXTM3U
            #EXT-X-VERSION:3
            #EXT-X-TARGETDURATION:6
            #EXTINF:6.0,
            segment0.ts
        """.trimIndent()

        assertTrue(HlsPlaylistAudioParser.parse(playlist).isEmpty())
    }

    @Test
    fun `non playlists yield no tracks`() {
        assertTrue(HlsPlaylistAudioParser.parse("not a playlist").isEmpty())
        assertTrue(HlsPlaylistAudioParser.parse("").isEmpty())
    }

    @Test
    fun `recognizes playlists with a BOM or leading whitespace`() {
        assertTrue(HlsPlaylistAudioParser.isPlaylist("\uFEFF#EXTM3U\n"))
        assertTrue(HlsPlaylistAudioParser.isPlaylist("\n  #EXTM3U\n"))
    }

    @Test
    fun `parses attribute lists`() {
        val attrs = HlsPlaylistAudioParser.parseAttributes(
            "TYPE=AUDIO,GROUP-ID=\"aud\",NAME=\"Latino, 5.1\",DEFAULT=YES"
        )

        assertEquals("AUDIO", attrs["TYPE"])
        assertEquals("aud", attrs["GROUP-ID"])
        assertEquals("Latino, 5.1", attrs["NAME"])
        assertEquals("YES", attrs["DEFAULT"])
    }
}
