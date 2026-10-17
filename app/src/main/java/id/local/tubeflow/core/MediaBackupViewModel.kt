package id.local.tubeflow.core

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class MediaBackupViewModel(app: Application) : AndroidViewModel(app) {
    private val backup = MediaBackup(app)
    private val _plan = MutableStateFlow<MediaBackup.Plan?>(null); val plan = _plan.asStateFlow()
    private val _busy = MutableStateFlow(false); val busy = _busy.asStateFlow()
    private val _message = MutableStateFlow<String?>(null); val message = _message.asStateFlow()
    fun dismiss() { if (!_busy.value) _plan.value = null }
    private fun operation(block: suspend () -> Unit) {
        if (_busy.value) return
        _busy.value = true; _message.value = null
        viewModelScope.launch(Dispatchers.IO) {
            try { block() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { _message.value = error.message ?: "Cadangan belum dapat diproses." }
            finally { _busy.value = false }
        }
    }
    private fun read(uri: Uri): ByteArray = getApplication<Application>().contentResolver.openInputStream(uri)?.use { input ->
        val out = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
        while (true) { val count = input.read(buffer); if (count < 0) break; require(out.size() + count <= MediaBackupCodec.MAX_BYTES) { "Cadangan terlalu besar." }; out.write(buffer, 0, count) }
        out.toByteArray()
    } ?: error("Cadangan tidak dapat dibaca.")
    fun export(uri: Uri) = operation {
        val result = backup.export()
        getApplication<Application>().contentResolver.openOutputStream(uri, "wt")?.use { it.write(result.bytes) } ?: error("Cadangan tidak dapat ditulis.")
        require(read(uri).contentEquals(result.bytes)) { "Verifikasi cadangan gagal; berkas ini jangan dipakai untuk pemulihan." }
        _message.value = "Cadangan diverifikasi: ${result.media} media; ${result.skipped} file tidak dapat dicadangkan dan ${result.lyricFailures} lirik dilewati. Simpan cadangan di tempat pribadi."
    }
    fun inspect(uri: Uri) = operation { _plan.value = null; _plan.value = backup.inspect(read(uri)) }
    fun restore(options: MediaBackup.Options) {
        val expected = _plan.value ?: return
        operation {
            _message.value = backup.restore(expected, options)
            _plan.value = null
            PlayerViewModel.active?.refreshLyrics()
        }
    }
}
