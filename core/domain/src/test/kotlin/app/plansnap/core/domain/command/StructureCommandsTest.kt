package app.plansnap.core.domain.command

import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.result.DomainRule
import app.plansnap.core.common.result.EntityKind
import app.plansnap.core.common.result.InvalidValueReason
import app.plansnap.core.common.result.ValueField
import app.plansnap.core.common.units.Angle
import app.plansnap.core.common.units.Length
import app.plansnap.core.common.units.TurnDirection
import app.plansnap.core.model.ClosureAdjustment
import app.plansnap.core.model.CornerSpec
import app.plansnap.core.model.Diagonal
import app.plansnap.core.model.DiagonalId
import app.plansnap.core.model.DockLink
import app.plansnap.core.model.Project
import app.plansnap.core.model.Room
import app.plansnap.core.model.RoomId
import app.plansnap.core.model.RoomPlacement
import app.plansnap.core.model.Wall
import app.plansnap.core.model.WallId
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** Table tests of the wall, corner, diagonal, close and thickness commands (DOMAIN sections 3 and 11). */
class StructureCommandsTest {
    private val factory = DefaultProjectCommandFactory(FakeOpeningRules())
    private val square = rectangle("a")
    private val project = projectOf(square, openRoom("o", 2))
    private val a = RoomId("a")
    private val o = RoomId("o")
    private val missingRoom = RoomId("x")
    private val newWall = Wall(WallId("new"), Length(2000), Length(120))

    private fun run(
        edit: ProjectEdit,
        on: Project = project,
    ) = factory.create(edit).apply(on)

    private fun guest(link: DockLink) = openRoom("o", 2).copy(placement = RoomPlacement(0.0, 0.0, 0.0, link))

    private fun lengthError(reason: InvalidValueReason) = DomainError.InvalidValue(ValueField.WALL_LENGTH, reason)

    private fun thicknessError(reason: InvalidValueReason) = DomainError.InvalidValue(ValueField.WALL_THICKNESS, reason)

    private fun assertRejected(
        expected: DomainError,
        edit: ProjectEdit,
        on: Project = project,
    ) {
        assertEquals(expected, errorOf(run(edit, on)))
    }

    @Test
    fun `given an open room when a wall is added then wall and corner are appended`() {
        val result = valueOf(run(ProjectEdit.AddWall(o, newWall, measuredRight())))

        val room = result.room("o")
        assertEquals(listOf("o-w0", "o-w1", "new"), room.walls.map { it.id.value })
        assertEquals(measuredRight(), room.corners.last())
        assertEquals(project.room("a"), result.room("a"))
    }

    @Test
    fun `given the first wall when it is added with an unspecified corner then it is accepted`() {
        val empty = projectOf(Room(o))

        val room = valueOf(run(ProjectEdit.AddWall(o, newWall, CornerSpec.Unspecified), empty)).room("o")

        assertEquals(listOf(CornerSpec.Unspecified), room.corners)
    }

    @Test
    fun `given invalid walls when added then each rule rejects without changes`() {
        val full =
            Room(
                o,
                walls = List(128) { Wall(WallId("w$it"), Length(1000), Length(0)) }.toImmutableList(),
                corners = List(128) { if (it == 0) CornerSpec.Unspecified else measuredRight() }.toImmutableList(),
            )
        val corner = measuredRight()
        val cases =
            listOf(
                Triple(
                    "missing room",
                    ProjectEdit.AddWall(missingRoom, newWall, corner),
                    missing(EntityKind.ROOM, "x"),
                ),
                Triple("closed", ProjectEdit.AddWall(a, newWall, corner), violation(DomainRule.ROOM_ALREADY_CLOSED)),
                Triple(
                    "duplicate",
                    ProjectEdit.AddWall(o, newWall.copy(id = WallId("o-w0")), corner),
                    violation(DomainRule.DUPLICATE_ID),
                ),
                Triple(
                    "unspecified",
                    ProjectEdit.AddWall(o, newWall, CornerSpec.Unspecified),
                    violation(DomainRule.CORNER_MUST_BE_SPECIFIED),
                ),
                Triple(
                    "too short",
                    ProjectEdit.AddWall(o, newWall.copy(length = Length(9)), corner),
                    lengthError(InvalidValueReason.TOO_SMALL),
                ),
                Triple(
                    "too long",
                    ProjectEdit.AddWall(o, newWall.copy(length = Length(100_001)), corner),
                    lengthError(InvalidValueReason.TOO_LARGE),
                ),
                Triple(
                    "too thick",
                    ProjectEdit.AddWall(o, newWall.copy(thickness = Length(1001)), corner),
                    thicknessError(InvalidValueReason.TOO_LARGE),
                ),
            )
        cases.forEach { (name, edit, expected) -> assertEquals(expected, errorOf(run(edit)), name) }
        assertRejected(violation(DomainRule.TOO_MANY_WALLS), ProjectEdit.AddWall(o, newWall, corner), projectOf(full))
    }

