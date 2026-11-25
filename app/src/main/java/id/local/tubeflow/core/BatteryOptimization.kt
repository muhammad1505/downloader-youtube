package id.local.tubeflow.core

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import java.util.Locale

/**
 * Pintasan pengecualian optimasi baterai dan autostart sesuai merek HP.
 *
 * Android tidak punya satu layar standar untuk autostart; tiap OEM memakai activity berbeda dan
 * pengguna harus mengaktifkannya sendiri. TubeFlow membuka Info aplikasi (dan layar autostart OEM
 * bila tersedia) lalu menjelaskan langkahnya. Dialog pengecualian baterai langsung tidak dipakai:
 * izinnya dibatasi Google dan navigasinya sering salah pada realme/ColorOS.
 */
internal object BatteryOptimization {
    enum class Family { REALME, OPPO, XIAOMI, VIVO, HUAWEI, SAMSUNG, OTHER }

    data class Guidance(
        val family: Family,
        val label: String,
        val summary: String,
        val steps: List<String>,
        /** Daftar "package/class" autostart OEM, dicoba berurutan. */
        val autostart: List<String>,
    )

    fun family(manufacturer: String, brand: String): Family {
        val maker = manufacturer.lowercase(Locale.ROOT).trim()
        val mark = brand.lowercase(Locale.ROOT).trim()
        return when {
            maker.contains("realme") || mark.contains("realme") -> Family.REALME
            maker.contains("oppo") || mark.contains("oppo") ||
                maker.contains("oneplus") || mark.contains("oneplus") -> Family.OPPO
            maker.contains("xiaomi") || mark.contains("xiaomi") ||
                mark.contains("redmi") || mark.contains("poco") -> Family.XIAOMI
            maker.contains("vivo") || mark.contains("vivo") || mark.contains("iqoo") -> Family.VIVO
            maker.contains("huawei") || mark.contains("huawei") ||
                maker.contains("honor") || mark.contains("honor") -> Family.HUAWEI
            maker.contains("samsung") || mark.contains("samsung") -> Family.SAMSUNG
            else -> Family.OTHER
        }
    }

