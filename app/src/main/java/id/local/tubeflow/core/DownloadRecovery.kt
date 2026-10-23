package id.local.tubeflow.core

/**
 * Recovery keeps journaled URIs whose deletion was refused. Clearing a journal after a failed
 * delete loses the only reference to the file (BUG-003/008).
 */
internal object DownloadRecovery {
    /** True only when the journaled output URI is gone or was deleted. */
    fun clearPendingAfterDelete(job: DownloadJob, delete: (String) -> Boolean): Boolean =
        job.pendingUri?.let { deleted(it, delete) } ?: false

    /** Failed sidecars stay journaled; `null` means every entry was removed. */
    fun retainedSidecarPending(job: DownloadJob, delete: (String) -> Boolean): String? =
        job.sidecarPending
            ?.lines()
            ?.filter { it.isNotBlank() }
            ?.filterNot { deleted(it, delete) }
            ?.takeIf { it.isNotEmpty() }
            ?.joinToString("\n")

    /** Orphan sidecars keep their `kind|uri` lines until deletion succeeds. */
    fun retainedOrphanSidecars(job: DownloadJob, delete: (String) -> Boolean): String? {
        if (job.outputUri != null) return job.sidecarUris
        val lines = job.sidecarUris?.lines()?.filter { it.isNotBlank() } ?: return null
        val retained = lines.filter { line ->
            val raw = line.substringAfter('|', "")
            raw.isBlank() || !deleted(raw, delete)
        }
        return retained.takeIf { it.isNotEmpty() }?.joinToString("\n")
    }

    private fun deleted(raw: String, delete: (String) -> Boolean): Boolean =
        runCatching { delete(raw) }.getOrDefault(false)
}
