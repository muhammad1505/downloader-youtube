package id.local.tubeflow.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import androidx.work.Constraints
import androidx.work.NetworkType

/**
 * Batasan WorkManager untuk unduhan. Default tetap "koneksi apa pun, tanpa syarat daya"
 * supaya perilaku lama tidak berubah; pengguna dapat memilih hemat kuota/baterai.
 */
object DownloadPolicy {
    fun networkType(wifiOnly: Boolean): NetworkType =
        if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED

    fun constraints(wifiOnly: Boolean, chargingOnly: Boolean): Constraints =
        Constraints.Builder()
            .setRequiredNetworkType(networkType(wifiOnly))
            .setRequiresCharging(chargingOnly)
            .build()

    /**
     * Apakah jaringan aktif memenuhi syarat WorkManager saat ini. `UNMETERED` menuntut
     * `NET_CAPABILITY_NOT_METERED`, jadi hotspot/WiFi berbayar sengaja tidak dihitung siap.
     */
    fun networkReady(context: Context, wifiOnly: Boolean): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val capabilities = runCatching { manager.getNetworkCapabilities(manager.activeNetwork) }.getOrNull() ?: return false
        if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return false
        return !wifiOnly || capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    fun charging(context: Context): Boolean =
        context.getSystemService(BatteryManager::class.java)?.isCharging == true

    /** Syarat yang dipakai WorkManager sudah terpenuhi sekarang, bukan sekadar toggle-nya menyala. */
    fun conditionsMet(context: Context, wifiOnly: Boolean, chargingOnly: Boolean): Boolean =
        networkReady(context, wifiOnly) && (!chargingOnly || charging(context))
}
