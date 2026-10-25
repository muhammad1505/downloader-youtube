package id.local.tubeflow.core

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

class InspectionCache(
    private val directory: File,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    @Synchronized
    fun read(url: String, maxAgeMs: Long = MAX_AGE_MS): VideoMetadata? {
        val file = fileFor(url)
        if (!file.isFile) return null
        if (clock() - file.lastModified() > maxAgeMs) {
            file.delete()
            return null
        }
        val metadata = runCatching {
            val json = JSONObject(file.readText())
            // Entri lama tanpa daftar resolusi tidak dipakai lagi agar detail selalu lengkap.
            if (json.optInt("v", 1) < SCHEMA_VERSION) null else fromJson(json)
        }.getOrNull()
        if (metadata == null || metadata.url != url.trim()) {
            file.delete()
            return null
        }
        return metadata
    }

    @Synchronized
    fun write(url: String, metadata: VideoMetadata) {
        directory.mkdirs()
        val target = fileFor(url)
        val temporary = File(directory, target.name + ".tmp")
        runCatching {
            temporary.writeText(toJson(metadata).toString())
            runCatching {
                Files.move(
                    temporary.toPath(),
                    target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE,
                )
            }.getOrElse {
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            prune()
        }.onFailure { temporary.delete() }
    }

    private fun prune() {
        val files = directory.listFiles()?.filter { it.isFile && it.name.endsWith(".json") }.orEmpty()
        if (files.size <= MAX_ENTRIES) return
        files.sortedBy(File::lastModified).take(files.size - MAX_ENTRIES).forEach(File::delete)
    }

    private fun fileFor(url: String): File {
        val digest = MessageDigest.getInstance("SHA-256").digest(url.trim().toByteArray())
        return File(directory, digest.joinToString("") { "%02x".format(it.toInt() and 0xff) } + ".json")
    }

    private fun toJson(metadata: VideoMetadata): JSONObject = JSONObject().apply {
        put("v", SCHEMA_VERSION)
        put("title", metadata.title)
        put("uploader", metadata.uploader)
        put("durationSeconds", metadata.durationSeconds)
        put("thumbnail", metadata.thumbnail)
        put("url", metadata.url)
        put("subtitleLangs", JSONArray(metadata.subtitleLangs))
        // Pasangan [tinggi, label] supaya entri cache lama (array tinggi saja) tetap bisa dibaca.
        put("resolutions", JSONArray().apply {
            metadata.resolutions.forEach { put(JSONArray().put(it.height).put(it.tier)) }
        })
        put("artist", metadata.artist)
        put("track", metadata.track)
        put("creator", metadata.creator)
        put("platformId", metadata.platformId)
        put("sourceId", metadata.sourceId)
        put("extractor", metadata.extractor)
        put("artistEvidence", metadata.artistEvidence)
    }

    private fun fromJson(json: JSONObject): VideoMetadata = VideoMetadata(
        title = json.getString("title"),
        uploader = json.optString("uploader"),
        durationSeconds = json.optLong("durationSeconds"),
        thumbnail = json.optString("thumbnail"),
        url = json.getString("url"),
        subtitleLangs = json.optJSONArray("subtitleLangs")?.let { array ->
            (0 until array.length()).mapNotNull { index -> array.optString(index).takeIf { it.isNotBlank() } }
        }.orEmpty(),
        resolutions = json.optJSONArray("resolutions")?.let { array ->
            (0 until array.length()).mapNotNull { index ->
                when (val value = array.opt(index)) {
                    is Int -> value.takeIf { it > 0 }?.let { VideoResolution(it, it) }
                    is JSONArray -> value.optInt(0, 0).takeIf { it > 0 }
                        ?.let { height -> VideoResolution(height, value.optInt(1, height)) }
                    else -> null
                }
            }
        }.orEmpty(),
        artist = json.optString("artist"),
        track = json.optString("track"),
        creator = json.optString("creator"),
        platformId = json.optString("platformId"),
        sourceId = json.optString("sourceId"),
        extractor = json.optString("extractor"),
        artistEvidence = json.optString("artistEvidence"),
    )

    companion object {
        const val MAX_AGE_MS = 30 * 60 * 1000L
        private const val SCHEMA_VERSION = 2
        private const val MAX_ENTRIES = 200
    }
}
