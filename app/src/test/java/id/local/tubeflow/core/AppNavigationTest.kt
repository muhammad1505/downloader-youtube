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
        assertEquals(4, AppDestination.downloadResult(true).tab)
        assertEquals(AppDestination.HISTORY, AppDestination.downloadResult(true))
        assertEquals(AppDestination.QUEUE, AppDestination.downloadResult(false))
        assertEquals(2, AppDestination.QUEUE.tab)
    }
    @Test fun repeatedTapsOnTheSameNotificationRemainSeparateNavigationEvents() {
        val first = AppNavigationRequest(AppDestination.PLAYER)
        val second = AppNavigationRequest(AppDestination.PLAYER)
        assertNotEquals(first.id, second.id)
        assertEquals(first.destination, second.destination)
    }
}
