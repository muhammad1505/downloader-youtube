package id.local.tubeflow.core

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.UUID

class KaraokeResultTest {
    @get:Rule val temp = TemporaryFolder()
    private val uri = "content://media/external/audio/media/12"
    private fun fixture(sample: Boolean = true): File {
        val directory = temp.newFolder(UUID.randomUUID().toString())
        val result = File(directory, "instrumental.flac").apply { writeText("verified preview bytes") }
        val hash = MessageDigest.getInstance("SHA-256").digest(result.readBytes()).joinToString("") { "%02x".format(it.toInt() and 255) }
        File(directory, "result.json").writeText(JSONObject().put("source", uri).put("sample", sample)
            .put("startMs", if (sample) 90000 else 0).put("durationMs", if (sample) 30000 else 180000)
            .put("resultSha256", hash).toString())
        return directory
    }
    private fun rejected(block: () -> Unit) {
        try { block(); fail("Invalid preview must be rejected") } catch (_: IllegalArgumentException) { }
    }
    @Test fun previewMustMatchTheActualSourceAndModeBeforePlaybackOrSave() {
        val directory = fixture()
        assertEquals(File(directory, "instrumental.flac"), KaraokeResult.verify(directory, uri, true))
        rejected { KaraokeResult.verify(directory, "content://media/external/audio/media/99", true) }
        rejected { KaraokeResult.verify(directory, uri, false) }
    }
    @Test fun changedPreviewBytesAndInvalidClipRangeAreRejected() {
        val directory = fixture()
        File(directory, "instrumental.flac").appendText("changed")
        rejected { KaraokeResult.verify(directory, uri, true) }
        val other = fixture()
        val manifest = File(other, "result.json")
        manifest.writeText(JSONObject(manifest.readText()).put("durationMs", 31000).toString())
        rejected { KaraokeResult.verify(other, uri, true) }
    }
    @Test fun fullSongAndShortClipAtEndAreValidWithTheirOwnModes() {
        val full = fixture(false)
        assertTrue(KaraokeResult.verify(full, uri, false).isFile)
        val clip = fixture()
        val manifest = File(clip, "result.json")
        manifest.writeText(JSONObject(manifest.readText()).put("startMs", 179000).put("durationMs", 1000).toString())
        assertTrue(KaraokeResult.verify(clip, uri, true).isFile)
    }
    @Test fun cancellingAfterPreviewCompletesDeletesAllPrivateJobDataOnly() {
        // Models and public output must survive even when the cancelled job has already produced a preview.
        val root = temp.newFolder("karaoke-work")
        val id = UUID.randomUUID().toString()
        val directory = File(root, id).apply { mkdirs() }
        for (name in listOf("source.bin", "mix.f32", "instrumental.f32", "instrumental.flac", "result.json", "saved.json", "ffmpeg.log"))
            File(directory, name).writeText("private data")
        val model = temp.newFolder("karaoke-models").resolve("model.onnx").apply { writeText("keep model") }
        val original = temp.newFile("original.m4a").apply { writeText("keep original") }
        val published = temp.newFile("saved-karaoke.flac").apply { writeText("keep saved result") }
        assertTrue(KaraokeResult.discard(root, id)); assertFalse(directory.exists())
        assertTrue(KaraokeResult.discard(root, id)) // cancellation cleanup is idempotent
        assertEquals("keep model", model.readText()); assertEquals("keep original", original.readText())
        assertEquals("keep saved result", published.readText())
    }
    @Test fun cleanupRefusesTraversalAndSymlinksInsteadOfClaimingEverythingIsClean() {
        val root = temp.newFolder("work")
        val original = temp.newFile("source.mp3").apply { writeText("keep") }
        assertFalse(KaraokeResult.discard(root, "../source.mp3"))
        val id = UUID.randomUUID().toString(); val directory = File(root, id).apply { mkdirs() }
        Files.createSymbolicLink(File(directory, "source.bin").toPath(), original.toPath())
        assertFalse(KaraokeResult.discard(root, id))
        assertEquals("keep", original.readText())
    }
    @Test fun recoveryAlsoRemovesInterruptedModelDownloadWithoutTouchingVerifiedWeights() {
        val models = temp.newFolder("models")
        File(models, "model.part").writeText("interrupted download")
        File(models, "model-adapted.part").writeText("interrupted preparation")
        val verified = File(models, "uvr-inst-hq3-t64.onnx").apply { writeText("keep verified model") }
        assertTrue(KaraokeResult.discardModelPartials(models))
        assertEquals(listOf(verified.name), models.listFiles()!!.map { it.name })
        assertEquals("keep verified model", verified.readText())
        assertTrue(KaraokeResult.discardModelPartials(models))
    }
    @Test fun unreadableFragmentShapeIsNotReportedAsSuccessfulCleanup() {
        val models = temp.newFolder("invalid-models")
        File(models, "model.part").mkdirs()
        assertFalse(KaraokeResult.discardModelPartials(models))
    }
}