    @Test
    fun `given a wall when its length is edited then only the raw length changes and an overlay stays stored`() {
        val overlay =
            ClosureAdjustment(1L, 1, persistentListOf(1.0, 1.0, 1.0, 1.0), persistentListOf(0.0, 0.0, 0.0, 0.0))
        val withOverlay = projectOf(square.copy(adjustment = overlay))

        val room = valueOf(run(ProjectEdit.EditWall(a, WallId("a-w1"), Length(3100)), withOverlay)).room("a")

        assertEquals(Length(3100), room.walls[1].length)
        assertEquals(square.walls[1].thickness, room.walls[1].thickness)
        assertEquals(overlay, room.adjustment)
        assertEquals(square.walls[0], room.walls[0])
    }

    @Test
    fun `given invalid wall edits when applied then they are rejected`() {
        assertRejected(missing(EntityKind.WALL, "zz"), ProjectEdit.EditWall(a, WallId("zz"), Length(3000)))
        assertRejected(missing(EntityKind.ROOM, "x"), ProjectEdit.EditWall(RoomId("x"), WallId("zz"), Length(3000)))
        assertRejected(
            DomainError.InvalidValue(ValueField.WALL_LENGTH, InvalidValueReason.TOO_SMALL),
            ProjectEdit.EditWall(a, WallId("a-w0"), Length(0)),
        )
        assertRejected(
            DomainError.InvalidValue(ValueField.WALL_LENGTH, InvalidValueReason.TOO_LARGE),
            ProjectEdit.EditWall(a, WallId("a-w0"), Length(100_001)),
        )
    }

    @Test
    fun `given an open room when the last wall is removed then wall and corner are dropped`() {
        val room = valueOf(run(ProjectEdit.RemoveLastWall(o))).room("o")

        assertEquals(listOf("o-w0"), room.walls.map { it.id.value })
        assertEquals(listOf<CornerSpec>(CornerSpec.Unspecified), room.corners)
    }

    @Test
    fun `given a closed room when the last wall is removed then it is reopened without overlay`() {
        val overlay =
            ClosureAdjustment(1L, 1, persistentListOf(1.0, 1.0, 1.0, 1.0), persistentListOf(0.0, 0.0, 0.0, 0.0))
        val adjusted = projectOf(square.copy(adjustment = overlay))

        val room = valueOf(run(ProjectEdit.RemoveLastWall(a), adjusted)).room("a")

        assertFalse(room.isClosed)
        assertNull(room.adjustment)
        assertEquals(3, room.walls.size)
        assertEquals(square.walls.take(3), room.walls)
    }

    @Test
    fun `given dependencies on the last wall when it is removed then nothing is deleted silently`() {
        val withDiagonal = square.copy(diagonals = persistentListOf(Diagonal(DiagonalId("d"), 0, 3, Length(5000))))
        val withOpening = square.copy(openings = persistentListOf(door("op", WallId("a-w3"))))
        val hostDock = DockLink(a, WallId("a-w3"), WallId("o-w0"), Length.ZERO)
        val ownDock = DockLink(a, WallId("a-w0"), WallId("o-w1"), Length.ZERO)
        val remove = ProjectEdit.RemoveLastWall(a)

        assertRejected(violation(DomainRule.CORNER_HAS_DIAGONALS), remove, projectOf(withDiagonal))
        assertRejected(violation(DomainRule.WALL_HAS_OPENINGS), remove, projectOf(withOpening))
        assertRejected(violation(DomainRule.WALL_HAS_DOCKED_ROOMS), remove, projectOf(square, guest(hostDock)))
        assertRejected(
            violation(DomainRule.WALL_HAS_DOCKED_ROOMS),
            ProjectEdit.RemoveLastWall(o),
            projectOf(square, guest(ownDock)),
        )
        assertRejected(violation(DomainRule.NO_WALL_TO_REMOVE), ProjectEdit.RemoveLastWall(o), projectOf(Room(o)))
        assertRejected(missing(EntityKind.ROOM, "x"), ProjectEdit.RemoveLastWall(missingRoom))
    }

