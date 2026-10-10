package id.local.tubeflow.ui

import android.content.Intent
import android.os.Build
import android.provider.DocumentsContract
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import id.local.tubeflow.BuildConfig
import id.local.tubeflow.core.Format
import id.local.tubeflow.core.Selection
import id.local.tubeflow.core.SettingsStore

@Composable
fun SettingsScreen(
    themeMode: String,
    onThemeMode: (String) -> Unit,
    dynamicColor: Boolean,
    onDynamicColor: (Boolean) -> Unit,
    storageBytes: Long?,
    storageError: Boolean,
    storageFolderAccess: Boolean,
    onRefreshStorage: () -> Unit,
    clearingFiles: Boolean = false,
    onClearStorage: () -> Unit = {},
) {
    val context = LocalContext.current
    var folderInput by rememberSaveable { mutableStateOf(SettingsStore.storageFolder) }
    var confirmClearFiles by rememberSaveable { mutableStateOf(false) }
    val folderValid = Selection.safeFolder(folderInput) != null
    val tree = SettingsStore.storageTree
    var folderAccessError by rememberSaveable { mutableStateOf(false) }
    if (confirmClearFiles) {
        AlertDialog(
            onDismissRequest = { confirmClearFiles = false },
            title = { Text("Hapus semua file?") },
            text = { Text("Seluruh file di ${if (tree != null) "folder pilihan" else "Downloads/${SettingsStore.storageFolder}"} (termasuk subfolder playlist dan file pendukung lirik/subtitle) akan dihapus permanen. Riwayat unduhan tetap tersimpan di aplikasi, tetapi file-nya hilang. Unduhan yang berjalan atau dijeda harus diselesaikan atau dibatalkan dulu.") },
            confirmButton = { TextButton(onClick = { confirmClearFiles = false; onClearStorage() }) { Text("Hapus semua", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmClearFiles = false }) { Text("Batal") } },
        )
    }

    val treeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            folderAccessError = runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }.isFailure
            if (!folderAccessError) {
                SettingsStore.setStorageTree(context, uri.toString())
                onRefreshStorage()
            }
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PageHeading("Pengaturan", "Dirancang untuk bekerja langsung di perangkatmu.")

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Tampilan", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Pilih mode tema yang paling nyaman untuk mata kamu.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf(
                        SettingsStore.MODE_SYSTEM to "Sistem",
                        SettingsStore.MODE_LIGHT to "Terang",
                        SettingsStore.MODE_DARK to "Gelap",
                    ).forEachIndexed { index, (value, label) ->
                        SegmentedButton(
                            selected = themeMode == value,
                            onClick = { onThemeMode(value) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = 3),
                            label = { Text(label) },
                        )
                    }
                }
                if (Build.VERSION.SDK_INT >= 31) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Warna dinamis", style = MaterialTheme.typography.bodyLarge)
                            Text("Ikuti warna wallpaper (Material You).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = dynamicColor, onCheckedChange = onDynamicColor)
                    }
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Penyimpanan", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Folder, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (tree != null) "Folder pilihan: ${treeLabel(tree)}" else "Downloads/${SettingsStore.storageFolder}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (tree == null) {
                    OutlinedTextField(
                        value = folderInput,
                        onValueChange = { input ->
                            folderInput = input
                            if (SettingsStore.setStorageFolder(context, input)) onRefreshStorage()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Folder penyimpanan") },
                        singleLine = true,
                        isError = !folderValid,
                        supportingText = {
                            Text(
                                if (folderValid) "Baru & selesai diunduh tersimpan di folder ini."
                                else "Maks 40 karakter: huruf, angka, spasi, titik, tanda minus/underscore/().",
                            )
                        },
                    )
                } else {
                    TextButton(onClick = { SettingsStore.setStorageTree(context, null); onRefreshStorage() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Kembali ke Downloads/${SettingsStore.storageFolder}")
                    }
                }
                OutlinedButton(onClick = {
                    val initialFolder = tree?.toUri() ?: DocumentsContract.buildDocumentUri(
                        "com.android.externalstorage.documents", "primary:Download/${SettingsStore.storageFolder}",
                    )
                    treeLauncher.launch(initialFolder)
                }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.FolderOpen, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (tree == null) "Pilih folder sendiri…" else "Pilih ulang folder…")
                }
                if (folderAccessError) {
                    Text("Akses folder tidak dapat disimpan. Pilih folder yang bisa dibaca dan ditulis.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Storage, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when {
                            storageError -> "Ukuran belum dapat dibaca"
                            storageBytes == null -> "Menghitung pemakaian…"
                            storageFolderAccess -> "Ukuran folder: ${Format.bytes(storageBytes)}"
                            else -> "File yang dapat diakses: ${Format.bytes(storageBytes)}"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = onRefreshStorage) { Text("Segarkan") }
                }
        if (storageError) {
            Text("Pilih ulang folder atau tekan Segarkan. Folder yang terlalu besar/dalam atau file tanpa ukuran belum dapat dihitung lengkap.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        OutlinedButton(onClick = { confirmClearFiles = true }, enabled = !clearingFiles, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.DeleteSweep, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (clearingFiles) "Menghapus file…" else "Hapus semua file…")
        }
                Text(
                    if (tree == null && storageFolderAccess)
                        "Download disimpan di Downloads/${SettingsStore.storageFolder}. Ukuran mencakup semua file dan subfolder yang bisa dibaca melalui akses folder yang sudah diberikan."
                    else if (tree == null)
                        "Angka hanya mencakup file yang dapat dibaca TubeFlow. Untuk menghitung semua file lama dan subfolder, pilih Downloads/${SettingsStore.storageFolder} lewat Pilih folder sendiri. Download tetap disimpan di folder ini."
                    else
                        "Download disimpan ke folder pilihan (internal atau kartu memori eksternal). Hak akses folder disimpan permanen dan dapat dikembalikan kapan saja. Tidak ada izin akses seluruh penyimpanan.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Notifikasi", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Izinkan notifikasi agar progres download tetap terlihat saat aplikasi ditutup.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = {
                    context.startActivity(
                        Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName),
                    )
                }) { Icon(Icons.Outlined.Notifications, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Atur notifikasi") }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Antrean", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Unduhan yang boleh berjalan bersamaan.", style = MaterialTheme.typography.bodyMedium)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    (1..4).forEachIndexed { index, count ->
                        SegmentedButton(
                            selected = SettingsStore.maxConcurrent == count,
                            onClick = { SettingsStore.setMaxConcurrent(context, count) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = 4),
                            label = { Text(if (count == 1) "1 · urut" else "$count×") },
                        )
                    }
                }
                Text(
                    "Urut (1) paling hemat CPU dan kuota. Mode paralel lebih cepat, dengan progres tiap unduhan tetap tampil di notifikasi. Maksimal 4 bersamaan; perubahan berlaku untuk antrean berikutnya, sedangkan unduhan yang sudah berjalan dibiarkan selesai.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Mesin download", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("yt-dlp + FFmpeg", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                Text(
                    "Mesin disertakan dalam aplikasi dan diverifikasi saat pertama kali dijalankan. Pembaruan datang lewat rilis aplikasi — tidak ada executable jaringan yang diunduh.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Privasi", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "Riwayat dan antrean tersimpan di HP kamu. Download menggunakan koneksi perangkat langsung ke penyedia konten, tanpa server perantara.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Tentang TubeFlow", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Versi ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", style = MaterialTheme.typography.bodyMedium)
                Text("Simpan momen. Putar kapan saja.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Text(
            "Download hanya konten yang kamu miliki atau diizinkan untuk disimpan.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(4.dp))
    }
}

/** Human-readable label for a picked SAF tree URI (e.g. "Download/Musik" or "SD card/Musik"). */
private fun treeLabel(tree: String): String = runCatching {
    val id = DocumentsContract.getTreeDocumentId(tree.toUri())
    val shown = if (':' in id) id.substringAfter(':') else id
    shown.ifBlank { "penyimpanan utama" }
}.getOrNull() ?: "folder pilihan"
