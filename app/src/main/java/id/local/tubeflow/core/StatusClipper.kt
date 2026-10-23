package id.local.tubeflow.core

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * Pemotong klip 30 detik untuk WhatsApp Status. Sumber dibaca lewat ContentResolver karena
 * FFmpeg tidak bisa membuka `content://`; hasil sementara berada di cache privat dan dibagikan
 * lewat FileProvider. Satu proses FFmpeg berjalan pada satu waktu.
 */
class StatusClipper(private val context: Context) {
    private companion object {
        val gate = Mutex()
        const val COPY_TIMEOUT_SECONDS = 120L
        const val ENCODE_TIMEOUT_SECONDS = 240L
        const val KEEP_CLIPS = 6
        const val MAX_AGE_MS = 24L * 60 * 60 * 1000
        const val COPY_READY_PERCENT = 45
    }

    /**
     * @param onProgress persentase 0..100 saat menyalin, `null` saat FFmpeg bekerja tanpa progres
     * pasti; callback dipanggil dari thread IO.
     */
    suspend fun clip(item: MediaItem, startMs: Long, onProgress: (Int?, String) -> Unit = { _, _ -> }): File =
        withContext(Dispatchers.IO) {
            gate.withLock {
                require(item.kind == "VIDEO") { "Pemotong Status hanya untuk video." }
                require(item.availability == "AVAILABLE") { "File video tidak dapat diakses." }
                val directory = File(context.cacheDir, "status-clips").apply { mkdirs() }
                prune(directory)
                val input = File.createTempFile("source-", ".${sourceExtension(item.displayName)}", directory)
                val output = File(directory, "status-${System.currentTimeMillis()}.mp4")
                try {
                    val sourceUri = item.location.toUri()
                    copySource(sourceUri, input, item.sizeBytes, onProgress)
                    val durationMs = item.durationMs?.takeIf { it > 0 } ?: probe(input)?.durationMs
                        ?: error("Durasi video belum diketahui.")
                    val start = StatusClip.startMs(durationMs, startMs)
                    val expected = StatusClip.expectedDurationMs(durationMs, start)
                    onProgress(null, "Memotong video…")
                    val copied = streamCopyWorks(sourceUri, input, output, start, durationMs, expected)
                    if (!copied) {
                        output.delete()
                        onProgress(null, "Mengenkode ulang agar kompatibel…")
                        runFfmpeg(StatusClip.encodeArguments(input, output, start, durationMs), directory, ENCODE_TIMEOUT_SECONDS)
                        val encoded = probe(output)
                        require(encoded != null && encoded.hasVideo && encoded.durationMs > 0) {
                            "Hasil klip tidak dapat dibaca."
                        }
                    }
                    onProgress(100, "Klip siap dibagikan.")
                    output
                } catch (error: Exception) {
                    output.delete()
                    throw error
                } finally {
                    input.delete()
                }
            }
        }

    private suspend fun streamCopyWorks(
        source: Uri,
        input: File,
        output: File,
        startMs: Long,
        durationMs: Long,
        expectedMs: Long,
    ): Boolean {
        if (!canStreamCopy(source)) return false
        return runCatching {
            runFfmpeg(StatusClip.copyArguments(input, output, startMs, durationMs), output.parentFile!!, COPY_TIMEOUT_SECONDS)
            val result = probe(output) ?: return@runCatching false
            result.hasVideo && result.durationMs in
                (expectedMs - StatusClip.COPY_SLOP_MS).coerceAtLeast(1)..(expectedMs + StatusClip.COPY_SLOP_MS)
        }.getOrDefault(false)
    }

