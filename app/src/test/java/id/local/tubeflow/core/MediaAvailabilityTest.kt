package id.local.tubeflow.core

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaAvailabilityTest {
    @Test fun confirmedMissingRowOrLegacyFileIsRemoved() {
        assertEquals(MediaAvailability.State.MISSING, MediaAvailability.decide(false,true))
        assertEquals(MediaAvailability.State.MISSING, MediaAvailability.decide(true,true,physicalMissing=true))
    }
    @Test fun revokedPermissionOrHiddenRowNeverProvesDeletion() {
        assertEquals(MediaAvailability.State.DENIED, MediaAvailability.decide(false,false))
        assertEquals(MediaAvailability.State.DENIED, MediaAvailability.decide(null,false))
        assertEquals(MediaAvailability.State.DENIED, MediaAvailability.decide(true,false,physicalMissing=true))
    }
    @Test fun nullProviderResponseIsUnknownRatherThanMissing() {
        assertEquals(MediaAvailability.State.UNKNOWN, MediaAvailability.decide(null,true))
        assertEquals(MediaAvailability.State.PRESENT, MediaAvailability.decide(true,true))
    }
}
