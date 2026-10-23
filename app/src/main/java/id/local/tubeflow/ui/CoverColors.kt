package id.local.tubeflow.ui

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object CoverColors {
    private val cache = LruCache<String, Int>(48)
    fun dominant(path: String): Int? {
        val file = File(path)
        if (path.isBlank() || !file.isFile || file.length() > 4 * 1024 * 1024) return null
        val key = "$path:${file.lastModified()}:${file.length()}"
        synchronized(cache) { cache.get(key) }?.let { return it }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds=true }; BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 64) sample *= 2
        val bitmap = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize=sample }) ?: return null
        val bins = mutableMapOf<Int, Int>()
        try {
            for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
                val c=bitmap[x,y]; val r=android.graphics.Color.red(c); val g=android.graphics.Color.green(c); val b=android.graphics.Color.blue(c)
                if (android.graphics.Color.alpha(c) < 128 || maxOf(r,g,b)-minOf(r,g,b) < 24) continue
                val bucket=(r / 32 shl 6) or (g / 32 shl 3) or (b / 32)
                bins[bucket]=(bins[bucket] ?: 0) + 1
            }
        } finally { bitmap.recycle() }
        val bucket=bins.maxByOrNull { it.value }?.key ?: return null
        val color=android.graphics.Color.rgb(((bucket shr 6) and 7)*32+16, ((bucket shr 3) and 7)*32+16, (bucket and 7)*32+16)
        synchronized(cache) { cache.put(key,color) }; return color
    }
}

@Composable
fun rememberCoverAccent(path: String): Color {
    val fallback=MaterialTheme.colorScheme.primary
    val dark=MaterialTheme.colorScheme.background.luminance() < 0.5f
    val raw by produceState<Int?>(null, path) { value=withContext(Dispatchers.IO) { runCatching { CoverColors.dominant(path) }.getOrNull() } }
    return raw?.let {
        val hsl=FloatArray(3); ColorUtils.colorToHSL(it,hsl)
        hsl[1]=hsl[1].coerceIn(0.35f,0.8f); hsl[2]=if (dark) 0.72f else 0.32f
        AccentContrast.ensure(Color(ColorUtils.HSLToColor(hsl)),MaterialTheme.colorScheme.background,MaterialTheme.colorScheme.surface)
    } ?: fallback
}

@Composable
fun AccentTheme(coverPath: String, enabled: Boolean = true, content: @Composable () -> Unit) {
    val base=MaterialTheme.colorScheme
    val target=rememberCoverAccent(if (enabled) coverPath else "")
    val primary by animateColorAsState(if (enabled) target else base.primary, tween(600), label="cover-accent")
    val container=Color(ColorUtils.blendARGB(base.surface.toArgb(), primary.toArgb(), 0.20f))
    fun ink(color: Color) = AccentContrast.ink(color)
    MaterialTheme(colorScheme=base.copy(primary=primary,onPrimary=ink(primary),primaryContainer=container,onPrimaryContainer=ink(container),secondaryContainer=container,onSecondaryContainer=ink(container)),
        typography=MaterialTheme.typography,shapes=MaterialTheme.shapes,content=content)
}
