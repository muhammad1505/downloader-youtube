package id.local.tubeflow.core

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import androidx.core.net.toUri

/**
 * Auto-play saat headset tersambung membutuhkan proses yang masih boleh dibangunkan sistem.
 * Penghemat baterai (terutama lapisan OEM) menghentikan proses TubeFlow sehingga broadcast
 * sambungan tidak pernah sampai atau baru diproses saat aplikasi dibuka.
 *
 * Menu ini hanya membuka dialog pengecualian resmi Android untuk paket ini; tidak ada setelan
 * lain yang diubah dan aplikasi tetap berjalan normal bila pengguna menolak.
 */
object BatteryExemption {
    fun isExempt(context: Context): Boolean = runCatching {
        context.getSystemService(PowerManager::class.java)
            ?.isIgnoringBatteryOptimizations(context.packageName) == true
    }.getOrDefault(false)

    /**
     * Dialog sistem "Izinkan TubeFlow tetap berjalan di latar"; butuh izin manifest terkait.
     *
     * Pengecualian hanya dibuka dari aksi eksplisit pengguna di Setelan, bukan otomatis, dan
     * aplikasi tetap berjalan normal bila ditolak. TubeFlow dipasang langsung, bukan lewat Play
     * Store, sehingga kebijakan penggunaan API ini tidak berlaku.
     */
    @SuppressLint("BatteryLife")
    fun open(context: Context) {
        val request = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            "package:${context.packageName}".toUri())
        val list = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        runCatching { context.startActivity(request) }
            .recoverCatching { context.startActivity(list) }
            .recoverCatching {
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    "package:${context.packageName}".toUri()))
            }
    }
}
