package id.local.tubeflow.core

import org.junit.Assert.*
import org.junit.Test

class PlaybackNotificationsTest {
    private val paused = PlayerViewModel.State(
        track = PlayerViewModel.Track("content://media/external/audio/media/1", "Song"),
        positionMs = 200, durationMs = 180000,
    )

    @Test fun pausedSeekUpdatesSystemPositionIncludingWithinTheSameSecond() {
        assertTrue(PlaybackNotifications.shouldUpdate(paused, paused.copy(positionMs = 80000)))
        assertTrue(PlaybackNotifications.shouldUpdate(paused, paused.copy(positionMs = 500)))
        assertFalse(PlaybackNotifications.shouldUpdate(paused, paused))
    }

    @Test fun playingTicksRenderOnlyWhenTheDisplayedSecondChanges() {
        val playing = paused.copy(playing = true)
        assertFalse(PlaybackNotifications.shouldUpdate(playing, playing.copy(positionMs = 400)))
        assertFalse(PlaybackNotifications.shouldUpdate(playing, playing.copy(positionMs = 999)))
        assertTrue(PlaybackNotifications.shouldUpdate(playing, playing.copy(positionMs = 1000)))
        assertTrue(PlaybackNotifications.shouldUpdate(playing.copy(positionMs = 81000), playing.copy(positionMs = 30000)))
    }

    @Test fun transportAndMetadataChangesRenderWithoutWaitingForATick() {
        assertTrue(PlaybackNotifications.shouldUpdate(paused, paused.copy(playing = true)))
        assertTrue(PlaybackNotifications.shouldUpdate(paused, paused.copy(speed = 1.5f)))
        assertTrue(PlaybackNotifications.shouldUpdate(paused, paused.copy(durationMs = 181000)))
        assertTrue(PlaybackNotifications.shouldUpdate(paused, paused.copy(track = paused.track!!.copy(title = "Renamed"))))
        assertTrue(PlaybackNotifications.shouldUpdate(paused, PlayerViewModel.State()))
        assertFalse(PlaybackNotifications.shouldUpdate(paused, paused.copy(queueIndex = 2)))
    }
}
