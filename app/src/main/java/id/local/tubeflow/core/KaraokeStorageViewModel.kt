package id.local.tubeflow.core

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.nio.file.Files

class KaraokeStorageViewModel(app: Application) : AndroidViewModel(app) {
    data class Usage(val model: PrivateStorage.Size, val preview: PrivateStorage.Size, val temporary: PrivateStorage.Size, val modelFragments: Long, val verified: Boolean)
    private val _usage = MutableStateFlow<Usage?>(null); val usage = _usage.asStateFlow()
    private val _busy = MutableStateFlow(false); val busy = _busy.asStateFlow()
    private val _message = MutableStateFlow<String?>(null); val message = _message.asStateFlow()
    init { refresh() }
    private fun read() {
        val models = KaraokeModel.directory(getApplication())
        val file = File(models, "uvr-inst-hq3-t64.onnx")
        val modelSize = PrivateStorage.measure(file)
        val modelsTotal = PrivateStorage.measure(models)
        val root = KaraokeWorker.root(getApplication())
        val work = PrivateStorage.measure(root)
        val previews = root.listFiles()?.filter { it.isDirectory && !Files.isSymbolicLink(it.toPath()) }?.map { PrivateStorage.measure(File(it, "instrumental.flac")) }.orEmpty()
        val preview = PrivateStorage.Size(previews.sumOf { it.bytes }, work.complete && previews.all { it.complete })
        val temporary = PrivateStorage.Size((work.bytes - preview.bytes).coerceAtLeast(0) + (modelsTotal.bytes - modelSize.bytes).coerceAtLeast(0), work.complete && modelsTotal.complete)
        _usage.value = Usage(modelSize, preview, temporary, (modelsTotal.bytes - modelSize.bytes).coerceAtLeast(0),
            file.isFile && file.length() == KaraokeModel.PREPARED_BYTES && KaraokeModel.digest(file) == KaraokeModel.SHA)
    }
    fun refresh() = action { read() }
    private fun action(block: suspend () -> Unit) {
        if (_busy.value) return
        _busy.value = true; _message.value = null
        viewModelScope.launch(Dispatchers.IO) {
            try { block() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { _message.value = error.message ?: "Penyimpanan karaoke belum dapat diubah." }
            finally { _busy.value = false }
        }
    }
    fun deleteModel() = action {
        KaraokeViewModel.requests.withLock {
            require(WorkManager.getInstance(getApplication()).getWorkInfosForUniqueWork(KaraokeWorker.UNIQUE).get().none { !it.state.isFinished }) {
                "Model masih diperlukan proses karaoke. Batalkan dan tunggu cleanup selesai."
            }
            KaraokeWorker.lock.withLock {
                val models = KaraokeModel.directory(getApplication())
                require(!Files.isSymbolicLink(models.toPath())) { "Lokasi model tidak aman; data tidak dihapus." }
                require(KaraokeResult.discardModelPartials(models)) { "Fragmen model belum dapat dibersihkan." }
                val file = File(models, "uvr-inst-hq3-t64.onnx")
                require(!Files.isSymbolicLink(file.toPath()) && (Files.notExists(file.toPath()) || file.isFile && file.delete())) { "Model belum dapat dihapus." }
            }
            read(); _message.value = "Model dihapus. Proses karaoke berikutnya akan mengunduh model kembali."
        }
    }
}
