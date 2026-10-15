package id.local.tubeflow.core

/** Keep derived-version information before the verified artist, using the shared naming rules. */
object KaraokeNaming {
    fun identity(title: String, artist: String, sample: Boolean): SongIdentity {
        val original = TitleSplit.identify(title, metadataArtist = artist, recognizeCollaboration = false)
        val version = if (sample) "Karaoke cuplikan" else "Karaoke"
        return original.copy(versionTag = listOf(original.versionTag, version).filter { it.isNotBlank() }.distinct().joinToString(", "))
    }
    fun fileName(title: String, artist: String, sample: Boolean): String = Format.musicFileName(identity(title, artist, sample)) + ".flac"
}
