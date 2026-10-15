package id.local.tubeflow.core

/** Compare with the last rendered state, not every 200 ms engine tick. */
internal object PlaybackNotifications {
    fun shouldUpdate(previous: PlayerViewModel.State, current: PlayerViewModel.State): Boolean {
        if (previous.track != current.track || previous.playing != current.playing ||
            previous.durationMs != current.durationMs || previous.speed != current.speed ||
            previous.loading != current.loading || previous.error != current.error) return true
        if (current.track == null) return false
        // Paused seeks have no following tick: publish even a seek within the same second.
        return if (!current.playing) previous.positionMs != current.positionMs
        else previous.positionMs / 1000 != current.positionMs / 1000
    }
}
