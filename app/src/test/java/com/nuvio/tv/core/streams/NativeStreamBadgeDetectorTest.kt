package com.nuvio.tv.core.streams

import com.nuvio.tv.domain.model.Stream
import com.nuvio.tv.domain.model.StreamBehaviorHints
import com.nuvio.tv.domain.model.StreamClientResolve
import com.nuvio.tv.domain.model.StreamClientResolveParsed
import com.nuvio.tv.domain.model.StreamClientResolveRaw
import com.nuvio.tv.domain.model.StreamClientResolveStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeStreamBadgeDetectorTest {

    @Test
    fun `detects remux from filename`() {
        val stream = createStream(
            filename = "Spider-Man.No.Way.Home.2021.2160p.UHD.BluRay.REMUX.DV.HDR.HEVC.TrueHD.7.1.Atmos-FraMeSToR.mkv"
        )
        assertTrue(NativeStreamBadgeDetector.isRemux(stream))
        assertTrue(NativeStreamBadgeDetector.is4K(stream))
    }

    @Test
    fun `detects latino from filename`() {
        val stream = createStream(
            filename = "Avatar.The.Way.of.Water.2022.1080p.Dual-Latino.x264.mkv"
        )
        assertTrue(NativeStreamBadgeDetector.isLatino(stream))
        assertFalse(NativeStreamBadgeDetector.isRemux(stream))
        val badges = NativeStreamBadgeDetector.detectBadges(stream)
        assertEquals(listOf("🇲🇽 LATINO"), badges.map { it.name })
    }

    @Test
    fun `detects castellano from filename`() {
        val stream = createStream(
            filename = "Interstellar.2014.1080p.Castellano.DTS-HD.mkv"
        )
        assertTrue(NativeStreamBadgeDetector.isCastellano(stream))
        assertFalse(NativeStreamBadgeDetector.isLatino(stream))
        val badges = NativeStreamBadgeDetector.detectBadges(stream)
        assertEquals(listOf("🇪🇸 CASTELLANO"), badges.map { it.name })
    }

    @Test
    fun `detects multi audio when not latino or castellano`() {
        val stream = createStream(
            filename = "Dune.Part.Two.2024.1080p.Multi.Audio.x265.mkv"
        )
        assertTrue(NativeStreamBadgeDetector.isMulti(stream))
        val badges = NativeStreamBadgeDetector.detectBadges(stream)
        assertEquals(listOf("🌐 MULTI"), badges.map { it.name })
    }

    @Test
    fun `detects remux and probable latino on large 4k uhd remux`() {
        val stream = createStream(
            filename = "Avengers.Endgame.2019.2160p.BluRay.REMUX.HEVC.DTS-HD.MA.7.1.Atmos-FGT.mkv",
            videoSize = 65L * 1024L * 1024L * 1024L // 65 GB
        )
        assertTrue(NativeStreamBadgeDetector.isRemux(stream))
        assertTrue(NativeStreamBadgeDetector.is4K(stream))
        assertFalse(NativeStreamBadgeDetector.isLatino(stream))
        assertTrue(NativeStreamBadgeDetector.isProbableUhdRemuxLatino(stream))
        assertTrue(NativeStreamBadgeDetector.isSpanishOrLatino(stream))

        val badges = NativeStreamBadgeDetector.detectBadges(stream)
        assertEquals(listOf("💎 REMUX", "🇲🇽 Latino en disco"), badges.map { it.name })
    }

    @Test
    fun `does not false positive probable latino on small web-dl`() {
        val stream = createStream(
            filename = "Spider-Man.Brand.New.Day.2026.2160p.iT.WEB-DL.DDP5.1.Atmos.DV.HDR.H.265.HUN.ENG-PTHD.mkv",
            videoSize = 25L * 1024L * 1024L * 1024L
        )
        assertFalse(NativeStreamBadgeDetector.isRemux(stream))
        assertFalse(NativeStreamBadgeDetector.isProbableUhdRemuxLatino(stream))
        assertFalse(NativeStreamBadgeDetector.isSpanishOrLatino(stream))
    }

    @Test
    fun `does not false positive latino on words like latest or format`() {
        val stream = createStream(
            filename = "Some.Movie.2024.Latest.Edition.Format.mkv"
        )
        assertFalse(NativeStreamBadgeDetector.isLatino(stream))
    }

    private fun createStream(
        filename: String,
        videoSize: Long? = null
    ): Stream {
        val raw = StreamClientResolveRaw(
            torrentName = filename,
            filename = filename,
            size = videoSize,
            folderSize = videoSize,
            tracker = null,
            indexer = null,
            network = null,
            parsed = null
        )
        val resolve = StreamClientResolve(
            type = "debrid",
            infoHash = "abc123",
            fileIdx = 0,
            magnetUri = null,
            sources = null,
            torrentName = filename,
            filename = filename,
            mediaType = "movie",
            mediaId = "tt123",
            mediaOnlyId = "tt123",
            title = filename,
            season = null,
            episode = null,
            service = "torbox",
            serviceIndex = 0,
            serviceExtension = "TB",
            isCached = true,
            stream = StreamClientResolveStream(raw = raw)
        )
        return Stream(
            name = filename,
            title = filename,
            description = filename,
            url = null,
            ytId = null,
            infoHash = null,
            fileIdx = null,
            externalUrl = null,
            behaviorHints = StreamBehaviorHints(
                notWebReady = null,
                bingeGroup = null,
                countryWhitelist = null,
                proxyHeaders = null,
                videoSize = videoSize,
                filename = filename
            ),
            addonName = "Comet",
            addonLogo = null,
            clientResolve = resolve
        )
    }
}
