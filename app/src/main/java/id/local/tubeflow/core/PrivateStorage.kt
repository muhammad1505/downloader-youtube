package id.local.tubeflow.core

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.sync.Mutex

/** Shared lease extends through worker finally; WorkManager CANCELLED alone is not an idle signal. */
object DownloadFileAccess {
    private val locks = Array(64) { Mutex() }
    fun lock(id: String): Mutex = locks[(id.hashCode() and Int.MAX_VALUE) % locks.size]
}

object PrivateStorage {
    data class Size(val bytes: Long, val complete: Boolean)
    data class Usage(val cache: Size, val partial: Size)
    private const val MAX_ENTRIES = 20000

    /** Walks only regular files beneath a private root, never following a symlink. */
    fun measure(root: File, maxEntries: Int = MAX_ENTRIES): Size {
        if (Files.notExists(root.toPath()) && !Files.isSymbolicLink(root.toPath())) return Size(0, true)
        var total = 0L; var complete = true; var visited = 0
        val waiting = java.util.ArrayDeque<Pair<File,Int>>(); waiting.add(root to 0)
        while (waiting.isNotEmpty()) {
            if (++visited > maxEntries) { complete = false; break }
            val (file,depth) = waiting.removeLast()
            if (depth > 32) { complete = false; continue }
            if (Files.isSymbolicLink(file.toPath())) { complete = false; continue }
            if (file.isFile) total = try { Math.addExact(total,file.length()) } catch (_: ArithmeticException) { complete = false; total }
            else if (file.isDirectory) {
                val children = file.listFiles()
                if (children == null) complete = false
                else if (waiting.size + children.size > maxEntries) { complete = false; break }
                else children.forEach { waiting.add(it to depth+1) }
            } else complete = false
        }
        return Size(total,complete)
    }

    /** Validate an app-owned job directory before deletion. Refuse incomplete traversal or links. */
    fun deleteJob(downloads: File, directory: File): Boolean {
        if (Files.isSymbolicLink(downloads.toPath()) || Files.isSymbolicLink(directory.toPath())) return false
        if (directory.parentFile?.canonicalFile != downloads.canonicalFile ||
            !directory.name.matches(Regex("[A-Za-z0-9_-]{1,100}"))) return false
        if (!measure(directory).complete) return false
        fun remove(file: File): Boolean {
            if (Files.isSymbolicLink(file.toPath())) return false
            if (file.isDirectory) {
                val children = file.listFiles() ?: return false
                if (!children.all(::remove)) return false
            }
            return !file.exists() || file.delete()
        }
        return remove(directory)
    }

    fun summary(cacheRoot: File, privateRoot: File): Usage {
        val caches = listOf(File(cacheRoot,"lyrics-responses"),File(cacheRoot,"extractor-cache"),
            File(privateRoot,"covers"),File(privateRoot,"library-covers")).map { measure(it) }
        return Usage(Size(caches.sumOf { it.bytes },caches.all { it.complete }),measure(File(privateRoot,"downloads")))
    }
}
