package id.local.tubeflow.core

import java.util.Locale

/** Results retain original queue positions so filtered actions target the actual entries. */
internal object PlaybackQueueSearch {
    fun <T> positions(tracks: List<T>, query: String, textOf: (T) -> String): List<Int> {
        val words = query.trim().lowercase(Locale.ROOT).split(Regex("\\s+")).filter(String::isNotEmpty)
        return tracks.indices.filter { position ->
            val text = textOf(tracks[position]).lowercase(Locale.ROOT)
            words.all(text::contains)
        }
    }
}
