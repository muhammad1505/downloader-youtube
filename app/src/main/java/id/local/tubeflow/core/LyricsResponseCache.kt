package id.local.tubeflow.core

import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/** Bounded provider responses. Network/rate-limit/invalid responses are never negative cached. */
class LyricsResponseCache(private val directory: File, private val clock: () -> Long = System::currentTimeMillis) {
    data class Response(val code: Int, val text: String, val failureStatus: String? = null)
    @Synchronized fun request(key: String, refreshMissing: Boolean = false,
            validateBody: (String) -> Boolean = { text -> runCatching {
                org.json.JSONTokener(text).nextValue().let { it is JSONObject || it is org.json.JSONArray }
            }.getOrDefault(false) }, load: () -> Response): Response {
        directory.mkdirs()
        val name = MessageDigest.getInstance("SHA-256").digest(key.toByteArray()).joinToString("") { "%02x".format(it) }
        val file = File(directory, "$name.json")
        runCatching {
            val json = JSONObject(file.readText())
            val age = clock() - json.getLong("at")
            val cached = Response(json.getInt("code"), json.getString("text"))
            val missing = cached.code == 404 || cached.code == 200 && cached.text.trim() == "[]"
            val ttl = if (missing) 3_600_000 else 86_400_000
            val valid = cached.code == 404 || cached.code == 200 && validateBody(cached.text)
            if (valid && age in 0 until ttl && !(refreshMissing && missing)) return cached
        }
        val result = load()
        val valid = result.failureStatus == null && (result.code == 404 || result.code == 200 && validateBody(result.text))
        if (valid && result.text.toByteArray().size <= 1024 * 1024) runCatching {
            val temporary = File.createTempFile("response-", ".tmp", directory)
            try {
                temporary.writeText(JSONObject().put("code", result.code).put("text", result.text).put("at", clock()).toString())
                require(temporary.renameTo(file))
            } finally { temporary.delete() }
            CacheBudget.prune(directory, 10L * 1024 * 1024, entriesLimit = 200)
        }
        return result
    }
    @Synchronized fun clear() { directory.listFiles()?.forEach(File::delete) }
}
