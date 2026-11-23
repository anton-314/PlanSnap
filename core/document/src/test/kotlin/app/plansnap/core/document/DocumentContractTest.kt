package app.plansnap.core.document

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DocumentContractTest {
    @Test
    fun `given current version when read then it is one point zero`() {
        assertEquals(FormatVersion(1, 0), FormatVersion.CURRENT)
    }

    @Test
    fun `given versions when compared then major dominates minor`() {
        assertTrue(FormatVersion(2, 0) > FormatVersion(1, 9))
        assertTrue(FormatVersion(1, 1) > FormatVersion(1, 0))
        assertEquals(0, FormatVersion(1, 2).compareTo(FormatVersion(1, 2)))
    }

    @Test
    fun `given invalid version parts when created then they are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { FormatVersion(0, 0) }
        assertThrows(IllegalArgumentException::class.java) { FormatVersion(1, -1) }
    }

    @Test
    fun `given document limit when read then it is twenty mebibytes`() {
        assertEquals(20_971_520L, DocumentLimits.MAX_DOCUMENT_BYTES)
    }
}
