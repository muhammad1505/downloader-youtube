package id.local.tubeflow.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitlePickerTest {
    private fun meta(platform: String, langs: List<String>) = VideoMetadata(
        title = "E1 - The Battle Begins", uploader = "", durationSeconds = 0, thumbnail = "",
        url = "https://www.bilibili.tv/play/2432940/31524599", subtitleLangs = langs, platformId = platform,
    )

    @Test fun youtubeKeepsOriginalLanguageConcept() {
        val video = meta("youtube", listOf("en", "id"))
        val options = SubtitlePicker.options(video)
        assertEquals("asli", options.first().first)
        assertEquals("Bahasa asli", options.first().second)
        assertTrue(options.any { it.first == "en" && it.second == "Inggris" })
        assertTrue(options.any { it.first == "id" && it.second == "Indonesia" })
        assertEquals(".*", options.last().first)
        assertEquals("asli", SubtitlePicker.defaultSelection(video, "id"))
    }

    @Test fun bstationListsOnlyLanguagesTheSourceProvides() {
        val video = meta(BstationUrls.PLATFORM_ID, listOf("en", "th", "id"))
        val options = SubtitlePicker.options(video)
        assertEquals(listOf("en", "th", "id", ".*"), options.map { it.first })
        assertEquals(listOf("Inggris", "Thai", "Indonesia", "Semua bahasa"), options.map { it.second })
        assertFalse(options.any { it.first == SubtitlePicker.ORIGINAL })
    }

    @Test fun defaultFollowsDeviceLanguageThenFirstAvailable() {
        val video = meta(BstationUrls.PLATFORM_ID, listOf("en", "th", "id"))
        assertEquals("id", SubtitlePicker.defaultSelection(video, "id"))
        assertEquals("id", SubtitlePicker.defaultSelection(video, "in")) // kode lama Java untuk Indonesia
        assertEquals("en", SubtitlePicker.defaultSelection(video, "ja"))
        assertEquals("en", SubtitlePicker.defaultSelection(video, ""))
    }

    @Test fun unknownSourceWithoutReportedLanguagesStillOffersCommonChoices() {
        val video = meta("tiktok", emptyList())
        assertEquals(listOf("id", "en", ".*"), SubtitlePicker.options(video).map { it.first })
        assertEquals("id", SubtitlePicker.defaultSelection(video, ""))
    }

    @Test fun labelsFallBackToBaseLanguageOrTheCodeItself() {
        assertEquals("Portugis", SubtitlePicker.label("pt-BR"))
        assertEquals("Indonesia", SubtitlePicker.label("id"))
        assertEquals("XX", SubtitlePicker.label("xx"))
        assertEquals("bahasa asli", SubtitlePicker.describe(SubtitlePicker.ORIGINAL))
        assertEquals("semua bahasa", SubtitlePicker.describe(SubtitlePicker.ALL))
        assertEquals("", SubtitlePicker.describe(""))
    }

    @Test fun subtitleOptionsAcceptRealLanguageTags() {
        listOf("th", "pt-BR", "jv", "zh-Hant").forEach { assertTrue(it, Selection.validSubtitleChoice(it)) }
        listOf("id; rm -rf /", "Bahasa", "", "asli", ".*").forEach {
            assertEquals(it, it in setOf("", "asli", ".*"), Selection.validSubtitleChoice(it))
        }
        Selection.validate(DownloadOptions("https://www.bilibili.tv/play/2432940/31524599", subtitles = "th"))
    }
}