    fun guidance(manufacturer: String, brand: String): Guidance = when (family(manufacturer, brand)) {
        Family.REALME -> Guidance(
            Family.REALME, "realme",
            "realme UI/ColorOS membatasi aktivitas latar setelah layar mati. Izinkan TubeFlow di baterai dan autostart.",
            listOf(
                "Pengaturan → Baterai → Optimasi baterai → TubeFlow → Jangan optimalkan.",
                "Pengaturan → Baterai → Aktivitas latar belakang aplikasi → TubeFlow → Izinkan aktivitas latar.",
                "Pengaturan → Aplikasi → Kelola aplikasi → TubeFlow → Autostart aktif.",
                "Di daftar aplikasi terbaru, kunci (lock) kartu TubeFlow agar tidak dibersihkan.",
            ),
            listOf(
                "com.coloros.safecenter/com.coloros.safecenter.permission.startup.StartupAppListActivity",
                "com.coloros.safecenter/com.coloros.safecenter.startupapp.StartupAppListActivity",
                "com.oplus.safecenter/com.oplus.safecenter.permission.startup.StartupAppListActivity",
            ),
        )
        Family.OPPO -> Guidance(
            Family.OPPO, "OPPO",
            "ColorOS memakai manajemen baterai dan autostart sendiri. Izinkan TubeFlow agar unduhan tidak dihentikan.",
            listOf(
                "Pengaturan → Baterai → Optimasi baterai → TubeFlow → Jangan optimalkan.",
                "Pengaturan → Baterai → Aktivitas latar belakang aplikasi → TubeFlow → Izinkan.",
                "Pengaturan → Aplikasi → Autostart → TubeFlow aktif.",
                "Kunci kartu TubeFlow di daftar aplikasi terbaru.",
            ),
            listOf(
                "com.coloros.safecenter/com.coloros.safecenter.permission.startup.StartupAppListActivity",
                "com.coloros.safecenter/com.coloros.safecenter.startupapp.StartupAppListActivity",
                "com.oppo.safe/com.oppo.safe.permission.startup.StartupAppListActivity",
                "com.oplus.safecenter/com.oplus.safecenter.permission.startup.StartupAppListActivity",
            ),
        )
        Family.XIAOMI -> Guidance(
            Family.XIAOMI, "MIUI/HyperOS",
            "MIUI menghentikan aplikasi latar dengan agresif. Autostart dan hemat baterai harus diatur manual.",
            listOf(
                "Pengaturan → Aplikasi → Kelola aplikasi → TubeFlow → Autostart aktif.",
                "Pengaturan → Aplikasi → Kelola aplikasi → TubeFlow → Hemat baterai → Tanpa batasan.",
                "Pengaturan → Baterai → Hemat baterai → TubeFlow → Tanpa batasan.",
                "Kunci kartu TubeFlow di daftar aplikasi terbaru.",
            ),
            listOf("com.miui.securitycenter/com.miui.permcenter.autostart.AutoStartManagementActivity"),
        )
        Family.VIVO -> Guidance(
            Family.VIVO, "vivo",
            "FuntouchOS mengatur autostart dan latar belakang daya tinggi di menu terpisah.",
            listOf(
                "Pengaturan → Baterai → Latar belakang daya tinggi → TubeFlow → Izinkan.",
                "Pengaturan → Aplikasi → Autostart → TubeFlow aktif.",
                "Pengaturan → Baterai → Optimasi baterai → TubeFlow → Jangan optimalkan.",
            ),
            listOf(
                "com.vivo.permissionmanager/com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
                "com.iqoo.secure/com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity",
            ),
        )
        Family.HUAWEI -> Guidance(
            Family.HUAWEI, "EMUI",
            "EMUI memakai peluncuran aplikasi manual. TubeFlow harus dikelola manual agar tetap berjalan.",
            listOf(
                "Pengaturan → Baterai → Peluncuran aplikasi → TubeFlow → Kelola secara manual, aktifkan ketiganya.",
                "Pengaturan → Baterai → Optimasi baterai → TubeFlow → Jangan optimalkan.",
                "Kunci kartu TubeFlow di daftar aplikasi terbaru.",
            ),
            listOf(
                "com.huawei.systemmanager/com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
                "com.huawei.systemmanager/com.huawei.systemmanager.optimize.process.ProtectActivity",
            ),
        )
        Family.SAMSUNG -> Guidance(
            Family.SAMSUNG, "One UI",
            "One UI memakai batas penggunaan latar dan daftar aplikasi tidur.",
            listOf(
                "Pengaturan → Baterai → Batas penggunaan latar belakang → TubeFlow → Jangan batasi.",
                "Pengaturan → Baterai → Aplikasi tidur → keluarkan TubeFlow.",
                "Pengaturan → Baterai → Optimasi baterai → TubeFlow → Jangan optimalkan.",
            ),
            listOf("com.samsung.android.lool/com.samsung.android.sm.ui.battery.BatteryActivity"),
        )
        Family.OTHER -> Guidance(
            Family.OTHER, "perangkat",
            "Izinkan TubeFlow berjalan tanpa optimasi baterai agar unduhan dan auto-play tidak diputus.",
            listOf(
                "Pengaturan → Aplikasi → TubeFlow → Baterai → Tidak dibatasi/Jangan optimalkan.",
                "Bila ada menu autostart atau pengelola latar belakang, aktifkan TubeFlow di sana.",
                "Kunci kartu TubeFlow di daftar aplikasi terbaru bila tersedia.",
            ),
            emptyList(),
        )
    }

    fun guidanceForDevice(): Guidance = guidance(Build.MANUFACTURER.orEmpty(), Build.BRAND.orEmpty())

    fun deviceSummary(): String {
        val maker = Build.MANUFACTURER.orEmpty().replaceFirstChar { it.uppercase(Locale.ROOT) }
        val model = Build.MODEL.orEmpty()
        val android = Build.VERSION.RELEASE.orEmpty()
        return listOf(maker, model, "Android $android").filter { it.isNotBlank() }.joinToString(" · ")
    }

    /** Status pengecualian dibaca tanpa membuka dialog sistem; panduan memakai Info aplikasi. */
    fun isIgnoring(context: Context): Boolean = BatteryExemption.isExempt(context)

    /** Layanan autostart OEM yang terpasang, atau null bila tidak ada. */
    fun autostartIntent(context: Context): Intent? {
        val manager = context.packageManager
        for (flat in guidanceForDevice().autostart) {
            val parts = flat.split('/')
            if (parts.size != 2) continue
            val intent = Intent().setComponent(ComponentName(parts[0], parts[1]))
            val resolvable = runCatching { manager.resolveActivity(intent, 0) != null }.getOrDefault(false)
            if (resolvable) return intent
        }
        return null
    }

    fun appInfoIntent(context: Context): Intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.parse("package:${context.packageName}"),
    )
}
