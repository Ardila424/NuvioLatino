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
        assertTrue(
            badges.any {
                it.name == "🇲🇽 LATINO (Verificado)" && it.tagStyle == "latino-name"
            }
        )
    }

    @Test
    fun `detects castellano from filename`() {
        val stream = createStream(
            filename = "Interstellar.2014.1080p.Castellano.DTS-HD.mkv"
        )
        assertTrue(NativeStreamBadgeDetector.isCastellano(stream))
        assertFalse(NativeStreamBadgeDetector.isLatino(stream))
        val badges = NativeStreamBadgeDetector.detectBadges(stream)
        assertTrue(badges.any { it.name == "🇪🇸 CASTELLANO" })
    }

    @Test
    fun `detects multi audio when not latino or castellano`() {
        val stream = createStream(
            filename = "Dune.Part.Two.2024.1080p.Multi.Audio.x265.mkv"
        )
        assertTrue(NativeStreamBadgeDetector.isMulti(stream))
        val badges = NativeStreamBadgeDetector.detectBadges(stream)
        assertTrue(badges.any { it.name == "🌐 MULTI" })
    }

    @Test
    fun `does not tag remux as latino when languages explicitly say english and russian`() {
        val stream = createStream(
            filename = "Spider-Man.Brand.New.Day.2026.2160p.IMAX.UHDRemux.HYBRID.HDR.DV-TheEqualizer.mkv",
            videoSize = 86L * 1024L * 1024L * 1024L,
            description = "Quality: BluRay REMUX\nLanguages: English, Russian | Subtitles: English, Russian"
        )
        assertTrue(NativeStreamBadgeDetector.isRemux(stream))
        assertTrue(NativeStreamBadgeDetector.is4K(stream))
        assertFalse(NativeStreamBadgeDetector.isLatino(stream))
        assertFalse(NativeStreamBadgeDetector.isCastellano(stream))
        assertFalse(NativeStreamBadgeDetector.isSpanishOrLatino(stream))

        val badges = NativeStreamBadgeDetector.detectBadges(stream)
        assertTrue(badges.any { it.name == "💎 REMUX" })
        assertFalse(badges.any { it.name.contains("LATINO") })
    }

    @Test
    fun `does not tag remux as latino when flags are english and czech`() {
        val stream = createStream(
            filename = "Project.Hail.Mary.2026.UHD.BluRay.2160p.TrueHD.Atmos.7.1.DV.HDR10P.HEVC.HYBRID.REMUX-FraMeSToR.mkv",
            videoSize = 79L * 1024L * 1024L * 1024L,
            description = "🌐 / 🇬🇧 / 🇨🇿"
        )
        assertTrue(NativeStreamBadgeDetector.isRemux(stream))
        assertFalse(NativeStreamBadgeDetector.isLatino(stream))
        assertFalse(NativeStreamBadgeDetector.isSpanishOrLatino(stream))

        val badges = NativeStreamBadgeDetector.detectBadges(stream)
        assertTrue(badges.any { it.name == "💎 REMUX" })
        assertFalse(badges.any { it.name.contains("LATINO") })
    }

    @Test
    fun `detects latino from mexican flag in description`() {
        val stream = createStream(
            filename = "Project.Hail.Mary.2026.UHD.BluRay.2160p.REMUX.mkv",
            videoSize = 79L * 1024L * 1024L * 1024L,
            description = "🌐 / 🇬🇧 / 🇲🇽"
        )
        assertTrue(NativeStreamBadgeDetector.isLatino(stream))
        assertTrue(NativeStreamBadgeDetector.isSpanishOrLatino(stream))
        val badges = NativeStreamBadgeDetector.detectBadges(stream)
        assertTrue(badges.any { it.name == "💎 REMUX" })
        assertTrue(badges.any { it.name == "🇲🇽 LATINO" })
        assertFalse(badges.any { it.name.contains("(Verificado)") })
    }

    @Test
    fun `does not mark latino or castellano from subtitle text only`() {
        val stream = createStream(
            filename = "Movie.2024.1080p.WEB-DL.mkv",
            description = "Subtitles: Latino, Spanish, English"
        )
        assertFalse(NativeStreamBadgeDetector.isLatino(stream))
        assertFalse(NativeStreamBadgeDetector.isCastellano(stream))
        assertFalse(NativeStreamBadgeDetector.hasExplicitLatinoMarker(stream))
        val names = NativeStreamBadgeDetector.detectBadges(stream).map { it.name }
        assertFalse(names.any { it.contains("LATINO") || it.contains("CASTELLANO") })
    }

    @Test
    fun `explicit latino audio with Spanish subtitles does not add castellano`() {
        val stream = createStream(
            filename = "Movie.2024.1080p.WEB-DL.mkv",
            description = "Languages: Latino, English | Subtitles: Spanish, English"
        )
        val names = NativeStreamBadgeDetector.detectBadges(stream).map { it.name }
        assertTrue(names.any { it == "🇲🇽 LATINO (Verificado)" })
        assertFalse(names.any { it.contains("CASTELLANO") })
    }

    @Test
    fun `explicit latino and explicit castellano audio show both`() {
        val stream = createStream(
            filename = "Movie.2024.1080p.WEB-DL.mkv",
            description = "Languages: Latino, Castellano, English | Subtitles: English"
        )
        val names = NativeStreamBadgeDetector.detectBadges(stream).map { it.name }
        assertTrue(names.any { it == "🇲🇽 LATINO (Verificado)" })
        assertTrue(names.any { it == "🇪🇸 CASTELLANO" })
    }

    @Test
    fun `mexican flag alone stays likely latino not verified`() {
        val stream = createStream(
            filename = "Movie.2024.1080p.WEB-DL.mkv",
            description = "🌐 / 🇬🇧 / 🇲🇽"
        )
        assertTrue(NativeStreamBadgeDetector.isLatino(stream))
        assertFalse(NativeStreamBadgeDetector.hasExplicitLatinoMarker(stream))
        val names = NativeStreamBadgeDetector.detectBadges(stream).map { it.name }
        assertTrue(names.any { it == "🇲🇽 LATINO" })
        assertFalse(names.any { it.contains("(Verificado)") })
    }

    @Test
    fun `explicit MX marker promotes to name verified`() {
        val stream = createStream(
            filename = "Movie.2024.1080p.DDP5.1.MX.x264.mkv"
        )
        val badge = NativeStreamBadgeDetector.detectBadges(stream)
            .first { it.name.contains("LATINO") }
        assertEquals("latino-name", badge.tagStyle)
    }

    @Test
    fun `does not false positive latino on words like latest or format`() {
        val stream = createStream(
            filename = "Some.Movie.2024.Latest.Edition.Format.mkv"
        )
        assertFalse(NativeStreamBadgeDetector.isLatino(stream))
    }

    @Test
    fun `sorting comparator orders latino first and size descending`() {
        val smallLatino = createStream(
            filename = "Movie.1080p.Dual-Latino.mkv",
            videoSize = 10L * 1024L * 1024L * 1024L // 10 GB
        )
        val bigLatino = createStream(
            filename = "Movie.2160p.UHD.Remux.Latino.mkv",
            videoSize = 80L * 1024L * 1024L * 1024L // 80 GB
        )
        val bigCastellano = createStream(
            filename = "Movie.2160p.Castellano.Remux.mkv",
            videoSize = 75L * 1024L * 1024L * 1024L // 75 GB
        )
        val smallCastellano = createStream(
            filename = "Movie.1080p.Castellano.mkv",
            videoSize = 8L * 1024L * 1024L * 1024L // 8 GB
        )

        val streams = listOf(smallCastellano, smallLatino, bigCastellano, bigLatino)
        val comparator = compareByDescending<Stream> { NativeStreamBadgeDetector.isLatino(it) }
            .thenByDescending { NativeStreamBadgeDetector.isCastellano(it) }
            .thenByDescending { com.nuvio.tv.core.debrid.StreamTextSizeParser.effectiveSizeBytes(it) ?: 0L }

        val sorted = streams.sortedWith(comparator)
        assertEquals(listOf(bigLatino, smallLatino, bigCastellano, smallCastellano), sorted)
    }

    @Test
    fun `detects latino from latin alternative titles`() {
        val stream = createStream(
            filename = "Spider-Man.Sin.Camino.A.Casa.2021.2160p.UHD.Remux.mkv"
        )
        val latinTitles = listOf("Sin camino a casa", "Spider-Man: Sin camino a casa")
        assertTrue(NativeStreamBadgeDetector.isLatino(stream, latinTitles))
        val badges = NativeStreamBadgeDetector.detectBadges(stream, latinTitles)
        assertTrue(badges.any { it.name == "🇲🇽 LATINO" })
    }

    @Test
    fun `verified latino badge renders LATINO (Verificado)`() {
        val stream = createStream(
            filename = "Movie.2024.2160p.Remux.mkv"
        ).copy(
            badges = listOf(
                com.nuvio.tv.domain.model.StreamBadge(
                    name = "🇲🇽 LATINO (Verificado)",
                    tagColor = "#059669",
                    textColor = "#FFFFFF",
                    borderColor = "#10B981"
                )
            )
        )
        assertTrue(NativeStreamBadgeDetector.isLatino(stream))
        val badges = NativeStreamBadgeDetector.detectBadges(stream)
        assertTrue(badges.any { it.name == "🇲🇽 LATINO (Verificado)" })
        assertFalse(badges.any { it.name == "🇲🇽 LATINO" }) // No duplicate plain badge
    }

    @Test
    fun `sin latino badge suppresses latino detection and preserves Sin Latino badge`() {
        val stream = createStream(
            filename = "Movie.2024.Dual-Latino.2160p.Remux.mkv"
        ).copy(
            badges = listOf(
                com.nuvio.tv.domain.model.StreamBadge(
                    name = "🚫 Sin Latino (ENG, RUS)",
                    tagColor = "#4B5563",
                    textColor = "#D1D5DB",
                    borderColor = "#6B7280"
                )
            )
        )
        assertFalse(NativeStreamBadgeDetector.isLatino(stream))
        val badges = NativeStreamBadgeDetector.detectBadges(stream)
        assertFalse(badges.any { it.name.contains("🇲🇽 LATINO") })
        assertTrue(badges.any { it.name == "🚫 Sin Latino (ENG, RUS)" })
    }

    @Test
    fun `verified latino badge suppresses castellano on MULTi remux`() {
        val stream = createStream(
            filename = "Project.Hail.Mary.2026.2160p.UHD.BluRay.Remux.DV.P7.HDR.MULTi.TrueHD.Atmos-BTM.mkv"
        ).copy(
            badges = listOf(
                com.nuvio.tv.domain.model.StreamBadge(
                    name = "🇲🇽 LATINO (Verificado)",
                    tagColor = "#059669",
                    textColor = "#FFFFFF",
                    borderColor = "#10B981"
                )
            )
        )
        assertTrue(NativeStreamBadgeDetector.isLatino(stream))
        assertFalse(NativeStreamBadgeDetector.isCastellano(stream))
        val badges = NativeStreamBadgeDetector.detectBadges(stream)
        assertTrue(badges.any { it.name == "💎 REMUX" })
        assertTrue(badges.any { it.name == "🇲🇽 LATINO (Verificado)" })
        assertFalse(badges.any { it.name == "🇲🇽 LATINO" })
    }

    @Test
    fun `castellano badge is preserved alongside verified latino`() {
        val stream = createStream(
            filename = "Movie.2024.Castellano.2160p.Remux.mkv"
        ).copy(
            badges = listOf(
                com.nuvio.tv.domain.model.StreamBadge(
                    name = "🇲🇽 LATINO (Verificado)",
                    tagColor = "#059669",
                    textColor = "#FFFFFF",
                    borderColor = "#10B981"
                )
            )
        )
        assertTrue(NativeStreamBadgeDetector.isLatino(stream))
        assertTrue(NativeStreamBadgeDetector.isCastellano(stream))
        val badges = NativeStreamBadgeDetector.detectBadges(stream)
        assertTrue(badges.any { it.name == "🇲🇽 LATINO (Verificado)" })
        assertTrue(badges.any { it.name == "🇪🇸 CASTELLANO" })
    }

    @Test
    fun `castellano badge is preserved when sin latino badge is present`() {
        val stream = createStream(
            filename = "Movie.2024.Castellano.2160p.Remux.mkv"
        ).copy(
            badges = listOf(
                com.nuvio.tv.domain.model.StreamBadge(
                    name = "🚫 Sin Latino (ENG, FRA)",
                    tagColor = "#4B5563",
                    textColor = "#D1D5DB",
                    borderColor = "#6B7280"
                )
            )
        )
        assertFalse(NativeStreamBadgeDetector.isLatino(stream))
        assertTrue(NativeStreamBadgeDetector.isCastellano(stream))
        val badges = NativeStreamBadgeDetector.detectBadges(stream)
        assertTrue(badges.any { it.name.startsWith("🚫 Sin Latino") })
        assertTrue(badges.any { it.name == "🇪🇸 CASTELLANO" })
    }

    @Test
    fun `detects technical badges from release name`() {
        val stream = createStream(
            filename = "Project.Hail.Mary.2026.2160p.UHD.BluRay.REMUX.DV.HDR10.HEVC.TrueHD.7.1.Atmos-FraMeSToR.mkv"
        )
        val names = NativeStreamBadgeDetector.detectBadges(stream).map { it.name }
        assertTrue("4K" in names)
        assertTrue("DV" in names)
        assertTrue("HDR10" in names)
        assertTrue("ATMOS" in names)
        assertTrue("7.1" in names)
        assertTrue("HEVC" in names)
        assertFalse("1080p" in names)
    }

    @Test
    fun `detects technical badges from addon display name and description`() {
        val stream = createStream(filename = "Project.Hail.Mary.2026.2160p.UHD.BluRay.Remux.DV.P7.HDR.MULTi.TrueHD.Atmos.H265-BTM.mkv")
            .copy(
                name = "[TB ⚡] Latinobrid",
                description = "🟫 REMUX · ✨ DV HDR · 📁 80.4 GB · 🌐 Latino · 📁\n" +
                    "Project.Hail.Mary.2026.2160p.UHD.BluRay.Remux.DV.P7.HDR.MULTi.TrueHD.Atmos.H265-BTM"
            )
        val names = NativeStreamBadgeDetector.detectBadges(stream).map { it.name }
        assertTrue(names.any { it.contains("REMUX") })
        assertTrue("4K" in names)
        assertTrue("DV" in names)
        assertTrue("HDR" in names)
    }

    @Test
    fun `does not report 4K or HDR for plain 1080p SDR release`() {
        val stream = createStream(
            filename = "Movie.2024.1080p.WEB-DL.AAC.2.0.x264-GROUP.mkv"
        )
        val names = NativeStreamBadgeDetector.detectBadges(stream).map { it.name }
        assertTrue("1080p" in names)
        assertTrue("AAC" in names)
        assertFalse("4K" in names)
        assertFalse("HDR" in names)
        assertFalse("HDR10" in names)
        assertFalse("DV" in names)
    }

    private fun createStream(
        filename: String,
        videoSize: Long? = null,
        description: String? = null
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
            description = description ?: filename,
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
