package id.local.tubeflow

import android.app.Application
import id.local.tubeflow.core.AppLog
import id.local.tubeflow.core.HeadsetAutoPlayMonitor
import id.local.tubeflow.core.PlaybackDnd

/** Titik awal log aplikasi dan penangkap crash sebelum Activity atau worker mana pun berjalan. */
class TubeFlowApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Diagnostik tidak boleh menggagalkan startup; kegagalan penyiapan log tetap ke logcat.
        runCatching { AppLog.initialize(this) }
            .onFailure { android.util.Log.e("TubeFlow", "Log aplikasi gagal disiapkan.", it) }
        runCatching { AppLog.installCrashHandler() }
            .onFailure { android.util.Log.e("TubeFlow", "Penangkap crash gagal dipasang.", it) }
        // Pemantau sambungan audio harus hidup selama proses, bukan selama Activity: sebelumnya
        // auto-play headset kabel berhenti begitu UI ditutup meski proses masih berjalan.
        runCatching { HeadsetAutoPlayMonitor.start(this) }
            .onFailure { android.util.Log.e("TubeFlow", "Pemantau headset gagal dipasang.", it) }
        // Proses sebelumnya bisa mati saat Jangan Ganggu aktif; kembalikan filter pengguna.
        runCatching { PlaybackDnd.release(this) }
            .onFailure { android.util.Log.e("TubeFlow", "Jangan Ganggu gagal dikembalikan.", it) }
    }
}
