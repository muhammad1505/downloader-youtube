package id.local.tubeflow.core

import android.app.Application
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Sambungan headset Bluetooth diterima lewat manifest agar auto-play tetap bekerja saat UI sudah
 * ditutup: `BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED` dan `BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED`
 * termasuk pengecualian resmi batas broadcast implicit Android 8+, sehingga proses dapat dibangunkan.
 * Headset KABEL hanya memakai `ACTION_HEADSET_PLUG` runtime (platform mengirimnya ke receiver
 * terdaftar saja), jadi kasus proses mati untuk headset kabel mengikuti pembatasan Android.
 * Pemutaran tetap dijalankan engine yang sama ([PlayerViewModel.obtain]); tidak ada player kedua.
 */
class HeadsetAutoPlayReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val connected = when (intent?.action) {
            Intent.ACTION_HEADSET_PLUG -> intent.getIntExtra("state", -1) == 1
            PlayerViewModel.ACTION_BT_A2DP, PlayerViewModel.ACTION_BT_HEADSET ->
                intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1) == BluetoothProfile.STATE_CONNECTED
            else -> false
        }
        android.util.Log.d("TubeFlow-autoload", "receiver action=${intent?.action} connected=$connected")
        if (!connected) return
        PlayerViewModel.autoPlayFromSystem(context.applicationContext as Application)
    }
}
