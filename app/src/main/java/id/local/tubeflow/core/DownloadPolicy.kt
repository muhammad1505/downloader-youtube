package id.local.tubeflow.core

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
}
