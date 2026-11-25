package id.local.tubeflow.core

import android.app.NotificationManager
import android.content.Context
import androidx.core.content.edit

/**
 * Jangan Ganggu selama pemutaran lagu.
 *
 * Filter dipilih [DndPolicy.FILTER_ALARMS] (bukan total senyap) supaya notifikasi aplikasi lain,
 * termasuk WhatsApp, tidak berbunyi/bergetar sementara alarm tetap berbunyi. Filter sebelumnya
 * disimpan di SharedPreferences agar proses yang mati saat memutar tidak meninggalkan HP senyap:
 * [release] dipanggil saat service berhenti dan saat proses berikutnya dimulai.
 */
object PlaybackDnd {
    internal const val PREFS = "settings"
    internal const val KEY = "dnd_previous_filter"
    internal const val TARGET = DndPolicy.FILTER_ALARMS
    private var held = false

    /** Izin kebijakan notifikasi diberikan pengguna lewat pengaturan sistem, bukan dialog aplikasi. */
    fun hasAccess(context: Context): Boolean = runCatching {
        context.getSystemService(NotificationManager::class.java)?.isNotificationPolicyAccessGranted == true
    }.getOrDefault(false)

    /** Terapkan Jangan Ganggu sekali per sesi putar; pemanggilan berikutnya tidak menimpa pilihan manual. */
    @Synchronized
    fun hold(context: Context) {
        if (held || !SettingsStore.dndWhilePlaying) return
        val app = context.applicationContext
        val manager = app.getSystemService(NotificationManager::class.java) ?: return
        if (!manager.isNotificationPolicyAccessGranted) return
        val current = manager.currentInterruptionFilter
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.contains(KEY)) prefs.edit { putInt(KEY, current) }
        held = true
        if (!DndPolicy.shouldApply(current, TARGET)) return
        runCatching { manager.setInterruptionFilter(TARGET) }.onSuccess {
            AppLog.info(LogCategory.PLAYER, "Jangan Ganggu aktif selama pemutaran.")
        }
    }

    /** Kembalikan filter pengguna bila TubeFlow yang masih memegang filter tersebut. */
    @Synchronized
    fun release(context: Context) {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        held = false
        if (!prefs.contains(KEY)) return
        val previous = prefs.getInt(KEY, DndPolicy.FILTER_ALL)
        prefs.edit { remove(KEY) }
        val manager = app.getSystemService(NotificationManager::class.java) ?: return
        if (!manager.isNotificationPolicyAccessGranted) return
        if (!DndPolicy.shouldRestore(manager.currentInterruptionFilter, TARGET)) return
        runCatching { manager.setInterruptionFilter(DndPolicy.restore(previous)) }.onSuccess {
            AppLog.info(LogCategory.PLAYER, "Jangan Ganggu dikembalikan setelah pemutaran.")
        }
    }
}

/** Keputusan filter yang murni agar dapat diuji tanpa perangkat. */
internal object DndPolicy {
    // Nilai sama dengan android.app.NotificationManager.INTERRUPTION_FILTER_*; ditulis ulang agar
    // logika ini dapat diuji host tanpa memuat kelas framework.
    const val FILTER_UNKNOWN = 0
    const val FILTER_ALL = 1
    const val FILTER_PRIORITY = 2
    const val FILTER_NONE = 3
    const val FILTER_ALARMS = 4

    /** Filter yang boleh dipasang kembali; nilai tak dikenal jatuh ke ALL agar HP tidak tetap senyap. */
    fun restore(saved: Int): Int = when (saved) {
        FILTER_ALL, FILTER_PRIORITY, FILTER_NONE, FILTER_ALARMS -> saved
        else -> FILTER_ALL
    }

    /** UNKNOWN berarti platform belum melaporkan filter; jangan mengubah apa pun. */
    fun shouldApply(current: Int, target: Int): Boolean = current != FILTER_UNKNOWN && current != target

    /** Hanya kembalikan filter jika nilainya masih filter yang TubeFlow pasang. */
    fun shouldRestore(current: Int, applied: Int): Boolean = current == applied
}
