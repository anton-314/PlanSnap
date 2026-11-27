package app.plansnap.feature.editor.save

import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.result.EntityKind
import app.plansnap.core.common.result.StorageProblem
import app.plansnap.feature.editor.state.SaveStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Which failed save is still unresolved after each persistence status. */
class PendingSaveFailureTest {
    @Test
    fun `given a failed write when reported then a general failure is pending`() {
        assertEquals(PendingSaveFailure.GENERAL, pendingSaveFailure(FAILED, previous = null))
    }

    @Test
    fun `given a full device when reported then the missing space is pending`() {
        assertEquals(PendingSaveFailure.NO_SPACE, pendingSaveFailure(SaveStatus.Failed(NO_SPACE), previous = null))
    }

    @Test
    fun `given any other failure when reported then a general failure is pending`() {
        val errors =
            listOf(
                DomainError.Storage(StorageProblem.READ_FAILED),
                DomainError.NotFound(EntityKind.PROJECT, "p1"),
            )
        errors.forEach {
            assertEquals(PendingSaveFailure.GENERAL, pendingSaveFailure(SaveStatus.Failed(it), previous = null))
        }
    }

    @Test
    fun `given a newer failure when reported then it replaces the previous cause`() {
        assertEquals(PendingSaveFailure.GENERAL, pendingSaveFailure(FAILED, PendingSaveFailure.NO_SPACE))
    }

    @Test
    fun `given a pending failure when a save runs then it stays pending`() {
        PendingSaveFailure.entries.forEach {
            assertEquals(it, pendingSaveFailure(SaveStatus.Saving, previous = it))
        }
    }

    @Test
    fun `given no failure when a save runs then nothing is pending`() {
        assertNull(pendingSaveFailure(SaveStatus.Saving, previous = null))
    }

    @Test
    fun `given a pending failure when the save succeeded then nothing is pending`() {
        assertNull(pendingSaveFailure(SaveStatus.Saved(1L), PendingSaveFailure.GENERAL))
    }

    @Test
    fun `given a remembered failure when the project is freshly opened then nothing is pending`() {
        assertNull(pendingSaveFailure(SaveStatus.Idle, PendingSaveFailure.GENERAL))
    }
}
