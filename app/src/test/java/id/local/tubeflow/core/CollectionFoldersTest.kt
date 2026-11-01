package id.local.tubeflow.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CollectionFoldersTest {
    private val music = "content://com.android.externalstorage.documents/tree/primary%3AMusic"
    private val sdCard = "content://com.android.externalstorage.documents/tree/1A2B-3C4D%3AMusic"

    @Test
    fun treeUriIsNormalizedWithoutTrailingSlash() {
        assertEquals(music, CollectionFolders.normalize("$music/"))
        assertEquals("", CollectionFolders.normalize("   "))
    }

    @Test
    fun documentIdDecodesPercentEncodedTreePath() {
        assertEquals("primary:Music", CollectionFolders.documentId(music))
        assertEquals("1A2B-3C4D:Music", CollectionFolders.documentId(sdCard))
        assertNull(CollectionFolders.documentId("content://media/external/audio/media/42"))
    }

    @Test
    fun labelForIdNamesVolumeWithoutInventingFiles() {
        assertEquals("Music/Lagu", CollectionFolders.labelForId("primary:Music/Lagu"))
        assertEquals("SD card/Music", CollectionFolders.labelForId("1A2B-3C4D:Music"))
        assertEquals("SD card/Music", CollectionFolders.labelForId("raw:/storage/1A2B-3C4D/Music"))
        assertEquals("Music", CollectionFolders.labelForId("raw:/storage/emulated/0/Music/"))
        assertEquals(CollectionFolders.FALLBACK_LABEL, CollectionFolders.labelForId("   "))
    }

    @Test
    fun duplicateLabelsGetSuffixSoTwoFoldersStayApart() {
        assertEquals("Music", CollectionFolders.uniqueLabel("Music", emptySet()))
        assertEquals("Music (2)", CollectionFolders.uniqueLabel("Music", setOf("Music")))
        assertEquals("Music (3)", CollectionFolders.uniqueLabel("Music", setOf("Music", "Music (2)")))
        assertEquals(CollectionFolders.FALLBACK_LABEL, CollectionFolders.uniqueLabel("  ", setOf("Lain")))
    }

    @Test
    fun overlapDetectsParentAndChildButNotSiblings() {
        assertTrue(CollectionFolders.overlaps("primary:Music", "primary:Music"))
        assertTrue(CollectionFolders.overlaps("primary:Music/Lagu", "primary:Music"))
        assertTrue(CollectionFolders.overlaps("primary:Music", "primary:Music/Lagu"))
        assertFalse(CollectionFolders.overlaps("primary:Music", "primary:Movies"))
        assertFalse(CollectionFolders.overlaps("", "primary:Music"))
    }

    @Test
    fun sourcesListDefaultThenOutputThenExtrasWithUniqueLabels() {
        val sources = CollectionFolders.sources(
            defaultTree = "content://com.android.externalstorage.documents/tree/primary%3ADownload%2FTubeFlow",
            defaultLabel = "Download/TubeFlow",
            outputTree = "content://com.android.externalstorage.documents/tree/primary%3AOutput",
            extras = listOf(CollectionFolder(music, "Musik"), CollectionFolder(sdCard, "Musik")),
        )
        assertEquals(
            listOf("Download/TubeFlow", CollectionFolders.FALLBACK_LABEL, "Musik", "Musik (2)"),
            sources.map { it.label },
        )
        assertEquals(listOf(false, false, true, true), sources.map { it.removable })
    }

    @Test
    fun duplicateTreeIsScannedOnceAndNotRemovable() {
        val sources = CollectionFolders.sources(null, "Download/TubeFlow", music, listOf(CollectionFolder("$music/", "Musik")))
        assertEquals(1, sources.size)
        assertEquals(CollectionFolders.FALLBACK_LABEL, sources.single().label)
        assertFalse(sources.single().removable)
    }

    @Test
    fun folderListRoundTripsThroughPreferences() {
        val decoded = CollectionFolders.decode(CollectionFolders.encode(listOf(
            CollectionFolder(music, "Musik"),
            CollectionFolder(sdCard, "Musik"),
        )))
        assertEquals(listOf(music, sdCard), decoded.map { it.uri })
        assertEquals(listOf("Musik", "Musik (2)"), decoded.map { it.label })
    }

    @Test
    fun decodeIgnoresBrokenPayloadAndDuplicates() {
        assertTrue(CollectionFolders.decode(null).isEmpty())
        assertTrue(CollectionFolders.decode("bukan json").isEmpty())
        val decoded = CollectionFolders.decode(
            """[{"uri":"$music/","label":" "},{"uri":"$music","label":"Lain"}]""",
        )
        assertEquals(1, decoded.size)
        assertEquals(music, decoded.single().uri)
        // Label kosong diturunkan dari document id, bukan ditebak dari isi folder.
        assertEquals("Music", decoded.single().label)
    }

    @Test
    fun decodeCapsAtMaximum() {
        val raw = (1..CollectionFolders.MAX + 3).joinToString(",", "[", "]") { index ->
            """{"uri":"content://com.android.externalstorage.documents/tree/primary%3AF$index","label":"F$index"}"""
        }
        assertEquals(CollectionFolders.MAX, CollectionFolders.decode(raw).size)
    }
}