    /** Hanya H.264/AAC yang aman di-stream-copy ke MP4 untuk WhatsApp. */
    private fun canStreamCopy(uri: Uri): Boolean = runCatching {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
            var videoMime: String? = null
            var audioMime: String? = null
            for (index in 0 until extractor.trackCount) {
                val mime = extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("video/") && videoMime == null) videoMime = mime
                if (mime.startsWith("audio/") && audioMime == null) audioMime = mime
            }
            videoMime == "video/avc" && (audioMime == null || audioMime == "audio/mp4a-latm")
        } finally {
            extractor.release()
        }
    }.getOrDefault(false)

    private suspend fun copySource(
        source: Uri,
        target: File,
        declaredSize: Long?,
        onProgress: (Int?, String) -> Unit,
    ) {
        val total = declaredSize?.takeIf { it > 0 } ?: querySize(source)
        val resolver = context.contentResolver
        val stream = resolver.openInputStream(source) ?: error("File video tidak dapat dibuka.")
        var copied = 0L
        var lastPercent = -1
        try {
            stream.use { input ->
                FileOutputStream(target).use { output ->
                    val buffer = ByteArray(256 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        copied += read
                        if (total != null && total > 0) {
                            val percent = (copied * COPY_READY_PERCENT / total).toInt().coerceIn(0, COPY_READY_PERCENT)
                            if (percent != lastPercent) {
                                lastPercent = percent
                                onProgress(percent, "Menyalin video…")
                            }
                        }
                    }
                }
            }
        } catch (error: Exception) {
            target.delete()
            throw error
        }
        require(copied > 0) { "File video kosong." }
        if (lastPercent < 0) onProgress(COPY_READY_PERCENT, "Video disalin.")
    }

    private fun querySize(uri: Uri): Long? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
        }
    }.getOrNull()

    private fun probe(file: File): ClipProbe? = runCatching {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.path)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0
            val hasVideo = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes"
            ClipProbe(duration, hasVideo)
        } finally {
            retriever.release()
        }
    }.getOrNull()

    private suspend fun runFfmpeg(arguments: List<String>, directory: File, timeoutSeconds: Long) {
        currentCoroutineContext().ensureActive()
        Engine.initialize(context)
        val binary = File(context.applicationInfo.nativeLibraryDir, "libffmpeg.so")
        require(binary.isFile) { "Mesin FFmpeg tidak tersedia." }
        val log = File(directory, "ffmpeg.log")
        val builder = ProcessBuilder(
            listOf(binary.path, "-nostdin", "-hide_banner", "-loglevel", "error", "-y", "-threads", "2") + arguments,
        ).redirectErrorStream(true).redirectOutput(log)
        val packages = File(context.noBackupFilesDir, "youtubedl-android/packages")
        builder.environment()["LD_LIBRARY_PATH"] = (listOf(context.applicationInfo.nativeLibraryDir) +
            listOf("python", "ffmpeg", "aria2c").map { File(packages, "$it/usr/lib").path }).joinToString(":")
        val process = builder.start()
        try {
            var waited = 0L
            while (!process.waitFor(500, TimeUnit.MILLISECONDS)) {
                currentCoroutineContext().ensureActive()
                waited += 500
                if (waited > timeoutSeconds * 1000) error("Pemotongan video melewati batas waktu.")
            }
            currentCoroutineContext().ensureActive()
            if (process.exitValue() != 0) {
                val detail = runCatching { log.readLines().lastOrNull { it.isNotBlank() }?.take(160) }.getOrNull()
                error("FFmpeg gagal memotong video." + (detail?.let { " ($it)" } ?: ""))
            }
        } finally {
            process.destroyForcibly()
            process.waitFor(5, TimeUnit.SECONDS)
        }
    }

    private fun prune(directory: File) {
        val files = directory.listFiles()?.filter { it.isFile }?.sortedByDescending { it.lastModified() } ?: return
        files.drop(KEEP_CLIPS).forEach { it.delete() }
        val cutoff = System.currentTimeMillis() - MAX_AGE_MS
        files.filter { it.lastModified() < cutoff }.forEach { it.delete() }
    }

    private fun sourceExtension(displayName: String): String =
        displayName.substringAfterLast('.', "").lowercase().takeIf { it.matches(Regex("[a-z0-9]{1,5}")) } ?: "mp4"

    private data class ClipProbe(val durationMs: Long, val hasVideo: Boolean)
}

/** Membuka pemilih bagikan; WhatsApp diprioritaskan, chooser dipakai bila tidak terpasang. */
fun shareStatusClip(context: Context, file: File): Boolean {
    require(file.isFile) { "Klip belum tersedia." }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.statusclips", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "video/mp4"
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newRawUri("status", uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    for (packageName in listOf("com.whatsapp", "com.whatsapp.w4b")) {
        if (launch(context, Intent(send).setPackage(packageName))) return true
    }
    return launch(context, Intent.createChooser(send, "Bagikan status ke WhatsApp"))
}

private fun launch(context: Context, intent: Intent): Boolean {
    if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return try {
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}
