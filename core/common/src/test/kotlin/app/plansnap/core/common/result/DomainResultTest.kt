package app.plansnap.core.common.result

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class DomainResultTest {
    private val error = DomainError.RuleViolation(DomainRule.DOCK_CYCLE)
    private val success: DomainResult<Int> = DomainResult.Success(2)
    private val failure: DomainResult<Int> = DomainResult.Failure(error)

    @Test
    fun `given success when mapped then value is transformed`() {
        assertEquals(DomainResult.Success(4), success.map { it * 2 })
    }

    @Test
    fun `given failure when mapped then error is kept`() {
        assertEquals(failure, failure.map { it * 2 })
    }

    @Test
    fun `given success when flat mapped then next result is returned`() {
        assertEquals(failure, success.flatMap { failure })
        assertEquals(DomainResult.Success(3), success.flatMap { DomainResult.Success(it + 1) })
    }

    @Test
    fun `given failure when flat mapped then transform is skipped`() {
        assertEquals(failure, failure.flatMap<Int, Int> { error("must not run") })
    }

    @Test
    fun `given both outcomes when folded then matching branch runs`() {
        assertEquals("2", success.fold({ it.toString() }, { "error" }))
        assertEquals("error", failure.fold({ it.toString() }, { "error" }))
    }

    @Test
    fun `given outcomes when accessed then value and error are nullable`() {
        assertEquals(2, success.getOrNull())
        assertNull(failure.getOrNull())
        assertEquals(error, failure.errorOrNull())
        assertNull(success.errorOrNull())
    }
}
