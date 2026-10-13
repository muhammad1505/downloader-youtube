package id.local.tubeflow.core

import android.content.Context
import android.util.AtomicFile
import androidx.core.net.toUri
import java.io.File
import java.security.MessageDigest

/** Retry deletion of known, abandoned publication URIs without changing a newer job's journal. */
object StalePublicationCleanup {
    private val lock = Any()
    private fun directory(context: Context) = File(context.noBackupFilesDir, "publication-cleanup")
    private fun record(context: Context, raw: String): AtomicFile {
        val name = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray()).joinToString("") { "%02x".format(it) }
        return AtomicFile(File(directory(context).apply { mkdirs() }, "$name.uri"))
    }
    fun delete(context: Context, raw: String): Boolean = synchronized(lock) {
        val journal = record(context, raw)
        val saved = runCatching {
            val stream = journal.startWrite()
            try { stream.write(raw.toByteArray()); journal.finishWrite(stream) }
            catch (failure: Exception) { journal.failWrite(stream); throw failure }
        }
        val deleted = StorageUris.delete(context, raw)
        if (deleted) journal.delete() else saved.getOrThrow()
        deleted
    }
    fun recover(context: Context) = synchronized(lock) {
        directory(context).listFiles().orEmpty().filter { it.name.endsWith(".uri") }.take(4000).forEach { file ->
            runCatching {
                val journal = AtomicFile(file)
                require(file.length() <= 16384)
                val raw = journal.openRead().use { String(it.readBytes(), Charsets.UTF_8) }
                require(raw.toUri().scheme == "content")
                if (StorageUris.delete(context, raw)) journal.delete()
            }
        }
    }
}
