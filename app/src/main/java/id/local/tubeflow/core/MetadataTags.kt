package id.local.tubeflow.core

/** meta_* overrides are required: yt-dlp otherwise falls back from artist to creator/uploader. */
object MetadataTags {
    fun arguments(identity: SongIdentity): List<String> = buildList {
        val tags = mapOf("title" to identity.lyricTitle, "artist" to identity.artist.orEmpty(),
            "creator" to identity.creator, "remixer" to identity.remixer)
        tags.forEach { (key, value) ->
            val literal = value.replace("\\", "").replace('\n', ' ').replace('\r', ' ').trim()
                .replace("%", "%%").replace(":", "\\:")
            add("--parse-metadata")
            // A non-field prefix keeps alphabetic values literal; .* deliberately allows an empty artist.
            add("TubeFlow=$literal:(?s)^TubeFlow=(?P<meta_$key>.*)$")
        }
    }
}
