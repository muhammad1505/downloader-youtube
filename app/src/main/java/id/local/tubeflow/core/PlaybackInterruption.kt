package id.local.tubeflow.core

/** In-memory intent for the current track. Owned by the audio Looper, never restored after a crash. */
class PlaybackInterruption {
    private var pending = false
    private var permanent = false
    private var heardOtherAudio = false
    val needsPlaybackEvents: Boolean get() = pending && permanent

    fun lost(permanentLoss: Boolean, wasPlaying: Boolean) {
        // Repeated loss/duck callbacks arrive after we have paused. Preserve the original intent.
        pending = pending || wasPlaying
        permanent = permanent || permanentLoss
    }

    fun takeFocusGain(): Boolean {
        val resume = pending
        cancel()
        return resume
    }

    /** A silent gap during focus handover alone must not trigger a competing focus request. */
    fun playbackChanged(otherAudioActive: Boolean): Boolean {
        if (!pending || !permanent) return false
        if (otherAudioActive) heardOtherAudio = true
        return heardOtherAudio && !otherAudioActive
    }

    /** Called after quiet debounce with fresh system state, immediately before requesting focus. */
    fun takeAfterQuiet(otherAudioActive: Boolean, normalAudioMode: Boolean): Boolean {
        if (!pending || !permanent || !heardOtherAudio || otherAudioActive || !normalAudioMode) return false
        cancel()
        return true
    }

    fun cancel() {
        pending = false
        permanent = false
        heardOtherAudio = false
    }
}
