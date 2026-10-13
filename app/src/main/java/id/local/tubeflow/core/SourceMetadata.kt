package id.local.tubeflow.core

import org.json.JSONObject

object SourceMetadata {
    private val officialCredit = Regex("""(?i)^official\s+(?:music|audio)(?:\s+live)?\s+from\s+(.+?)\s+[-–—]\s+(.+?)\s*$""")
    private fun normalized(value: String) = value.lowercase().replace(Regex("""[^\p{L}\p{N}]+"""), " ").trim()

    /** Explicit performer/track credit corroborated by the title; unrelated names and composers are ignored. */
    private fun descriptionCredit(title: String, description: String): Pair<String, String>? {
        val titleWords = " ${normalized(title)} "
        return description.lineSequence().take(50).mapNotNull { line ->
            val match = officialCredit.matchEntire(line.trim()) ?: return@mapNotNull null
            val artist = match.groupValues[1].trim()
            val track = match.groupValues[2].trim()
            val artistWords = normalized(artist)
            val trackWords = normalized(track)
            if (artistWords.isBlank() || trackWords.isBlank() ||
                !titleWords.contains(" $artistWords ") || !titleWords.contains(" $trackWords ")) null
            else artist to track
        }.distinctBy { normalized(it.first) to normalized(it.second) }.toList().singleOrNull()
    }

    fun parse(json: JSONObject, url: String): VideoMetadata {
        fun text(key: String) = if (json.isNull(key)) "" else json.optString(key).trim().takeUnless { it == "null" }.orEmpty()
        val artist = text("artist").ifBlank {
            json.optJSONArray("artists")?.let { values ->
                (0 until values.length()).mapNotNull { values.optString(it).takeIf { value -> value.isNotBlank() && value != "null" } }.joinToString(" & ")
            }.orEmpty()
        }
        val title = text("title").ifBlank { "Video" }
        val track = text("track")
        val credit = if (artist.isBlank() && track.isBlank()) descriptionCredit(title, text("description")) else null
        return VideoMetadata(title, text("uploader").ifBlank { text("channel") },
            json.optLong("duration"), text("thumbnail").ifBlank {
                json.optJSONArray("thumbnails")?.let { list -> (list.length() - 1 downTo 0).firstNotNullOfOrNull { list.optJSONObject(it)?.optString("url")?.takeIf(String::isNotBlank) } }.orEmpty()
            }, url, artist = artist.ifBlank { credit?.first.orEmpty() }, track = track.ifBlank { credit?.second.orEmpty() }, creator = text("creator").ifBlank { text("uploader") },
            platformId = SourcePlatforms.fromUrl(url).id, sourceId = text("id"), extractor = text("extractor_key").ifBlank { text("extractor") },
            artistEvidence = if (credit != null) "source_description_credit" else "")
    }
}
