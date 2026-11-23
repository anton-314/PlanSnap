package app.plansnap.core.common.result

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class DomainErrorTest {
    @Test
    fun `given error variants when compared then equality follows their payload`() {
        assertEquals(
            DomainError.InvalidValue(ValueField.WALL_LENGTH, InvalidValueReason.TOO_SMALL),
            DomainError.InvalidValue(ValueField.WALL_LENGTH, InvalidValueReason.TOO_SMALL),
        )
        assertNotEquals(
            DomainError.InvalidValue(ValueField.WALL_LENGTH, InvalidValueReason.TOO_SMALL),
            DomainError.InvalidValue(ValueField.WALL_LENGTH, InvalidValueReason.TOO_LARGE),
        )
        assertEquals("w1", DomainError.NotFound(EntityKind.WALL, "w1").id)
        assertEquals(DomainRule.DOCK_CYCLE, DomainError.RuleViolation(DomainRule.DOCK_CYCLE).rule)
        assertEquals(DocumentProblem.TOO_LARGE, DomainError.Document(DocumentProblem.TOO_LARGE).problem)
        assertEquals(StorageProblem.NO_SPACE, DomainError.Storage(StorageProblem.NO_SPACE).problem)
        assertEquals(ExportProblem.PLAN_TOO_LARGE, DomainError.Export(ExportProblem.PLAN_TOO_LARGE).problem)
    }

    @Test
    fun `given error enums when read then contract values are stable`() {
        assertEquals(
            listOf(
                "WALL_LENGTH",
                "WALL_THICKNESS",
                "CORNER_ANGLE",
                "DIAGONAL_LENGTH",
                "OPENING_OFFSET",
                "OPENING_WIDTH",
                "OPENING_HEIGHT",
                "SILL_HEIGHT",
                "ROOM_HEIGHT",
                "AREA_FACTOR",
                "TOLERANCE",
                "NAME",
            ),
            ValueField.entries.map { it.name },
        )
        assertEquals(
            listOf("MALFORMED", "TOO_SMALL", "TOO_LARGE", "NOT_REPRESENTABLE", "NOT_APPLICABLE"),
            InvalidValueReason.entries.map { it.name },
        )
        assertEquals(
            listOf("PROJECT", "ROOM", "WALL", "CORNER", "DIAGONAL", "OPENING"),
            EntityKind.entries.map { it.name },
        )
    }

    @Test
    fun `given rule and problem enums when read then contract values are stable`() {
        assertEquals(
            listOf(
                "TOO_MANY_WALLS",
                "TOO_FEW_WALLS",
                "ROOM_ALREADY_CLOSED",
                "ROOM_NOT_CLOSED",
                "CORNER_MUST_BE_SPECIFIED",
                "DIAGONAL_INVALID_CORNERS",
                "OPENING_OUT_OF_BOUNDS",
                "OPENING_OVERLAP",
                "DOCK_CYCLE",
                "DOCK_OPENING_NOT_ON_WALL",
                "ADJUSTMENT_NOT_AVAILABLE",
                "DUPLICATE_ID",
                "DOCK_TO_SELF",
                "NO_WALL_TO_REMOVE",
                "WALL_HAS_OPENINGS",
                "WALL_HAS_DOCKED_ROOMS",
                "CORNER_HAS_DIAGONALS",
                "OPENING_HAS_DOCKED_ROOMS",
            ),
            DomainRule.entries.map { it.name },
        )
        assertEquals(
            listOf("MALFORMED", "UNSUPPORTED_VERSION", "TOO_LARGE", "INVALID_VALUES"),
            DocumentProblem.entries.map { it.name },
        )
        assertEquals(listOf("READ_FAILED", "WRITE_FAILED", "NO_SPACE"), StorageProblem.entries.map { it.name })
        assertEquals(
            listOf(
                "NOTHING_TO_EXPORT",
                "PLAN_TOO_LARGE",
                "WRITE_FAILED",
                "PLAN_DOES_NOT_FIT_SELECTION",
                "ENCODING_FAILED",
                "NO_SPACE",
            ),
            ExportProblem.entries.map { it.name },
        )
    }
}
