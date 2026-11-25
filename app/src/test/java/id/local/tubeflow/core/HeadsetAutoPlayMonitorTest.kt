package id.local.tubeflow.core

import android.media.AudioDeviceInfo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HeadsetAutoPlayMonitorTest {
    @Test
    fun newWiredHeadsetTriggersAutoPlay() {
        assertTrue(HeadsetAutoPlayPolicy.shouldAutoPlay(
            AudioDeviceInfo.TYPE_WIRED_HEADSET, id = 7, initialOutputs = emptySet(),
        ))
        assertTrue(HeadsetAutoPlayPolicy.shouldAutoPlay(
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES, id = 8, initialOutputs = emptySet(),
        ))
    }

    @Test
    fun bluetoothAndUsbOutputsTriggerAutoPlay() {
        listOf(
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_USB_DEVICE,
        ).forEach { type ->
            assertTrue("type=$type", HeadsetAutoPlayPolicy.shouldAutoPlay(type, id = 9, initialOutputs = emptySet()))
        }
    }

    @Test
    fun alreadyConnectedOutputAndSpeakerDoNotTrigger() {
        assertFalse(HeadsetAutoPlayPolicy.shouldAutoPlay(
            AudioDeviceInfo.TYPE_WIRED_HEADSET, id = 7, initialOutputs = setOf(7),
        ))
        assertFalse(HeadsetAutoPlayPolicy.shouldAutoPlay(
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, id = 3, initialOutputs = emptySet(),
        ))
    }
}
