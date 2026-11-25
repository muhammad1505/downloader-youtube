package id.local.tubeflow.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.file.NoSuchFileException

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

    @Test fun definitiveFileNotFoundIsMissing() {
        assertEquals(MediaAvailability.State.MISSING,
            MediaAvailability.classifyFailure(FileNotFoundException("gone"), readAllowed = true))
        assertEquals(MediaAvailability.State.MISSING,
            MediaAvailability.classifyFailure(NoSuchFileException("gone"), readAllowed = true))
    }

    @Test fun wrappedMissingFileCauseIsMissing() {
        val wrapped = IllegalArgumentException("Failed to open", FileNotFoundException("Missing file"))
        assertEquals(MediaAvailability.State.MISSING,
            MediaAvailability.classifyFailure(wrapped, readAllowed = true))
        val messageOnly = IllegalStateException("Missing file: content://media/1")
        assertEquals(MediaAvailability.State.MISSING,
            MediaAvailability.classifyFailure(messageOnly, readAllowed = true))
    }

    @Test fun revokedPermissionWinsOverMissingCause() {
        assertEquals(MediaAvailability.State.DENIED,
            MediaAvailability.classifyFailure(FileNotFoundException("gone"), readAllowed = false))
        assertEquals(MediaAvailability.State.DENIED,
            MediaAvailability.classifyFailure(SecurityException("denied"), readAllowed = true))
    }

    @Test fun otherProviderFailuresStayUnknown() {
        assertEquals(MediaAvailability.State.UNKNOWN,
            MediaAvailability.classifyFailure(IOException("timeout"), readAllowed = true))
        assertEquals(MediaAvailability.State.UNKNOWN,
            MediaAvailability.classifyFailure(IllegalStateException("cursor closed"), readAllowed = true))
    }
}
