package id.local.tubeflow.core

/** Bagian tab Download yang harus dibuka setelah navigasi dari shortcut atau notifikasi. */
enum class DownloadTarget { COMPOSER, HISTORY }

enum class AppDestination(
    val action: String,
    val tab: Int,
    val downloadTarget: DownloadTarget? = null,
) {
    DOWNLOAD("id.local.tubeflow.action.OPEN_DOWNLOAD", 1, DownloadTarget.COMPOSER),
    PLAYER("id.local.tubeflow.action.OPEN_PLAYER", 2),
    HISTORY("id.local.tubeflow.action.OPEN_HISTORY", 1, DownloadTarget.HISTORY),
    QUEUE("id.local.tubeflow.action.OPEN_QUEUE", 1),
    KARAOKE("id.local.tubeflow.action.OPEN_KARAOKE", 2),
    // Shortcut launcher: membuka Koleksi dengan filter Musik atau Video, bukan layar terpisah.
    AUDIO_PLAYER("id.local.tubeflow.action.OPEN_AUDIO", 2),
    VIDEO_PLAYER("id.local.tubeflow.action.OPEN_VIDEO", 2);

    companion object {
        fun fromAction(action: String?): AppDestination? = entries.firstOrNull { it.action == action }
        fun downloadResult(success: Boolean) = if (success) HISTORY else QUEUE
    }
}

data class AppNavigationRequest(val destination: AppDestination, val id: String = java.util.UUID.randomUUID().toString())
