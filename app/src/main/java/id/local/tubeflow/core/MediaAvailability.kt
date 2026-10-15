package id.local.tubeflow.core

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import java.io.File
import java.nio.file.Files

/** Only proven absence removes a catalogue row; an inaccessible provider is not a deleted file. */
internal object MediaAvailability {
    enum class State { PRESENT, MISSING, DENIED, UNKNOWN }

    internal fun decide(rowPresent: Boolean?, readAllowed: Boolean, physicalMissing: Boolean = false): State = when {
        !readAllowed -> State.DENIED
        rowPresent == false || physicalMissing -> State.MISSING
        rowPresent == true -> State.PRESENT
        else -> State.UNKNOWN
    }

    @Suppress("DEPRECATION")
    fun probe(context: Context, item: MediaItem): State {
        val uri = item.location.toUri()
        val granted = if (uri.authority == "media") {
            val permission = if (Build.VERSION.SDK_INT >= 33) {
                if (item.kind == "AUDIO") android.Manifest.permission.READ_MEDIA_AUDIO else android.Manifest.permission.READ_MEDIA_VIDEO
            } else android.Manifest.permission.READ_EXTERNAL_STORAGE
            val broadRead = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
            // Downloads owned by another installation may be hidden even with media permission.
            val downloads = uri.pathSegments.getOrNull(1) == "downloads"
            broadRead && (!downloads || Build.VERSION.SDK_INT == 29 && Environment.isExternalStorageLegacy()) ||
                context.checkUriPermission(uri,android.os.Process.myPid(),android.os.Process.myUid(),
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) == PackageManager.PERMISSION_GRANTED
        } else context.checkUriPermission(uri, android.os.Process.myPid(), android.os.Process.myUid(),
            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) == PackageManager.PERMISSION_GRANTED
        return try {
            val projection = if (uri.authority == "media") arrayOf(OpenableColumns.DISPLAY_NAME, MediaStore.MediaColumns.DATA)
                else arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return decide(false, granted)
                val path = if (uri.authority == "media" && Build.VERSION.SDK_INT == 29 &&
                    Environment.isExternalStorageLegacy() && granted) cursor.getString(1) else null
                val missing = path?.takeIf { it.startsWith("/") }?.let { Files.notExists(File(it).toPath()) } == true
                // A visible row can be owned by this app even without broad read permission.
                if (missing) State.MISSING else {
                    context.contentResolver.openFileDescriptor(uri, "r")?.use { }
                        ?: return State.UNKNOWN
                    State.PRESENT
                }
            } ?: State.UNKNOWN
        } catch (_: SecurityException) { State.DENIED }
          catch (_: Exception) { State.UNKNOWN }
    }
}
