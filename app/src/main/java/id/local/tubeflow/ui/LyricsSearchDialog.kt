package id.local.tubeflow.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.local.tubeflow.core.Lyrics
import id.local.tubeflow.core.LyricStatus
import id.local.tubeflow.core.MediaLibraryViewModel
import id.local.tubeflow.R
import kotlinx.coroutines.delay

@Composable
fun LyricsSearchDialog(model: MediaLibraryViewModel) {
    val state by model.lyrics.collectAsStateWithLifecycle()
    val current=state ?: return
    var title by remember(current.item.uri) { mutableStateOf(current.title) }
    var artist by remember(current.item.uri) { mutableStateOf(current.artist) }
    var selected by remember(current.choices) { mutableStateOf<Lyrics.Choice?>(null) }
    var acknowledge by remember(selected) { mutableStateOf(false) }
    var manualOpen by remember(current.item.uri) { mutableStateOf(false) }
    var manualText by remember(current.item.uri) { mutableStateOf("") }
    val mismatch=selected?.automaticMatch(current.query) == false
    val blocked=current.loading || current.saving
    var retrySeconds by remember(current.failureStatus) { mutableLongStateOf(Lyrics.retryAfterSeconds()) }
    LaunchedEffect(current.failureStatus) {
        while (true) { retrySeconds=Lyrics.retryAfterSeconds(); delay(1000) }
    }
    AlertDialog(onDismissRequest=model::closeLyrics, title={ Text("Cari dan pilih lirik") }, text={
        Column(Modifier.fillMaxWidth().heightIn(max=560.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(current.item.displayName,style=MaterialTheme.typography.labelMedium)
            Text("Sumber: LRCLIB. Parameter pencarian dapat diedit; tag audio tetap sama.",style=MaterialTheme.typography.bodySmall)
            OutlinedTextField(title,{ title=it.take(200) },label={ Text("Judul lagu") },singleLine=true,enabled=!blocked,modifier=Modifier.fillMaxWidth())
            OutlinedTextField(artist,{ artist=it.take(200) },label={ Text("Artis (opsional)") },singleLine=true,enabled=!blocked,modifier=Modifier.fillMaxWidth())
            ActionButton("Cari lirik", { model.searchLyrics(title,artist) }, icon = Icons.Outlined.Search, enabled = !blocked && retrySeconds == 0L && title.isNotBlank())
            if (retrySeconds > 0) Text(pluralStringResource(R.plurals.lyrics_retry_after,
                retrySeconds.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),retrySeconds),style=MaterialTheme.typography.bodySmall)
            if (blocked) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(if (current.saving) "Menyimpan pilihan…" else "Mencari di LRCLIB…") }
            current.error?.let {
                if (current.failureStatus != LyricStatus.RATE_LIMIT) Text(it,color=MaterialTheme.colorScheme.error)
                else if (retrySeconds == 0L) Text(stringResource(R.string.lyrics_retry_ready),style=MaterialTheme.typography.bodySmall)
            }
            if (current.failureStatus != null) current.httpCode?.let { code ->
                Text(stringResource(R.string.lyrics_http_detail,code),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (current.searched && current.choices.isEmpty() && current.error == null) Text("Tidak ada hasil. Coba judul/artis lain.")
            if (current.choices.isNotEmpty()) {
                Text("${current.choices.size} hasil · pilih lirik bertimestamp",style=MaterialTheme.typography.labelMedium)
                LazyColumn(Modifier.fillMaxWidth().heightIn(max=220.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    items(current.choices,key={ it.id }) { choice ->
                        Surface(color=if (selected?.id == choice.id) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,shape=MaterialTheme.shapes.medium) {
                            Row(Modifier.fillMaxWidth().clickable(enabled=!blocked && choice.hasSyncedLyrics) { selected=choice }.padding(8.dp),verticalAlignment=Alignment.CenterVertically) {
                                RadioButton(selected?.id == choice.id,onClick={ selected=choice },enabled=!blocked && choice.hasSyncedLyrics)
                                Column(Modifier.weight(1f)) {
                                    Text(choice.title,style=MaterialTheme.typography.titleSmall,maxLines=2)
                                    Text(choice.artist.ifBlank { "Artis tidak tersedia" },style=MaterialTheme.typography.bodySmall,maxLines=2)
                                    if (choice.album.isNotBlank()) Text(choice.album,style=MaterialTheme.typography.labelSmall,maxLines=1)
                                    Text((if (choice.durationSeconds > 0) duration(choice.durationSeconds*1000) else "Durasi tidak tersedia")+" · "+if (choice.hasSyncedLyrics) "LRC tersedia" else "Tanpa timestamp",style=MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
            if (current.canExpand && title.trim() == current.title && artist.trim() == current.artist) {
                ActionButton(stringResource(R.string.lyrics_search_more), { model.searchLyrics(title,artist,expand=true) }, icon = Icons.Outlined.Add, emphasis = ActionEmphasis.Quiet, enabled = !blocked && retrySeconds == 0L)
            }
            selected?.let { choice ->
                Text("Preview pilihan",style=MaterialTheme.typography.labelMedium)
                Text(choice.text.orEmpty().lineSequence().take(6).joinToString("\n"),style=MaterialTheme.typography.bodySmall)
                if (mismatch) {
                    Text("Identitas/versi/durasi berbeda dari audio. Timestamp mungkin tidak sinkron; pilih secara manual hanya jika rekamannya sesuai.",color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
                    CheckRow("Saya tetap memilih versi ini", acknowledge, { acknowledge=it }, enabled=!blocked)
                }
                if (current.item.lyricUri != null) Text("Lirik lama diganti setelah penyimpanan terverifikasi. Koreksi waktu sebelumnya direset untuk lirik baru.",style=MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick={ manualOpen=!manualOpen }, enabled=!blocked) {
                Icon(Icons.Outlined.ContentPaste,null,Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (manualOpen) "Tutup lirik manual" else "Tempel LRC manual")
            }
            if (manualOpen) {
                Text("Format LRC dengan timestamp, mis. [00:12.00]Baris lirik. Lirik tanpa timestamp ditolak.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value=manualText,
                    onValueChange={ manualText=it.take(512*1024) },
                    label={ Text("LRC manual") },
                    placeholder={ Text("[00:00.00]…") },
                    minLines=5,
                    maxLines=10,
                    enabled=!blocked,
                    modifier=Modifier.fillMaxWidth(),
                )
                ActionButton(
                    "Simpan lirik manual",
                    { model.saveManualLyrics(manualText) },
                    icon=Icons.Outlined.Save,
                    enabled=!blocked && manualText.isNotBlank(),
                )
            }
        }
    },confirmButton={ TextButton(onClick={ selected?.let { model.saveLyrics(it,acknowledge) } },enabled=!blocked && selected != null && (!mismatch || acknowledge)) { Text("Simpan pilihan") } },
        dismissButton={ TextButton(onClick=model::closeLyrics,enabled=!current.saving) { Text("Tutup") } })
}
