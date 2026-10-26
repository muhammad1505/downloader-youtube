package id.local.tubeflow.ui

import android.app.Activity
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import android.provider.Settings
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import id.local.tubeflow.core.PlaybackPolicy
import kotlin.math.abs

@Composable
internal fun AutomaticVideoBrightness(activity: Activity?, inPip: Boolean, saving: Boolean) {
    val owner = LocalLifecycleOwner.current
    DisposableEffect(activity, owner, inPip, saving) {
        val window = activity?.window
        val original = window?.attributes?.screenBrightness ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        val sensors = activity?.getSystemService(SensorManager::class.java)
        val sensor = sensors?.getDefaultSensor(Sensor.TYPE_LIGHT)
        var registered = false
        var lastUpdate = 0L
        var smoothed: Float? = null
        fun brightness(value: Float) {
            window?.let { host -> host.attributes = host.attributes.apply { screenBrightness = value } }
        }
        val listener = object : SensorEventListener {
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            override fun onSensorChanged(event: SensorEvent) {
                val target = PlaybackPolicy.ambientBrightness(event.values.firstOrNull() ?: return) ?: return
                val now = SystemClock.elapsedRealtime()
                if (lastUpdate != 0L && now - lastUpdate < 800L) return
                val value = (smoothed?.let { it + (target - it) * 0.25f } ?: target)
                    .coerceAtMost(if (saving) 0.65f else 1f)
                if (smoothed == null || abs(value - smoothed!!) >= 0.01f) {
                    smoothed = value
                    brightness(value)
                }
                lastUpdate = now
            }
        }
        fun stop() {
            if (registered) sensors?.unregisterListener(listener)
            registered = false
            smoothed = null
            brightness(original)
        }
        fun start() {
            if (inPip) return
            brightness(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE)
            val adaptive = activity?.let {
                Settings.System.getInt(it.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL) == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
            } == true
            if (!adaptive && !registered && sensor != null) {
                registered = sensors.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> start()
                Lifecycle.Event.ON_STOP -> stop()
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) start()
        onDispose {
            owner.lifecycle.removeObserver(observer)
            stop()
        }
    }
}
