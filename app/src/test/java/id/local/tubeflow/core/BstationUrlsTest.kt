package id.local.tubeflow.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BstationUrlsTest {

    private val episode = "https://www.bilibili.tv/id/play/2432940/31524599?bstar_from=bstar-web.pgc-video-detail.episode.all"

    @Test
    fun episodeIdentityDropsLocaleAndTrackingQuery() {
        val canonical = "https://www.bilibili.tv/play/2432940/31524599"
        assertEquals(canonical, BstationUrls.canonical(episode))
        assertEquals(canonical, BstationUrls.canonical("https://bilibili.tv/en/play/2432940/31524599/"))
        assertEquals(canonical, BstationUrls.canonical("https://biliintl.com/id/play/2432940/31524599"))
        // m.bilibili.tv bukan host yang dikenali extractor BiliIntl upstream.
        assertNull(BstationUrls.canonical("https://m.bilibili.tv/play/2432940/31524599"))
    }

    @Test
    fun ugcVideoIsSupportedToo() {
        assertEquals(
            "https://www.bilibili.tv/video/2045730385",
            BstationUrls.canonical("https://www.bilibili.tv/en/video/2045730385?spm=test"),
        )
    }

    @Test
    fun seasonPagesAreDetectedAndHaveNoCanonicalEpisodeIdentity() {
        listOf(
            "https://www.bilibili.tv/id/play/2432940",
            "https://www.bilibili.tv/id/play/2432940/",
            "https://www.bilibili.tv/en/media/1048837",
            "https://www.bilibili.tv/id/play/2432940?bstar_from=season",
        ).forEach {
            assertTrue(it, BstationUrls.isSeasonOnly(it))
            assertNull(BstationUrls.canonical(it))
        }
        assertFalse(BstationUrls.isSeasonOnly(episode))
        assertFalse(BstationUrls.isSeasonOnly("https://www.bilibili.tv/en/video/2045730385"))
    }

    @Test
    fun seasonPagesHaveCanonicalCollectionIdentity() {
        assertEquals(
            "https://www.bilibili.tv/play/2432940",
            BstationUrls.canonicalSeason("https://www.bilibili.tv/id/play/2432940?bstar_from=season"),
        )
        assertEquals(
            "https://www.bilibili.tv/play/1048837",
            BstationUrls.canonicalSeason("https://biliintl.com/en/media/1048837/"),
        )
        assertEquals("2432940", BstationUrls.seasonId("https://www.bilibili.tv/id/play/2432940"))
        assertNull(BstationUrls.canonicalSeason(episode))
        assertNull(BstationUrls.canonicalSeason("https://www.bilibili.tv.evil.org/play/2432940"))
    }

    @Test
    fun seriesEpisodeHelpersStayInsidePlayPaths() {
        assertEquals(
            "https://www.bilibili.tv/play/2432940/31524599",
            BstationUrls.episodeUrl("2432940", "31524599"),
        )
        assertNull(BstationUrls.episodeUrl("2432940", "not-a-number"))
        assertTrue(BstationUrls.isSeriesEpisode(episode))
        assertFalse(BstationUrls.isSeriesEpisode("https://www.bilibili.tv/en/video/2045730385"))
        assertFalse(BstationUrls.isSeriesEpisode("https://www.bilibili.tv/id/play/2432940"))
    }

    @Test
    fun hostGateDoesNotAcceptLookalikes() {
        assertTrue(BstationUrls.isHost("bilibili.tv"))
        assertTrue(BstationUrls.isHost("www.bilibili.tv"))
        assertTrue(BstationUrls.isHost("biliintl.com"))
        assertFalse(BstationUrls.isHost("bilibili.tv.evil.org"))
        assertFalse(BstationUrls.isHost("evilbilibili.tv"))
        assertFalse(BstationUrls.isHost("bilibili.com"))
        assertFalse(BstationUrls.isHost(null))
    }

    @Test
    fun unsafeOrIncompleteUrlsStayUnsupported() {
        listOf(
            "http://www.bilibili.tv/id/play/2432940/31524599",
            "https://user@www.bilibili.tv/id/play/2432940/31524599",
            "https://www.bilibili.tv:8443/id/play/2432940/31524599",
            "https://www.bilibili.tv/id/play/2432940/not-a-number",
            "https://www.bilibili.tv/id/play/2432940/31524599/extra",
            "https://www.bilibili.tv/id/anime",
            "https://www.bilibili.tv.evil.org/id/play/2432940/31524599",
        ).forEach {
            assertNull(it, BstationUrls.canonical(it))
            assertFalse(it, BstationUrls.isSeasonOnly(it))
        }
    }
}
