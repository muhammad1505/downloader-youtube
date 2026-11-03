package id.local.tubeflow.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class CatalogGridTest {

    @Test
    fun phonesUseThreeColumns() {
        assertEquals(3, CatalogGrid.folderColumns(320f))
        assertEquals(3, CatalogGrid.folderColumns(360f))
        assertEquals(3, CatalogGrid.folderColumns(599f))
    }

    @Test
    fun wideScreensUseFourColumns() {
        assertEquals(4, CatalogGrid.folderColumns(600f))
        assertEquals(4, CatalogGrid.folderColumns(840f))
    }
}
