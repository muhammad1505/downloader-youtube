package id.local.tubeflow.ui

import android.content.Intent
import android.os.Build
import android.provider.DocumentsContract
import kotlinx.coroutines.launch
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import id.local.tubeflow.BuildConfig
import id.local.tubeflow.core.Format
import id.local.tubeflow.core.Lyrics
import id.local.tubeflow.core.SettingsStore
import id.local.tubeflow.core.StorageFolderAccess

@Composable
fun SettingsScreen(
    karaoke: id.local.tubeflow.core.KaraokeViewModel,
    player: id.local.tubeflow.core.PlayerViewModel,
    themeMode: String,
    onThemeMode: (String) -> Unit,
    dynamicColor: Boolean,
    onDynamicColor: (Boolean) -> Unit,
    storageBytes: Long?,
    storageError: Boolean,
    storageFolderAccess: Boolean,
    storageCatalogue: Boolean = false,
    storageUnknownSizes: Long = 0,
    onRefreshStorage: () -> Unit,
    temporaryUsage: id.local.tubeflow.core.PrivateStorage.Usage? = null,
    cleaningPartial: Boolean = false,
    onClearPartial: () -> Unit = {},
    clearingFiles: Boolean = false,
    onClearStorage: () -> Unit = {},
) {
    val context = LocalContext.current
    val smartScope = androidx.compose.runtime.rememberCoroutineScope()
    var clearingLyrics by androidx.compose.runtime.remember { mutableStateOf(false) }
    var lyricsCacheMessage by androidx.compose.runtime.remember { mutableStateOf<Int?>(null) }
    val resumeLabel = androidx.compose.ui.res.stringResource(id.local.tubeflow.R.string.resume_interruption_title)
    val partialSuffix = androidx.compose.ui.res.stringResource(id.local.tubeflow.R.string.size_partial)
    val unavailableSize = androidx.compose.ui.res.stringResource(id.local.tubeflow.R.string.size_unavailable)
    var confirmPartial by rememberSaveable { mutableStateOf(false) }
    if (confirmPartial) AlertDialog(onDismissRequest = { confirmPartial = false },
        title = { Text(androidx.compose.ui.res.stringResource(id.local.tubeflow.R.string.partial_clear_title)) },
        text = { Text(androidx.compose.ui.res.stringResource(id.local.tubeflow.R.string.partial_clear_warning)) },
        confirmButton = { TextButton(onClick = { confirmPartial = false; onClearPartial() }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text(androidx.compose.ui.res.stringResource(id.local.tubeflow.R.string.partial_clear_confirm)) } },
        dismissButton = { TextButton(onClick = { confirmPartial = false }) { Text(androidx.compose.ui.res.stringResource(id.local.tubeflow.R.string.action_cancel)) } })
    var confirmClearFiles by rememberSaveable { mutableStateOf(false) }
    val tree = SettingsStore.storageTree
    var folderAccessError by rememberSaveable { mutableStateOf(false) }
    var pendingClearFolder by rememberSaveable { mutableStateOf<String?>(null) }
    var clearAccessError by rememberSaveable { mutableStateOf<String?>(null) }
    val clearTreeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val expected = pendingClearFolder
        pendingClearFolder = null
        if (uri != null && expected != null) {
            clearAccessError = runCatching {
                require(SettingsStore.storageTree == null && SettingsStore.storageFolder == expected &&
                    StorageFolderAccess.matchesDefault(uri.authority, DocumentsContract.getTreeDocumentId(uri), expected)) {
                    "Pilih tepat Download/$expected. Folder lain tidak dihapus dan tujuan download tetap sama."
                }
                context.contentResolver.takePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                onRefreshStorage()
                onClearStorage()
            }.exceptionOrNull()?.message
        }
    }
    if (confirmClearFiles) {
        AlertDialog(
            onDismissRequest = { confirmClearFiles = false },
            title = { Text("Hapus semua file?") },
            text = { Text(androidx.compose.ui.res.stringResource(id.local.tubeflow.R.string.storage_clear_description,
                if (tree != null) "folder pilihan" else "Download/${SettingsStore.storageFolder}")) },
            confirmButton = { TextButton(onClick = {
                confirmClearFiles = false
                clearAccessError = null
                val folder = SettingsStore.storageFolder
                val writableDefault = StorageFolderAccess.canClearDefault(context, folder)
                if (tree != null || writableDefault) onClearStorage() else {
                    pendingClearFolder = folder
                    clearTreeLauncher.launch(DocumentsContract.buildDocumentUri(
                        "com.android.externalstorage.documents", "primary:Download/$folder"))
                }
            }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Hapus semua") } },
            dismissButton = { TextButton(onClick = { confirmClearFiles = false }) { Text("Batal") } },
        )
    }

    val treeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            folderAccessError = runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }.isFailure
            if (!folderAccessError) {
                // Folder bawaan itu sendiri TIDAK dijadikan target SAF: penyimpanan lewat MediaStore
                // tetap tanpa izin. Pemilihan folder bawaan justru mengembalikan tujuan ke default
                // (grant-nya tetap dipakai untuk membaca ukuran folder secara penuh).
                val pickedDefault = runCatching {
                    StorageFolderAccess.matchesDefault(uri.authority, DocumentsContract.getTreeDocumentId(uri), SettingsStore.storageFolder)
                }.getOrDefault(false)
                SettingsStore.setStorageTree(context, if (pickedDefault) null else uri.toString())
                onRefreshStorage()
            }
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(UiSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(UiSpacing.item),
    ) {
        PageHeading(androidx.compose.ui.res.stringResource(id.local.tubeflow.R.string.page_settings), androidx.compose.ui.res.stringResource(id.local.tubeflow.R.string.page_settings_help))
        MediaToolsSettings(karaoke, player)

        Panel(title = androidx.compose.ui.res.stringResource(id.local.tubeflow.R.string.playback_settings_title)) {
            ToggleRow(
                title = androidx.compose.ui.res.stringResource(id.local.tubeflow.R.string.resume_interruption_title),
                description = androidx.compose.ui.res.stringResource(id.local.tubeflow.R.string.resume_interruption_description),
                checked = SettingsStore.resumeAfterInterruption,
                onCheckedChange = { SettingsStore.setResumeAfterInterruption(context, it) },
                modifier = Modifier.semantics { contentDescription = resumeLabel },
            )
        }

        Panel(
            title = "Tampilan",
            description = "Pilih mode tema yang paling nyaman untuk mata kamu.",
        ) {
            OptionMenu(
                label = "Mode tema",
                options = listOf(
                    SettingsStore.MODE_SYSTEM to "Ikut sistem",
                    SettingsStore.MODE_LIGHT to "Terang",
                    SettingsStore.MODE_DARK to "Gelap",
                ),
                selected = themeMode,
                onSelect = onThemeMode,
            )
            ToggleRow(
                title = "Aksen dari sampul",
                description = "Ikuti sampul lagu yang diputar atau unduhan terbaru.",
                checked = SettingsStore.artworkAccent,
                onCheckedChange = { SettingsStore.setArtworkAccent(context, it) },
            )
            if (Build.VERSION.SDK_INT >= 31) {
                ToggleRow(
                    title = "Warna dinamis",
                    description = "Ikuti warna wallpaper (Material You).",
                    checked = dynamicColor,
                    onCheckedChange = onDynamicColor,
                )
            }
        }

        Panel(title = "Penyimpanan") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Folder, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (tree != null) "Folder pilihan: ${treeLabel(tree)}" else "Downloads/${SettingsStore.storageFolder}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (tree == null) {
                    Text(
                        "Folder bawaan ini bagian dari penyimpanan internal — unduhan disimpan tanpa izin apa pun. Untuk memindahkan hasil download (mis. ke kartu memori), pakai tombol pilih folder di bawah: dari pemilih folder sistem kamu bisa langsung membuat folder baru.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    ActionButton(
                        "Kembali ke Downloads/${SettingsStore.storageFolder}",
                        { SettingsStore.setStorageTree(context, null); onRefreshStorage() },
                        emphasis = ActionEmphasis.Quiet,
                        fillWidth = true,
                    )
                }
                ActionButton(
                    text = if (tree == null) "Pilih folder… (bisa buat baru, SD card, dll.)" else "Pilih ulang folder…",
                    onClick = {
                        val initialFolder = tree?.toUri() ?: DocumentsContract.buildDocumentUri(
                            "com.android.externalstorage.documents", "primary:Download/${SettingsStore.storageFolder}",
                        )
                        treeLauncher.launch(initialFolder)
                    },
                    icon = Icons.Outlined.FolderOpen,
                    fillWidth = true,
                )
                if (tree == null) {
                    Text(
                        "Folder di luar Downloads/${SettingsStore.storageFolder} (mis. kartu memori) disimpan lewat pilihan folder sistem dan butuh izin aksesnya — buat folder baru langsung dari pemilih folder. Memilih kembali folder bawaan mengembalikan tujuan ke Downloads/${SettingsStore.storageFolder} tanpa izin.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (clearAccessError != null) {
                    Text(clearAccessError.orEmpty(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                if (folderAccessError) {
                    Text("Akses folder tidak dapat disimpan. Pilih folder yang bisa dibaca dan ditulis.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                AdaptivePair(leading = { available -> Row(available, verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Storage, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when {
                            storageError -> "Ukuran belum dapat dibaca"
                            storageBytes == null -> "Menghitung pemakaian…"
                            storageFolderAccess -> "Ukuran folder: ${Format.bytes(storageBytes)}"
                            // Angka katalog selalu terbaca (sumber yang sama dengan layar Koleksi), tetapi
                            // sengaja diberi label berbeda dari ukuran folder penuh.
                            storageCatalogue -> "Ukuran file di koleksi: ${Format.bytes(storageBytes)}"
                            else -> "File yang dapat diakses: ${Format.bytes(storageBytes)}"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                } }, trailing = {
                    ActionButton("Segarkan", onRefreshStorage, icon = Icons.Outlined.Refresh, emphasis = ActionEmphasis.Quiet)
                })
                if (storageCatalogue && storageUnknownSizes > 0) {
                    Text(pluralStringResource(id.local.tubeflow.R.plurals.storage_partial_sizes, storageUnknownSizes.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), storageUnknownSizes),
                        style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
        if (storageError) {
            Text("Pilih ulang folder atau tekan Segarkan. Folder yang terlalu besar/dalam atau file tanpa ukuran belum dapat dihitung lengkap.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
                HorizontalDivider()
                Text(androidx.compose.ui.res.stringResource(id.local.tubeflow.R.string.temporary_storage_title), style = MaterialTheme.typography.titleSmall)
                fun sizeLabel(size: id.local.tubeflow.core.PrivateStorage.Size?): String =
                    size?.let { Format.bytes(it.bytes) + if (!it.complete) " $partialSuffix" else "" } ?: unavailableSize
                Text(androidx.compose.ui.res.stringResource(id.local.tubeflow.R.string.cache_size_label, sizeLabel(temporaryUsage?.cache)), style = MaterialTheme.typography.bodyMedium)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(androidx.compose.ui.res.stringResource(id.local.tubeflow.R.string.cache_lyrics_size, sizeLabel(temporaryUsage?.lyrics)), style = MaterialTheme.typography.bodySmall)
                    Text(androidx.compose.ui.res.stringResource(id.local.tubeflow.R.string.cache_metadata_size, sizeLabel(temporaryUsage?.metadata)), style = MaterialTheme.typography.bodySmall)
                    Text(androidx.compose.ui.res.stringResource(id.local.tubeflow.R.string.cache_artwork_size, sizeLabel(temporaryUsage?.artwork)), style = MaterialTheme.typography.bodySmall)
                }
                Text(androidx.compose.ui.res.stringResource(id.local.tubeflow.R.string.partial_size_label, sizeLabel(temporaryUsage?.partial)), style = MaterialTheme.typography.bodyMedium)
                Text(androidx.compose.ui.res.stringResource(id.local.tubeflow.R.string.temporary_storage_scope), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ActionButton(
                    androidx.compose.ui.res.stringResource(if (clearingLyrics) id.local.tubeflow.R.string.cache_clearing_lyrics else id.local.tubeflow.R.string.cache_clear_lyrics),
                    {
                        clearingLyrics = true
                        lyricsCacheMessage = null
                        smartScope.launch {
                            try {
                                val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                    Lyrics.clearCache(java.io.File(context.cacheDir, "lyrics-responses"))
                                }
                                lyricsCacheMessage = when {
                                    !result.complete -> id.local.tubeflow.R.string.cache_clear_failed
                                    result.removed == 0 -> id.local.tubeflow.R.string.cache_lyrics_empty
                                    else -> id.local.tubeflow.R.string.cache_lyrics_cleared
                                }
                                onRefreshStorage()
                            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                            catch (_: Exception) { lyricsCacheMessage = id.local.tubeflow.R.string.cache_clear_failed }
                            finally { clearingLyrics = false }
                        }
                    },
                    icon = Icons.Outlined.CleaningServices,
                    enabled = !clearingLyrics,
                )
                lyricsCacheMessage?.let { message -> Text(androidx.compose.ui.res.stringResource(message),
                    modifier = Modifier.semantics { liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite },
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                ActionButton(
                    androidx.compose.ui.res.stringResource(if (cleaningPartial) id.local.tubeflow.R.string.partial_clearing else id.local.tubeflow.R.string.partial_clear_title),
                    { confirmPartial = true },
                    enabled = !cleaningPartial && temporaryUsage != null,
                    destructive = true,
                    fillWidth = true,
                )
                ActionButton(
                    if (clearingFiles) "Menghapus file…" else "Hapus semua file…",
                    { confirmClearFiles = true },
                    icon = Icons.Outlined.DeleteSweep,
                    enabled = !clearingFiles,
                    destructive = true,
                    fillWidth = true,
                )
                Text(
                    if (tree == null && storageCatalogue)
                        "Angka dijumlahkan dari file yang sudah terindeks di Koleksi — tidak butuh izin atau pemilihan folder lebih dulu. Untuk ukuran seluruh folder (termasuk file yang belum terindeks), pilih Downloads/${SettingsStore.storageFolder} lewat Pilih folder."
                    else if (tree == null && storageFolderAccess)
                        "Download disimpan di Downloads/${SettingsStore.storageFolder}. Ukuran mencakup semua file dan subfolder yang bisa dibaca melalui akses folder yang sudah diberikan."
                    else if (tree == null)
                        "Angka hanya mencakup file yang dapat dibaca TubeFlow. Untuk menghitung semua file lama dan subfolder, pilih Downloads/${SettingsStore.storageFolder} lewat Pilih folder sendiri. Download tetap disimpan di folder ini."
                    else
                        "Download disimpan ke folder pilihan (internal atau kartu memori eksternal). Hak akses folder disimpan permanen dan dapat dikembalikan kapan saja. Tidak ada izin akses seluruh penyimpanan.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
        }

        Panel(
            title = "Notifikasi",
            description = "Izinkan notifikasi agar progres download tetap terlihat saat aplikasi ditutup.",
        ) {
            ActionButton(
                "Atur notifikasi",
                {
                    context.startActivity(
                        Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName),
                    )
                },
                icon = Icons.Outlined.Notifications,
            )
        }

        Panel(
            title = "Antrean",
            description = "Unduhan yang boleh berjalan bersamaan.",
        ) {
            OptionMenu(
                label = "Unduhan bersamaan",
                options = (1..4).map { count -> count to (if (count == 1) "1 · urut" else "$count×") },
                selected = SettingsStore.maxConcurrent,
                onSelect = { count -> SettingsStore.setMaxConcurrent(context, count) },
            )
            Text(
                "Urut (1) paling hemat CPU dan kuota. Mode paralel lebih cepat, dengan progres tiap unduhan tetap tampil di notifikasi. Maksimal 4 bersamaan; perubahan berlaku untuk antrean berikutnya, sedangkan unduhan yang sudah berjalan dibiarkan selesai.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Panel(
            title = "Smart Media",
            description = "Berlaku untuk download baru. Lirik dan sampul di panel download tetap dapat dipilih secara eksplisit.",
        ) {
            listOf(
                Triple("rename", "Normalisasi nama audio/video", SettingsStore.autoRename),
                Triple("tag", "Tag metadata audio", SettingsStore.autoTag),
                Triple("artwork", "Sampul audio otomatis", SettingsStore.autoArtwork),
                Triple("lyrics", "Lirik audio otomatis", SettingsStore.autoLyrics),
                Triple("library", "Tambah audio otomatis ke koleksi", SettingsStore.autoLibrary),
            ).forEach { (key, label, checked) ->
                ToggleRow(label, checked, { SettingsStore.setSmart(context, key, it) })
            }
            Text("Video selalu ditambahkan ke koleksi. Impor/pindai manual hanya mengindeks file; tidak menulis tag, mengubah kualitas atau mengambil lirik.", style = MaterialTheme.typography.bodySmall)
        }

        Panel(title = "Mesin download") {
            Text("yt-dlp + FFmpeg", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            Text(
                "Mesin disertakan dalam aplikasi dan diverifikasi saat pertama kali dijalankan. Pembaruan datang lewat rilis aplikasi — tidak ada executable jaringan yang diunduh.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (Build.VERSION.SDK_INT >= 31) BluetoothAutoPlaySettings()

        Panel(title = "Privasi") {
            Text(
                "Riwayat dan antrean tersimpan di HP kamu. Download menggunakan koneksi perangkat langsung ke penyedia konten, tanpa server perantara.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Panel(title = "Tentang TubeFlow") {
            Text("Versi ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", style = MaterialTheme.typography.bodyMedium)
            Text("Simpan momen. Putar kapan saja.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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


@Composable
private fun BluetoothAutoPlaySettings() {
    if (Build.VERSION.SDK_INT < 31) return
    val context=LocalContext.current
    fun isGranted()=androidx.core.content.ContextCompat.checkSelfPermission(context,
        android.Manifest.permission.BLUETOOTH_CONNECT)==android.content.pm.PackageManager.PERMISSION_GRANTED
    var granted by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(isGranted()) }
    val launcher=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted=it }
    val owner=androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(owner) {
        val observer=androidx.lifecycle.LifecycleEventObserver { _,event ->
            if (event==androidx.lifecycle.Lifecycle.Event.ON_RESUME) granted=isGranted()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    Panel(
        title = androidx.compose.ui.res.stringResource(id.local.tubeflow.R.string.autoplay_bluetooth_title),
        description = androidx.compose.ui.res.stringResource(id.local.tubeflow.R.string.autoplay_bluetooth_description),
    ) {
        Text(androidx.compose.ui.res.stringResource(if (granted) id.local.tubeflow.R.string.autoplay_bluetooth_granted else id.local.tubeflow.R.string.autoplay_bluetooth_missing),
            style=MaterialTheme.typography.bodyMedium)
        if (!granted) {
            ActionButton(
                androidx.compose.ui.res.stringResource(id.local.tubeflow.R.string.autoplay_bluetooth_enable),
                { launcher.launch(android.Manifest.permission.BLUETOOTH_CONNECT) },
                icon = Icons.Outlined.Bluetooth,
                emphasis = ActionEmphasis.Primary,
            )
            ActionButton(
                androidx.compose.ui.res.stringResource(id.local.tubeflow.R.string.autoplay_bluetooth_app_settings),
                { context.startActivity(Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    "package:${context.packageName}".toUri())) },
                icon = Icons.Outlined.Settings,
                emphasis = ActionEmphasis.Quiet,
            )
        }
    }
}
