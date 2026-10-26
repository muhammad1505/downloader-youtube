package id.local.tubeflow.core

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BstationSeasonTest {
    private val seasonUrl = "https://www.bilibili.tv/play/2432940"

    private val seasonInfo = JSONObject(
        """
        {"code":0,"data":{"season":{
          "title":"Black Clover Season 2",
          "horizontal_cover":"https://pic.example/season.jpg"
        }}}
        """.trimIndent(),
    )

    private val episodes = JSONObject(
        """
        {"code":0,"data":{"sections":[
          {"title":"","episodes":[
            {"episode_id":"31524599","title_display":"E1 - The Battle Begins","short_title_display":"E1","long_title_display":"The Battle Begins","cover":"https://pic.example/e1.png","limit":0,"limit_text":""},
            {"episode_id":"31524678","title_display":"E2 - The Gate to Hell","short_title_display":"E2","long_title_display":"The Gate to Hell","cover":"https://pic.example/e2.png","limit":0,"limit_text":""},
            {"episode_id":"31524747","title_display":"E3 - Black Guardian","short_title_display":"E3","long_title_display":"Black Guardian","cover":"https://pic.example/e3.png","limit":0,"limit_text":""},
            {"episode_id":"31524861","title_display":"E4 - Day of Atonement","short_title_display":"E4","long_title_display":"Day of Atonement","cover":"https://pic.example/e4.png","limit":2,"limit_text":"Premium"}
          ]},
          {"title":"","episodes":[
            {"episode_id":"30838244","title_display":"PV","short_title_display":"PV","long_title_display":"","cover":"https://pic.example/pv.png","limit":0,"limit_text":""}
          ]}
        ]}}
        """.trimIndent(),
    )

    @Test
    fun catalogKeepsEpisodeOrderAndPremiumFlag() {
        val catalog = BstationSeason.parse(episodes, seasonInfo, seasonUrl, "2432940")

        assertEquals("Black Clover Season 2", catalog.title)
        assertEquals(seasonUrl, catalog.url)
        assertEquals("black-clover-season-2", catalog.folderName)
        assertEquals(5, catalog.entries.size)
        assertEquals("E01 - The Battle Begins", catalog.entries[0].title)
        assertEquals(1, catalog.entries[0].episodeNumber)
        assertEquals("https://www.bilibili.tv/play/2432940/31524599", catalog.entries[0].url)
        assertEquals("https://pic.example/e1.png", catalog.entries[0].thumbnail)
        assertFalse(catalog.entries[0].premium == true)
        assertEquals(4, catalog.entries[3].episodeNumber)
        assertTrue(catalog.entries[3].premium == true)
        // PV tidak punya nomor dan diletakkan setelah episode bernomor.
        assertEquals("PV", catalog.entries.last().title)
        assertNull(catalog.entries.last().episodeNumber)
    }

    @Test
    fun initialSelectionSkipsPremiumAndSpecials() {
        val catalog = BstationSeason.parse(episodes, seasonInfo, seasonUrl, "2432940")

        assertEquals(
            setOf("31524599", "31524678", "31524747"),
            BstationSeason.initialSelection(catalog.entries),
        )
    }

    @Test
    fun premiumEpisodesUnlockOnlyAfterLogin() {
        val catalog = BstationSeason.parse(episodes, seasonInfo, seasonUrl, "2432940")
        val free = catalog.entries.first { it.episodeNumber == 1 }
        val premium = catalog.entries.first { it.episodeNumber == 4 }

        assertFalse(BstationSeason.premiumLocked(free, signedIn = false))
        assertFalse(BstationSeason.premiumLocked(free, signedIn = true))
        assertTrue(BstationSeason.premiumLocked(premium, signedIn = false))
        assertFalse(BstationSeason.premiumLocked(premium, signedIn = true))
    }

    @Test
    fun missingSeasonInfoFallsBackToSeasonIdAndEpisodeCover() {
        val catalog = BstationSeason.parse(episodes, null, seasonUrl, "2432940")

        assertEquals("Bstation season 2432940", catalog.title)
        assertEquals("bstation-2432940", catalog.folderName)
        assertEquals("https://pic.example/e1.png", catalog.entries.first().thumbnail)
    }

    @Test
    fun malformedAndDuplicateEpisodesAreSkipped() {
        val json = JSONObject(
            """
            {"code":0,"data":{"sections":[{"episodes":[
              {"episode_id":"31524599","title_display":"E1 - One","short_title_display":"E1","limit":0},
              {"episode_id":"31524599","title_display":"E1 - Duplicate","short_title_display":"E1","limit":0},
              {"episode_id":"not-a-number","title_display":"Bad","short_title_display":"E9","limit":0}
            ]}]}}
            """.trimIndent(),
        )
        val catalog = BstationSeason.parse(json, null, seasonUrl, "2432940")
        assertEquals(1, catalog.entries.size)
        assertEquals("E01 - One", catalog.entries.single().title)
    }

    @Test(expected = BstationSeasonEmptyException::class)
    fun emptyCatalogIsRejected() {
        BstationSeason.parse(JSONObject("""{"code":0,"data":{"sections":[]}}"""), null, seasonUrl, "2432940")
    }

    @Test
    fun episodeNumbersAndTitlesAreNormalized() {
        assertEquals(7, BstationSeason.episodeNumber("E7", ""))
        assertEquals(12, BstationSeason.episodeNumber("", "Episode 12 - Title"))
        assertNull(BstationSeason.episodeNumber("PV", "PV"))
        assertEquals("E07 - Judul", BstationSeason.episodeTitle(7, "E7", "Judul", "E7 - Judul"))
        assertEquals("PV", BstationSeason.episodeTitle(null, "PV", "", "PV"))
    }
}
