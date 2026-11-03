package id.local.tubeflow.core

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Izin baca media per API level. Android 14+ dapat memberi akses visual sebagian
 * (`READ_MEDIA_VISUAL_USER_SELECTED`) ketika pengguna memilih hanya sebagian foto/video.
 *
 * Konstanta `Manifest.permission.READ_MEDIA_*` di-inline saat kompilasi menjadi String, jadi
 * aman dipakai di API 29–32 selama nilainya hanya dipakai untuk pemeriksaan izin, bukan API 33.
 */
@SuppressLint("InlinedApi")
object MediaPermissions {
    const val VISUAL_USER_SELECTED = "android.permission.READ_MEDIA_VISUAL_USER_SELECTED"

    enum class VisualAccess { FULL, PARTIAL, NONE }

    fun requested(sdk: Int): Array<String> = when {
        sdk >= 34 -> arrayOf(
            Manifest.permission.READ_MEDIA_AUDIO,
            Manifest.permission.READ_MEDIA_VIDEO,
            VISUAL_USER_SELECTED,
        )
        sdk >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.READ_MEDIA_VIDEO)
        // API 29 dengan mode legacy memerlukan izin baca/tulis klasik untuk membaca file lama.
        sdk <= 29 -> arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
        )
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    /** Hanya API 34+ yang membedakan akses visual penuh dan sebagian. */
    fun visualAccess(sdk: Int, videoGranted: Boolean, selectedGranted: Boolean): VisualAccess = when {
        sdk < 34 -> if (videoGranted) VisualAccess.FULL else VisualAccess.NONE
        videoGranted -> VisualAccess.FULL
        selectedGranted -> VisualAccess.PARTIAL
        else -> VisualAccess.NONE
    }

    fun visualAccess(context: Context): VisualAccess = visualAccess(
        sdk = Build.VERSION.SDK_INT,
        videoGranted = granted(context, Manifest.permission.READ_MEDIA_VIDEO),
        selectedGranted = Build.VERSION.SDK_INT >= 34 && granted(context, VISUAL_USER_SELECTED),
    )

    fun hasFullRead(context: Context): Boolean {
        val permissions = if (Build.VERSION.SDK_INT >= 33) listOf(
            Manifest.permission.READ_MEDIA_AUDIO,
            Manifest.permission.READ_MEDIA_VIDEO,
        ) else listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        return permissions.all { granted(context, it) }
    }

    fun hasPartialVisualAccess(context: Context): Boolean =
        visualAccess(context) == VisualAccess.PARTIAL

    private fun granted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
