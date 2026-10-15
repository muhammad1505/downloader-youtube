package id.local.tubeflow.core

/** A renamed file is a new query hint, not proof that its second segment is an artist. */
internal object PublishedLyricIdentity {
    fun fromName(job: DownloadJob, name: String): SongIdentity {
        val original=job.songIdentity()
        val stem=name.substringBeforeLast('.')
        val expected=if (job.autoRename) Format.musicFileName(original)
            else Format.tidyFileName(job.rawTitle.ifBlank { job.title })
        if (stem == expected || Regex("^${Regex.escape(expected)} \\([1-9][0-9]*\\)$").matches(stem)) return original
        val verified=original.artist?.takeIf { credit ->
            TitleSplit.segments(stem).any { TitleSplit.creditKey(it) == TitleSplit.creditKey(credit) }
        }
        return TitleSplit.identify(stem,metadataArtist=verified,recognizeCollaboration=false)
    }
}
