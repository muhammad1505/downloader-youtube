package id.local.tubeflow.core

/** User-entered filename supplies editable search parameters, never verified audio tags. */
data class ManualLyricQuery(val title: String, val artist: String = "") {
    companion object {
        fun fromFilename(name: String): ManualLyricQuery {
            val stem=name.substringBeforeLast('.').trim()
            val parts=stem.split(" - ")
            return if (parts.size == 2 && parts.all { it.isNotBlank() }) ManualLyricQuery(parts[0].trim(),parts[1].trim())
                else ManualLyricQuery(stem)
        }
    }
}
