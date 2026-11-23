package app.plansnap.core.model

import app.plansnap.core.common.units.Angle
import app.plansnap.core.common.units.Length
import app.plansnap.core.common.units.TurnDirection
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ModelInvariantsTest {
    private val left = CornerSpec.DeclaredRight(TurnDirection.LEFT)

    private fun assertRejected(block: () -> Unit) {
        assertThrows(IllegalArgumentException::class.java) { block() }
    }

    private fun wall(id: String) = Wall(WallId(id), Length(3000L), Length(120L))

    private fun walls(n: Int) = List(n) { wall("w$it") }.toImmutableList()

    private fun corners(n: Int) = (listOf<CornerSpec>(CornerSpec.Unspecified) + List(n - 1) { left }).toImmutableList()

    private fun adjustment(n: Int) =
        ClosureAdjustment(
            inputFingerprint = 1L,
            solverVersion = 1,
            adjustedLengthsMm = List(n) { 3000.0 }.toImmutableList(),
            adjustedTurnsRad = List(n) { 1.0 }.toImmutableList(),
        )

    private fun opening(
        type: OpeningType = OpeningType.DOOR,
        offset: Long = 0L,
        width: Long = 900L,
        sill: Length? = null,
        swing: DoorSwing? = null,
    ) = Opening(OpeningId("o"), WallId("w0"), type, Length(offset), Length(width), null, sill, swing)

    @Test
    fun `given blank ids when created then they are rejected`() {
        listOf("", "   ").forEach {
            assertRejected { ProjectId(it) }
            assertRejected { RoomId(it) }
            assertRejected { WallId(it) }
            assertRejected { DiagonalId(it) }
            assertRejected { OpeningId(it) }
            assertRejected { RoomTypeKey(it) }
        }
    }

    @Test
    fun `given wall values when created then length is positive and thickness not negative`() {
        assertDoesNotThrow { Wall(WallId("w"), Length(1L), Length.ZERO) }
        assertRejected { Wall(WallId("w"), Length.ZERO, Length.ZERO) }
        assertRejected { Wall(WallId("w"), Length(-1L), Length.ZERO) }
        assertRejected { Wall(WallId("w"), Length(1L), Length(-1L)) }
    }

    @Test
    fun `given corner specs when read then direction matches`() {
        assertEquals(TurnDirection.LEFT, left.direction)
        assertEquals(TurnDirection.RIGHT, CornerSpec.Measured(TurnDirection.RIGHT, Angle.RIGHT).direction)
        assertNull(CornerSpec.Unspecified.direction)
    }

    @Test
    fun `given measured angle limits when created then only valid angles pass`() {
        assertRejected { CornerSpec.Measured(TurnDirection.LEFT, Angle.ZERO) }
        assertRejected { CornerSpec.Measured(TurnDirection.LEFT, Angle(-1)) }
        assertRejected { CornerSpec.Measured(TurnDirection.LEFT, Angle(180_001)) }
        assertDoesNotThrow { CornerSpec.Measured(TurnDirection.LEFT, Angle(1)) }
        assertDoesNotThrow { CornerSpec.Measured(TurnDirection.LEFT, Angle.STRAIGHT) }
    }

    @Test
    fun `given invalid diagonal when created then it is rejected`() {
        val id = DiagonalId("d")
        assertRejected { Diagonal(id, -1, 1, Length(1L)) }
        assertRejected { Diagonal(id, 1, -1, Length(1L)) }
        assertRejected { Diagonal(id, 1, 1, Length(1L)) }
        assertRejected { Diagonal(id, 0, 1, Length.ZERO) }
        assertRejected { Diagonal(id, 0, 1, Length(-1L)) }
    }

    @Test
    fun `given invalid adjustment when created then it is rejected`() {
        val ok = persistentListOf(1.0, 1.0, 1.0)
        assertRejected { ClosureAdjustment(1L, 0, ok, ok) }
        assertRejected { ClosureAdjustment(1L, 1, ok, persistentListOf(1.0)) }
        assertRejected { ClosureAdjustment(1L, 1, persistentListOf(1.0, 0.0, 1.0), ok) }
        assertRejected { ClosureAdjustment(1L, 1, persistentListOf(1.0, Double.NaN, 1.0), ok) }
        assertRejected { ClosureAdjustment(1L, 1, persistentListOf(1.0, Double.POSITIVE_INFINITY, 1.0), ok) }
        assertRejected { ClosureAdjustment(1L, 1, ok, persistentListOf(1.0, Double.NaN, 1.0)) }
    }

    @Test
    fun `given opening limits when created then only valid values pass`() {
        assertDoesNotThrow { opening(offset = 0L, width = 1L) }
        assertRejected { opening(offset = -1L) }
        assertRejected { opening(width = 0L) }
        assertRejected { opening(width = -1L) }
        listOf(Length.ZERO, Length(-1L)).forEach { height ->
            assertRejected {
                Opening(OpeningId("o"), WallId("w0"), OpeningType.DOOR, Length.ZERO, Length(1L), height, null, null)
            }
        }
        assertRejected { opening(offset = Long.MAX_VALUE, width = 1L) }
        assertEquals(Length(1000L), opening(offset = 100L, width = 900L).endOffset)
    }

    @Test
    fun `given type specific fields when created then they are bound to their type`() {
        assertDoesNotThrow { opening(OpeningType.WINDOW, sill = Length.ZERO) }
        assertRejected { opening(OpeningType.WINDOW, sill = Length(-1L)) }
        assertRejected { opening(OpeningType.DOOR, sill = Length(900L)) }
        assertRejected { opening(OpeningType.PASSAGE, sill = Length(900L)) }
        DoorSwing.entries.forEach { swing -> assertDoesNotThrow { opening(OpeningType.DOOR, swing = swing) } }
        assertRejected { opening(OpeningType.WINDOW, swing = DoorSwing.HINGE_LEFT_INWARD) }
        assertRejected { opening(OpeningType.PASSAGE, swing = DoorSwing.HINGE_RIGHT_OUTWARD) }
    }

    @Test
    fun `given optional fields missing when created then opening is storable`() {
        OpeningType.entries.forEach { type -> assertDoesNotThrow { opening(type) } }
    }

    @Test
    fun `given domain contract when enums are read then value names are stable`() {
        assertEquals(
            listOf("HINGE_LEFT_INWARD", "HINGE_LEFT_OUTWARD", "HINGE_RIGHT_INWARD", "HINGE_RIGHT_OUTWARD"),
            DoorSwing.entries.map { it.name },
        )
        assertEquals(
            listOf("OPEN", "CONFIRMED", "NEEDS_ADJUSTMENT", "ADJUSTED", "PROPOSED", "CONFLICT"),
            ShapeStatus.entries.map { it.name },
        )
        assertEquals(
            listOf("WALL_TOO_LONG", "DIAGONAL_IMPOSSIBLE", "NO_SIMPLE_SHAPE", "MEASUREMENTS_INCONSISTENT"),
            ConflictReason.entries.map { it.name },
        )
        assertEquals(listOf("VALID", "OUT_OF_BOUNDS"), OpeningStatus.entries.map { it.name })
    }

    @Test
    fun `given area factor limits when created then only 0 to 100 pass`() {
        assertEquals(0, AreaFactor(0).percent)
        assertEquals(100, AreaFactor.FULL.percent)
        assertRejected { AreaFactor(-1) }
        assertRejected { AreaFactor(101) }
    }

    @Test
    fun `given non finite placement when created then it is rejected`() {
        listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY).forEach {
            assertRejected { RoomPlacement(it, 0.0, 0.0, null) }
            assertRejected { RoomPlacement(0.0, it, 0.0, null) }
            assertRejected { RoomPlacement(0.0, 0.0, it, null) }
        }
    }

    @Test
    fun `given dock link when created then opening reference defaults to null`() {
        val link = DockLink(RoomId("h"), WallId("a"), WallId("b"), Length(-5L))
        assertNull(link.hostOpeningId)
        assertEquals(OpeningId("o"), link.copy(hostOpeningId = OpeningId("o")).hostOpeningId)
    }

    @Test
    fun `given empty room when created then defaults are valid`() {
        val room = Room(RoomId("r"))
        assertEquals(AreaFactor.FULL, room.areaFactor)
        assertEquals(RoomPlacement.DEFAULT, room.placement)
    }

    @Test
    fun `given corners after closing corner when unspecified then room is rejected`() {
        val bad = persistentListOf(left, CornerSpec.Unspecified, left)
        assertRejected { Room(RoomId("r"), walls = walls(3), corners = bad) }
        assertDoesNotThrow { Room(RoomId("r"), walls = walls(3), corners = persistentListOf(left, left, left)) }
    }

    @Test
    fun `given structural violations when room is created then it is rejected`() {
        val id = RoomId("r")
        assertRejected { Room(id, walls = walls(3), corners = corners(2)) }
        assertRejected { Room(id, walls = persistentListOf(wall("a"), wall("a")), corners = corners(2)) }
        val diagonal = Diagonal(DiagonalId("d"), 0, 1, Length(1L))
        assertRejected {
            Room(id, walls = walls(3), corners = corners(3), diagonals = persistentListOf(diagonal, diagonal))
        }
        assertRejected {
            Room(
                id,
                walls = walls(3),
                corners = corners(3),
                diagonals = persistentListOf(Diagonal(DiagonalId("d"), 0, 3, Length(1L))),
            )
        }
        assertRejected {
            Room(
                id,
                walls = walls(3),
                corners = corners(3),
                diagonals = persistentListOf(Diagonal(DiagonalId("d"), 3, 0, Length(1L))),
            )
        }
        assertRejected {
            Room(id, walls = walls(3), corners = corners(3), openings = persistentListOf(opening(), opening()))
        }
    }

    @Test
    fun `given closed state when room has too few walls then it is rejected`() {
        assertRejected { Room(RoomId("r"), walls = walls(2), corners = corners(2), isClosed = true) }
        assertDoesNotThrow { Room(RoomId("r"), walls = walls(3), corners = corners(3), isClosed = true) }
    }

    @Test
    fun `given adjustment when room is open or sizes differ then it is rejected`() {
        val id = RoomId("r")
        assertRejected { Room(id, walls = walls(3), corners = corners(3), adjustment = adjustment(3)) }
        assertRejected {
            Room(id, walls = walls(3), corners = corners(3), isClosed = true, adjustment = adjustment(4))
        }
        assertDoesNotThrow {
            Room(id, walls = walls(3), corners = corners(3), isClosed = true, adjustment = adjustment(3))
        }
    }

    @Test
    fun `given room docked to itself when created then it is rejected`() {
        val link = DockLink(RoomId("r"), WallId("a"), WallId("b"), Length.ZERO)
        assertRejected { Room(RoomId("r"), placement = RoomPlacement(0.0, 0.0, 0.0, link)) }
        assertDoesNotThrow {
            Room(RoomId("other"), placement = RoomPlacement(0.0, 0.0, 0.0, link))
        }
    }

    @Test
    fun `given room height when effective height is read then override wins`() {
        val fallback = Length(2500L)
        assertEquals(fallback, Room(RoomId("r")).effectiveHeight(fallback))
        assertEquals(Length(3000L), Room(RoomId("r"), heightOverride = Length(3000L)).effectiveHeight(fallback))
        assertRejected { Room(RoomId("r"), heightOverride = Length.ZERO) }
        assertRejected { Room(RoomId("r"), heightOverride = Length(-1L)) }
    }

    @Test
    fun `given project values when created then invariants hold`() {
        val id = ProjectId("p")
        val room = Room(RoomId("r"))
        assertDoesNotThrow { Project(id, "n", Length(2500L), persistentListOf(room), 0L, 0L) }
        assertRejected { Project(id, "n", Length.ZERO, persistentListOf(), 0L, 0L) }
        assertRejected { Project(id, "n", Length(-1L), persistentListOf(), 0L, 0L) }
        assertRejected { Project(id, "n", Length(2500L), persistentListOf(room, room), 0L, 0L) }
    }
}
