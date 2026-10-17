package id.local.tubeflow.core

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/** Bounded, versioned data document. It deliberately contains no URI grants or device paths. */
object MediaBackupCodec {
    const val MAX_BYTES = 12 * 1024 * 1024
    const val MAX_ITEMS = 3000
    fun digest(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
    fun encode(payload: JSONObject): ByteArray {
        val content = payload.toString()
        val result = JSONObject().put("app", "TubeFlow").put("schema", 1).put("payload", content)
            .put("sha256", digest(content.toByteArray(Charsets.UTF_8))).toString().toByteArray(Charsets.UTF_8)
        require(result.size <= MAX_BYTES) { "Cadangan melebihi batas 12 MB. Kurangi jumlah lirik." }
        return result
    }
    private fun checkDepth(text: String) {
        var depth = 0; var quoted = false; var escaped = false
        for (char in text) {
            if (escaped) { escaped = false; continue }
            if (quoted && char == '\\') { escaped = true; continue }
            if (char == '"') { quoted = !quoted; continue }
            if (!quoted) when (char) {
                '{', '[' -> { depth++; require(depth <= 16) { "Cadangan terlalu kompleks." } }
                '}', ']' -> { depth--; require(depth >= 0) }
            }
        }
        require(depth == 0 && !quoted)
    }
    fun decode(bytes: ByteArray): JSONObject {
        require(bytes.size in 1..MAX_BYTES) { "Ukuran cadangan tidak valid." }
        val raw = Charsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
            .decode(java.nio.ByteBuffer.wrap(bytes)).toString()
        checkDepth(raw)
        val envelope = JSONObject(raw)
        require(envelope.getString("app") == "TubeFlow" && envelope.getInt("schema") == 1) { "Versi cadangan tidak didukung." }
        val content = envelope.getString("payload")
        require(digest(content.toByteArray(Charsets.UTF_8)) == envelope.getString("sha256")) { "Cadangan rusak atau berubah." }
        checkDepth(content)
        val payload = JSONObject(content)
        payload.getJSONObject("settings")
        val items = payload.getJSONArray("items")
        require(items.length() <= MAX_ITEMS && payload.getJSONArray("playlists").length() <= 201)
        val ids = mutableSetOf<String>()
        repeat(items.length()) {
            val item = items.getJSONObject(it)
            val id = item.getString("id")
            require(id.matches(Regex("[a-f0-9]{64}")) && ids.add(id)) { "Identitas media pada cadangan tidak valid atau ganda." }
            listOf("title", "artist", "album", "albumArtist", "genre", "track", "year").forEach { key -> require(item.getString(key).length <= 1000) }
            if (item.has("lyrics")) {
                val lyrics = item.getJSONObject("lyrics")
                require(lyrics.getString("original").toByteArray().size <= 512 * 1024)
                require(lyrics.getLong("offset") in -30_000L..30_000L)
                require(LrcLines.parse(LrcTiming.shift(lyrics.getString("original"), lyrics.getLong("offset"), null)).isNotEmpty())
            }
        }
        val playlistIds = mutableSetOf<String>()
        val playlists = payload.getJSONArray("playlists")
        repeat(playlists.length()) {
            val list = playlists.getJSONObject(it)
            val id = list.getString("id")
            require((id == PersonalLibrary.FAVORITES || runCatching { java.util.UUID.fromString(id).toString() == id }.getOrDefault(false)) && playlistIds.add(id))
            PersonalLibrary.name(list.getString("name"))
            val members = list.getJSONArray("members")
            require(members.length() <= MAX_ITEMS)
            val seen = mutableSetOf<String>()
            repeat(members.length()) { index -> require(members.getString(index) in ids && seen.add(members.getString(index))) }
        }
        return payload
    }
    fun array(values: Iterable<String>) = JSONArray().apply { values.forEach(::put) }
}
