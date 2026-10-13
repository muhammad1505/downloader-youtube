package id.local.tubeflow.core

import java.io.File

/** Evict private cache entries, preserving the current result and files still being written. */
object CacheBudget {
    @Synchronized fun prune(directory: File, bytesLimit: Long, entriesLimit: Int = Int.MAX_VALUE, keep: Set<File> = emptySet()): Long {
        require(bytesLimit >= 0 && entriesLimit > 0)
        val files = directory.listFiles()?.filter { it.isFile && !it.name.endsWith(".tmp") }?.sortedBy(File::lastModified).orEmpty()
        var total = files.sumOf(File::length); var count = files.size
        for (file in files) if ((total > bytesLimit || count > entriesLimit) && file !in keep) {
            val size = file.length()
            if (file.delete()) { total -= size; count-- }
        }
        // A process can die before its atomic cache rename. Never delete a recent writer's temporary.
        directory.listFiles()?.filter { it.isFile && it.name.endsWith(".tmp") && System.currentTimeMillis() - it.lastModified() > 86400000L }?.forEach(File::delete)
        return total
    }
}
