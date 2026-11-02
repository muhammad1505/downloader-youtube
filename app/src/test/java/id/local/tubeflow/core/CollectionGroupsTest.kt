package id.local.tubeflow.core

import org.junit.Assert.assertEquals
import org.junit.Test

class CollectionGroupsTest {
    private fun item(uri: String, folder: String, title: String = uri) = MediaItem(
        uri = uri, kind = "AUDIO", title = title, displayName = "$title.mp3", mime = "audio/mpeg", folder = folder,
    )

    @Test
    fun groupsFollowPageOrderAndUseWholeDatabaseTotals() {
        val loaded = listOf(item("a1", "Album A"), item("b1", "Album B"), item("a2", "Album A"))
        val groups = CollectionGroups.group(loaded, mapOf("Album A" to 40, "Album B" to 7))

        assertEquals(listOf("Album A", "Album B"), groups.map { it.folder })
        assertEquals(listOf(2, 1), groups.map { it.items.size })
        // Halaman hanya memuat 3 baris, tetapi header memakai hitungan database penuh.
        assertEquals(listOf(40L, 7L), groups.map { it.total })
    }

    @Test
    fun missingTotalFallsBackToLoadedRowsAndNeverShrinksBelowThem() {
        assertEquals(
            2L,
            CollectionGroups.group(listOf(item("a1", "Album A"), item("a2", "Album A")), mapOf("Album A" to 1))
                .single().total,
        )
        assertEquals(
            1L,
            CollectionGroups.group(listOf(item("a1", "Album A")), emptyMap()).single().total,
        )
    }

    @Test
    fun blankFolderGetsReadableLabelWithoutChangingGroupKey() {
        val groups = CollectionGroups.group(listOf(item("root", " "), item("a", "Download/TubeFlow")), emptyMap())

        assertEquals(listOf(" ", "Download/TubeFlow"), groups.map { it.folder })
        assertEquals(CollectionGroups.UNKNOWN_FOLDER, CollectionGroups.label(" "))
        assertEquals("Download/TubeFlow", CollectionGroups.label("Download/TubeFlow"))
    }

    @Test
    fun emptyPageHasNoHeadersEvenWithStaleTotals() {
        assertEquals(emptyList<CollectionGroup>(), CollectionGroups.group(emptyList(), mapOf("Album A" to 5)))
    }
}
