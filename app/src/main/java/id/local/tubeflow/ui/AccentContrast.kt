package id.local.tubeflow.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import kotlin.math.max
import kotlin.math.min

/** Primary text must remain readable on both background and surface, including light yellow artwork. */
object AccentContrast {
    fun ratio(first: Color, second: Color): Float =
        (max(first.luminance(),second.luminance())+0.05f)/(min(first.luminance(),second.luminance())+0.05f)
    fun ensure(color: Color, background: Color, surface: Color): Color {
        val anchor=if (background.luminance() < 0.5f) Color.White else Color.Black
        for (step in 0..25) {
            val result=lerp(color,anchor,step/25f)
            if (ratio(result,background) >= 5f && ratio(result,surface) >= 5f) return result
        }
        return anchor
    }
    fun ink(color: Color): Color = if (ratio(Color.Black,color) >= ratio(Color.White,color)) Color.Black else Color.White
}
