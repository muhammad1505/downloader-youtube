package id.local.tubeflow.core

import org.json.JSONObject

object SourceMetadata {
    private val officialCredit = Regex("""(?i)^official\s+(?:music|audio)(?:\s+live)?\s+from\s+(.+?)\s+[-–—]\s+(.+?)\s*$""")
    private val trackLabel = Regex("""(?i)^(?:title|song(?: title)?|track|judul(?: lagu)?)\s*:\s*(.+)$""")
    private val performerLabel = Regex("""(?i)^(?:artist|artis|voc|vocals?|vokal|vocalist|singer|penyanyi|performed by)\s*:\s*(.+)$""")
    private val collaboration = Regex("""(?i)\s+(?:x|ft\.?|feat\.?|featuring|&)\s+""")
    private fun normalized(value: String) = value.lowercase().replace(Regex("""[^\p{L}\p{N}]+"""), " ").trim()
    private fun creditWords(value: String) = normalized(value.replace(collaboration, " & "))

    /** Explicit performer/track credit corroborated by the title; unrelated names and composers are ignored. */
    private fun descriptionCredit(title: String, description: String): Pair<String, String>? {
        val titleWords = " ${creditWords(title)} "
        fun corroborated(artist: String, track: String): Boolean {
            val artistWords = creditWords(artist)
            val trackWords = normalized(track)
            return artistWords.isNotBlank() && trackWords.isNotBlank() &&
                titleWords.contains(" $artistWords ") && titleWords.contains(" $trackWords ")
        }
        val lines = description.take(64 * 1024).lineSequence().take(200).map(String::trim).toList()
        val credits = lines.mapNotNull { line ->
            val match = officialCredit.matchEntire(line.trim()) ?: return@mapNotNull null
            val artist = match.groupValues[1].trim()
            val track = match.groupValues[2].trim()
            if (corroborated(artist, track)) artist to track else null
        }.toMutableList()
        // Accept one explicit title/performer pair, not composers, producers or channel names.
        val tracks = lines.mapNotNull { trackLabel.matchEntire(it)?.groupValues?.get(1)?.trim() }
            .distinctBy(::normalized)
        val performers = lines.mapNotNull { performerLabel.matchEntire(it)?.groupValues?.get(1)?.trim() }
            .distinctBy(::creditWords)
        if (tracks.size == 1 && performers.size == 1 && corroborated(performers.single(), tracks.single())) {
            credits += performers.single() to tracks.single()
        }
        return credits.distinctBy { creditWords(it.first) to normalized(it.second) }.singleOrNull()
    }

    /** Tinggi video yang dilaporkan `formats[]`; audio/storyboard dan format tanpa tinggi dilewati. */
    private fun videoResolutions(json: JSONObject): List<Int> {
        val heights = json.optJSONArray("formats")?.let { formats ->
            (0 until formats.length()).mapNotNull { index ->
                val format = formats.optJSONObject(index) ?: return@mapNotNull null
                if (format.optString("vcodec") == "none") return@mapNotNull null
                format.optInt("height", 0).takeIf { it > 0 }
            }
        }.orEmpty()
        // Sebagian extractor (mis. Instagram) hanya mengisi height di level info, bukan per format.
        val rootHeight = json.optInt("height", 0)
        return (heights + listOfNotNull(rootHeight.takeIf { heights.isEmpty() && it > 0 }))
            .distinct().sortedDescending()
    }

    fun parse(json: JSONObject, url: String): VideoMetadata {
        fun text(key: String) = if (json.isNull(key)) "" else json.optString(key).trim().takeUnless { it == "null" }.orEmpty()
        fun usable(value: String) = value.takeUnless { TitleSplit.creditKey(it) in setOf("null", "unknown", "unknownartist", "variousartists") }.orEmpty()
        val artist = usable(text("artist")).ifBlank {
            json.optJSONArray("artists")?.let { values ->
                (0 until values.length()).mapNotNull { usable(values.optString(it)).takeIf(String::isNotBlank) }.joinToString(" & ")
            }.orEmpty()
        }
        val title = text("title").ifBlank { "Video" }
        val track = text("track")
        val credit = descriptionCredit(title, text("description"))?.takeIf { (who, song) ->
            (artist.isBlank() || creditWords(artist) == creditWords(who)) &&
                (track.isBlank() || normalized(track) == normalized(song))
        }
        return VideoMetadata(title, text("uploader").ifBlank { text("channel") },
            json.optLong("duration"), text("thumbnail").ifBlank {
                json.optJSONArray("thumbnails")?.let { list -> (list.length() - 1 downTo 0).firstNotNullOfOrNull { list.optJSONObject(it)?.optString("url")?.takeIf(String::isNotBlank) } }.orEmpty()
            }, url, artist = artist.ifBlank { credit?.first.orEmpty() }, track = track.ifBlank { credit?.second.orEmpty() }, creator = text("creator").ifBlank { text("uploader") },
            platformId = SourcePlatforms.fromUrl(url).id, sourceId = text("id"), extractor = text("extractor_key").ifBlank { text("extractor") },
            artistEvidence = if (artist.isBlank() && credit != null) "source_description_credit" else "",
            resolutions = videoResolutions(json))
    }
}
