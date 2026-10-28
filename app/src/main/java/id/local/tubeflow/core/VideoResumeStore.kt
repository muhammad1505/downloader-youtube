package id.local.tubeflow.core

import android.content.Context
import androidx.core.content.edit

/**
 * Menyimpan posisi tonton per URI video. Hanya dipakai pemutar video; pemutar audio punya
 * sesi pemulihannya sendiri di [PlayerSessionStore].
 */
class VideoResumeStore(context: Context) {
    private val preferences =
        context.applicationContext.getSharedPreferences("video-resume", Context.MODE_PRIVATE)

    fun position(uri: String): Long? = entries()[uri]?.positionMs

    /** URI video yang paling terakhir ditonton; cadangan untuk shortcut sebelum ada sesi baru. */
    fun latestUri(): String? = entries().maxByOrNull { it.value.updatedAt }?.key

    fun save(uri: String, positionMs: Long, durationMs: Long) {
        if (uri.isBlank()) return
        val now = System.currentTimeMillis()
        val current = entries(now).toMutableMap()
        val keep = positionMs >= VideoPlayback.RESUME_MIN_MS &&
            (durationMs <= 0 || positionMs < durationMs - VideoPlayback.RESUME_TAIL_MS)
        if (keep) {
            current[uri] = VideoResumeCodec.Entry(positionMs, durationMs.coerceAtLeast(0), now)
            if (current.size > VideoResumeCodec.LIMIT) {
                current.entries.sortedBy { it.value.updatedAt }
                    .take(current.size - VideoResumeCodec.LIMIT)
                    .forEach { current.remove(it.key) }
            }
        } else {
            current.remove(uri)
        }
        preferences.edit { putString(KEY, VideoResumeCodec.encode(current)) }
    }

    fun clear(uri: String) {
        if (uri.isBlank()) return
        val current = entries().toMutableMap()
        if (current.remove(uri) == null) return
        preferences.edit { putString(KEY, VideoResumeCodec.encode(current)) }
    }

    private fun entries(now: Long = System.currentTimeMillis()): Map<String, VideoResumeCodec.Entry> =
        VideoResumeCodec.decode(preferences.getString(KEY, null), now)

    private companion object {
        const val KEY = "entries"
    }
}
