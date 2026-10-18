package id.local.tubeflow.core

import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.security.MessageDigest

/** Bounded provider responses. Network/rate-limit/invalid responses are never negative cached. */
class LyricsResponseCache(private val directory: File, private val clock: () -> Long = System::currentTimeMillis) {
    data class Response(val code: Int, val text: String, val failureStatus: String? = null)
    data class ClearResult(val removed: Int, val complete: Boolean)
    private val lock = Any()
    private val requests = Any()
    private var generation = 0L

    fun request(key: String, refreshMissing: Boolean = false,
            validateBody: (String) -> Boolean = { text -> runCatching {
                org.json.JSONTokener(text).nextValue().let { it is JSONObject || it is org.json.JSONArray }
            }.getOrDefault(false) }, load: () -> Response): Response {
        synchronized(requests) {
            val name = MessageDigest.getInstance("SHA-256").digest(key.toByteArray()).joinToString("") { "%02x".format(it) }
            val file = File(directory, "$name.json")
            val (epoch, hit) = synchronized(lock) {
                generation to runCatching {
                    val json = JSONObject(file.readText())
                    val age = clock() - json.getLong("at")
                    val cached = Response(json.getInt("code"), json.getString("text"))
                    val missing = cached.code == 404 || cached.code == 200 && cached.text.trim() == "[]"
                    val ttl = if (missing) 3_600_000 else 86_400_000
                    val valid = cached.code == 404 || cached.code == 200 && validateBody(cached.text)
                    cached.takeIf { valid && age in 0 until ttl && !(refreshMissing && missing) }
                }.getOrNull()
            }
            if (hit != null) return hit
            // Network work must not hold the disk lock or delay an explicit clear.
            val result = load()
            val valid = result.failureStatus == null && (result.code == 404 || result.code == 200 && validateBody(result.text))
            if (valid && result.text.toByteArray().size <= 1024 * 1024) synchronized(lock) {
                // A response started before clear may finish, but must not repopulate the cache.
                if (epoch == generation) runCatching {
                    directory.mkdirs()
                    val temporary = File.createTempFile("response-", ".tmp", directory)
                    try {
                        temporary.writeText(JSONObject().put("code", result.code).put("text", result.text).put("at", clock()).toString())
                        require(temporary.renameTo(file))
                    } finally { temporary.delete() }
                    CacheBudget.prune(directory, 10L * 1024 * 1024, entriesLimit = 200)
                }
            }
            return result
        }
    }

    fun clear(): ClearResult = synchronized(lock) {
        generation++
        if (Files.isSymbolicLink(directory.toPath())) return@synchronized ClearResult(0, false)
        if (Files.notExists(directory.toPath())) return@synchronized ClearResult(0, true)
        val files = directory.listFiles() ?: return@synchronized ClearResult(0, false)
        var removed = 0
        var complete = true
        files.forEach { file ->
            if (!Files.isRegularFile(file.toPath(), LinkOption.NOFOLLOW_LINKS) || !file.delete()) complete = false
            else removed++
        }
        ClearResult(removed, complete)
    }
}
