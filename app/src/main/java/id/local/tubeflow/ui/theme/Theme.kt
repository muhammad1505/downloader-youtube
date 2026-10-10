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

private val DarkPalette = darkColorScheme(
    primary = Color(0xFFC6B7FF), onPrimary = Color(0xFF251B46),
    primaryContainer = Color(0xFF3C305F), onPrimaryContainer = Color(0xFFE9DDFF),
    secondary = Color(0xFFACDCCE), onSecondary = Color(0xFF12392E),
    secondaryContainer = Color(0xFF2F4A41), onSecondaryContainer = Color(0xFFCFF2E5),
    background = Color(0xFF10131C), onBackground = Color(0xFFF0EDF4),
    surface = Color(0xFF171B27), onSurface = Color(0xFFF0EDF4),
    surfaceVariant = Color(0xFF252A39), onSurfaceVariant = Color(0xFFBFC3D0),
    outline = Color(0xFF858B9D), outlineVariant = Color(0xFF3B404F),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
)

private val LightPalette = lightColorScheme(
    primary = Color(0xFF4B2FD1), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE4DBFF), onPrimaryContainer = Color(0xFF12005E),
    secondary = Color(0xFF00695B), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFF9CF2DF), onSecondaryContainer = Color(0xFF00201A),
    background = Color(0xFFF7F5FF), onBackground = Color(0xFF1C1B22),
    surface = Color(0xFFFCFAFF), onSurface = Color(0xFF1C1B22),
    surfaceVariant = Color(0xFFE7E0EC), onSurfaceVariant = Color(0xFF49454E),
    outline = Color(0xFF7A757F), outlineVariant = Color(0xFFCAC4CF),
    error = Color(0xFFB3261E), onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC), onErrorContainer = Color(0xFF410002),
)

private val AppTypography = Typography(
    headlineSmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 32.sp, letterSpacing = 0.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 28.sp, letterSpacing = 0.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.1.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.2.sp),
    bodySmall = TextStyle(fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.3.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp),
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
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
