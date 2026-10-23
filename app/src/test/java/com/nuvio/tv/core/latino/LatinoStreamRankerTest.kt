package com.nuvio.tv.core.latino

import com.nuvio.tv.domain.model.Stream
import com.nuvio.tv.domain.model.StreamBadge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LatinoStreamRankerTest {

    @Test
    fun `language tiers are ordered from verified latino to verified no spanish`() {
        val tiers = listOf(
            LatinoStreamRanker.languageTier(stream(badges = listOf(verifiedLatino()))),
            LatinoStreamRanker.languageTier(stream(badges = listOf(sinLatino("SPA, ENG")))),
            LatinoStreamRanker.languageTier(stream(name = "Movie Latino 1080p")),
            LatinoStreamRanker.languageTier(stream(name = "Movie Castellano 1080p")),
            LatinoStreamRanker.languageTier(stream(name = "Movie 1080p")),
            LatinoStreamRanker.languageTier(stream(badges = listOf(sinLatino("ENG, FRA"))))
        )

        assertEquals(tiers.sortedDescending(), tiers)
        assertEquals(tiers.distinct().size, tiers.size)
    }

    @Test
    fun `verified latino outranks a better quality unverified latino`() {
        val verified = LatinoStreamRanker.attributes(
            stream(name = "Movie 1080p", badges = listOf(verifiedLatino()))
        )
        val unverified = LatinoStreamRanker.attributes(
            stream(name = "Movie 2160p REMUX Latino")
        )

        assertTrue(verified.latinoScore > unverified.latinoScore)
    }

    @Test
    fun `unknown streams outrank verified no spanish streams`() {
        val unknown = LatinoStreamRanker.languageTier(stream(name = "Movie 1080p"))
        val noSpanish = LatinoStreamRanker.languageTier(
            stream(name = "Movie 1080p", badges = listOf(sinLatino("ENG")))
        )

        assertTrue(unknown > noSpanish)
    }

    @Test
    fun `quality tier orders remux above web-dl above plain`() {
        val remux = LatinoStreamRanker.attributes(
            stream(name = "Movie 2160p", quality = "REMUX", badges = listOf(verifiedLatino()))
        )
        val webDl = LatinoStreamRanker.attributes(
            stream(name = "Movie 2160p WEB-DL", badges = listOf(verifiedLatino()))
        )
        val plain = LatinoStreamRanker.attributes(
            stream(name = "Movie 2160p", badges = listOf(verifiedLatino()))
        )

        assertTrue(remux.latinoScore > webDl.latinoScore)
        assertTrue(webDl.latinoScore > plain.latinoScore)
    }

    @Test
    fun `higher resolution wins within the same quality tier`() {
        val uhd = LatinoStreamRanker.attributes(
            stream(name = "Movie 2160p WEB-DL", badges = listOf(verifiedLatino()))
        )
        val fullHd = LatinoStreamRanker.attributes(
            stream(name = "Movie 1080p WEB-DL", badges = listOf(verifiedLatino()))
        )

        assertTrue(uhd.resolutionScore > fullHd.resolutionScore)
        assertTrue(uhd.latinoScore > fullHd.latinoScore)
    }

    @Test
    fun `recognizes spanish codes inside a no-latino probe result`() {
        assertTrue(LatinoStreamRanker.isVerifiedNoLatinoWithSpanish(stream(badges = listOf(sinLatino("SPA, ENG")))))
        assertTrue(LatinoStreamRanker.isVerifiedNoLatinoWithSpanish(stream(badges = listOf(sinLatino("ES-419, ENG")))))
        assertTrue(LatinoStreamRanker.isVerifiedNoLatinoWithSpanish(stream(badges = listOf(sinLatino("ENG, ES)")))))
        assertFalse(LatinoStreamRanker.isVerifiedNoLatinoWithSpanish(stream(badges = listOf(sinLatino("ENG, FRA")))))
        assertFalse(LatinoStreamRanker.isVerifiedNoLatinoWithSpanish(stream(name = "Movie 1080p")))
    }

    @Test
    fun `hdr adds a small bonus`() {
        val hdr = LatinoStreamRanker.attributes(
            stream(name = "Movie 2160p WEB-DL HDR", badges = listOf(verifiedLatino()))
        )
        val sdr = LatinoStreamRanker.attributes(
            stream(name = "Movie 2160p WEB-DL", badges = listOf(verifiedLatino()))
        )

        assertTrue(hdr.hdrScore > 0)
        assertTrue(hdr.latinoScore > sdr.latinoScore)
    }

    private fun stream(
        name: String = "Movie 1080p",
        quality: String? = null,
        description: String? = null,
        badges: List<StreamBadge> = emptyList()
    ) = Stream(
        name = name,
        title = null,
        description = description,
        url = "http://host/${name.hashCode()}.mkv",
        ytId = null,
        infoHash = null,
        fileIdx = null,
        externalUrl = null,
        behaviorHints = null,
        addonName = "addon",
        addonLogo = null,
        quality = quality,
        badges = badges
    )

    private fun verifiedLatino() = StreamBadge(name = "🇲🇽 LATINO (Verificado)")

    private fun sinLatino(languages: String) = StreamBadge(name = "🚫 Sin Latino ($languages)")
}
