package id.local.tubeflow.core

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.content.edit

/**
 * Menahan notifikasi melayang selama video diputar penuh.
 *
 * Aplikasi tidak dapat menyembunyikan notifikasi aplikasi lain sendiri; satu-satunya jalur yang
 * didukung Android adalah mengganti filter interupsi (mode Jangan Ganggu) memakai izin akses
 * kebijakan notifikasi. Notifikasi tetap masuk ke panel dan riwayat; hanya suara/heads-up yang
 * ditahan. Filter sebelumnya disimpan sekali lalu dipulihkan saat video dijeda, masuk PiP, atau
 * ditutup — termasuk setelah proses mati lewat [restoreLeftover].
 */
object WatchAlerts {
    private const val PREFS = "watch-alerts"
    private const val KEY_ACTIVE = "active"
    private const val KEY_PREVIOUS = "previous"

    /** Izin akses kebijakan notifikasi; tanpa izin ini perubahan filter ditolak sistem. */
    fun canControl(context: Context): Boolean =
        context.getSystemService(NotificationManager::class.java)?.isNotificationPolicyAccessGranted == true

    fun accessIntent(): Intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)

    /** Menahan notifikasi lain; aman dipanggil berulang karena filter awal hanya disimpan sekali. */
    fun suppress(context: Context): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        if (!manager.isNotificationPolicyAccessGranted) return false
        return runCatching {
            val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (!prefs.getBoolean(KEY_ACTIVE, false)) {
                prefs.edit {
                    putBoolean(KEY_ACTIVE, true)
                    putInt(KEY_PREVIOUS, manager.currentInterruptionFilter)
                }
            }
            if (manager.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_PRIORITY) {
                manager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
            }
            true
        }.getOrDefault(false)
    }

    /** Mengembalikan filter interupsi yang dipakai sebelum menonton; tidak menyentuh setelan lain. */
    fun restore(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_ACTIVE, false)) return
        val previous = prefs.getInt(KEY_PREVIOUS, NotificationManager.INTERRUPTION_FILTER_ALL)
        prefs.edit {
            putBoolean(KEY_ACTIVE, false)
            remove(KEY_PREVIOUS)
        }
        runCatching {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            if (manager.isNotificationPolicyAccessGranted) manager.setInterruptionFilter(previous)
        }
    }

    /** Filter yang tertinggal karena proses berhenti saat video masih diputar. */
    fun restoreLeftover(context: Context) = restore(context)
}
