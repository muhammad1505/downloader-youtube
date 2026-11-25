package id.local.tubeflow.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BatteryOptimizationTest {
    @Test
    fun realmeDeviceGetsColorOsGuidance() {
        val guidance = BatteryOptimization.guidance("realme", "realme")
        assertEquals(BatteryOptimization.Family.REALME, guidance.family)
        assertEquals("realme", guidance.label)
        assertTrue(guidance.steps.any { it.contains("Optimasi baterai") })
        assertTrue(guidance.steps.any { it.contains("Autostart") })
        assertTrue(guidance.autostart.any { it.startsWith("com.coloros.safecenter/") })
    }

    @Test
    fun brandIsUsedWhenManufacturerIsGeneric() {
        assertEquals(BatteryOptimization.Family.XIAOMI, BatteryOptimization.family("Xiaomi", "Redmi"))
        assertEquals(BatteryOptimization.Family.XIAOMI, BatteryOptimization.family("unknown", "POCO"))
        assertEquals(BatteryOptimization.Family.VIVO, BatteryOptimization.family("vivo", "iQOO"))
        assertEquals(BatteryOptimization.Family.SAMSUNG, BatteryOptimization.family("samsung", "samsung"))
    }

    @Test
    fun unknownDeviceStillGetsUsefulStepsWithoutAutostartShortcut() {
        val guidance = BatteryOptimization.guidance("acme", "acme")
        assertEquals(BatteryOptimization.Family.OTHER, guidance.family)
        assertTrue(guidance.autostart.isEmpty())
        assertTrue(guidance.steps.isNotEmpty())
    }

    @Test
    fun everyKnownFamilyExplainsBatteryOptimization() {
        BatteryOptimization.Family.values().forEach { family ->
            val guidance = BatteryOptimization.guidance(family.name, family.name)
            assertTrue(family.name, guidance.steps.isNotEmpty())
        }
    }

    @Test
    fun manifestDoesNotRequestDirectBatteryExemption() {
        val manifest = listOf(
            File("src/main/AndroidManifest.xml"),
            File("app/src/main/AndroidManifest.xml"),
        ).first { it.isFile }.readText()
        // Komentar boleh menyebut izin ini; yang dilarang adalah deklarasi uses-permission.
        assertFalse(
            Regex("""<uses-permission[^>]*REQUEST_IGNORE_BATTERY_OPTIMIZATIONS""")
                .containsMatchIn(manifest),
        )
    }
}
