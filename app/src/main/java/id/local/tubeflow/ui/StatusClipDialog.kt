package id.local.tubeflow.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import id.local.tubeflow.R
import id.local.tubeflow.core.MediaItem
import id.local.tubeflow.core.StatusClip
import id.local.tubeflow.core.StatusClipper
import id.local.tubeflow.core.location
import id.local.tubeflow.core.shareStatusClip
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.roundToLong

/**
 * Pengaturan klip Status: pilih titik awal dan akhir, lalu potong dan buka pemilih bagikan
 * WhatsApp. Salinan sumber dan hasil berada di cache privat; file asli tidak diubah.
 */
@Composable
fun StatusClipDialog(item: MediaItem, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val durationMs = item.durationMs?.takeIf { it > 0 } ?: 0L
    var startMs by remember(item.uri) { mutableLongStateOf(0L) }
    var endMs by remember(item.uri) { mutableLongStateOf(minOf(durationMs, StatusClip.DURATION_MS)) }
    var busy by remember(item.uri) { mutableStateOf(false) }
    var percent by remember(item.uri) { mutableStateOf<Int?>(null) }
    var phase by remember(item.uri) { mutableStateOf("") }
    var error by remember(item.uri) { mutableStateOf<String?>(null) }
    var task by remember(item.uri) { mutableStateOf<Job?>(null) }

    val selected = if (durationMs > 0) StatusClip.range(durationMs, startMs, endMs) else null
    val rangeStart = selected?.startMs ?: 0L
    val rangeEnd = selected?.endMs ?: 0L
    val maxEndMs = minOf(durationMs, rangeStart + StatusClip.DURATION_MS)
    val editable = durationMs > StatusClip.MIN_DURATION_MS

    fun setStart(valueMs: Float) {
        val next = StatusClip.range(durationMs, valueMs.toSecondMs(), endMs)
        startMs = next.startMs
        endMs = next.endMs
    }

    fun setEnd(valueMs: Float) {
        val next = StatusClip.range(durationMs, startMs, valueMs.toSecondMs())
        startMs = next.startMs
        endMs = next.endMs
    }

    fun begin() {
        val range = selected ?: return
        busy = true
        error = null
        percent = 0
        phase = "Menyiapkan video…"
        task = scope.launch {
            try {
                val file = withContext(Dispatchers.IO) {
                    StatusClipper(context).clip(item, range.startMs, range.endMs) { value, label ->
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
        title = { Text("Potong Status · maks 30 detik") },
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
                    if (!busy) StatusClipPreview(item.location, rangeStart, rangeEnd)
                    Text(
                        "Tentukan titik awal dan akhir. Panjang klip 1–30 detik; video yang lebih pendek dipakai seluruhnya.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        "${clipTime(rangeStart)} – ${clipTime(rangeEnd)}  ·  ${clipTime(rangeEnd - rangeStart)}",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (editable) {
                        Text("Awal: ${clipTime(rangeStart)}", style = MaterialTheme.typography.labelSmall)
                        Scrubber(
                            value = rangeStart.toFloat(),
                            duration = durationMs.toFloat(),
                            onScrub = { setStart(it) },
                            onScrubFinished = {},
                            contentColor = MaterialTheme.colorScheme.primary,
                            touchHeight = 48.dp,
                            enabled = !busy,
                            contentDescription = stringResource(R.string.clip_start_position),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            "Akhir: ${clipTime(rangeEnd)}  ·  maks ${clipTime(maxEndMs)}",
                            style = MaterialTheme.typography.labelSmall,
                        )
                        Scrubber(
                            value = rangeEnd.toFloat(),
                            duration = durationMs.toFloat(),
                            onScrub = { setEnd(it) },
                            onScrubFinished = {},
                            contentColor = MaterialTheme.colorScheme.primary,
                            touchHeight = 48.dp,
                            enabled = !busy,
                            contentDescription = stringResource(R.string.clip_end_position),
                            modifier = Modifier.fillMaxWidth(),
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
            TextButton(enabled = selected != null && !busy, onClick = { begin() }) {
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

/** Slider bekerja dalam milidetik; titik potong dibulatkan ke detik terdekat. */
private fun Float.toSecondMs(): Long = (this / 1_000f).roundToLong().coerceAtLeast(0) * 1_000L

private fun clipTime(ms: Long): String =
    "%d:%02d".format(Locale.ROOT, ms / 60000, ms / 1000 % 60)
