package id.local.tubeflow.ui

import android.content.Intent
import android.os.Build
import android.provider.DocumentsContract
import kotlinx.coroutines.launch
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import id.local.tubeflow.BuildConfig
import id.local.tubeflow.core.CollectionFolders
import id.local.tubeflow.core.Format
import id.local.tubeflow.core.Lyrics
import id.local.tubeflow.core.SettingsStore
import id.local.tubeflow.core.StorageFolderAccess

@Composable
fun SettingsScreen(
    karaoke: id.local.tubeflow.core.KaraokeViewModel,
    player: id.local.tubeflow.core.PlayerViewModel,
    library: id.local.tubeflow.core.MediaLibraryViewModel,
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
    engineUpdateBlockedReason: String? = null,
    bstationSignedIn: Boolean = false,
    bstationAccountId: String = "",
    onBstationLogin: () -> Unit = {},
    onBstationLogout: () -> Unit = {},
    youtubeSignedIn: Boolean = false,
    onYouTubeLogin: () -> Unit = {},
    onYouTubeLogout: () -> Unit = {},
    section: String? = null,
    onSection: (String?) -> Unit = {},
    onShowReleaseNotes: () -> Unit = {},
) {
    val context = LocalContext.current
    val smartScope = androidx.compose.runtime.rememberCoroutineScope()
    var clearingLyrics by androidx.compose.runtime.remember { mutableStateOf(false) }
    var lyricsCacheMessage by androidx.compose.runtime.remember { mutableStateOf<Int?>(null) }
    val resumeLabel = androidx.compose.ui.res.stringResource(id.local.tubeflow.R.string.resume_interruption_title)
    val partialSuffix = androidx.compose.ui.res.stringResource(id.local.tubeflow.R.string.size_partial)
    val unavailableSize = androidx.compose.ui.res.stringResource(id.local.tubeflow.R.string.size_unavailable)
    var confirmPartial by rememberSaveable { mutableStateOf(false) }
    var confirmBstationLogout by rememberSaveable { mutableStateOf(false) }
    if (confirmBstationLogout) AlertDialog(
        onDismissRequest = { confirmBstationLogout = false },
        title = { DialogTitle("Keluar dari Bstation?", icon = Icons.AutoMirrored.Outlined.Logout) },
        text = { Text("Cookie login akan dihapus dari perangkat. Episode Premium tidak bisa dipilih lagi sampai kamu masuk kembali. Unduhan yang sudah selesai tidak terpengaruh.") },
        confirmButton = { TextButton(onClick = { confirmBstationLogout = false; onBstationLogout() }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Keluar") } },
        dismissButton = { TextButton(onClick = { confirmBstationLogout = false }) { Text("Batal") } },
    )
    if (confirmPartial) AlertDialog(onDismissRequest = { confirmPartial = false },
        title = { DialogTitle(androidx.compose.ui.res.stringResource(id.local.tubeflow.R.string.partial_clear_title), icon = Icons.Outlined.DeleteSweep, destructive = true) },
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
            title = { DialogTitle("Hapus semua file?", icon = Icons.Outlined.DeleteForever, destructive = true) },
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

    // Folder koleksi tambahan: hanya izin baca; tujuan download tetap diatur panel penyimpanan di atas.
    val collectionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) library.addCollectionFolder(uri)
    }
    var pendingRemoveCollection by rememberSaveable { mutableStateOf<String?>(null) }
    val pendingRemoveFolder = SettingsStore.collectionFolders.firstOrNull { it.uri == pendingRemoveCollection }
    if (pendingRemoveFolder != null) AlertDialog(
        onDismissRequest = { pendingRemoveCollection = null },
        title = { DialogTitle("Lepas folder?", icon = Icons.Outlined.FolderOff, destructive = true) },
        text = { Text("\"${pendingRemoveFolder.label}\" tidak lagi dipindai ke Koleksi. File dan subfolder di penyimpanan tidak dihapus, dan tujuan download tidak berubah.") },
        confirmButton = {
            TextButton(
                onClick = { pendingRemoveCollection = null; library.removeCollectionFolder(pendingRemoveFolder) },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text("Lepas") }
        },
        dismissButton = { TextButton(onClick = { pendingRemoveCollection = null }) { Text("Batal") } },
    )

    val scroll = rememberScrollState()
    LaunchedEffect(section) { scroll.scrollTo(0) }
    Column(
        Modifier.fillMaxSize().verticalScroll(scroll).padding(UiSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(UiSpacing.item),
    ) {
        if (section == null) {
            PageHeading(
                androidx.compose.ui.res.stringResource(id.local.tubeflow.R.string.page_settings),
                androidx.compose.ui.res.stringResource(id.local.tubeflow.R.string.page_settings_help),
                Icons.Outlined.Settings,
            )
            val smartCount = listOf(
                SettingsStore.autoRename, SettingsStore.autoTag, SettingsStore.autoArtwork,
                SettingsStore.autoLyrics, SettingsStore.autoLibrary,
            ).count { it }
            val categories = listOf(
                SettingsCategory(
                    title = "Unduhan",
                    summary = "Antrean ${SettingsStore.maxConcurrent}× · Smart Media $smartCount/5 · notifikasi",
                    icon = Icons.Outlined.Download,
                ) { onSection("downloads") },
                SettingsCategory(
                    title = "Penyimpanan",
                    summary = (if (tree != null) "Folder pilihan" else "Downloads/${SettingsStore.storageFolder}") +
                        " · ukuran, cache, dan pembersihan",
                    icon = Icons.Outlined.Folder,
                ) { onSection("storage") },
                SettingsCategory(
                    title = "Tampilan",
                    summary = "${themeModeLabel(themeMode)} · " + when {
                        themeMode == SettingsStore.MODE_CYBERPUNK -> "palet neon"
                        themeMode == SettingsStore.MODE_TRANSFORMERS -> "baja, biru elektrik, dan merah"
                        SettingsStore.artworkAccent -> "aksen dari sampul"
                        else -> "aksen tema"
                    },
                    icon = Icons.Outlined.Palette,
                ) { onSection("display") },
                SettingsCategory(
                    title = "Pemutaran",
                    summary = "Lanjutkan otomatis setelah terputus" + if (Build.VERSION.SDK_INT >= 31) " · Bluetooth" else "",
                    icon = Icons.Outlined.PlayCircle,
                ) { onSection("playback") },
                SettingsCategory(
                    title = "Riwayat",
                    summary = if (SettingsStore.historyAutoDays == 0) "Hapus otomatis nonaktif"
                        else "Hapus otomatis setelah ${SettingsStore.historyAutoDays} hari",
                    icon = Icons.Outlined.History,
                ) { onSection("history") },
                SettingsCategory(
                    title = "Media & alat",
                    summary = "Karaoke, cadangan lirik, dan pembaruan mesin",
                    icon = Icons.Outlined.Build,
                ) { onSection("media") },
                SettingsCategory(
                    title = "Akun Bstation",
                    summary = if (bstationSignedIn)
                        "Masuk" + (bstationAccountId.takeIf { it.isNotBlank() }?.let { " · UID $it" } ?: "") + " · episode Premium dapat dipilih"
                    else "Belum masuk · episode Premium terkunci",
                    icon = Icons.Outlined.AccountCircle,
                ) { onSection("accounts") },
                SettingsCategory(
                    title = "Log aplikasi",
                    summary = "Aktivitas, peringatan, dan galat semua proses",
                    icon = Icons.Outlined.Article,
                ) { onSection("logs") },
                SettingsCategory(
                    title = "Tentang",
                    summary = "Versi ${BuildConfig.VERSION_NAME} · privasi dan ketentuan",
                    icon = Icons.Outlined.Info,
                ) { onSection("about") },
            )
            // Satu kolom tanpa kartu: ikon, judul, dan ringkasan saja — tanpa panah yang kaku.
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                categories.forEachIndexed { index, category ->
                    SettingsCategoryRow(
                        title = category.title,
                        summary = category.summary,
                        icon = category.icon,
                        onClick = category.onClick,
                    )
                    if (index != categories.lastIndex) HorizontalDivider(
                        Modifier.padding(start = 52.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                    )
                }
            }
        } else {
            val (title, subtitle) = settingsSectionInfo(section)
            PageHeading(title, subtitle, settingsSectionIcon(section))
        }
        if (section == "media") {
            MediaToolsSettings(karaoke, player)
        }

        if (section == "accounts") Panel(
            description = "Login Bstation diperlukan untuk episode Premium. TubeFlow memakai halaman login resmi Bstation, bukan formulir kata sandi sendiri.",
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(
                    if (bstationSignedIn) Icons.Outlined.AccountCircle else Icons.Outlined.Lock,
                    null,
                    Modifier.size(24.dp),
                    tint = if (bstationSignedIn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        if (bstationSignedIn) "Akun Bstation aktif" else "Belum masuk ke Bstation",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        when {
                            !bstationSignedIn -> "Episode Premium tetap terlihat di pemilih, tetapi terkunci sampai kamu masuk."
                            bstationAccountId.isNotBlank() -> "UID $bstationAccountId · sesi tersimpan di perangkat ini."
                            else -> "Sesi tersimpan di perangkat ini."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (bstationSignedIn) {
                Text(
                    "Akses Premium tetap mengikuti langganan akun. Bila episode tetap ditolak, periksa status langganan di Bstation lalu coba lagi.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ActionButton(
                    "Keluar dari Bstation",
                    { confirmBstationLogout = true },
                    icon = Icons.AutoMirrored.Outlined.Logout,
                    destructive = true,
                    fillWidth = true,
                )
            } else {
                ActionButton(
                    "Masuk ke Bstation",
                    onBstationLogin,
                    icon = Icons.AutoMirrored.Outlined.Login,
                    emphasis = ActionEmphasis.Primary,
                    fillWidth = true,
                )
            }
            Text(
                "Salinan sesi login disimpan terenkripsi oleh TubeFlow di penyimpanan aplikasi dan hanya dikirim ke Bstation saat membaca katalog atau mengunduh episode. TubeFlow tidak mengirim cookie ke server lain.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(
                    if (youtubeSignedIn) Icons.Outlined.AccountCircle else Icons.Outlined.Lock,
                    null,
                    Modifier.size(24.dp),
                    tint = if (youtubeSignedIn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        if (youtubeSignedIn) "Akun YouTube aktif" else "Belum masuk ke YouTube",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        if (youtubeSignedIn) "Sesi tersimpan di perangkat ini dan dipakai untuk video terbatas."
                        else "Video yang butuh login (usia/anggota kanal) belum bisa diunduh sampai kamu masuk.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (youtubeSignedIn) {
                ActionButton(
                    "Keluar dari YouTube",
                    onYouTubeLogout,
                    icon = Icons.AutoMirrored.Outlined.Logout,
                    destructive = true,
                    fillWidth = true,
                )
            } else {
                ActionButton(
                    "Masuk ke YouTube",
                    onYouTubeLogin,
                    icon = Icons.AutoMirrored.Outlined.Login,
                    emphasis = ActionEmphasis.Primary,
                    fillWidth = true,
                )
            }
            Text(
                "Cookie YouTube/Google disimpan terenkripsi di perangkat dan hanya dipakai untuk membaca atau mengunduh tautan YouTube. TubeFlow tidak mengirim cookie ke platform lain.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (section == "history") Panel(
            description = "Bersihkan catatan riwayat lama tanpa menghapus file media.",
        ) {
            OptionMenu(
                label = "Hapus otomatis",
                options = listOf(
                    0 to "Nonaktif",
                    7 to "Setelah 7 hari",
                    30 to "Setelah 30 hari",
                    90 to "Setelah 90 hari",
                ),
                selected = SettingsStore.historyAutoDays,
                onSelect = { SettingsStore.setHistoryAutoDays(context, it) },
            )
            Text(
                "Hanya entri riwayat selesai yang lebih lama dari batas ini yang dihapus. File hasil download tetap tersimpan di perangkat.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (section == "playback") Panel {
            OptionMenu(
                label = androidx.compose.ui.res.stringResource(id.local.tubeflow.R.string.power_saver_title),
                options = listOf(
                    id.local.tubeflow.core.PlaybackPolicy.SYSTEM to androidx.compose.ui.res.stringResource(id.local.tubeflow.R.string.power_saver_system),
                    id.local.tubeflow.core.PlaybackPolicy.ON to androidx.compose.ui.res.stringResource(id.local.tubeflow.R.string.power_saver_on),
                    id.local.tubeflow.core.PlaybackPolicy.OFF to androidx.compose.ui.res.stringResource(id.local.tubeflow.R.string.power_saver_off),
                ),
                selected = SettingsStore.powerMode,
                onSelect = { SettingsStore.setPowerMode(context, it) },
            )
            Text(androidx.compose.ui.res.stringResource(id.local.tubeflow.R.string.power_saver_help), style = MaterialTheme.typography.bodySmall)
            Text(androidx.compose.ui.res.stringResource(id.local.tubeflow.R.string.video_brightness_help), style = MaterialTheme.typography.bodySmall)
            ToggleRow(
                title = androidx.compose.ui.res.stringResource(id.local.tubeflow.R.string.resume_interruption_title),
                description = androidx.compose.ui.res.stringResource(id.local.tubeflow.R.string.resume_interruption_description),
                checked = SettingsStore.resumeAfterInterruption,
                onCheckedChange = { SettingsStore.setResumeAfterInterruption(context, it) },
                modifier = Modifier.semantics { contentDescription = resumeLabel },
            )
            HorizontalDivider()
            val alertsAllowed = id.local.tubeflow.core.WatchAlerts.canControl(context)
            ToggleRow(
                title = androidx.compose.ui.res.stringResource(id.local.tubeflow.R.string.watch_alerts_title),
                description = androidx.compose.ui.res.stringResource(id.local.tubeflow.R.string.watch_alerts_description),
                checked = SettingsStore.hideAlertsWhileWatching,
                onCheckedChange = { enabled ->
                    SettingsStore.setHideAlertsWhileWatching(context, enabled)
                    // Tanpa izin kebijakan notifikasi, pilihan ini tidak berbuat apa pun.
                    if (enabled && !alertsAllowed) {
                        runCatching { context.startActivity(id.local.tubeflow.core.WatchAlerts.accessIntent()) }
                    }
                },
            )
            Text(
                androidx.compose.ui.res.stringResource(
                    if (alertsAllowed) id.local.tubeflow.R.string.watch_alerts_ready
                    else id.local.tubeflow.R.string.watch_alerts_permission_missing
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (section == "display") Panel(
            description = "Pilih mode tema yang paling nyaman untuk mata kamu.",
        ) {
            OptionMenu(
                label = "Mode tema",
                // Urutan tetap: ikut sistem → terang → gelap → tema khusus di ujung daftar.
                options = listOf(
                    SettingsStore.MODE_SYSTEM to "Ikut sistem",
                    SettingsStore.MODE_LIGHT to "Terang",
                    SettingsStore.MODE_DARK to "Gelap",
                    SettingsStore.MODE_CYBERPUNK to "Cyberpunk",
                    SettingsStore.MODE_TRANSFORMERS to "Transformers",
                ),
                selected = themeMode,
                onSelect = onThemeMode,
            )
            if (themeMode == SettingsStore.MODE_CYBERPUNK) {
                Text("Neon turquoise, cyan, dan magenta di atas latar navy gelap. Tema Cyberpunk tetap memakai paletnya sendiri meski aksen sampul atau warna dinamis diaktifkan.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (themeMode == SettingsStore.MODE_TRANSFORMERS) {
                Text("Tema Transformers memakai baja navy gelap, biru elektrik, merah, dan sudut terpotong yang sama di semua menu: Ringkasan, Download, Koleksi, Alat, serta Pengaturan.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            ToggleRow(
                title = "Aksen dari sampul",
                description = "Ikuti sampul lagu yang diputar atau unduhan terbaru.",
                checked = SettingsStore.artworkAccent,
                onCheckedChange = { SettingsStore.setArtworkAccent(context, it) },
                enabled = themeMode != SettingsStore.MODE_CYBERPUNK && themeMode != SettingsStore.MODE_TRANSFORMERS,
            )
            if (Build.VERSION.SDK_INT >= 31) {
                ToggleRow(
                    title = "Warna dinamis",
                    description = "Ikuti warna wallpaper (Material You).",
                    checked = dynamicColor,
                    onCheckedChange = onDynamicColor,
                    enabled = themeMode != SettingsStore.MODE_CYBERPUNK && themeMode != SettingsStore.MODE_TRANSFORMERS,
                )
            }
        }

        if (section == "storage") Panel {
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

        if (section == "storage") Panel(
            title = "Folder koleksi",
            description = "Folder lain yang ikut dipindai ke Koleksi. Tujuan download tidak berubah dan file tidak dipindahkan.",
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.Folder, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Text(
                    if (tree != null) "Folder pilihan: ${treeLabel(tree)}" else "Downloads/${SettingsStore.storageFolder}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                "Folder ini selalu dipindai dan tidak bisa dilepas.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // Baca dan tulis dipisah: folder dengan grant baca saja tetap bisa dipindai, tetapi
            // penghapusan file akan ditolak provider sampai folder ditambahkan ulang.
            val collectionGrants = remember(SettingsStore.collectionFolders) {
                context.contentResolver.persistedUriPermissions.associate {
                    CollectionFolders.normalize(it.uri.toString()) to (it.isReadPermission to it.isWritePermission)
                }
            }
            SettingsStore.collectionFolders.forEach { folder ->
                val grant = collectionGrants[CollectionFolders.normalize(folder.uri)]
                val readable = grant?.first == true
                val writable = grant?.second == true
                AdaptivePair(
                    leading = { available ->
                        Row(available, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(
                                if (readable) Icons.Outlined.FolderOpen else Icons.Outlined.FolderOff,
                                null,
                                Modifier.size(18.dp),
                                tint = if (readable) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                            )
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(folder.label, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    when {
                                        !readable -> "Akses folder hilang. Lepas lalu tambahkan ulang lewat pemilih folder."
                                        !writable -> "Hanya izin baca. Lepas lalu tambahkan ulang folder agar file bisa dihapus."
                                        else -> treeLabel(folder.uri)
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (readable) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    },
                    trailing = {
                        ActionButton(
                            "Lepas",
                            { pendingRemoveCollection = folder.uri },
                            emphasis = ActionEmphasis.Quiet,
                            destructive = true,
                        )
                    },
                )
            }
            if (SettingsStore.collectionFolders.isEmpty()) Text(
                "Belum ada folder tambahan. Musik atau video di luar Downloads/${SettingsStore.storageFolder} (mis. kartu memori) perlu ditambahkan di sini agar muncul di Koleksi.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ActionButton(
                text = "Periksa ulang indeks koleksi",
                onClick = { library.scan() },
                icon = Icons.Outlined.Refresh,
                fillWidth = true,
            )
            ActionButton(
                text = "Tambah folder…",
                onClick = {
                    collectionLauncher.launch(DocumentsContract.buildDocumentUri(
                        "com.android.externalstorage.documents", "primary:Download/${SettingsStore.storageFolder}",
                    ))
                },
                icon = Icons.Outlined.CreateNewFolder,
                enabled = SettingsStore.collectionFolders.size < CollectionFolders.MAX,
                fillWidth = true,
            )
            Text(
                "Maksimum ${CollectionFolders.MAX} folder. Melepas folder hanya membersihkan indeks Koleksi; file dan subfolder di penyimpanan tetap utuh. Bila izin dicabut sistem, lepas lalu tambahkan ulang folder yang sama.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (section == "downloads") Panel(
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

        if (section == "downloads") Panel(
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
            HorizontalDivider()
            ToggleRow(
                title = "Hanya lewat WiFi",
                description = "Unduhan yang belum berjalan menunggu koneksi tidak berbayar. Mengubah pilihan ini menjadwalkan ulang antrean; unduhan yang sedang berjalan tidak dihentikan.",
                checked = SettingsStore.downloadWifiOnly,
                onCheckedChange = { SettingsStore.setDownloadWifiOnly(context, it) },
            )
            ToggleRow(
                title = "Hanya saat mengisi daya",
                description = "Hemat baterai untuk unduhan besar; syarat ini digabung dengan pilihan jaringan di atas dan menjadwalkan ulang antrean yang belum berjalan.",
                checked = SettingsStore.downloadChargingOnly,
                onCheckedChange = { SettingsStore.setDownloadChargingOnly(context, it) },
            )
        }

        if (section == "downloads") Panel(
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

        if (section == "media") EngineUpdatePanel(engineUpdateBlockedReason)

        if (section == "playback" && Build.VERSION.SDK_INT >= 31) BluetoothAutoPlaySettings()

        if (section == "playback") BatteryAutoPlaySettings()

        if (section == "about") Panel(title = "Privasi") {
            Text(
                "Riwayat dan antrean tersimpan di HP kamu. Download menggunakan koneksi perangkat langsung ke penyedia konten, tanpa server perantara.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (section == "about") Panel {
            Text("Versi ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", style = MaterialTheme.typography.bodyMedium)
            Text("Simpan momen. Putar kapan saja.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ActionButton(androidx.compose.ui.res.stringResource(id.local.tubeflow.R.string.whats_new_open), onShowReleaseNotes,
                icon = Icons.Outlined.NewReleases, emphasis = ActionEmphasis.Quiet, fillWidth = true)
        }

        if (section == null || section == "about") {
            Text(
                "Semua proses berjalan di perangkat ini. Hormati hak cipta dan ketentuan platform sumber.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))
        }
    }
}

private fun themeModeLabel(mode: String): String = when (mode) {
    SettingsStore.MODE_LIGHT -> "Mode terang"
    SettingsStore.MODE_DARK -> "Mode gelap"
    SettingsStore.MODE_CYBERPUNK -> "Cyberpunk"
    SettingsStore.MODE_TRANSFORMERS -> "Transformers"
    else -> "Ikut sistem"
}

/** Ikon judul bagian agar banner tiap layar Pengaturan konsisten dengan menu lain. */
private fun settingsSectionIcon(section: String): ImageVector = when (section) {
    "downloads" -> Icons.Outlined.Download
    "storage" -> Icons.Outlined.Folder
    "display" -> Icons.Outlined.Palette
    "playback" -> Icons.Outlined.PlayCircle
    "history" -> Icons.Outlined.History
    "media" -> Icons.Outlined.Build
    "accounts" -> Icons.Outlined.AccountCircle
    "logs" -> Icons.Outlined.Article
    else -> Icons.Outlined.Info
}

private fun settingsSectionInfo(section: String): Pair<String, String> = when (section) {
    "downloads" -> "Unduhan" to "Antrean, notifikasi, dan Smart Media untuk unduhan baru."
    "storage" -> "Penyimpanan" to "Tujuan file, pemakaian ruang, dan pembersihan."
    "display" -> "Tampilan" to "Tema, aksen, dan warna sistem."
    "playback" -> "Pemutaran" to "Perilaku saat pemutaran terputus dan sambungan Bluetooth."
    "history" -> "Riwayat" to "Batas umur catatan unduhan; file media tidak ikut dihapus."
    "media" -> "Media & alat" to "Karaoke, cadangan lirik, dan mesin media."
    "accounts" -> "Akun Bstation" to "Login untuk episode Premium; cookie disimpan terenkripsi di perangkat."
    "logs" -> "Log aplikasi" to "Aktivitas, peringatan, dan galat semua proses di perangkat ini."
    else -> "Tentang TubeFlow" to "Versi, privasi, dan ketentuan pemakaian."
}

/** Satu baris kategori: ikon, judul, ringkasan nilai aktif, dan tanda buka. */
private data class SettingsCategory(
    val title: String,
    val summary: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

/** Satu baris kategori tanpa kartu dan tanpa panah: ikon, judul, dan ringkasan nilai aktif. */
@Composable
private fun SettingsCategoryRow(
    title: String,
    summary: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(MaterialTheme.shapes.small)
            .clickable(onClickLabel = "Buka $title", onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconBadge(icon, size = 40.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
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

/**
 * Status pengecualian baterai dibaca ulang setiap layar kembali aktif karena pengguna mengubahnya
 * di dialog sistem, bukan di dalam aplikasi.
 */
@Composable
private fun BatteryAutoPlaySettings() {
    val context = LocalContext.current
    var exempt by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(id.local.tubeflow.core.BatteryExemption.isExempt(context))
    }
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(owner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                exempt = id.local.tubeflow.core.BatteryExemption.isExempt(context)
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    Panel(
        title = androidx.compose.ui.res.stringResource(id.local.tubeflow.R.string.battery_unrestricted_title),
        description = androidx.compose.ui.res.stringResource(id.local.tubeflow.R.string.battery_unrestricted_description),
    ) {
        Text(
            androidx.compose.ui.res.stringResource(
                if (exempt) id.local.tubeflow.R.string.battery_unrestricted_on
                else id.local.tubeflow.R.string.battery_unrestricted_off
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
        ActionButton(
            androidx.compose.ui.res.stringResource(id.local.tubeflow.R.string.battery_unrestricted_action),
            { id.local.tubeflow.core.BatteryExemption.open(context) },
            icon = Icons.Outlined.Settings,
            emphasis = ActionEmphasis.Secondary,
        )
    }
}
