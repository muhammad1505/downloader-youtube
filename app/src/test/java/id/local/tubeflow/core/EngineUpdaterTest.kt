package id.local.tubeflow.core

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class EngineUpdaterTest {
    private val digest = "a".repeat(64)
    private val otherDigest = "b".repeat(64)

    private fun release(tag: String = "2026.10.21", engineDigest: String = digest, sumsDigest: String = otherDigest): String {
        val assets = JSONArray()
            .put(
                JSONObject()
                    .put("name", "yt-dlp")
                    .put("size", 3_074_818)
                    .put("digest", "sha256:$engineDigest")
                    .put("browser_download_url", "https://github.com/yt-dlp/yt-dlp/releases/download/$tag/yt-dlp"),
            )
            .put(
                JSONObject()
                    .put("name", "SHA2-256SUMS")
                    .put("size", 1_595)
                    .put("digest", "sha256:$sumsDigest")
                    .put("browser_download_url", "https://github.com/yt-dlp/yt-dlp/releases/download/$tag/SHA2-256SUMS"),
            )
        return JSONObject().put("tag_name", tag).put("assets", assets).toString()
    }

    @Test
    fun compareVersionsUsesDateParts() {
        assertTrue(EngineUpdater.compareVersions("2026.10.21", "2026.08.19") > 0)
        assertTrue(EngineUpdater.compareVersions("2026.02.01", "2026.10.01") < 0)
        assertEquals(0, EngineUpdater.compareVersions("2026.10.21", "2026.10.21"))
    }

    @Test
    fun parseReleaseAcceptsOfficialAssets() {
        val release = EngineUpdater.parseRelease(release())
        assertEquals("2026.10.21", release.version)
        assertEquals(digest, release.sha256)
        assertEquals(otherDigest, release.sumsSha256)
        assertTrue(release.downloadUrl.endsWith("/2026.10.21/yt-dlp"))
        assertTrue(release.sumsUrl.endsWith("/2026.10.21/SHA2-256SUMS"))
        assertEquals(3_074_818L, release.size)
    }

    @Test
    fun parseReleaseRejectsUnstableOrIncompleteMetadata() {
        assertThrows(EngineUpdateException::class.java) {
            EngineUpdater.parseRelease(release(tag = "latest"))
        }
        assertThrows(EngineUpdateException::class.java) {
            val assets = JSONArray().put(
                JSONObject()
                    .put("name", "yt-dlp")
                    .put("size", 3_074_818)
                    .put("browser_download_url", "https://github.com/yt-dlp/yt-dlp/releases/download/2026.10.21/yt-dlp"),
            )
            EngineUpdater.parseRelease(JSONObject().put("tag_name", "2026.10.21").put("assets", assets).toString())
        }
        assertThrows(EngineUpdateException::class.java) {
            val json = JSONObject(release())
            json.getJSONArray("assets").getJSONObject(0)
                .put("browser_download_url", "https://example.com/yt-dlp")
            EngineUpdater.parseRelease(json.toString())
        }
    }

    @Test
    fun parseSumsFindsOnlyTheEngineEntry() {
        val text = "c".repeat(64) + "  ffmpeg\n" + digest + "  yt-dlp\n"
        assertEquals(digest, EngineUpdater.parseSums(text))
        assertEquals(digest, EngineUpdater.parseSums("$digest *yt-dlp"))
        assertNull(EngineUpdater.parseSums("not-a-digest  yt-dlp"))
        assertNull(EngineUpdater.parseSums("$digest  other"))
    }

    @Test
    fun readZipappVersionReadsPinnedVersion() {
        val file = zip(
            "__main__.py" to "print('ok')\n",
            "yt_dlp/version.py" to "# generated\n__version__ = '2026.10.21'\n",
        )
        try {
            assertEquals("2026.10.21", EngineUpdater.readZipappVersion(file))
        } finally {
            file.delete()
        }
    }

    @Test
    fun readZipappVersionRejectsUnsafeOrIncompletePackages() {
        val missingMain = zip("yt_dlp/version.py" to "__version__ = '2026.10.21'\n")
        val traversal = zip("../escape.py" to "bad", "__main__.py" to "ok", "yt_dlp/version.py" to "__version__ = '2026.10.21'\n")
        try {
            assertThrows(EngineUpdateException::class.java) { EngineUpdater.readZipappVersion(missingMain) }
            assertThrows(EngineUpdateException::class.java) { EngineUpdater.readZipappVersion(traversal) }
        } finally {
            missingMain.delete()
            traversal.delete()
        }
    }

    @Test
    fun releaseUrlsMustStayOnOfficialHttpsHosts() {
        EngineUpdater.validateReleaseUrl("https://github.com/yt-dlp/yt-dlp/releases/download/2026.10.21/yt-dlp")
        EngineUpdater.validateReleaseUrl("https://release-assets.githubusercontent.com/file?token=abc")
        assertThrows(EngineUpdateException::class.java) {
            EngineUpdater.validateReleaseUrl("http://github.com/yt-dlp/yt-dlp/releases/download/2026.10.21/yt-dlp")
        }
        assertThrows(EngineUpdateException::class.java) {
            EngineUpdater.validateReleaseUrl("https://github.com.evil.example/yt-dlp")
        }
        assertThrows(EngineUpdateException::class.java) {
            EngineUpdater.validateReleaseUrl("https://user@github.com/yt-dlp")
        }
        assertThrows(EngineUpdateException::class.java) {
            EngineUpdater.validateReleaseUrl("https://github.com:444/yt-dlp")
        }
    }

    @Test
    fun atomicReplaceKeepsTargetComplete() {
        val directory = Files.createTempDirectory("tubeflow-engine").toFile()
        val source = File(directory, "next")
        val target = File(directory, "current")
        try {
            source.writeText("new-engine")
            target.writeText("old-engine")
            EngineUpdater.atomicReplace(source, target)
            assertEquals("new-engine", target.readText())
            assertTrue(!source.exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun zip(vararg entries: Pair<String, String>): File {
        val file = File.createTempFile("tubeflow-engine", ".zip")
        ZipOutputStream(FileOutputStream(file)).use { zip ->
            for ((name, content) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
        return file
    }
}
