package id.local.tubeflow.core

import org.junit.Assert.*
import org.junit.Test

class AppNavigationTest {
    @Test fun notificationsHaveDistinctDestinationsAndOrdinaryLaunchesDoNotOverrideTheCurrentScreen() {
        val actions = AppDestination.entries.map { it.action }
        assertEquals(actions.size, actions.toSet().size)
        for (destination in AppDestination.entries) assertEquals(destination, AppDestination.fromAction(destination.action))
        assertNull(AppDestination.fromAction(null))
        assertNull(AppDestination.fromAction("android.intent.action.MAIN"))
        assertNull(AppDestination.fromAction("android.intent.action.SEND"))
        assertNull(AppDestination.fromAction("unknown"))
    }
    @Test fun successfulDownloadsOpenHistoryWhileFailuresAndTransfersOpenTheQueue() {
        assertEquals(1, AppDestination.downloadResult(true).tab)
        assertEquals(AppDestination.HISTORY, AppDestination.downloadResult(true))
        assertEquals(AppDestination.QUEUE, AppDestination.downloadResult(false))
        assertEquals(1, AppDestination.QUEUE.tab)
    }
    @Test fun downloadTabOwnsBothQueueAndHistoryWhilePlayerUsesCollection() {
        assertEquals(1, AppDestination.DOWNLOAD.tab)
        assertEquals(2, AppDestination.PLAYER.tab)
        assertEquals(2, AppDestination.KARAOKE.tab)
    }
    @Test fun launcherShortcutsOpenTheCollectionWithTheirOwnMediaFilter() {
        assertEquals(2, AppDestination.AUDIO_PLAYER.tab)
        assertEquals(2, AppDestination.VIDEO_PLAYER.tab)
        assertEquals(AppDestination.AUDIO_PLAYER, AppDestination.fromAction("id.local.tubeflow.action.OPEN_AUDIO"))
        assertEquals(AppDestination.VIDEO_PLAYER, AppDestination.fromAction("id.local.tubeflow.action.OPEN_VIDEO"))
    }

    @Test fun launcherShortcutsTargetTheirDownloadSection() {
        assertEquals(DownloadTarget.COMPOSER, AppDestination.DOWNLOAD.downloadTarget)
        assertEquals(DownloadTarget.HISTORY, AppDestination.HISTORY.downloadTarget)
        assertNull(AppDestination.QUEUE.downloadTarget)
    }

    @Test fun repeatedTapsOnTheSameNotificationRemainSeparateNavigationEvents() {
        val first = AppNavigationRequest(AppDestination.PLAYER)
        val second = AppNavigationRequest(AppDestination.PLAYER)
        assertNotEquals(first.id, second.id)
        assertEquals(first.destination, second.destination)
    }
}
