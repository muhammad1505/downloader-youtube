package id.local.tubeflow.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoEqTest {
    private val min = -15f
    private val max = 15f

    @Test fun bassBandsGetFullRoundBoost() {
        assertEquals(6f, AutoEq.levelDb(60_000, min, max), 0.05f)
        assertEquals(4.5f, AutoEq.levelDb(120_000, min, max), 0.05f)
        assertEquals(2f, AutoEq.levelDb(250_000, min, max), 0.05f)
    }

    @Test fun midsStayClearAndTrebleGetsSubtleShine() {
        assertEquals(0f, AutoEq.levelDb(1_000_000, min, max), 0.05f)
        assertEquals(0f, AutoEq.levelDb(2_000_000, min, max), 0.05f)
        assertTrue(AutoEq.levelDb(8_000_000, min, max) > 0f)
        assertTrue(AutoEq.levelDb(8_000_000, min, max) <= 3f)
    }

    @Test fun fullBassLeadsWhileTrebleStaysRounded() {
        val bass = AutoEq.levelDb(60_000, min, max)
        val treble = AutoEq.levelDb(16_000_000, min, max)
        assertTrue("Bass harus penuh", bass >= 5.5f)
        assertTrue("Treble hanya kilau, bukan menyaingi bass", treble in 1f..3f)
        for (hz in listOf(20, 60, 120, 230, 250, 500, 910, 1000, 2000, 3600, 4000, 8000, 14000, 16000, 20000)) {
            assertTrue("$hz Hz", AutoEq.levelDb(hz * 1000, min, max) in 0f..6f)
        }
    }

    @Test fun bassShoulderIsBroadInsteadOfASpike() {
        // 30–60 Hz sama penuh, lalu turun bertahap sampai mid; bukan lonjakan satu band.
        assertEquals(AutoEq.levelDb(60_000, min, max), AutoEq.levelDb(30_000, min, max), 0.05f)
        assertTrue(AutoEq.levelDb(120_000, min, max) > 3f)
        assertTrue(AutoEq.levelDb(250_000, min, max) > 0f)
    }

    @Test fun curveDecreasesTowardMidsThenRisesAgain() {
        val bass = AutoEq.levelDb(60_000, min, max)
        val lowMid = AutoEq.levelDb(250_000, min, max)
        val mid = AutoEq.levelDb(1_000_000, min, max)
        assertTrue(bass > lowMid)
        assertTrue(lowMid > mid)
    }

    @Test fun interpolationIsSmoothBetweenAnchors() {
        val low = AutoEq.levelDb(60_000, min, max)
        val high = AutoEq.levelDb(120_000, min, max)
        val between = AutoEq.levelDb(85_000, min, max)
        assertTrue(between < low && between > high)
    }

    @Test fun levelsAreClampedToBandRange() {
        assertEquals(2f, AutoEq.levelDb(60_000, -2f, 2f), 0.05f)
        assertEquals(1f, AutoEq.levelDb(8_000_000, -1f, 1f), 0.05f)
        assertEquals(0f, AutoEq.levelDb(2_000_000, -1f, 1f), 0.05f)
    }

    @Test fun levelsAlwaysStayInsideRange() {
        listOf(30, 60_000, 120_000, 250_000, 500_000, 1_000_000, 2_000_000, 4_000_000, 8_000_000, 16_000_000, 24_000_000).forEach { frequency ->
            val level = AutoEq.levelDb(frequency, -6f, 6f)
            assertTrue("frekuensi $frequency → $level", level in -6f..6f)
        }
    }

    @Test fun defaultPresetIsAutoUnlessUserChoseSomething() {
        assertEquals(AutoEq.AUTO_PRESET, AutoEq.defaultPreset(null, hasCustomLevels = false))
        // "Kustom" tanpa level tersimpan = datar, belum ada pilihan nyata → aman diganti kurva auto.
        assertEquals(AutoEq.AUTO_PRESET, AutoEq.defaultPreset(PlayerEqualizer.DEFAULT_PRESET, hasCustomLevels = false))
        // Pilihan pengguna (level kustom atau preset perangkat) dipertahankan.
        assertEquals(PlayerEqualizer.DEFAULT_PRESET, AutoEq.defaultPreset(PlayerEqualizer.DEFAULT_PRESET, hasCustomLevels = true))
        assertEquals("Rock", AutoEq.defaultPreset("Rock", hasCustomLevels = false))
    }
}
