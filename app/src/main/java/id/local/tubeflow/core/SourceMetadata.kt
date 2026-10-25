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

    /** Tangga label "p" yang lazim dipakai pemutar dan situs video. */
    private val standardTiers = listOf(144, 240, 360, 480, 720, 1080, 1440, 2160, 4320)

    /** Label "p" dari `format_note` (mis. "1080p60"), atau tangga standar dari sisi pendek video. */
    private val tierNote = Regex("""^(\d+)p""")
    private fun tierOf(format: JSONObject, height: Int): Int {
        tierNote.find(format.optString("format_note"))?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?.takeIf { it > 0 }?.let { return it }
        val width = format.optInt("width", 0)
        // Label "p" memakai sisi pendek, jadi video vertikal (1080×1920) tetap 1080p. Tanpa width
        // tidak ada dasar untuk menebak label, jadi tinggi apa adanya yang dipakai.
        return when {
            width in 1 until height -> standardTiers.firstOrNull { it >= width } ?: width
            width > height -> standardTiers.firstOrNull { it >= height } ?: height
            else -> height
        }
    }

    /**
     * Resolusi video yang dilaporkan `formats[]`; audio/storyboard dan format tanpa tinggi dilewati.
     * Tinggi asli disimpan untuk filter unduhan, label tier dipakai agar daftar terbaca seperti
     * situs video (1080p, 720p, …) meskipun rasio videonya bukan 16:9.
     */
    private fun videoResolutions(json: JSONObject): List<VideoResolution> {
        val found = json.optJSONArray("formats")?.let { formats ->
            (0 until formats.length()).mapNotNull { index ->
                val format = formats.optJSONObject(index) ?: return@mapNotNull null
                if (format.optString("vcodec") == "none") return@mapNotNull null
                val height = format.optInt("height", 0)
                if (height <= 0) return@mapNotNull null
                VideoResolution(height, tierOf(format, height))
            }
        }.orEmpty()
        // Sebagian extractor (mis. Instagram) hanya mengisi height di level info, bukan per format.
        val rootHeight = json.optInt("height", 0)
        val rootWidth = json.optInt("width", 0)
        val resolved = if (found.isEmpty() && rootHeight > 0) {
            val tier = when {
                rootWidth in 1 until rootHeight -> standardTiers.firstOrNull { it >= rootWidth } ?: rootWidth
                rootWidth > rootHeight -> standardTiers.firstOrNull { it >= rootHeight } ?: rootHeight
                else -> rootHeight
            }
            listOf(VideoResolution(rootHeight, tier))
        } else {
            found
        }
        // Satu label per tier: ambil tinggi terbesar agar filter `height<=?` tetap menyertakan format itu.
        return resolved.groupBy { it.tier }
            .map { (tier, list) -> VideoResolution(list.maxOf { it.height }, tier) }
            .sortedByDescending { it.tier }
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
