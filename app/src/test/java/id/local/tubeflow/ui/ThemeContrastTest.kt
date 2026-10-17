package id.local.tubeflow.ui.theme

import androidx.compose.ui.graphics.Color
import id.local.tubeflow.ui.AccentContrast
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Kontras adalah bagian dari kualitas visual yang harus terbukti, bukan sekadar terlihat.
 * Pasangan teks wajib 4,5:1; pasangan komponen/ikon besar minimal 3:1. Regresi ini mengunci
 * kedua palet (gelap dan terang) agar pembaruan warna tidak diam-diam merusak keterbacaan.
 */
class ThemeContrastTest {
    @Test fun sharedPanelsAndPausedStateKeepReadableTextInBothThemes() {
        for (palette in listOf(DarkPalette,LightPalette)) {
            assertRatio("panel text",palette.onSurface,palette.surfaceContainer,4.5f)
            assertRatio("panel secondary text",palette.onSurfaceVariant,palette.surfaceContainer,4.5f)
            assertRatio("paused state",palette.onTertiaryContainer,palette.tertiaryContainer,4.5f)
        }
    }
    private fun assertRatio(name: String, first: Color, second: Color, minimum: Float) {
        val ratio = AccentContrast.ratio(first, second)
        assertTrue("$name: kontras $ratio tidak mencapai $minimum", ratio >= minimum)
    }

    @Test fun darkPaletteKeepsReadableTextAndControls() {
        val p = DarkPalette
        assertRatio("teks utama di latar gelap", p.onBackground, p.background, 4.5f)
        assertRatio("teks utama di permukaan gelap", p.onSurface, p.surface, 4.5f)
        assertRatio("teks sekunder di permukaan gelap", p.onSurfaceVariant, p.surface, 4.5f)
        assertRatio("teks aksen di latar gelap", p.primary, p.background, 4.5f)
        assertRatio("ikon di tombol hijau gelap", p.onPrimary, p.primary, 4.5f)
        assertRatio("teks wadah aksen gelap", p.onPrimaryContainer, p.primaryContainer, 4.5f)
    }

    @Test fun lightPaletteKeepsReadableTextAndControls() {
        val p = LightPalette
        assertRatio("teks utama di latar terang", p.onBackground, p.background, 4.5f)
        assertRatio("teks utama di permukaan terang", p.onSurface, p.surface, 4.5f)
        assertRatio("teks sekunder di permukaan terang", p.onSurfaceVariant, p.surface, 4.5f)
        assertRatio("teks aksen di latar terang", p.primary, p.background, 4.5f)
        assertRatio("ikon di tombol hijau terang", p.onPrimary, p.primary, 4.5f)
        assertRatio("teks wadah aksen terang", p.onPrimaryContainer, p.primaryContainer, 4.5f)
    }
}
