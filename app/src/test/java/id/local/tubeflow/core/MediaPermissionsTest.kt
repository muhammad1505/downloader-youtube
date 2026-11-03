package id.local.tubeflow.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaPermissionsTest {

    @Test
    fun api34RequestsSelectedVisualAccessAlongsideVideo() {
        val requested = MediaPermissions.requested(34).toList()

        assertTrue(MediaPermissions.VISUAL_USER_SELECTED in requested)
        assertTrue(android.Manifest.permission.READ_MEDIA_VIDEO in requested)
        assertTrue(android.Manifest.permission.READ_MEDIA_AUDIO in requested)
    }

    @Test
    fun api33KeepsTheOlderTwoPermissionRequest() {
        val requested = MediaPermissions.requested(33).toList()

        assertFalse(MediaPermissions.VISUAL_USER_SELECTED in requested)
        assertEquals(2, requested.size)
    }

    @Test
    fun legacyApiStillRequestsClassicStoragePermission() {
        val requested = MediaPermissions.requested(29).toList()

        assertTrue(android.Manifest.permission.READ_EXTERNAL_STORAGE in requested)
        assertTrue(android.Manifest.permission.WRITE_EXTERNAL_STORAGE in requested)
    }

    @Test
    fun partialAccessOnlyWhenVideoIsDeniedAndSelectionIsGranted() {
        assertEquals(MediaPermissions.VisualAccess.PARTIAL, MediaPermissions.visualAccess(34, videoGranted = false, selectedGranted = true))
        assertEquals(MediaPermissions.VisualAccess.FULL, MediaPermissions.visualAccess(34, videoGranted = true, selectedGranted = true))
        assertEquals(MediaPermissions.VisualAccess.NONE, MediaPermissions.visualAccess(34, videoGranted = false, selectedGranted = false))
        assertEquals(MediaPermissions.VisualAccess.FULL, MediaPermissions.visualAccess(33, videoGranted = true, selectedGranted = true))
    }
}
