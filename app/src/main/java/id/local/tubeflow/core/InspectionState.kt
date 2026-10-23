package id.local.tubeflow.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Only the current inspection may publish results. Mutations run on the ViewModel's main thread. */
internal class InspectionState {
    data class Snapshot(
        val metadata: VideoMetadata? = null,
        val playlist: PlaylistMetadata? = null,
        val busy: Boolean = false,
        val phase: String? = null,
        val startedAt: Long = 0L,
        val error: String? = null,
        val details: FailureDetails? = null,
    )

    private val current = MutableStateFlow(Snapshot())
    val state = current.asStateFlow()
    var token: String? = null
        private set

    fun begin(id: String, phase: String = InspectionPhase.PREPARING_ENGINE, startedAt: Long = System.currentTimeMillis()) {
        token = id
        current.value = Snapshot(busy = true, phase = phase, startedAt = startedAt)
    }

    fun phase(id: String, phase: String): Boolean = commit(id) { it.copy(phase = phase) }
    fun video(id: String, metadata: VideoMetadata): Boolean = commit(id) { it.copy(metadata = metadata) }
    fun playlist(id: String, playlist: PlaylistMetadata): Boolean = commit(id) { it.copy(playlist = playlist) }
    fun fail(id: String, message: String, details: FailureDetails): Boolean = commit(id) {
        it.copy(error = message, details = details)
    }
    fun finish(id: String) { commit(id) { it.copy(busy = false, phase = null) } }

    fun clear(): String? {
        val previous = token
        token = null
        // Keep the completed video cache for enqueue; its URL is checked by the caller.
        current.value = Snapshot(metadata = current.value.metadata)
        return previous
    }

    private fun commit(id: String, update: (Snapshot) -> Snapshot): Boolean {
        if (token != id) return false
        current.value = update(current.value)
        return true
    }
}
