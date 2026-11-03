package id.local.tubeflow.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RowQuickActionsTest {
    @Test
    fun audioAvailableGetsFavoriteAndShare() {
        assertEquals(
            listOf(MediaActionKind.FAVORITE_ADD, MediaActionKind.SHARE),
            rowQuickActions(audio = true, available = true, favorite = false),
        )
    }

    @Test
    fun favoriteAudioOffersRemoveInsteadOfAdd() {
        val actions = rowQuickActions(audio = true, available = true, favorite = true)
        assertTrue(MediaActionKind.FAVORITE_REMOVE in actions)
        assertTrue(MediaActionKind.FAVORITE_ADD !in actions)
        assertEquals(MediaActionKind.SHARE, actions.last())
    }

    @Test
    fun videoKeepsOnlyItsRowBadges() {
        // Video tidak mendapat aksi cepat; penanda hati dan menu ⋮ tetap menjadi jalurnya.
        assertTrue(rowQuickActions(audio = false, available = true, favorite = false).isEmpty())
    }

    @Test
    fun unavailableAudioHasNoQuickAction() {
        // Berbagi/membuka berkas yang hilang hanya menghasilkan galat, jadi tidak ditawarkan.
        assertTrue(rowQuickActions(audio = true, available = false, favorite = true).isEmpty())
    }
}
