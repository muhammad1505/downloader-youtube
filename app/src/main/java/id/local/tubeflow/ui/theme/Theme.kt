package id.local.tubeflow.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

// Nuansa pemutar musik modern: latar gelap netral, teks putih-redup, dan aksen hijau sebagai
// satu-satunya warna kuat. Nilai hijau dipilih sendiri (bukan warna merek aplikasi mana pun).
// internal agar pasangan warna dapat diuji kontrasnya (ThemeContrastTest).
internal val DarkPalette = darkColorScheme(
    primary = Color(0xFF33D07C), onPrimary = Color(0xFF00391C),
    primaryContainer = Color(0xFF1E5B39), onPrimaryContainer = Color(0xFFB9F2D3),
    secondary = Color(0xFF7ED8A8), onSecondary = Color(0xFF0B3B26),
    secondaryContainer = Color(0xFF22443A), onSecondaryContainer = Color(0xFFBFF0D8),
    tertiary = Color(0xFFAFC6E8), onTertiary = Color(0xFF17304B),
    tertiaryContainer = Color(0xFF2D4561), onTertiaryContainer = Color(0xFFD4E4FF),
    background = Color(0xFF121212), onBackground = Color(0xFFF1F1F1),
    surface = Color(0xFF161616), onSurface = Color(0xFFF1F1F1),
    surfaceVariant = Color(0xFF2A2A2A), onSurfaceVariant = Color(0xFFB3B3B3),
    surfaceContainerLowest=Color(0xFF0A0A0A), surfaceContainerLow=Color(0xFF131313),
    surfaceContainer=Color(0xFF1D1D1D), surfaceContainerHigh=Color(0xFF282828), surfaceContainerHighest=Color(0xFF333333),
    outline = Color(0xFF8A8A8A), outlineVariant = Color(0xFF3E3E3E),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
)

internal val LightPalette = lightColorScheme(
    // Hijau lebih tua di mode terang agar teks aksen mencapai kontras 4,5:1 di atas latar terang.
    primary = Color(0xFF0B7A42), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB4F0CE), onPrimaryContainer = Color(0xFF04331C),
    secondary = Color(0xFF0B7A50), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFB4F0CE), onSecondaryContainer = Color(0xFF04331C),
    tertiary = Color(0xFF3B608F), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD4E4FF), onTertiaryContainer = Color(0xFF17304B),
    background = Color(0xFFF4F6F4), onBackground = Color(0xFF161A17),
    surface = Color(0xFFFAFBFA), onSurface = Color(0xFF161A17),
    surfaceVariant = Color(0xFFE2E6E2), onSurfaceVariant = Color(0xFF4A524D),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF5F8F5),
    surfaceContainer = Color(0xFFEDF1ED), surfaceContainerHigh = Color(0xFFE6EBE6),
    surfaceContainerHighest = Color(0xFFE0E6E0),
    outline = Color(0xFF727A75), outlineVariant = Color(0xFFC6CCC7),
    error = Color(0xFFB3261E), onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC), onErrorContainer = Color(0xFF410002),
)

private val AppTypography = Typography(
    headlineLarge = TextStyle(fontWeight=FontWeight.ExtraBold,fontSize=32.sp,lineHeight=40.sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 28.sp, lineHeight = 36.sp, letterSpacing = 0.sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, lineHeight = 32.sp, letterSpacing = 0.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 28.sp, letterSpacing = 0.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.1.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.2.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.2.sp),
    bodySmall = TextStyle(fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.3.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun TubeFlowTheme(darkTheme: Boolean = isSystemInDarkTheme(), dynamicColor: Boolean = false, content: @Composable () -> Unit) {
    val view = LocalView.current
    val context = LocalContext.current
    if (!view.isInEditMode) SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !darkTheme
            isAppearanceLightNavigationBars = !darkTheme
        }
    }
    val supportsDynamic = Build.VERSION.SDK_INT >= 31
    val colorScheme = when {
        dynamicColor && supportsDynamic && darkTheme -> dynamicDarkColorScheme(context)
        dynamicColor && supportsDynamic -> dynamicLightColorScheme(context)
        darkTheme -> DarkPalette
        else -> LightPalette
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
