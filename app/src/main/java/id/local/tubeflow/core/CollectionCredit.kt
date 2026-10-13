package id.local.tubeflow.core

/** Collection evidence is corroboration, never an artist inferred solely from title position/frequency. */
class CollectionCredit private constructor(private val artists: Map<String, String>, private val albumArtist: String?) {
    fun apply(meta: VideoMetadata): VideoMetadata {
        val identity = TitleSplit.identify(meta.title, meta.uploader, meta.artist, meta.track, meta.creator)
        if (!identity.artist.isNullOrBlank() || TitleSplit.isRemixTitle(meta.title) || "cover" in LyricMatch.versionSet(meta.title)) return meta
        val parts = TitleSplit.segments(meta.title)
        val matches = parts.mapNotNull { artists[TitleSplit.creditKey(it)] }.distinct()
        val artist = if (parts.size == 2) matches.singleOrNull() else null
        if (artist != null) return meta.copy(artist=artist, artistEvidence="collection_verified_credit")
        if (parts.size == 1 && albumArtist != null && !LyricMatch.versionSet(meta.title).contains("cover"))
            return meta.copy(artist=albumArtist, artistEvidence="collection_album_credit")
        return meta
    }

    companion object {
        fun learn(playlist: PlaylistMetadata): CollectionCredit {
            val metadata = playlist.entries.map { VideoMetadata(it.title, it.uploader, it.durationSeconds, it.thumbnail, it.url,
                artist=it.artist, track=it.track, creator=it.uploader) } + listOfNotNull(playlist.seedMetadata)
            val identities = metadata.map { TitleSplit.identify(it.title, it.uploader, it.artist, it.track, it.creator) }
            val proven = linkedMapOf<String, String>()
            identities.filter { !it.artist.isNullOrBlank() && it.evidence.isNotBlank() }.forEach { identity ->
                val artist = requireNotNull(identity.artist)
                if (TitleSplit.creditKey(artist) in setOf("variousartists", "artisberagam", "unknownartist", "unknown")) return@forEach
                proven[TitleSplit.creditKey(artist)] = artist
                val lead = TitleSplit.primaryArtist(artist)
                proven.putIfAbsent(TitleSplit.creditKey(lead), lead)
            }
            val album = Selection.listParam(playlist.url)?.startsWith("OLAK5uy_") == true
            val albumCredits = identities.mapNotNull { it.artist?.takeIf { name -> name.isNotBlank() } }.distinctBy(TitleSplit::creditKey)
            val albumArtist = albumCredits.singleOrNull()?.takeIf { artist -> album &&
                TitleSplit.creditKey(artist) !in setOf("variousartists", "artisberagam") &&
                (playlist.channel.isBlank() || TitleSplit.channelMatches(artist, playlist.channel)) }
            return CollectionCredit(proven, albumArtist)
        }
    }
}
