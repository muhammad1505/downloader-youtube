package id.local.tubeflow.ui

import android.app.Activity
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import android.provider.Settings
import android.view.WindowManager
import android.view.Window
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import id.local.tubeflow.core.PlaybackPolicy
import id.local.tubeflow.core.VideoBrightnessMode
import id.local.tubeflow.core.VideoBrightnessPolicy
import kotlin.math.abs

@Composable
internal fun rememberVideoBrightness(activity: Activity?, inPip: Boolean, saving: Boolean,
    mode: VideoBrightnessMode, manualValue: Float): Float {
    val owner = LocalLifecycleOwner.current
    val original = remember(activity) { activity?.window?.attributes?.screenBrightness ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE }
    val manual by rememberUpdatedState(VideoBrightnessPolicy.clamp(manualValue))
    var applied by remember(activity) { mutableFloatStateOf(original) }
    LaunchedEffect(activity, owner, inPip, mode, manualValue) {
        if (!inPip && mode == VideoBrightnessMode.MANUAL && owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            activity?.window?.let { it.attributes = it.attributes.apply { screenBrightness = manual } }
            applied = manual
        }
    }
    DisposableEffect(activity, owner, inPip, saving, mode) {
        val window = activity?.window
        val sensors = activity?.getSystemService(SensorManager::class.java)
        val sensor = sensors?.getDefaultSensor(Sensor.TYPE_LIGHT)
        var registered = false
        var lastUpdate = 0L
        var smoothed: Float? = null
        fun brightness(value: Float) {
            window?.let { host -> host.attributes = host.attributes.apply { screenBrightness = value } }
            applied = value
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
            if (mode == VideoBrightnessMode.MANUAL) {
                brightness(manual)
                return
            }
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
    return applied
}

/** Modal sheets have their own Window; mirror the active player override while they are open. */
@Composable
internal fun VideoDialogBrightness(window: Window?, level: Float) {
    val original = remember(window) { window?.attributes?.screenBrightness ?: -1f }
    LaunchedEffect(window, level) { window?.let { it.attributes = it.attributes.apply { screenBrightness = level } } }
    DisposableEffect(window) {
        onDispose { window?.let { it.attributes = it.attributes.apply { screenBrightness = original } } }
    }
}
