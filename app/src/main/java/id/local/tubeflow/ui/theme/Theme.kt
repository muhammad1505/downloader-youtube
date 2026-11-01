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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
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
    primary = Color(0xFF58D994), onPrimary = Color(0xFF00391D),
    primaryContainer = Color(0xFF1D5738), onPrimaryContainer = Color(0xFFB9F2D1),
    secondary = Color(0xFFB9D9C3), onSecondary = Color(0xFF20382A),
    secondaryContainer = Color(0xFF354A3B), onSecondaryContainer = Color(0xFFD5E9D9),
    tertiary = Color(0xFFE0C681), onTertiary = Color(0xFF392D10),
    tertiaryContainer = Color(0xFF554726), onTertiaryContainer = Color(0xFFF4E5B9),
    background = Color(0xFF101411), onBackground = Color(0xFFF0F3EF),
    surface = Color(0xFF141915), onSurface = Color(0xFFF0F3EF),
    surfaceVariant = Color(0xFF252D27), onSurfaceVariant = Color(0xFFBAC5BC),
    surfaceContainerLowest = Color(0xFF0B0F0C), surfaceContainerLow = Color(0xFF141A15),
    surfaceContainer = Color(0xFF1B221D), surfaceContainerHigh = Color(0xFF242D26), surfaceContainerHighest = Color(0xFF2D372F),
    outline = Color(0xFF87968B), outlineVariant = Color(0xFF3B473F),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
)

internal val LightPalette = lightColorScheme(
    // Hijau lebih tua di mode terang agar teks aksen mencapai kontras 4,5:1 di atas latar terang.
    primary = Color(0xFF0B7A42), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB4F0CE), onPrimaryContainer = Color(0xFF04331C),
    secondary = Color(0xFF0B7A50), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFB4F0CE), onSecondaryContainer = Color(0xFF04331C),
    tertiary = Color(0xFF806019), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF1E4BE), onTertiaryContainer = Color(0xFF35290E),
    background = Color(0xFFF4F7F3), onBackground = Color(0xFF161A17),
    surface = Color(0xFFFAFBFA), onSurface = Color(0xFF161A17),
    surfaceVariant = Color(0xFFE2E6E2), onSurfaceVariant = Color(0xFF4A524D),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF5F8F5),
    surfaceContainer = Color(0xFFEDF1ED), surfaceContainerHigh = Color(0xFFE6EBE6),
    surfaceContainerHighest = Color(0xFFE0E6E0),
    outline = Color(0xFF727A75), outlineVariant = Color(0xFFC6CCC7),
    error = Color(0xFFB3261E), onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC), onErrorContainer = Color(0xFF410002),
)

internal val CyberpunkPalette = darkColorScheme(
    primary = Color(0xFF00F5C8), onPrimary = Color(0xFF00211B),
    primaryContainer = Color(0xFF07594C), onPrimaryContainer = Color(0xFF9CFFE9),
    secondary = Color(0xFF70E8FF), onSecondary = Color(0xFF00212A),
    secondaryContainer = Color(0xFF123F50), onSecondaryContainer = Color(0xFFB7F1FF),
    tertiary = Color(0xFFFF73D2), onTertiary = Color(0xFF3F0030),
    tertiaryContainer = Color(0xFF5A1748), onTertiaryContainer = Color(0xFFFFD7F2),
    background = Color(0xFF030A12), onBackground = Color(0xFFE5F7FA),
    surface = Color(0xFF07131D), onSurface = Color(0xFFE5F7FA),
    surfaceVariant = Color(0xFF142531), onSurfaceVariant = Color(0xFFB7CDD5),
    surfaceContainerLowest = Color(0xFF02070D), surfaceContainerLow = Color(0xFF08131C),
    surfaceContainer = Color(0xFF0D1C27), surfaceContainerHigh = Color(0xFF122634), surfaceContainerHighest = Color(0xFF1A3242),
    outline = Color(0xFF7895A2), outlineVariant = Color(0xFF294754),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
)

val LocalCyberpunkTheme = staticCompositionLocalOf { false }

private val AppTypography = Typography(
    headlineLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 38.sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 34.sp, letterSpacing = 0.sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 23.sp, lineHeight = 30.sp, letterSpacing = 0.sp),
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
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun TubeFlowTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    cyberpunk: Boolean = false,
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    val context = LocalContext.current
    if (!view.isInEditMode) SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !darkTheme
            isAppearanceLightNavigationBars = !darkTheme
        }
        window.isNavigationBarContrastEnforced = !cyberpunk
        window.isStatusBarContrastEnforced = !cyberpunk
    }
    val supportsDynamic = Build.VERSION.SDK_INT >= 31
    val useDynamicColor = dynamicColor && !cyberpunk
    val colorScheme = when {
        useDynamicColor && supportsDynamic && darkTheme -> dynamicDarkColorScheme(context)
        useDynamicColor && supportsDynamic -> dynamicLightColorScheme(context)
        cyberpunk -> CyberpunkPalette
        darkTheme -> DarkPalette
        else -> LightPalette
    }
    CompositionLocalProvider(LocalCyberpunkTheme provides cyberpunk) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
