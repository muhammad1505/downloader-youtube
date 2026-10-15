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
    private val promoSegment = Regex("""(?i)^(?:dc musik|official(?: music)?(?: video| audio)?|lyrics?(?: video)?|[48]k|hd)$""")
    private val topic = Regex("""(?i)\s*-\s*Topic$""")
    private fun clean(value: String) = value.replace(spaces, " ").trim(' ', '-', '|', ':')
    private fun norm(value: String) = value.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
    private fun knownName(value: String) = clean(value).replace(Regex("""(?i)\s*\(?official\)?$"""), "").trim()

    private val collaboration = Regex("""\s+(?:x|xx|ft\.?|feat(?:uring)?\.?|vs\.?)\s+|\s*[&+]\s*""", RegexOption.IGNORE_CASE)
    fun primaryArtist(credit: String): String = credit.split(collaboration).first().trim()
    fun creditKey(value: String): String = value.lowercase(java.util.Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}]"), "")
    fun channelMatches(credit: String, channel: String): Boolean {
        var key = creditKey(channel.replace(topic, ""))
        val suffix = Regex("(?i)(official|vevo|tv|music|channel|records)$")
        while (suffix.containsMatchIn(key)) key = key.replace(suffix, "")
        return key.isNotBlank() && key == creditKey(credit)
    }
    fun isRemixTitle(title: String): Boolean = Regex("(?i)\\b(dj|remix|rmx|bootleg|mashup|megamix|slowed|sped|nightcore|reverb)\\b").containsMatchIn(title)
    private fun withoutPromoSegments(value: String): String = value
        .split(Regex("\\s+[-–—]\\s+"))
        .map(::clean).filterNot { promoSegment.matches(it) }.joinToString(" - ")

    fun segments(raw: String): List<String> {
        var text = groups.replace(raw) { if (version.containsMatchIn(it.groupValues[1]) || promoGroup.containsMatchIn(it.groupValues[1])) " " else it.value }
        while (promoTail.containsMatchIn(text)) text = clean(text.replace(promoTail, ""))
        return withoutPromoSegments(text).split(Regex("\\s+[-–—]\\s+")).map(::clean).filter { it.isNotBlank() }
    }

    fun identify(rawTitle: String, channel: String? = null, metadataArtist: String? = null,
                 metadataTrack: String? = null, metadataCreator: String? = null, recognizeCollaboration: Boolean = true): SongIdentity {
        var text = clean(rawTitle)
        val bits = linkedSetOf<String>()
        fun withoutGroups(value: String) = groups.replace(value) { match ->
            val inner = clean(match.groupValues[1])
            when {
                version.containsMatchIn(inner) -> {
                    bits += clean(inner.replace(Regex("""(?i)\b(?:official|offical|music|video|audio)\b"""), " "))
                    " "
                }
                promoGroup.containsMatchIn(inner) -> " "
                else -> match.value
            }
        }
        text = withoutGroups(text)
        text = clean(text)
        while (promoTail.containsMatchIn(text)) text = clean(text.replace(promoTail, ""))
        text = withoutPromoSegments(text)
        val segments = text.split(Regex("\\s+[-–—]\\s+")).map(::clean).filter { it.isNotEmpty() }
        val chan = knownName(channel.orEmpty().replace(topic, ""))
        var artist = metadataArtist?.takeUnless { it.isBlank() || creditKey(it) in setOf("null", "unknown", "unknownartist") }?.let(::knownName)
        var evidence = if (artist != null) "metadata_artist" else ""
        var remixer = ""
        val isRemix = isRemixTitle(rawTitle)
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
        if (artist == null && channel?.let { topic.containsMatchIn(it) } == true && chan.isNotEmpty() && creditKey(chan) !in setOf("variousartists", "artisberagam", "unknown", "unknownartist", "null") && !isRemix) {
            artist = chan; evidence = "topic_channel"
        }
        // Exact, complete credit at a boundary corroborates a channel name. No dropped tokens/substrings.
        if (artist == null && chan.isNotBlank() && !isRemix && segments.size > 1 &&
            segments.filter { channelMatches(it, chan) }.size == 1) {
            artist = segments.single { channelMatches(it, chan) }; evidence = "title_channel_credit"
        }
        if (artist == null && chan.isNotBlank() && !isRemix && metadataTrack?.isNotBlank() == true &&
            norm(text) == norm("$metadataTrack - $chan")) {
            artist = chan; evidence = "track_channel_credit"
        }
        // Explicit collaboration at one boundary corroborates a performer credit; ambiguous sides stay unresolved.
        if (recognizeCollaboration && artist == null && !isRemix && bits.none { LyricMatch.versionSet(it).contains("cover") } && segments.size == 2) {
            val credits = segments.filter { side ->
                val names = side.split(collaboration)
                names.size > 1 && names.all { it.trim().length >= 2 } && !version.containsMatchIn(names.joinToString(" "))
            }
            if (credits.size == 1) { artist = credits.single(); evidence = "title_collaboration_credit" }
        }
        val credit = remixer.ifBlank { artist.orEmpty() }
        fun withoutCredit(value: String): String {
            var result = value
            // Original artist and remixer can both be explicit; strip each only at a boundary.
            for (name in listOfNotNull(artist,remixer.takeIf(String::isNotBlank)).distinct()) {
                val escaped = Regex.escape(name)
                result = result.replace(Regex("(?i)^$escaped\\s*[-–—]\\s*|\\s*[-–—]\\s*$escaped$"), "")
                if (name == artist && metadataArtist?.isNotBlank() == true)
                    result = result.replace(Regex("(?i)\\s+$escaped$"), "")
            }
            return result
        }
        text = withoutCredit(text)
        if (text.startsWith("DJ ", ignoreCase = true)) { bits += "DJ"; text = text.substring(3) }
        // A pipe may contain an alternative lyric/title, not just a version. Preserve that information.
        val parts = text.split('|').map(::clean).filter { it.isNotEmpty() }
        val trackParts = metadataTrack?.takeIf { it.isNotBlank() && it != "null" }?.let { raw ->
            var track = clean(withoutCredit(withoutPromoSegments(withoutGroups(raw))))
            while (promoTail.containsMatchIn(track)) track = clean(track.replace(promoTail, ""))
            if (track.startsWith("DJ ", ignoreCase=true)) { bits += "DJ"; track = track.substring(3) }
            track.split('|').map(::clean).filter { it.isNotEmpty() }
        }.orEmpty()
        var title = trackParts.firstOrNull() ?: parts.firstOrNull().orEmpty()
        trackParts.drop(1).filterNot { promoSegment.matches(it) || (credit.isNotBlank() && creditKey(it) == creditKey(credit)) }.forEach { bits += it }
        parts.drop(1).forEach { part ->
            var kept = part
            while (promoTail.containsMatchIn(kept)) kept = clean(kept.replace(promoTail, ""))
            if (kept.isNotBlank() && !promoSegment.matches(kept) && (credit.isBlank() || creditKey(kept) != creditKey(credit))) bits += kept
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
