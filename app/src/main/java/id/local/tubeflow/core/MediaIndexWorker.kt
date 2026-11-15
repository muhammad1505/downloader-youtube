package id.local.tubeflow.core

import android.content.Context
import androidx.work.*
import kotlinx.coroutines.CancellationException

/** Persistent indexing is separate from download completion and never waits in the downloader. */
class MediaIndexWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getString("jobId") ?: return Result.failure()
        return try {
            MediaRepository(applicationContext).indexDownload(id)
            AppLog.info(LogCategory.LIBRARY, "Indeks media selesai untuk ${id.take(8)}.")
            Result.success()
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
            if (runAttemptCount < 2) {
                AppLog.warn(LogCategory.LIBRARY, "Indeks media gagal; akan mencoba lagi.", error)
                Result.retry()
            } else {
                AppLog.error(LogCategory.LIBRARY, "Indeks media gagal setelah percobaan ulang.", error)
                val database = DownloadDatabase.get(applicationContext)
                database.downloads().get(id)?.let { job -> database.media().event(MediaEvent(jobId = id, action = "INDEX_FAILED", kind = job.mode.name, platformId = job.platformId, at = System.currentTimeMillis())) }
                Result.failure()
            }
        }
    }
    companion object {
        const val TAG = "media-index"
        fun enqueue(context: Context, id: String) {
            val request = OneTimeWorkRequestBuilder<MediaIndexWorker>().setInputData(workDataOf("jobId" to id)).addTag(TAG).build()
            WorkManager.getInstance(context).enqueueUniqueWork("media-index-$id", ExistingWorkPolicy.KEEP, request)
        }
    }
}
