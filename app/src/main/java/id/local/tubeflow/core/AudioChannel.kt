package id.local.tubeflow.core

/** Playback balance only: no vocal separation, transcoding or file mutation. */
enum class AudioChannel(val label: String, val volumes: Pair<Float, Float>) {
    STEREO("L/R", 1f to 1f), LEFT("L", 1f to 0f), RIGHT("R", 0f to 1f);
    fun next(): AudioChannel = entries[(ordinal + 1) % entries.size]
}