    @Test
    fun `given a corner when it is set then the raw input is replaced`() {
        val corner = CornerSpec.Measured(TurnDirection.RIGHT, Angle(100_000))

        val room = valueOf(run(ProjectEdit.SetCorner(a, 2, corner))).room("a")
        val first = valueOf(run(ProjectEdit.SetCorner(a, 0, measuredRight()))).room("a")

        assertEquals(corner, room.corners[2])
        assertEquals(measuredRight(), first.corners[0])
        assertEquals(square.corners[1], room.corners[1])
    }

    @Test
    fun `given invalid corners when set then they are rejected`() {
        assertRejected(missing(EntityKind.CORNER, "4"), ProjectEdit.SetCorner(a, 4, measuredRight()))
        assertRejected(missing(EntityKind.CORNER, "-1"), ProjectEdit.SetCorner(a, -1, measuredRight()))
        assertRejected(
            violation(DomainRule.CORNER_MUST_BE_SPECIFIED),
            ProjectEdit.SetCorner(a, 1, CornerSpec.Unspecified),
        )
    }

    @Test
    fun `given diagonals when added and removed then the list follows`() {
        val diagonal = Diagonal(DiagonalId("d1"), 0, 2, Length(5000))

        val added = valueOf(run(ProjectEdit.AddDiagonal(a, diagonal)))
        val removed = valueOf(run(ProjectEdit.RemoveDiagonal(a, diagonal.id), added))

        assertEquals(listOf(diagonal), added.room("a").diagonals)
        assertEquals(project, removed)
    }

    @Test
    fun `given invalid diagonals when applied then they are rejected`() {
        val diagonal = Diagonal(DiagonalId("d1"), 0, 2, Length(5000))
        val withDiagonal = projectOf(square.copy(diagonals = persistentListOf(diagonal)))

        assertRejected(
            violation(DomainRule.DIAGONAL_INVALID_CORNERS),
            ProjectEdit.AddDiagonal(a, diagonal.copy(toCorner = 4)),
        )
        assertRejected(
            violation(DomainRule.DIAGONAL_INVALID_CORNERS),
            ProjectEdit.AddDiagonal(a, diagonal.copy(fromCorner = 7, toCorner = 1)),
        )
        assertRejected(
            violation(DomainRule.DUPLICATE_ID),
            ProjectEdit.AddDiagonal(a, diagonal.copy(fromCorner = 1, toCorner = 3)),
            withDiagonal,
        )
        assertRejected(missing(EntityKind.DIAGONAL, "nope"), ProjectEdit.RemoveDiagonal(a, DiagonalId("nope")))
    }

    @Test
    fun `given an open room when it is closed then rules for walls and state apply`() {
        val three = projectOf(openRoom("o", 3))

        assertEquals(true, valueOf(run(ProjectEdit.CloseRoom(o), three)).room("o").isClosed)
        assertRejected(violation(DomainRule.TOO_FEW_WALLS), ProjectEdit.CloseRoom(o))
        assertRejected(violation(DomainRule.ROOM_ALREADY_CLOSED), ProjectEdit.CloseRoom(a))
        assertRejected(missing(EntityKind.ROOM, "x"), ProjectEdit.CloseRoom(RoomId("x")))
    }

    @Test
    fun `given a wall when its thickness is set then the range zero to one meter applies`() {
        val room = valueOf(run(ProjectEdit.SetThickness(a, WallId("a-w2"), Length(0)))).room("a")

        assertEquals(Length(0), room.walls[2].thickness)
        assertEquals(
            Length(1000),
            valueOf(run(ProjectEdit.SetThickness(a, WallId("a-w2"), Length(1000)))).room("a").walls[2].thickness,
        )
        assertRejected(
            DomainError.InvalidValue(ValueField.WALL_THICKNESS, InvalidValueReason.TOO_LARGE),
            ProjectEdit.SetThickness(a, WallId("a-w2"), Length(1001)),
        )
        assertRejected(
            DomainError.InvalidValue(ValueField.WALL_THICKNESS, InvalidValueReason.TOO_SMALL),
            ProjectEdit.SetThickness(a, WallId("a-w2"), Length(-1)),
        )
        assertRejected(missing(EntityKind.WALL, "q"), ProjectEdit.SetThickness(a, WallId("q"), Length(10)))
    }

    @Test
    fun `given equal edits when applied twice then the results are equal and the input is untouched`() =
        runBlocking<Unit> {
            val edit = ProjectEdit.SetThickness(a, WallId("a-w1"), Length(300))
            val before = project

            assertEquals(run(edit), run(edit))
            assertEquals(run(edit), factory.create(edit).apply(project))
            assertEquals(before, project)
        }
}
