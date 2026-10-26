package id.local.tubeflow.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat

object PlaybackPower {
    private var initialized = false
    private var systemSaving by mutableStateOf(false)
    val saving: Boolean get() = PlaybackPolicy.saving(SettingsStore.powerMode, systemSaving)

    @Synchronized
    fun initialize(context: Context) {
        if (initialized) return
        val app = context.applicationContext
        val power = app.getSystemService(PowerManager::class.java)
        systemSaving = power?.isPowerSaveMode == true
        ContextCompat.registerReceiver(app, object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                systemSaving = power?.isPowerSaveMode == true
            }
        }, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
        initialized = true
    }
}
