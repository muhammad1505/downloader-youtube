package id.local.tubeflow.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderActionsTest {

    private fun item(
        kind: String,
        available: Boolean = true,
        size: Long? = 100,
        title: String = "Lagu",
    ) = MediaItem(
        uri = "content://media/${kind.lowercase()}/$title",
        kind = kind,
        title = title,
        displayName = "$title.bin",
        mime = if (kind == "AUDIO") "audio/mpeg" else "video/mp4",
        folder = "Download/TubeFlow",
        sizeBytes = size,
        availability = if (available) "AVAILABLE" else "UNAVAILABLE",
    )

    @Test
    fun labelUsesAliasAndFallsBackToFolderPath() {
        val aliases = mapOf("Download/TubeFlow/Musik" to "Lagu Favorit")
        assertEquals("Lagu Favorit", FolderAliases.label("Download/TubeFlow/Musik/", aliases))
        assertEquals("Download/TubeFlow", FolderAliases.label("Download/TubeFlow", aliases))
        assertEquals(CollectionGroups.UNKNOWN_FOLDER, FolderAliases.label("", aliases))
    }

    @Test
    fun validateRejectsEmptyLongSlashAndDuplicateNames() {
        assertEquals("Nama folder tidak boleh kosong.", FolderAliases.validate("  ", "a/b", emptyMap()))
        assertNotNull(FolderAliases.validate("x".repeat(FolderAliases.MAX_NAME + 1), "a/b", emptyMap()))
        assertEquals("Nama folder tidak boleh memakai garis miring.", FolderAliases.validate("a/b", "a/b", emptyMap()))
        val aliases = mapOf("lain" to "Musik")
        assertEquals("Nama itu sudah dipakai folder lain.", FolderAliases.validate("musik", "a/b", aliases))
        assertNull(FolderAliases.validate("Musik", "a/b", emptyMap()))
        assertNull(FolderAliases.validate("  Musik  ", "a/b", emptyMap()))
    }

    @Test
    fun withAliasAddsNormalizedKeyAndRemovesBlankName() {
        val added = FolderAliases.withAlias(emptyMap(), "Download/TubeFlow/Musik/", "  Lagu  ")
        assertEquals(mapOf("Download/TubeFlow/Musik" to "Lagu"), added)
        assertTrue(FolderAliases.withAlias(added, "Download/TubeFlow/Musik", " ").isEmpty())
    }

    @Test
    fun encodeDecodeRoundTripsAliasesAndIgnoresBrokenJson() {
        val aliases = mapOf("Download/TubeFlow" to "Unduhan", "Download/Lagu" to "Lagu")
        assertEquals(aliases, FolderAliases.decode(FolderAliases.encode(aliases)))
        assertTrue(FolderAliases.decode("bukan json").isEmpty())
        assertTrue(FolderAliases.decode("").isEmpty())
    }

    @Test
    fun summaryCountsKindsAvailabilityAndSizes() {
        val summary = FolderSummary.of(
            "Download/TubeFlow",
            listOf(
                item("AUDIO", size = 100),
                item("AUDIO", size = null),
                item("VIDEO", size = 250),
                item("VIDEO", available = false, size = 999),
            ),
        )
        assertEquals(4, summary.total)
        assertEquals(2, summary.audio)
        assertEquals(2, summary.video)
        assertEquals(3, summary.available)
        assertEquals(1, summary.unavailable)
        assertEquals(350, summary.bytes)
        assertEquals(1, summary.unknownSizes)
    }

    @Test
    fun summaryOfEmptyFolderIsZeroed() {
        val summary = FolderSummary.of("Download/Kosong", emptyList())
        assertEquals(0, summary.total)
        assertEquals(0, summary.audio)
        assertEquals(0, summary.video)
        assertEquals(0, summary.bytes)
        assertFalse(summary.unavailable > 0)
    }
}
