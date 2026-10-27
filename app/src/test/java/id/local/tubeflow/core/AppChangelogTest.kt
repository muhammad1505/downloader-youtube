package id.local.tubeflow.core

import id.local.tubeflow.BuildConfig
import org.junit.Assert.*
import org.junit.Test

class AppChangelogTest {

    @Test
    fun runningVersionAlwaysHasItsOwnNotes() {
        // Gagal saat versi dinaikkan tanpa menambah sorotan rilis untuk versi itu.
        assertNotNull(AppChangelog.forVersion(BuildConfig.VERSION_CODE))
        assertEquals(BuildConfig.VERSION_NAME, AppChangelog.forVersion(BuildConfig.VERSION_CODE)?.versionName)
    }

    @Test
    fun offerOnlyForNewerVersionsThatHaveTheirOwnNotes() {
        assertTrue(AppChangelog.shouldOffer(0, BuildConfig.VERSION_CODE))
        assertTrue(AppChangelog.shouldOffer(BuildConfig.VERSION_CODE - 1, BuildConfig.VERSION_CODE))
        assertFalse(AppChangelog.shouldOffer(BuildConfig.VERSION_CODE, BuildConfig.VERSION_CODE))
        assertFalse(AppChangelog.shouldOffer(BuildConfig.VERSION_CODE + 1, BuildConfig.VERSION_CODE))
    }

    @Test
    fun versionWithoutNotesIsSkippedInsteadOfShowingOlderHighlights() {
        val missing = AppChangelog.releases.maxOf { it.versionCode } + 5

        assertNull(AppChangelog.forVersion(missing))
        assertFalse(AppChangelog.shouldOffer(missing - 1, missing))
        // Tombol di Tentang tetap punya isi: catatan terbaru yang tersedia.
        assertEquals(AppChangelog.releases.first(), AppChangelog.latestAtOrBefore(missing))
    }

    @Test
    fun releasesAreNewestFirstWithUniqueCodesAndReadableHighlights() {
        val codes = AppChangelog.releases.map { it.versionCode }

        assertEquals(codes.sortedDescending(), codes)
        assertEquals(codes.size, codes.toSet().size)
        AppChangelog.releases.forEach { notes ->
            assertTrue(notes.versionName.startsWith("1."))
            assertTrue("Sorotan ${notes.versionName} kosong", notes.highlights.isNotEmpty())
            notes.highlights.forEach { line ->
                assertTrue(line.length in 20..220)
                assertFalse(line.endsWith(" "))
            }
        }
    }
}
