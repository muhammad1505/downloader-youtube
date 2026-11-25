package id.local.tubeflow.core

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat

/** Kebijakan murni agar keputusan "baru tersambung" dapat diuji tanpa perangkat/Android. */
internal object HeadsetAutoPlayPolicy {
    /** Jenis output yang memicu auto-play: headset kabel, TWS/Bluetooth, dan USB audio. */
    private val OUTPUT_TYPES = setOf(
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
        AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_USB_DEVICE,
    )

    fun shouldAutoPlay(type: Int, id: Int, initialOutputs: Set<Int>): Boolean =
        type in OUTPUT_TYPES && id !in initialOutputs
}

/**
 * Pemantau sambungan audio milik proses, bukan milik Activity/ViewModel.
 *
 * `ACTION_HEADSET_PLUG` hanya dikirim ke receiver runtime dan `AudioDeviceCallback` juga runtime,
 * sehingga sebelumnya auto-play headset kabel berhenti bekerja begitu Activity/audio player ditutup
 * meski proses masih hidup. Registrasi di Application membuat jalur itu tetap aktif; Bluetooth
 * tetap memakai receiver manifest agar proses mati pun dapat dibangunkan.
 */
internal object HeadsetAutoPlayMonitor {
    private var registered = false
    private lateinit var app: Application
    private lateinit var manager: AudioManager
    private val handler = Handler(Looper.getMainLooper())
    private val initialOutputs = mutableSetOf<Int>()

    private val devices = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
            val connected = addedDevices.filter {
                it.isSink && HeadsetAutoPlayPolicy.shouldAutoPlay(it.type, it.id, initialOutputs)
            }
            if (connected.isEmpty()) return
            initialOutputs += connected.map { it.id }
            AppLog.info(LogCategory.PLAYER, "Output audio tersambung; mencoba auto-play.")
            PlaybackService.requestAutoPlay(app)
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
            removedDevices.forEach { initialOutputs.remove(it.id) }
        }
    }

    private val plug = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != Intent.ACTION_HEADSET_PLUG || isInitialStickyBroadcast) return
            if (intent.getIntExtra("state", -1) != 1) return
            AppLog.info(LogCategory.PLAYER, "Headset kabel tersambung; mencoba auto-play.")
            PlaybackService.requestAutoPlay(app)
        }
    }

    fun start(context: Context) {
        synchronized(this) {
            if (registered) return
            val application = context.applicationContext as? Application ?: return
            app = application
            manager = application.getSystemService(AudioManager::class.java)
            // Output yang sudah ada saat proses lahir bukan koneksi baru; jangan memicu auto-play
            // hanya karena aplikasi dibuka dengan headset masih terpasang.
            initialOutputs.clear()
            initialOutputs += manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).map { it.id }
            ContextCompat.registerReceiver(
                application,
                plug,
                IntentFilter(Intent.ACTION_HEADSET_PLUG),
                ContextCompat.RECEIVER_EXPORTED,
            )
            manager.registerAudioDeviceCallback(devices, handler)
            registered = true
        }
    }
}
