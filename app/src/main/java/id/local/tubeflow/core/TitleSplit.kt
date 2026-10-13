package id.local.tubeflow.core

/** Artist and uploader/creator are different roles. Empty evidence means an unresolved artist. */
data class SongIdentity(
    val title: String,
    val artist: String?,
    val versionTag: String = "",
    val creator: String = "",
    val remixer: String = "",
    val evidence: String = "",
) {
    val lyricTitle: String get() = listOf(title, versionTag).filter { it.isNotBlank() }.joinToString(" | ")
}

object TitleSplit {
    private val spaces = Regex("\\s+")
    private val groups = Regex("""\s*[(\[]([^\)\]]*)(?:[)\]]|$)""")
    private val version = Regex("""(?i)\b(live|acoustic|acustik|unplugged|remix|rmx|cover|instrumental|karaoke|slowed|slow|sped(?:\s+up)?|speed|nightcore|reverb|8d|extended|demo|session|edit|bootleg|mashup|megamix|dj|feat\.?|ft\.?)\b""")
    private val promoGroup = Regex("""(?i)official|visuali[sz]er|lyric|\b[48]k\b|\bhd\b|remaster|dc musik|sampek tuwek""")
    private val promoTail = Regex("""(?i)\s*(?:[-|]\s*)?(?:official\s+(?:music\s+)?(?:video|audio)|viral(?:\s+tiktok)?|full\s+(?:song|album)|\d{4}\s*hd)\s*$""")
    private val topic = Regex("""(?i)\s*-\s*Topic$""")
    private fun clean(value: String) = value.replace(spaces, " ").trim(' ', '-', '|', ':')
    private fun norm(value: String) = value.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
    private fun knownName(value: String) = clean(value).replace(Regex("""(?i)\s*\(?official\)?$"""), "").trim()

    fun identify(rawTitle: String, channel: String? = null, metadataArtist: String? = null,
                 metadataTrack: String? = null, metadataCreator: String? = null): SongIdentity {
        var text = clean(rawTitle)
        val bits = linkedSetOf<String>()
        fun withoutGroups(value: String) = groups.replace(value) { match ->
            val inner = clean(match.groupValues[1])
            when {
                version.containsMatchIn(inner) -> {
                    bits += clean(inner.replace(Regex("""(?i)\b(?:official|music|video|audio)\b"""), " "))
                    " "
                }
                promoGroup.containsMatchIn(inner) -> " "
                else -> match.value
            }
        }
        text = withoutGroups(text)
        text = clean(text)
        while (promoTail.containsMatchIn(text)) text = clean(text.replace(promoTail, ""))
        val segments = text.split(Regex("\\s+[-–—]\\s+")).map(::clean).filter { it.isNotEmpty() }
        val chan = knownName(channel.orEmpty().replace(topic, ""))
        var artist = metadataArtist?.takeUnless { it.isBlank() || it == "null" }?.let(::knownName)
        var evidence = if (artist != null) "metadata_artist" else ""
        var remixer = ""
        val isRemix = version.findAll(text).any { it.value.lowercase() in setOf("dj", "remix", "rmx", "mashup", "bootleg") } || bits.any { version.containsMatchIn(it) }
        // Explicit remix credit is retained as a remixer, never promoted to the original artist.
        if (segments.size > 1 && isRemix) {
            val credit = segments.last()
            if (Regex("(?i)\\b(dj|rmx|remix|remixer)\\b").containsMatchIn(credit)) remixer = credit
        }
        // A remix uploader can corroborate a remix credit, but never supplies the original artist.
        if (remixer.isBlank() && isRemix && Regex("(?i)\\b(rmx|remix)\\b").containsMatchIn(chan)) {
            val base = chan.replace(Regex("(?i)\\s+(rmx|remix)$"), "").trim()
            if (base.isNotBlank() && Regex("(?i)\\b${Regex.escape(base)}(?:\\s+\\d{4})?$").containsMatchIn(text)) {
                remixer = chan
                text = clean(text.replace(Regex("(?i)\\b${Regex.escape(base)}(?:\\s+\\d{4})?$"), ""))
                while (promoTail.containsMatchIn(text)) text = clean(text.replace(promoTail, ""))
            }
        }
        if (artist == null && channel?.let { topic.containsMatchIn(it) } == true && chan.isNotEmpty() && !isRemix) {
            artist = chan; evidence = "topic_channel"
        }
        // Exact, complete credit at a boundary corroborates a channel name. No dropped tokens/substrings.
        if (artist == null && chan.isNotBlank() && !isRemix && segments.size > 1 &&
            (norm(segments.first()) == norm(chan) || norm(segments.last()) == norm(chan))) {
            artist = chan; evidence = "title_channel_credit"
        }
        if (artist == null && chan.isNotBlank() && !isRemix && metadataTrack?.isNotBlank() == true &&
            norm(text) == norm("$metadataTrack - $chan")) {
            artist = chan; evidence = "track_channel_credit"
        }
        val credit = remixer.ifBlank { artist.orEmpty() }
        fun withoutCredit(value: String): String {
            if (credit.isBlank()) return value
            val escaped = Regex.escape(credit)
            var result = value.replace(Regex("(?i)^$escaped\\s*[-–—]\\s*|\\s*[-–—]\\s*$escaped$"), "")
            // A verified artist can be removed at an inline suffix; never strip a name from the middle.
            if (artist != null && metadataArtist?.isNotBlank() == true)
                result = result.replace(Regex("(?i)\\s+$escaped$"), "")
            return result
        }
        text = withoutCredit(text)
        if (text.startsWith("DJ ", ignoreCase = true)) { bits += "DJ"; text = text.substring(3) }
        // A pipe may contain an alternative lyric/title, not just a version. Preserve that information.
        val parts = text.split('|').map(::clean).filter { it.isNotEmpty() }
        val trackParts = metadataTrack?.takeIf { it.isNotBlank() && it != "null" }?.let { raw ->
            var track = clean(withoutCredit(withoutGroups(raw)))
            while (promoTail.containsMatchIn(track)) track = clean(track.replace(promoTail, ""))
            if (track.startsWith("DJ ", ignoreCase=true)) { bits += "DJ"; track = track.substring(3) }
            track.split('|').map(::clean).filter { it.isNotEmpty() }
        }.orEmpty()
        var title = trackParts.firstOrNull() ?: parts.firstOrNull().orEmpty()
        trackParts.drop(1).forEach { bits += it }
        parts.drop(1).forEach { part ->
            var kept = part
            while (promoTail.containsMatchIn(kept)) kept = clean(kept.replace(promoTail, ""))
            if (kept.isNotBlank()) bits += kept
        }
        if (title.isBlank()) title = clean(rawTitle)
        return SongIdentity(title, artist, bits.joinToString(" · "), metadataCreator.orEmpty().ifBlank { channel.orEmpty() }, remixer, evidence)
    }

    fun display(identity: SongIdentity): String {
        val head = identity.lyricTitle.ifBlank { "Tanpa judul" }
        val credit = identity.artist.orEmpty().ifBlank { identity.remixer }
        return if (credit.isBlank()) head else "$head - $credit"
    }
}
