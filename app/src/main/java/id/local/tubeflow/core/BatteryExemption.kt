package id.local.tubeflow.core

import android.content.Context
import android.os.PowerManager

/**
 * Status pengecualian optimasi baterai untuk auto-play dan unduhan latar.
 *
 * TubeFlow tidak lagi meminta pengecualian langsung lewat dialog sistem. Google membatasi izin
 * `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, dan Realme/ColorOS sering mengarahkan dialog itu ke layar
 * yang salah. Panduan di Setelan membuka Info aplikasi, tempat pengguna dapat memilih Baterai →
 * Jangan optimalkan, Aktivitas latar belakang, dan Autostart sesuai merek HP.
 */
object BatteryExemption {
    fun isExempt(context: Context): Boolean = runCatching {
        context.getSystemService(PowerManager::class.java)
            ?.isIgnoringBatteryOptimizations(context.packageName) == true
    }.getOrDefault(false)
}
