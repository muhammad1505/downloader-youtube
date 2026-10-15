package id.local.tubeflow.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.WorkInfo
import id.local.tubeflow.core.KaraokeInterval
import id.local.tubeflow.core.KaraokeViewModel
import id.local.tubeflow.core.PlayerViewModel

@Composable
fun KaraokeDialog(model: KaraokeViewModel, player: PlayerViewModel, notifications: () -> Unit) {
    val selected by model.selected.collectAsStateWithLifecycle()
    val track = selected ?: return
    val work by model.info.collectAsStateWithLifecycle()
    val source by model.source.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val sample by model.sample.collectAsStateWithLifecycle()
    val startText by model.startText.collectAsStateWithLifecycle()
    val validStart = KaraokeInterval.parseStart(startText) != null
    val message by model.message.collectAsStateWithLifecycle()
    val saved by model.saved.collectAsStateWithLifecycle()
    val active = work?.state?.isFinished == false
    val ready = work?.state == WorkInfo.State.SUCCEEDED
    AlertDialog(onDismissRequest = model::close, title = { Text("Karaoke · Pisahkan vokal") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Detail selalu mengikuti lagu yang dipilih; proses lama diberi label sumber sendiri
            // agar ganti lagu tidak terlihat seolah judul/status ikut berubah.
            Text(track.title, style = MaterialTheme.typography.titleSmall)
            if ((active || ready) && source?.uri != track.uri) Text(
                "Proses karaoke yang tampil adalah untuk ${source?.title.orEmpty().ifBlank { "lagu lain" }}.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            Text("Diproses di HP dengan model UVR (Anjok07, aufr33). Model 67 MB diunduh sekali; audio tidak diunggah. Hasil instrumental bisa menyisakan backing vocal atau artefak.", style = MaterialTheme.typography.bodySmall)
            if (active) {
                val percent = work?.progress?.getInt("percent", -1) ?: -1
                Text(work?.progress?.getString("phase") ?: "Menunggu pemrosesan")
                if (percent >= 0) { LinearProgressIndicator(progress = { percent / 100f }, modifier = Modifier.fillMaxWidth()); Text("$percent%") }
                else LinearProgressIndicator(Modifier.fillMaxWidth())
                TextButton(onClick = model::cancel) { Text("Batalkan proses") }
            } else if (ready) {
                Text(if (work?.outputData?.getBoolean("sample", false) == true) "Cuplikan instrumental siap. Lirik tersedia pada hasil lagu penuh." else "Instrumental lagu penuh siap. Audio asli tidak berubah.")
                if (work?.outputData?.getBoolean("sample", false) == true) {
                    val beginning = work?.outputData?.getLong("startMs", 0) ?: 0
                    val length = work?.outputData?.getLong("durationMs", 0) ?: 0
                    Text("Bagian sumber: ${KaraokeInterval.formatStart(beginning)}–${KaraokeInterval.formatStart(beginning + length)}", style = MaterialTheme.typography.bodySmall)
                }
                Button(onClick = { model.preview()?.let(player::play) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Dengarkan instrumental") }
                Button(onClick = model::save, enabled = !busy && saved == null, modifier = Modifier.fillMaxWidth()) { Text(if (saved != null) "Sudah disimpan" else if (work?.outputData?.getBoolean("sample", false) == true) "Simpan cuplikan FLAC" else "Simpan FLAC ke Koleksi") }
            } else {
                if (work?.state == WorkInfo.State.FAILED) Text(work?.outputData?.getString("error") ?: "Pemrosesan gagal.", color = MaterialTheme.colorScheme.error)
                if (work?.state == WorkInfo.State.CANCELLED) Text("Proses dibatalkan. File audio asli tetap tersedia.")
            }
            if (!active && track.uri.startsWith("content://")) {
                Text("Untuk audio hingga 10 menit, aplikasi 64-bit, RAM kosong ≥512 MB. Proses berat; tunggu unduhan selesai. Pratinjau lama diganti saat proses baru dimulai.", style = MaterialTheme.typography.bodySmall)
                if (source?.uri != track.uri && ready) Text("Memulai proses baru akan mengganti hasil lama untuk ${source?.title.orEmpty()}: ${track.title}", style = MaterialTheme.typography.bodySmall)
                Row { Checkbox(checked = sample, onCheckedChange = model::sample, enabled = !busy); Text("Uji cuplikan (maksimal 30 detik)", Modifier.weight(1f)) }
                if (sample) OutlinedTextField(value = startText, onValueChange = model::startText,
                    label = { Text("Mulai dari (menit:detik atau detik)") },
                    supportingText = { Text(if (validStart) "Contoh 01:30 atau 90. Dekat akhir lagu, cuplikan memakai sisa durasi." else "Gunakan 01:30 atau 90; rentang 0–10 menit.") },
                    isError = !validStart, singleLine = true, enabled = !busy,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Done), modifier = Modifier.fillMaxWidth())
                if (!sample) Text("Seluruh lagu diproses. Waktu bergantung pada HP; bisa puluhan menit.", style = MaterialTheme.typography.bodySmall)
                Button(onClick = { notifications(); player.stop(); model.start() }, enabled = !busy && (!sample || validStart), modifier = Modifier.fillMaxWidth()) { Text(if (sample) "Proses cuplikan" else "Buat instrumental lagu penuh") }
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }, confirmButton = { TextButton(onClick = model::close) { Text("Tutup") } })
}
