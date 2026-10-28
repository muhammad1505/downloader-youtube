package id.local.tubeflow.core

import id.local.tubeflow.BuildConfig
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class AppChangelogTest {

    private val bundled by lazy { AppChangelog.parse(bundledJson()) }

    @Test
    fun runningVersionAlwaysHasItsOwnNotes() {
        // Gagal saat versi dinaikkan tanpa menambah sorotan rilis untuk versi itu.
        assertNotNull(AppChangelog.forVersion(bundled, BuildConfig.VERSION_CODE))
        assertEquals(
            BuildConfig.VERSION_NAME,
            AppChangelog.forVersion(bundled, BuildConfig.VERSION_CODE)?.versionName,
        )
    }

    @Test
    fun offerOnlyForNewerVersionsThatHaveTheirOwnNotes() {
        assertTrue(AppChangelog.shouldOffer(0, BuildConfig.VERSION_CODE, bundled))
        assertTrue(AppChangelog.shouldOffer(BuildConfig.VERSION_CODE - 1, BuildConfig.VERSION_CODE, bundled))
        assertFalse(AppChangelog.shouldOffer(BuildConfig.VERSION_CODE, BuildConfig.VERSION_CODE, bundled))
        assertFalse(AppChangelog.shouldOffer(BuildConfig.VERSION_CODE + 1, BuildConfig.VERSION_CODE, bundled))
        assertFalse(AppChangelog.shouldOffer(0, BuildConfig.VERSION_CODE, emptyList()))
    }

    @Test
    fun versionWithoutNotesIsSkippedInsteadOfShowingOlderHighlights() {
        val missing = bundled.maxOf { it.versionCode } + 5

        assertNull(AppChangelog.forVersion(bundled, missing))
        assertFalse(AppChangelog.shouldOffer(missing - 1, missing, bundled))
        // Tombol di Tentang tetap punya isi: catatan terbaru yang tersedia.
        assertEquals(bundled.first(), AppChangelog.latestAtOrBefore(bundled, missing))
    }

    @Test
    fun releasesAreNewestFirstWithUniqueCodesAndReadableHighlights() {
        val codes = bundled.map { it.versionCode }

        assertEquals(codes.sortedDescending(), codes)
        assertEquals(codes.size, codes.toSet().size)
        bundled.forEach { notes ->
            assertTrue(notes.versionName.startsWith("1."))
            assertTrue("Sorotan ${notes.versionName} kosong", notes.highlights.isNotEmpty())
            notes.highlights.forEach { line ->
                assertTrue(line.length in 20..220)
                assertFalse(line.endsWith(" "))
            }
        }
    }

    @Test
    fun incompleteEntriesAreIgnored() {
        val parsed = AppChangelog.parse(
            """
            [
              {"versionCode": 7, "versionName": "1.2.0", "highlights": ["Sorotan valid untuk data uji."]},
              {"versionCode": 0, "versionName": "1.2.1", "highlights": ["Sorotan valid."]},
              {"versionCode": 8, "versionName": " ", "highlights": ["Sorotan valid."]},
              {"versionCode": 9, "versionName": "1.2.3", "highlights": []},
              {"versionCode": 10, "versionName": "1.2.4"}
            ]
            """.trimIndent(),
        )

        assertEquals(listOf(7), parsed.map { it.versionCode })
    }

    /**
     * Tes unit berjalan dari root repo atau dari direktori modul app, tergantung task Gradle.
     * Cari naik beberapa tingkat agar aset tetap ditemukan di kedua lingkungan.
     */
    private fun bundledJson(): String {
        var directory = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(6) {
            listOf(
                File(directory, "app/src/main/assets/changelog.json"),
                File(directory, "src/main/assets/changelog.json"),
            ).firstOrNull { it.isFile }?.let { return it.readText() }
            directory = directory.parentFile ?: return@repeat
        }
        error("changelog.json tidak ditemukan dari ${System.getProperty("user.dir")}")
    }
}
