package id.local.tubeflow.ui

import android.content.Intent
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import id.local.tubeflow.core.BatteryOptimization
import id.local.tubeflow.core.NetworkDns
import id.local.tubeflow.core.PlaybackDnd
import id.local.tubeflow.core.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Jangan Ganggu untuk pemutaran lagu. Izin kebijakan notifikasi diberikan lewat pengaturan sistem,
 * jadi tombol izin dibuka di sini dan statusnya diperiksa ulang setelah pengguna kembali.
 */
@Composable
fun DndPlaybackControls() {
    val context = LocalContext.current
    var access by remember { mutableStateOf(PlaybackDnd.hasAccess(context)) }
    ToggleRow(
        title = "Jangan Ganggu saat memutar",
        description = "Notifikasi aplikasi lain, termasuk WhatsApp, tidak berbunyi atau bergetar selama lagu diputar. Alarm tetap berbunyi. Filter dikembalikan saat lagu dijeda dan bila aplikasi ditutup.",
        checked = SettingsStore.dndWhilePlaying,
        onCheckedChange = { enabled ->
            SettingsStore.setDndWhilePlaying(context, enabled)
            access = PlaybackDnd.hasAccess(context)
            if (enabled && !access) runCatching {
                context.startActivity(Intent(AndroidSettings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            }
        },
    )
    if (!access) {
        Text(
            "Akses Jangan Ganggu belum diberikan. Tanpa akses ini TubeFlow tidak boleh menyenyapkan notifikasi apa pun.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        ActionButton(
            "Izinkan akses Jangan Ganggu",
            { runCatching { context.startActivity(Intent(AndroidSettings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) } },
            icon = Icons.Outlined.Notifications,
            fillWidth = true,
        )
        ActionButton(
            "Periksa ulang akses",
            { access = PlaybackDnd.hasAccess(context) },
            icon = Icons.Outlined.Refresh,
            emphasis = ActionEmphasis.Quiet,
        )
    } else {
        Text(
            "Akses Jangan Ganggu tersedia. TubeFlow memasang filter “hanya alarm” saat lagu berjalan, bukan senyap total.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Langkah optimasi baterai dan autostart sesuai merek HP yang sedang dipakai. */
@Composable
fun BatterySetupPanel() {
    val context = LocalContext.current
    val guidance = remember { BatteryOptimization.guidanceForDevice() }
    var ignoring by remember { mutableStateOf(BatteryOptimization.isIgnoring(context)) }
    var autostart by remember { mutableStateOf(BatteryOptimization.autostartIntent(context)) }
    Panel(
        title = "Optimasi baterai · ${guidance.label}",
        description = guidance.summary,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(
                Icons.Outlined.BatteryFull,
                null,
                Modifier.size(22.dp),
                tint = if (ignoring) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(if (ignoring) "Pengecualian optimasi baterai aktif" else "TubeFlow masih dibatasi optimasi baterai",
                    style = MaterialTheme.typography.bodyMedium)
                Text(BatteryOptimization.deviceSummary(), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        ActionButton(
            text = if (ignoring) "Buka daftar optimasi baterai" else "Matikan optimasi baterai",
            onClick = {
                BatteryOptimization.openExemption(context)
                ignoring = BatteryOptimization.isIgnoring(context)
            },
            icon = Icons.Outlined.BatteryFull,
            emphasis = ActionEmphasis.Secondary,
            fillWidth = true,
        )
        ActionButton(
            text = if (autostart != null) "Buka Autostart ${guidance.label}" else "Buka info aplikasi",
            onClick = {
                val intent = autostart ?: BatteryOptimization.appInfoIntent(context)
                runCatching { context.startActivity(intent) }
                autostart = BatteryOptimization.autostartIntent(context)
            },
            icon = Icons.Outlined.Refresh,
            fillWidth = true,
        )
        guidance.steps.forEachIndexed { index, step ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${index + 1}.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary)
                Text(step, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(
            "Menu tiap merek berbeda. Bila nama menu tidak sama, cari kata “baterai”, “autostart”, atau “latar belakang” di pengaturan. Perubahan berlaku untuk unduhan dan auto-play headset.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** DNS sistem yang aktif, uji latensi, dan pintasan Private DNS. */
@Composable
fun DnsSetupPanel() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var servers by remember { mutableStateOf(NetworkDns.currentServers(context)) }
    var results by remember { mutableStateOf<List<NetworkDns.Probe>>(emptyList()) }
    var checking by remember { mutableStateOf(false) }
    Panel(
        title = "DNS aktif",
        description = "DNS ini dipakai semua koneksi di HP, termasuk unduhan TubeFlow. Tanpa VPN, aplikasi tidak dapat mengganti DNS hanya untuk dirinya sendiri.",
    ) {
        if (servers.isEmpty()) {
            Text("Resolver belum terbaca. Sambungkan WiFi atau data seluler lalu segarkan.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            servers.forEach { server ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.Wifi, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(server, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        ActionButton(
            "Segarkan DNS",
            { servers = NetworkDns.currentServers(context) },
            icon = Icons.Outlined.Refresh,
            emphasis = ActionEmphasis.Quiet,
        )
        ActionButton(
            text = if (checking) "Mengukur…" else "Uji kecepatan resolver",
            onClick = {
                checking = true
                scope.launch {
                    val probes = withContext(Dispatchers.IO) {
                        NetworkDns.candidates().map { candidate ->
                            candidate.copy(latencyMs = NetworkDns.latencyMs(candidate.host))
                        }
                    }
                    results = NetworkDns.rank(probes)
                    checking = false
                }
            },
            icon = Icons.Outlined.Speed,
            enabled = !checking,
            fillWidth = true,
        )
        if (results.isNotEmpty()) {
            results.forEach { probe ->
                Text(
                    "${probe.label} · ${probe.host} · " + (probe.latencyMs?.let { "$it ms" } ?: "tidak menjawab"),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (probe.latencyMs != null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                )
            }
            Text("Paling cepat: ${results.first().label}. Hasil bisa berbeda tiap pengukuran dan tidak mengubah DNS otomatis.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    Panel(
        title = "Private DNS",
        description = "Mengenkripsi pencarian nama domain untuk seluruh perangkat dan sering lebih cepat daripada DNS operator.",
    ) {
        NetworkDns.privateDnsHosts().forEach { (label, host) ->
            Text("$label · $host", style = MaterialTheme.typography.bodyMedium)
        }
        ActionButton(
            "Buka Private DNS",
            { runCatching { context.startActivity(Intent(AndroidSettings.ACTION_PRIVATE_DNS_SETTINGS)) } },
            icon = Icons.Outlined.Wifi,
            fillWidth = true,
        )
        Text(
            "Pilih “Nama host penyedia DNS privat”, lalu isi salah satu nama di atas. Mode otomatis memakai DNS jaringan; mematikan Private DNS mengembalikan setelan sebelumnya.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (Build.VERSION.SDK_INT < 28) Text(
            "Private DNS memerlukan Android 9 ke atas dan tidak tersedia di perangkat ini.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}
