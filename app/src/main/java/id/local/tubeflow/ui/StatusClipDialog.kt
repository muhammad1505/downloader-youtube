package id.local.tubeflow.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import id.local.tubeflow.core.MediaItem
import id.local.tubeflow.core.StatusClip
import id.local.tubeflow.core.StatusClipper
import id.local.tubeflow.core.shareStatusClip
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Pengaturan klip Status: pilih titik awal, lalu potong dan buka pemilih bagikan WhatsApp.
 * Salinan sumber dan hasil berada di cache privat; file asli tidak diubah.
 */
@Composable
fun StatusClipDialog(item: MediaItem, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val durationMs = item.durationMs?.takeIf { it > 0 } ?: 0L
    val maxStartSec = ((durationMs - StatusClip.DURATION_MS).coerceAtLeast(0) / 1000)
    var startSec by remember(item.uri) { mutableFloatStateOf(0f) }
    var busy by remember(item.uri) { mutableStateOf(false) }
    var percent by remember(item.uri) { mutableStateOf<Int?>(null) }
    var phase by remember(item.uri) { mutableStateOf("") }
    var error by remember(item.uri) { mutableStateOf<String?>(null) }
    var task by remember(item.uri) { mutableStateOf<Job?>(null) }
    val startMs = (startSec.toLong() * 1000).coerceIn(0, (durationMs - StatusClip.DURATION_MS).coerceAtLeast(0))
    val endMs = if (durationMs > 0) (startMs + StatusClip.DURATION_MS).coerceAtMost(durationMs) else 0

    fun begin() {
        busy = true
        error = null
        percent = 0
        phase = "Menyiapkan video…"
        task = scope.launch {
            try {
                val file = withContext(Dispatchers.IO) {
                    StatusClipper(context).clip(item, startMs) { value, label ->
                        scope.launch(Dispatchers.Main) {
                            percent = value
                            phase = label
                        }
                    }
                }
                if (!shareStatusClip(context, file)) {
                    error = "Tidak ada aplikasi untuk membagikan klip."
                } else {
                    onDismiss()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                error = failure.message ?: "Klip Status gagal dibuat."
            } finally {
                busy = false
                task = null
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Potong Status · 30 detik") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(item.displayName, style = MaterialTheme.typography.titleSmall)
                if (durationMs <= 0) {
                    Text(
                        "Durasi video belum diketahui. Pindai ulang Koleksi lalu coba lagi.",
                        color = MaterialTheme.colorScheme.error,
                    )
                } else {
                    Text(
                        "Pilih awal klip. Video yang lebih pendek dari 30 detik akan dipakai seluruhnya.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        "${clipTime(startMs)} – ${clipTime(endMs)}  ·  ${clipTime(endMs - startMs)}",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (maxStartSec > 0) {
                        Slider(
                            value = startSec.coerceIn(0f, maxStartSec.toFloat()),
                            onValueChange = { startSec = it.roundToInt().toFloat().coerceIn(0f, maxStartSec.toFloat()) },
                            valueRange = 0f..maxStartSec.toFloat(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            "Awal: ${clipTime(startMs)} dari ${clipTime(durationMs)}",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    Text(
                        "Potongan cepat tanpa encode ulang bila video sudah H.264/AAC. Format lain dienkode ulang agar kompatibel dengan WhatsApp.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (busy) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(phase + (percent?.let { " ($it%)" } ?: ""), style = MaterialTheme.typography.bodySmall)
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(enabled = durationMs > 0 && !busy, onClick = { begin() }) {
                Text(if (busy) "Memproses…" else "Potong & bagikan")
            }
        },
        dismissButton = {
            TextButton(onClick = {
                if (busy) {
                    task?.cancel()
                    task = null
                    busy = false
                    percent = null
                    phase = ""
                } else {
                    onDismiss()
                }
            }) {
                Text(if (busy) "Batalkan" else "Batal")
            }
        },
    )
}

private fun clipTime(ms: Long): String =
    "%d:%02d".format(Locale.ROOT, ms / 60000, ms / 1000 % 60)
