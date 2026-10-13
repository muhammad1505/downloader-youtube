package id.local.tubeflow.ui

import androidx.compose.ui.graphics.Color
import org.junit.Assert.*
import org.junit.Test

class AccentContrastTest {
    private val colors=listOf(Color.Yellow,Color.Cyan,Color.Green,Color.Magenta,Color.Red,Color.Blue,Color.White,Color.Black)
    @Test fun lightArtworkAndItsInkMeetBodyTextContrast() {
        val background=Color(0xFFF7F5FF); val surface=Color(0xFFFCFAFF)
        for (source in colors) {
            val accent=AccentContrast.ensure(source,background,surface)
            assertTrue(AccentContrast.ratio(accent,background) >= 4.5f)
            assertTrue(AccentContrast.ratio(accent,surface) >= 4.5f)
            assertTrue(AccentContrast.ratio(AccentContrast.ink(accent),accent) >= 4.5f)
        }
    }
    @Test fun darkArtworkAndItsInkMeetBodyTextContrast() {
        val background=Color.Black; val surface=Color(0xFF111113)
        for (source in colors) {
            val accent=AccentContrast.ensure(source,background,surface)
            assertTrue(AccentContrast.ratio(accent,background) >= 4.5f)
            assertTrue(AccentContrast.ratio(accent,surface) >= 4.5f)
            assertTrue(AccentContrast.ratio(AccentContrast.ink(accent),accent) >= 4.5f)
        }
    }
}
