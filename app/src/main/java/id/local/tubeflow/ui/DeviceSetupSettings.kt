package id.local.tubeflow.ui

import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Langkah optimasi baterai dan autostart sesuai merek HP yang sedang dipakai. */
@Composable
fun BatterySetupPanel() {
    val context = LocalContext.current
    val guidance = remember { BatteryOptimization.guidanceForDevice() }
    var ignoring by remember { mutableStateOf(BatteryOptimization.isIgnoring(context)) }
    var autostart by remember { mutableStateOf(BatteryOptimization.autostartIntent(context)) }
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(owner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                ignoring = BatteryOptimization.isIgnoring(context)
                autostart = BatteryOptimization.autostartIntent(context)
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
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
            text = "Buka info aplikasi TubeFlow",
            onClick = { runCatching { context.startActivity(BatteryOptimization.appInfoIntent(context)) } },
            icon = Icons.Outlined.Settings,
            emphasis = ActionEmphasis.Secondary,
            fillWidth = true,
        )
        autostart?.let { target ->
            ActionButton(
                text = "Buka Autostart ${guidance.label}",
                onClick = { runCatching { context.startActivity(target) } },
                icon = Icons.Outlined.Refresh,
                fillWidth = true,
            )
        }
        guidance.steps.forEachIndexed { index, step ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${index + 1}.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary)
                Text(step, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(
            "Di Info aplikasi, buka Baterai atau Aktivitas latar belakang, pilih Jangan optimalkan/Tanpa batasan, lalu atur Autostart. Menu tiap merek berbeda; bila nama menu tidak sama, cari kata “baterai”, “autostart”, atau “latar belakang”.",
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
            {
                // ColorOS/realme tidak selalu menyediakan aksi Private DNS langsung. Coba layar
                // khusus, lalu Jaringan & internet, lalu Setelan; jangan menelan kegagalan diam-diam.
                val actions = NetworkDns.privateDnsActions()
                var openedAt = -1
                for ((index, action) in actions.withIndex()) {
                    // Jangan memakai resolveActivity sebagai syarat: pembatasan visibilitas paket
                    // Android 11+ dapat mengembalikan null untuk intent Setelan yang sebenarnya
                    // dapat dibuka. Cukup coba startActivity dan lanjut ke fallback bila gagal.
                    val intent = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    if (runCatching { context.startActivity(intent) }.isSuccess) {
                        openedAt = index
                        break
                    }
                }
                when {
                    openedAt < 0 -> Toast.makeText(
                        context,
                        "Pengaturan jaringan tidak dapat dibuka di perangkat ini.",
                        Toast.LENGTH_LONG,
                    ).show()
                    openedAt > 0 -> Toast.makeText(
                        context,
                        "Private DNS langsung tidak tersedia; membuka pengaturan jaringan. Pilih Private DNS di sana.",
                        Toast.LENGTH_LONG,
                    ).show()
                }
            },
            icon = Icons.Outlined.Wifi,
            fillWidth = true,
        )
        Text(
            "Pilih “Nama host penyedia DNS privat”, lalu isi salah satu nama di atas. Bila layar langsung tidak tersedia, tombol membuka Jaringan & internet; pilih Private DNS di sana. Mode otomatis memakai DNS jaringan; mematikan Private DNS mengembalikan setelan sebelumnya.",
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
