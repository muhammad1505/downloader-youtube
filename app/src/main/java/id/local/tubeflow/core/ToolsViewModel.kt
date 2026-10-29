package id.local.tubeflow.core

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

/**
 * Satu pekerjaan alat berjalan pada satu waktu. Operasi massal memakai jalur yang sudah ada
 * ([MediaRepository.rename], [MediaRepository.applyCatalogTag], dan [KaraokeWorker]), sehingga
 * verifikasi nama, jurnal pemulihan, pemeriksaan baris, serta pemisahan vokal tetap sama dengan
 * jalur satu berkas. Hasil dilaporkan per berkas: satu kegagalan tidak membatalkan sisanya.
 */
class ToolsViewModel(app: Application) : AndroidViewModel(app) {
    private val database = DownloadDatabase.get(app)
    private val dao = database.media()
    private val repository = MediaRepository(app)
    private val work = WorkManager.getInstance(app)
    private val activeWork = AtomicReference<UUID?>(null)

    enum class Kind { RENAME, TAG, KARAOKE }

    data class Progress(
        val kind: Kind,
        val label: String,
        val done: Int,
        val total: Int,
        val failures: List<String> = emptyList(),
        val finished: Boolean = false,
        val cancelled: Boolean = false,
    ) {
        val running: Boolean get() = !finished
        val succeeded: Int get() = (done - failures.size).coerceAtLeast(0)
    }

    val audio = dao.audioItems().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _progress = MutableStateFlow<Progress?>(null)
    val progress = _progress.asStateFlow()
    private var worker: Job? = null

    val busy: Boolean get() = worker?.isActive == true

    /** Batalkan pekerjaan berjalan, termasuk job WorkManager karaoke yang sedang diproses. */
    fun cancel() {
        worker?.cancel()
        activeWork.get()?.let { id -> work.cancelWorkById(id) }
    }

    fun dismissProgress() { if (_progress.value?.finished == true) _progress.value = null }

    fun rename(plan: RenamePlan.Plan) {
        val targets = plan.proposals.filter { it.applies }.associate { it.uri to it.after }
        start(Kind.RENAME, targets.keys.toList()) { uri ->
            val item = dao.get(uri) ?: error("Media sudah tidak ada di koleksi.")
            repository.rename(item, targets.getValue(uri))
        }
    }

    fun tag(plan: List<TagSuggestion.Proposal>) {
        val targets = plan.filter { it.changed }.associateBy { it.uri }
        start(Kind.TAG, targets.keys.toList()) { uri ->
            val proposal = targets.getValue(uri)
            require(repository.applyCatalogTag(TagUpdate(uri, proposal.displayName, proposal.title, proposal.artist))) {
                "Baris koleksi berubah; buka ulang alat ini."
            }
        }
    }

    /** Massal: setiap lagu diproses berurutan dan hasilnya langsung dipublikasikan ke Koleksi. */
    fun instrumental(items: List<MediaItem>) {
        start(Kind.KARAOKE, items.map { it.uri }) { uri -> processInstrumental(uri) }
    }

    private suspend fun processInstrumental(uri: String) {
        val item = dao.get(uri) ?: error("Media sudah tidak ada di koleksi.")
        require(item.kind == "AUDIO" && item.availability == "AVAILABLE") { "Hanya audio yang tersedia yang dapat dipisahkan." }
        val track = PlayerViewModel.Track(
            item.location,
            Format.musicLabel(TitleSplit.identify(item.title, metadataArtist = item.artist, recognizeCollaboration = false)),
            item.artist, item.coverPath, item.lyricUri,
        )
        // Permintaan penuh (bukan cuplikan) dan tanpa menyentuh audio asli; worker menangani
        // notifikasi, model UVR, batas RAM/ruang, dan pembersihan privatnya sendiri.
        val request = OneTimeWorkRequestBuilder<KaraokeWorker>()
            .setInputData(
                workDataOf(
                    "uri" to track.uri, "title" to track.title.take(500), "artist" to track.artist.take(300),
                    "cover" to track.cover.take(2000), "lyrics" to track.lyricUri.orEmpty().take(2000),
                    "createdAt" to System.currentTimeMillis(), "sample" to false, "startMs" to 0L,
                ),
            )
            .addTag(KaraokeWorker.TAG)
            .build()
        activeWork.set(request.id)
        try {
            work.enqueue(request).result.get()
            val info = awaitFinished(request.id)
            when (info.state) {
                WorkInfo.State.SUCCEEDED -> KaraokePublication(getApplication()).publish(
                    request.id.toString(), info.outputData.getString("name").orEmpty(), track, sample = false, format = KaraokeOutput.FLAC,
                )
                WorkInfo.State.CANCELLED -> error("Proses karaoke dibatalkan.")
                else -> error(info.outputData.getString("error") ?: "Pemrosesan karaoke gagal.")
            }
        } finally {
            activeWork.compareAndSet(request.id, null)
        }
    }

    private suspend fun awaitFinished(id: UUID): WorkInfo {
        while (true) {
            currentCoroutineContext().ensureActive()
            val info = work.getWorkInfoById(id).get() ?: error("Status proses karaoke tidak ditemukan.")
            if (info.state.isFinished) return info
            delay(2_000)
        }
    }

    private fun start(kind: Kind, targets: List<String>, action: suspend (String) -> Unit) {
        if (targets.isEmpty() || worker?.isActive == true) return
        _progress.value = Progress(kind, "", 0, targets.size)
        worker = viewModelScope.launch(Dispatchers.IO) {
            var done = 0
            val failures = mutableListOf<String>()
            try {
                for (uri in targets) {
                    ensureActive()
                    val name = dao.get(uri)?.displayName ?: uri.substringAfterLast('/')
                    try {
                        action(uri)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Exception) {
                        failures += "$name: ${failure.message ?: "gagal"}"
                    }
                    done++
                    _progress.value = Progress(kind, name, done, targets.size, failures.toList())
                }
                _progress.value = Progress(kind, "", done, targets.size, failures.toList(), finished = true)
            } catch (cancelled: CancellationException) {
                _progress.value = Progress(kind, "", done, targets.size, failures.toList(), finished = true, cancelled = true)
                throw cancelled
            }
        }
    }
}
