package id.local.tubeflow.core

import android.content.ContentUris
import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

object MediaAttachments {
    data class Summary(val lyrics: Boolean = false, val subtitles: Int = 0, val checked: Boolean = true)
    private val gate = Semaphore(2)
    private val subtitleExtensions = setOf("srt", "vtt", "ass", "ssa", "ttml", "dfxp", "srv1", "srv2", "srv3")

    fun isSubtitle(name: String): Boolean = name.substringAfterLast('.', "").lowercase(Locale.ROOT) in subtitleExtensions
    fun belongsTo(mediaName: String, sidecarName: String): Boolean {
        val stem = mediaName.substringBeforeLast('.')
        if (stem.isBlank() || !sidecarName.startsWith("$stem.", true)) return false
        val suffix = sidecarName.drop(stem.length + 1)
        if (suffix.equals("lrc", true)) return true
        if (!isSubtitle(sidecarName)) return false
        val language = suffix.substringBeforeLast('.', "")
        return language.isBlank() || Regex("[a-z]{2,3}(?:[-_][a-z0-9]{2,8})*", RegexOption.IGNORE_CASE).matches(language)
    }

    private fun nearbySidecars(context: Context, raw: String): List<String> {
        val resolver = context.contentResolver
        val source = raw.toUri()
        if (source.scheme == "file") {
            val media = source.path?.let(::File) ?: return emptyList()
            return media.parentFile?.listFiles()?.asSequence()?.take(500)
                ?.filter { it.isFile && belongsTo(media.name, it.name) }
                ?.map { it.toURI().toString() }?.toList().orEmpty()
        }
        if (source.authority != "media") return emptyList()
        val result = mutableListOf<String>()
        resolver.query(source, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.RELATIVE_PATH), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val mediaName = cursor.getString(0).orEmpty()
                val folder = cursor.getString(1).orEmpty()
                if (mediaName.isBlank() || folder.isBlank()) return@use
                val prefix = mediaName.substringBeforeLast('.').replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
                val collection = MediaStore.Files.getContentUri("external")
                resolver.query(collection, arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME),
                    "relative_path = ? AND _display_name LIKE ? ESCAPE '\\'", arrayOf(folder, "$prefix.%"), null)?.use { siblings ->
                    var count = 0
                    while (siblings.moveToNext() && count++ < 100) {
                        if (belongsTo(mediaName, siblings.getString(1).orEmpty())) result += ContentUris.withAppendedId(collection, siblings.getLong(0)).toString()
                    }
                }
            }
        }
        return result
    }

    /** The player uses the same exact-stem/folder discovery as attachment labels. */
    suspend fun localVideoSubtitles(context: Context, raw: String): List<VideoSubtitle> =
        withContext(Dispatchers.IO) { gate.withPermit {
            val resolver = context.contentResolver
            val sidecars = runCatching { nearbySidecars(context, raw) }.getOrDefault(emptyList())
                .filter { uri -> runCatching { resolver.openInputStream(uri.toUri())?.use { it.read() >= 0 } == true }.getOrDefault(false) }
                .joinToString("\n") { "sub|$it" }
            VideoPlayback.subtitlesFromSidecars(sidecars) { uri ->
                runCatching {
                    resolver.query(uri.toUri(), arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                        if (it.moveToFirst()) it.getString(0) else null
                    }
                }.getOrNull()
            }
        } }

    suspend fun inspect(context: Context, raw: String, audio: Boolean, lyricUri: String?, sidecars: String?): Summary =
        withContext(Dispatchers.IO) { gate.withPermit {
            val resolver = context.contentResolver
            var checked = true
            fun name(uri: String): String = runCatching {
                resolver.query(uri.toUri(), arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                    if (it.moveToFirst()) it.getString(0) else null
                }
            }.getOrNull() ?: uri.toUri().lastPathSegment.orEmpty()
            fun readable(uri: String): Boolean = try {
                resolver.openInputStream(uri.toUri())?.use { it.read() >= 0 } == true
            } catch (_: java.io.FileNotFoundException) {
                false
            } catch (_: Exception) {
                checked = false
                false
            }
            val external = sidecars.orEmpty().lineSequence().filter { it.startsWith("sub|") }
                .map { it.substringAfter('|') }.filter { it.isNotBlank() }.toMutableSet()
            lyricUri?.takeIf { it.isNotBlank() }?.let(external::add)
            val source = raw.toUri()
            if (!runCatching { resolver.openFileDescriptor(source, "r")?.use { } != null }.getOrDefault(false)) checked = false
            try { external += nearbySidecars(context, raw) }
            catch (_: Exception) { checked = false }
            if (source.scheme == "content" && source.authority != "media" && external.isEmpty()) checked = false
            val names = external.mapNotNull { uri -> if (readable(uri)) name(uri).takeIf { it.isNotBlank() } else null }.distinct()
            var embedded = 0
            if (!audio) {
                val extractor = MediaExtractor()
                try {
                    extractor.setDataSource(context, source, null)
                    for (index in 0 until extractor.trackCount) {
                        val mime = extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME).orEmpty()
                        if (mime.startsWith("text/") || mime in setOf("application/x-subrip", "application/ttml+xml", "application/cea-608", "application/cea-708", "application/x-quicktime-tx3g", "application/dvbsubs", "application/pgs")) embedded++
                    }
                } catch (_: Exception) { checked = false }
                finally { extractor.release() }
            }
            Summary(audio && names.any { it.endsWith(".lrc", true) }, if (audio) 0 else names.count(::isSubtitle) + embedded, checked)
        } }
}
