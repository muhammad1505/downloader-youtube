package id.local.tubeflow.core

import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/** A private preview must prove its source and mode before either playback or publication. */
object KaraokeResult {
    fun verify(directory: File, source: String, sample: Boolean): File {
        val manifest = File(directory, "result.json")
        require(manifest.length() in 1..65536) { "Identitas hasil karaoke tidak tersedia; proses ulang." }
        val proof = JSONObject(manifest.readText())
        require(proof.getString("source") == source && proof.getBoolean("sample") == sample) {
            "Hasil karaoke bukan milik sumber atau mode ini; proses ulang."
        }
        val start = proof.getLong("startMs")
        val duration = proof.getLong("durationMs")
        require(start in 0..600000 && duration in 1..600000 &&
            (if (sample) duration <= 30050 && start + duration <= 600050 else start == 0L)) {
            "Rentang hasil karaoke tidak valid."
        }
        val result = File(directory, "instrumental.flac")
        require(result.isFile && result.length() > 0 && result.canonicalFile.parentFile == directory.canonicalFile &&
            digest(result) == proof.getString("resultSha256")) { "Pratinjau berubah atau hilang; proses ulang karaoke." }
        return result
    }

    /** Caller holds the worker lease. A finished preview is still private temporary data. */
    fun discard(root: File, workId: String): Boolean {
        if (!runCatching { java.util.UUID.fromString(workId).toString().equals(workId, true) }.getOrDefault(false)) return false
        val directory = File(root, workId)
        if (java.nio.file.Files.notExists(directory.toPath()) && !java.nio.file.Files.isSymbolicLink(directory.toPath())) return true
        return PrivateStorage.deleteJob(root, directory)
    }

    /** Model download fragments are temporary; the verified .onnx is deliberately retained. */
    fun discardModelPartials(models: File): Boolean = listOf("model.part", "model-adapted.part").map { name ->
        val file = File(models, name)
        if (java.nio.file.Files.notExists(file.toPath()) && !java.nio.file.Files.isSymbolicLink(file.toPath())) true
        else !java.nio.file.Files.isSymbolicLink(file.toPath()) && file.isFile && file.delete()
    }.all { it }

    private fun digest(file: File): String {
        val hash = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(65536)
            while (true) { val count = input.read(buffer); if (count < 0) break; hash.update(buffer, 0, count) }
        }
        return hash.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    }
}
