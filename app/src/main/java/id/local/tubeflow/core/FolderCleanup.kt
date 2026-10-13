package id.local.tubeflow.core

import java.util.concurrent.CancellationException

/** Bounded, bottom-up cleanup. Unreadable or nonempty directories are never deleted. */
object FolderCleanup {
    data class Entry(val id: String, val directory: Boolean)
    data class Result(val files: Int, val folders: Int, val failures: Int, val clean: Boolean)

    fun clear(root: String, list: (String) -> List<Entry>, delete: (String) -> Boolean,
              maxDepth: Int = 8, maxEntries: Int = 4000): Result {
        var files = 0
        var folders = 0
        var failures = 0
        var visited = 0
        val seen = mutableSetOf(root)
        fun read(id: String): List<Entry>? = try { list(id) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failures++; null }
        fun remove(id: String): Boolean = try { delete(id).also { if (!it) failures++ } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failures++; false }
        fun walk(id: String, depth: Int): Boolean {
            if (depth > maxDepth) { failures++; return false }
            val entries = read(id) ?: return false
            var complete = true
            for (entry in entries) {
                if (++visited > maxEntries) { failures++; complete = false; break }
                if (entry.id.isBlank() || !seen.add(entry.id)) { failures++; complete = false; continue }
                if (entry.directory && !walk(entry.id, depth + 1)) { complete = false; continue }
                if (remove(entry.id)) {
                    if (entry.directory) folders++ else files++
                } else complete = false
            }
            if (!complete) return false
            val remaining = read(id) ?: return false
            if (remaining.isNotEmpty()) { failures++; return false }
            return true
        }
        val empty = walk(root, 0)
        return Result(files, folders, failures, empty && failures == 0)
    }
}
