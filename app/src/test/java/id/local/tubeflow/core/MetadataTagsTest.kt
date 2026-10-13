package id.local.tubeflow.core

import org.junit.Assert.*
import org.junit.Test

class MetadataTagsTest {
    @Test fun unknownArtistIsExplicitlyClearedWhileCreatorAndRemixerRemainSeparate() {
        val arguments = MetadataTags.arguments(SongIdentity("JAUH KO PERGI", null, "DJ", "Upload channel", "Maman Fvndy Rmx"))
        assertTrue(arguments.contains("TubeFlow=:(?s)^TubeFlow=(?P<meta_artist>.*)$"))
        assertTrue(arguments.contains("TubeFlow=Upload channel:(?s)^TubeFlow=(?P<meta_creator>.*)$"))
        assertTrue(arguments.contains("TubeFlow=Maman Fvndy Rmx:(?s)^TubeFlow=(?P<meta_remixer>.*)$"))
    }
    @Test fun trackTitleAndProvenArtistUseExplicitOutputOverrides() {
        val arguments = MetadataTags.arguments(SongIdentity("PINGAL", "DENNY CAKNAN", "Live"))
        assertTrue(arguments.contains("TubeFlow=PINGAL | Live:(?s)^TubeFlow=(?P<meta_title>.*)$"))
        assertTrue(arguments.contains("TubeFlow=DENNY CAKNAN:(?s)^TubeFlow=(?P<meta_artist>.*)$"))
    }
    @Test fun titleTemplateCharactersRemainLiteral() {
        val arguments = MetadataTags.arguments(SongIdentity("Love: 100%", "A"))
        assertTrue(arguments.contains("TubeFlow=Love\\: 100%%:(?s)^TubeFlow=(?P<meta_title>.*)$"))
    }
}
