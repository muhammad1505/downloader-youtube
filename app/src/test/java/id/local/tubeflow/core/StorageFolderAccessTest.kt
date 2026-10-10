package id.local.tubeflow.core

import org.junit.Assert.*
import org.junit.Test

class StorageFolderAccessTest {
    @Test fun matchesPrimaryFolderAndTrailingSlash() {
        val authority = "com.android.externalstorage.documents"
        assertTrue(StorageFolderAccess.matchesDefault(authority, "primary:Download/TubeFlow", "TubeFlow"))
        assertTrue(StorageFolderAccess.matchesDefault(authority, "primary:Download/TubeFlow/", "TubeFlow"))
        assertFalse(StorageFolderAccess.matchesDefault(authority, "ABCD-1234:Download/TubeFlow", "TubeFlow"))
    }

    @Test fun supportsDownloadsProviderRawTreeAcrossUserProfiles() {
        val authority = "com.android.providers.downloads.documents"
        listOf("0", "10").forEach {
            assertTrue(StorageFolderAccess.matchesDefault(authority, "raw:/storage/emulated/$it/Download/TubeFlow", "TubeFlow"))
        }
        assertFalse(StorageFolderAccess.matchesDefault(authority, "msd:123", "TubeFlow"))
    }

    @Test fun rejectsSubfoldersSiblingFoldersAndDifferentParents() {
        listOf("primary:Download/TubeFlow/Denny-Caknan", "primary:Download/TubeFlowBackup",
            "primary:Pictures/TubeFlow", "primary:Download/tubeflow").forEach {
            assertFalse(it, StorageFolderAccess.matchesDefault("com.android.externalstorage.documents", it, "TubeFlow"))
        }
        assertFalse(StorageFolderAccess.matchesDefault("com.android.providers.downloads.documents",
            "raw:/storage/emulated/0/Download/TubeFlow/Denny-Caknan", "TubeFlow"))
    }

    @Test fun folderNamesAreLiteralAndValidated() {
        val authority = "com.android.providers.downloads.documents"
        assertTrue(StorageFolderAccess.matchesDefault(authority, "raw:/storage/emulated/0/Download/my_folder (v1.0)", "my_folder (v1.0)"))
        assertFalse(StorageFolderAccess.matchesDefault(authority, "raw:/storage/emulated/0/Download/myXfolder", "my_folder"))
        assertFalse(StorageFolderAccess.matchesDefault(authority, "raw:/storage/emulated/0/Download/v1x0", "v1.0"))
        assertFalse(StorageFolderAccess.matchesDefault(authority, "raw:/storage/emulated/0/Download/../TubeFlow", "../TubeFlow"))
    }

    @Test fun rejectsUnrecognizedProviders() {
        assertFalse(StorageFolderAccess.matchesDefault("example.cloud.documents", "primary:Download/TubeFlow", "TubeFlow"))
        assertFalse(StorageFolderAccess.matchesDefault(null, "primary:Download/TubeFlow", "TubeFlow"))
    }
}
