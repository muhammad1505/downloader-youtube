package id.local.tubeflow.core

import org.junit.Assert.*
import org.junit.Test

class VideoBrightnessPolicyTest {
    @Test fun initialManualUsesActualWindowOrSystemInsteadOfJumpingToHalfBrightness() {
        assertEquals(.75f, VideoBrightnessPolicy.initial(.75f, 10), .001f)
        assertEquals(25 / 255f, VideoBrightnessPolicy.initial(-1f, 25), .001f)
        assertEquals(1f, VideoBrightnessPolicy.initial(-1f, 255), .001f)
    }

    @Test fun manualCannotBlackOutWindowOrPassInvalidValuesToAndroid() {
        assertEquals(.04f, VideoBrightnessPolicy.clamp(-10f), .001f)
        assertEquals(.04f, VideoBrightnessPolicy.initial(-1f, -1), .001f)
        assertEquals(1f, VideoBrightnessPolicy.clamp(2f), .001f)
        assertEquals(.5f, VideoBrightnessPolicy.clamp(Float.NaN), .001f)
        assertEquals(100 / 255f, VideoBrightnessPolicy.initial(Float.POSITIVE_INFINITY, 100), .001f)
    }
}
