package id.local.tubeflow.core

enum class AppDestination(val action: String, val tab: Int) {
    DOWNLOAD("id.local.tubeflow.action.OPEN_DOWNLOAD", 1),
    PLAYER("id.local.tubeflow.action.OPEN_PLAYER", 2),
    HISTORY("id.local.tubeflow.action.OPEN_HISTORY", 1),
    QUEUE("id.local.tubeflow.action.OPEN_QUEUE", 1),
    KARAOKE("id.local.tubeflow.action.OPEN_KARAOKE", 2);

    companion object {
        fun fromAction(action: String?): AppDestination? = entries.firstOrNull { it.action == action }
        fun downloadResult(success: Boolean) = if (success) HISTORY else QUEUE
    }
}

data class AppNavigationRequest(val destination: AppDestination, val id: String = java.util.UUID.randomUUID().toString())
