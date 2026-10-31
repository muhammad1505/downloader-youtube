package id.local.tubeflow.core

import android.app.ActivityManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.media.MediaMetadataRetriever
import android.provider.OpenableColumns
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import androidx.work.*
import id.local.tubeflow.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/** Persistent, explicitly requested processing. Never changes downloader status or its original file. */
class KaraokeWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    private val engine = KaraokeEngine(context)
    override suspend fun getForegroundInfo() = foreground("Menyiapkan karaoke", -1)
    private fun foreground(label: String, percent: Int): ForegroundInfo {
        applicationContext.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel("karaoke", "Pemrosesan karaoke", NotificationManager.IMPORTANCE_LOW))
        val notification = NotificationCompat.Builder(applicationContext, "karaoke")
            .setSmallIcon(R.drawable.ic_stat_karaoke).setContentTitle("TubeFlow · Karaoke")
            .setContentIntent(AppIntents.pending(applicationContext, AppDestination.KARAOKE))
            .setContentText(label).setOnlyAlertOnce(true).setOngoing(true).setProgress(100, percent.coerceAtLeast(0), percent < 0)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Batalkan", WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)).build()
        return ForegroundInfo(id.hashCode().let { if (it == 0) 1 else it }, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }
    private fun resultData(vararg values: Pair<String, Any?>): Data =
        Data.Builder().putAll(inputData).putAll(workDataOf(*values)).build()

    private suspend fun report(label: String, percent: Int = -1) {
        currentCoroutineContext().ensureActive()
        setProgress(resultData("phase" to label, "percent" to percent))
        setForeground(foreground(label, percent))
    }
    override suspend fun doWork(): Result = coroutineScope {
        val cancellation = launch(Dispatchers.IO) { try { awaitCancellation() } finally { engine.cancel() } }
        try { withContext(Dispatchers.IO) { lock.withLock {
        val directory = directory(applicationContext, id.toString())
        var complete = false
        try {
            require(runAttemptCount < 2) { "Sistem menghentikan proses karaoke berulang kali. Coba cuplikan atau kosongkan RAM." }
            report("Menyiapkan karaoke")
            // Resolve a pending publication before replacing any old private preview.
            KaraokePublication(applicationContext).recover()
            require(android.os.Process.is64Bit()) { "Karaoke membutuhkan aplikasi/perangkat 64-bit. Downloader tetap dapat digunakan." }
            val memory = ActivityManager.MemoryInfo().also { applicationContext.getSystemService(ActivityManager::class.java).getMemoryInfo(it) }
            require(!memory.lowMemory && memory.availMem >= 512L * 1024 * 1024) { "RAM tersedia kurang dari 512 MB. Tutup aplikasi lain lalu coba lagi." }
            val database = DownloadDatabase.get(applicationContext)
            require(database.downloads().activeCount() == 0) { "Tunggu unduhan selesai sebelum memproses karaoke." }
            val source = requireNotNull(inputData.getString("uri")).toUri()
            require(source.scheme == "content") { "Pilih file audio asli dari Koleksi atau Riwayat." }
            val resolver = applicationContext.contentResolver
            val name = resolver.query(source, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                require(cursor.moveToFirst()); cursor.getString(0)
            } ?: error("File sumber tidak tersedia.")
            require(SmartMedia.classify(resolver.getType(source).orEmpty(), name) == "AUDIO") { "Karaoke hanya memproses audio, bukan video." }
            val reader = MediaMetadataRetriever()
            val duration = try { reader.setDataSource(applicationContext, source); reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() }
                finally { reader.release() }
            require(duration != null && duration in 1..600000) { "Pilih audio berdurasi paling lama 10 menit." }
            val sample = inputData.getBoolean("sample", false)
            val requestedEnd = if (inputData.hasKeyWithValueOfType<Long>("endMs")) inputData.getLong("endMs", 0) else null
            val segment = KaraokeInterval.select(duration, inputData.getLong("startMs", 0), requestedEnd, sample)
            val startMs = segment.startMs
            val budget = duration * 44100 / 1000 * 20 + KaraokeModel.BYTES + 256L * 1024 * 1024
            require(android.os.StatFs(directory.path).availableBytes >= budget) { "Ruang kosong belum cukup untuk pemrosesan karaoke." }
            report("Mengunduh model UVR")
            val model = KaraokeModel.obtain(applicationContext) { report("Mengunduh model UVR", it) }
            report("Membaca audio")
            // Starting a new request replaces only old private previews; never touches published media.
            root(applicationContext).listFiles()?.filter { it.isDirectory && it != directory }?.forEach {
                require(KaraokeResult.discard(root(applicationContext), it.name)) {
                    "Pratinjau lama belum dapat dibersihkan. Buka status karaoke dan buang pratinjau terlebih dahulu."
                }
            }
            val original = File(directory, "source.bin")
            MediaFileOperations.mutex.withLock {
                resolver.openInputStream(source)?.use { input -> original.outputStream().use { output ->
                    val buffer = ByteArray(65536); var bytes = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = input.read(buffer); if (n < 0) break
                        bytes += n; require(bytes <= 256L * 1024 * 1024) { "File sumber melewati batas 256 MB." }
                        output.write(buffer, 0, n)
                    }
                } } ?: error("File sumber tidak dapat dibaca.")
            }
            val pcm = File(directory, "mix.f32")
            val interval = if (sample) listOf("-ss", (startMs / 1000.0).toString(), "-t", (segment.durationMs / 1000.0).toString()) else emptyList()
            engine.ffmpeg(directory, listOf("-i", original.path) + interval + listOf("-vn", "-ac", "2", "-ar", "44100", "-f", "f32le", pcm.path))
            val separated = File(directory, "instrumental.f32")
            report("Memisahkan vokal", 0)
            engine.separate(model, pcm, separated) { report("Memisahkan vokal", it) }
            report("Menyiapkan pratinjau")
            val result = File(directory, "instrumental.flac")
            engine.ffmpeg(directory, listOf("-f", "f32le", "-ar", "44100", "-ac", "2", "-i", separated.path,
                "-c:a", "flac", "-sample_fmt", "s16", "-metadata", "title=${Format.musicLabel(KaraokeNaming.identity(inputData.getString("title").orEmpty(), inputData.getString("artist").orEmpty(), sample))}",
                "-metadata", "artist=${inputData.getString("artist").orEmpty()}", result.path))
            require(result.length() > 0)
            val verify = MediaMetadataRetriever()
            val actual = try { verify.setDataSource(result.path); verify.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() } finally { verify.release() }
            val expected = pcm.length() / 8 * 1000 / 44100
            require(actual != null && kotlin.math.abs(actual - expected) <= 50) { "Verifikasi durasi hasil gagal." }
            val manifest = JSONObject().put("source", source.toString()).put("sourceName", name)
                .put("sourceSha256", KaraokeModel.digest(original)).put("resultSha256", KaraokeModel.digest(result))
                .put("modelSha256", KaraokeModel.SHA).put("durationMs", actual).put("sample", sample).put("startMs", startMs)
            File(directory, "result.json").writeText(manifest.toString())
            currentCoroutineContext().ensureActive()
            complete = true
            Result.success(resultData("path" to result.path, "name" to name, "resultBytes" to result.length(), "durationMs" to actual, "sample" to sample, "startMs" to startMs))
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { Result.failure(resultData("error" to (failure.message ?: "Pemrosesan karaoke gagal.").take(500))) }
        catch (_: LinkageError) { Result.failure(resultData("error" to "Runtime karaoke tidak dapat dimuat pada perangkat ini.")) }
        finally {
            engine.cancel()
            if (!KaraokeResult.discardModelPartials(KaraokeModel.directory(applicationContext)))
                android.util.Log.w("KaraokeCleanup", "Model fragments need another cleanup attempt")
            if (!complete || isStopped || !currentCoroutineContext().isActive) {
                if (!KaraokeResult.discard(root(applicationContext), id.toString()))
                    android.util.Log.w("KaraokeCleanup", "Private job data needs another cleanup attempt")
            }
            else directory.listFiles()?.filter { it.name !in setOf("instrumental.flac", "result.json") }?.forEach { it.delete() }
        }
    } } } finally { cancellation.cancel() }
    }
    companion object {
        const val TAG = "karaoke"
        const val UNIQUE = "karaoke-separation"
        val lock = Mutex()
        fun root(context: Context) = File(context.noBackupFilesDir, "karaoke-work").apply { mkdirs() }
        fun directory(context: Context, id: String): File {
            require(id.matches(Regex("[a-fA-F0-9-]{36}")))
            return File(root(context), id).apply { mkdirs() }
        }
    }
}
