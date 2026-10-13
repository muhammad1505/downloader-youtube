package id.local.tubeflow.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** A bounded, persistent preview, independent of whether embedding a cover was requested. */
object CoverCache {
    suspend fun fetch(context: Context, job: DownloadJob): String? = withContext(Dispatchers.IO) {
        job.coverPath.takeIf { it.isNotBlank() && File(it).isFile }?.let { existing ->
            File(existing).setLastModified(System.currentTimeMillis())
            return@withContext existing
        }
        if (!job.thumbnailUrl.startsWith("https://")) return@withContext null
        runCatching {
            val connection = URL(job.thumbnailUrl).openConnection() as HttpURLConnection
            connection.connectTimeout = 8000; connection.readTimeout = 8000
            val bytes = try {
                require(connection.responseCode in 200..299)
                connection.inputStream.use { input ->
                    val output = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(16384)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        require(output.size() + count <= 5 * 1024 * 1024)
                        output.write(buffer, 0, count)
                    }
                    output.toByteArray()
                }
            } finally { connection.disconnect() }
            save(context,job,bytes)
        }.getOrNull()
    }
    suspend fun fromMedia(context: Context, job: DownloadJob): String? = withContext(Dispatchers.IO) {
        val uri=job.outputUri ?: return@withContext null
        runCatching {
            val retriever=android.media.MediaMetadataRetriever()
            val bytes=try {
                retriever.setDataSource(context, uri.toUri())
                retriever.embeddedPicture
            } finally { retriever.release() }
            bytes?.takeIf { it.size <= 5 * 1024 * 1024 }?.let { save(context,job,it) }
        }.getOrNull()
    }
    suspend fun fromFile(context: Context, job: DownloadJob, file: File): String? = withContext(Dispatchers.IO) {
        runCatching { require(file.length() in 1..5L * 1024 * 1024); save(context, job, file.readBytes()) }.getOrNull()
    }
    /** Concurrent preview fetches may share a generation's file; a rejected writer must not delete it. */
    suspend fun deleteIfUnused(context: Context, jobId: String, path: String) {
        if (DownloadDatabase.get(context).downloads().get(jobId)?.coverPath != path) File(path).delete()
    }
    private fun save(context: Context, job: DownloadJob, bytes: ByteArray): String {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0)
        val options = BitmapFactory.Options().apply {
            inSampleSize = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / inSampleSize > 800) inSampleSize *= 2
        }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: error("Cover tidak dapat dibaca")
        val dir = File(context.noBackupFilesDir, "covers").apply { mkdirs() }
        val target = File(dir, "${job.id}-${job.generation}.jpg")
        val temporary = File.createTempFile("cover-", ".tmp", dir)
        return try {
            temporary.outputStream().use { require(bitmap.compress(Bitmap.CompressFormat.JPEG, 82, it)) }
            require(temporary.renameTo(target))
            CacheBudget.prune(dir, 32L * 1024 * 1024, keep = setOf(target))
            target.absolutePath
        } finally { bitmap.recycle(); temporary.delete() }
    }

}
