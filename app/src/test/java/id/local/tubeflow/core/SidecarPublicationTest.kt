package id.local.tubeflow.core

import org.junit.Assert.assertEquals
import org.junit.Test

class SidecarPublicationTest {

    @Test
    fun subtitleLanguageSuffixIsKeptWithThePublishedMediaStem() {
        val publishedStem = "E01 - The Battle Begins"
        val suffix = SidecarPublication.suffix("media", "media.id.srt")
        assertEquals(".id", suffix)
        assertEquals(
            "E01 - The Battle Begins.id.srt",
            SidecarPublication.requestedName(publishedStem, suffix, "srt"),
        )
    }

    @Test
    fun plainSidecarsUseThePublishedStemWithoutAnExtraSuffix() {
        val suffix = SidecarPublication.suffix("media", "media.srt")
        assertEquals("", suffix)
        assertEquals(
            "E01 - The Battle Begins.srt",
            SidecarPublication.requestedName("E01 - The Battle Begins", suffix, "srt"),
        )
    }

    @Test
    fun regionalLanguageTagsSurvive() {
        val suffix = SidecarPublication.suffix("media", "media.zh-Hant.ass")
        assertEquals(".zh-Hant", suffix)
        assertEquals(
            "Episode 1.zh-Hant.ass",
            SidecarPublication.requestedName("Episode 1", suffix, "ass"),
        )
    }

    @Test
    fun unrelatedSidecarNameDoesNotBecomePartOfTheTitle() {
        val suffix = SidecarPublication.suffix("media", "subtitle.srt")
        assertEquals("", suffix)
        assertEquals(
            "Episode 1.srt",
            SidecarPublication.requestedName("Episode 1", suffix, "srt"),
        )
    }

    @Test
    fun malformedSuffixesAreIgnored() {
        assertEquals("", SidecarPublication.suffix("media", "media..srt"))
        assertEquals("", SidecarPublication.suffix("media", "media"))
    }
}
