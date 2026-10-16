package id.local.tubeflow.core

/** Decisions shared by request submission and the result actions shown in the dialog. */
object KaraokeProcessPolicy {
    fun startBlocked(trackedUnfinished: Boolean, uniqueUnfinished: Boolean): Boolean =
        trackedUnfinished || uniqueUnfinished

    /** A ready result belongs to one exact song, mode and clip position. */
    fun matchesSelection(sourceUri: String?, selectedUri: String?, sample: Boolean, requestedSample: Boolean,
        startMs: Long, requestedStartMs: Long?): Boolean =
        !sourceUri.isNullOrBlank() && sourceUri == selectedUri && sample == requestedSample &&
            (!sample || requestedStartMs == startMs)
}
