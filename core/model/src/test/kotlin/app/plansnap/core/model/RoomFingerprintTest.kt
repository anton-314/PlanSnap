package app.plansnap.core.model

import app.plansnap.core.common.units.Angle
import app.plansnap.core.common.units.Length
import app.plansnap.core.common.units.TurnDirection
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Reference hashes were computed by an independent Python implementation of DOMAIN section 6. */
class RoomFingerprintTest {
    private val left = CornerSpec.DeclaredRight(TurnDirection.LEFT)
    private val right = CornerSpec.DeclaredRight(TurnDirection.RIGHT)

    private fun walls(
        vararg mm: Long,
        thickness: Long = 120L,
        prefix: String = "w",
    ) = mm.mapIndexed { i, v -> Wall(WallId("$prefix$i"), Length(v), Length(thickness)) }.toImmutableList()

    private fun diagonal(
        id: String,
        from: Int,
        to: Int,
        mm: Long,
    ) = Diagonal(DiagonalId(id), from, to, Length(mm))

    private val mixedCorners =
        persistentListOf(
            CornerSpec.Measured(TurnDirection.LEFT, Angle(70_529)),
            right,
            CornerSpec.Measured(TurnDirection.RIGHT, Angle(109_471)),
            left,
        )

    private fun mixedRoom(vararg diagonals: Diagonal) =
        Room(
            id = RoomId("r"),
            walls = walls(3000L, 3000L, 3000L, 5000L),
            corners = mixedCorners,
            diagonals = diagonals.toList().toImmutableList(),
        )

    private val square =
        Room(
            id = RoomId("r"),
            walls = walls(3000L, 3000L, 3000L, 3000L),
            corners = persistentListOf(CornerSpec.Unspecified, left, left, left),
        )

    @Test
    fun `given empty room when hashed then reference value matches`() {
        assertEquals(-6284781860667377211L, Room(RoomId("r")).inputFingerprint())
    }

    @Test
    fun `given single wall when hashed then reference value matches`() {
        val room =
            Room(
                RoomId("r"),
                walls = walls(10L),
                corners = persistentListOf(CornerSpec.Unspecified),
            )
        assertEquals(-724480570260252652L, room.inputFingerprint())
    }

    @Test
    fun `given square when hashed then reference value matches`() {
        assertEquals(3746567627424826631L, square.inputFingerprint())
    }

    @Test
    fun `given measured corners and diagonals when hashed then reference value matches`() {
        val room = mixedRoom(diagonal("a", 2, 0, 4000L), diagonal("b", 1, 3, 4100L))
        assertEquals(4220351464599974562L, room.inputFingerprint())
    }

    @Test
    fun `given reordered diagonals when hashed then fingerprint is unchanged`() {
        val first = diagonal("a", 0, 2, 4000L)
        val second = diagonal("b", 1, 3, 4100L)
        assertEquals(mixedRoom(first, second).inputFingerprint(), mixedRoom(second, first).inputFingerprint())
    }

    @Test
    fun `given same corner pair with different lengths when reordered then fingerprint is unchanged`() {
        val shorter = diagonal("a", 0, 2, 4000L)
        val longer = diagonal("b", 0, 2, 4100L)
        assertEquals(mixedRoom(shorter, longer).inputFingerprint(), mixedRoom(longer, shorter).inputFingerprint())
    }

    @Test
    fun `given swapped diagonal endpoints when hashed then fingerprint is unchanged`() {
        assertEquals(
            mixedRoom(diagonal("a", 0, 2, 4000L)).inputFingerprint(),
            mixedRoom(diagonal("a", 2, 0, 4000L)).inputFingerprint(),
        )
    }

    @Test
    fun `given different ids when hashed then fingerprint is unchanged`() {
        val renamed = square.copy(id = RoomId("other"), walls = walls(3000L, 3000L, 3000L, 3000L, prefix = "x"))
        assertEquals(square.inputFingerprint(), renamed.inputFingerprint())
        assertEquals(
            mixedRoom(diagonal("a", 0, 2, 4000L)).inputFingerprint(),
            mixedRoom(diagonal("z", 0, 2, 4000L)).inputFingerprint(),
        )
    }

    @Test
    fun `given different thicknesses when hashed then fingerprint is unchanged`() {
        val thick = square.copy(walls = walls(3000L, 3000L, 3000L, 3000L, thickness = 0L))
        assertEquals(square.inputFingerprint(), thick.inputFingerprint())
    }

    @Test
    fun `given non shape data when changed then fingerprint is unchanged`() {
        val changed =
            square.copy(
                name = "Kitchen",
                notes = "note",
                heightOverride = Length(2600L),
                areaFactor = AreaFactor(50),
                isClosed = true,
                placement = RoomPlacement(1.0, 2.0, 3.0, null),
            )
        assertEquals(square.inputFingerprint(), changed.inputFingerprint())
    }

    @Test
    fun `given changed length when hashed then fingerprint differs`() {
        val changed = square.copy(walls = walls(3001L, 3000L, 3000L, 3000L))
        assertNotEquals(square.inputFingerprint(), changed.inputFingerprint())
    }

    @Test
    fun `given reordered walls when hashed then fingerprint differs`() {
        val a = mixedRoom().copy(walls = walls(3000L, 3000L, 3000L, 5000L))
        val b = mixedRoom().copy(walls = walls(5000L, 3000L, 3000L, 3000L))
        assertNotEquals(a.inputFingerprint(), b.inputFingerprint())
    }

    @Test
    fun `given changed corner when hashed then fingerprint differs`() {
        val direction = square.copy(corners = persistentListOf(CornerSpec.Unspecified, right, left, left))
        val tag = square.copy(corners = persistentListOf(left, left, left, left))
        val measuredRight =
            square.copy(
                corners =
                    persistentListOf(
                        CornerSpec.Unspecified,
                        CornerSpec.Measured(TurnDirection.LEFT, Angle.RIGHT),
                        left,
                        left,
                    ),
            )
        listOf(direction, tag, measuredRight).forEach {
            assertNotEquals(square.inputFingerprint(), it.inputFingerprint())
        }
    }

    @Test
    fun `given changed measured angle when hashed then fingerprint differs`() {
        val changed =
            mixedRoom().copy(
                corners =
                    persistentListOf(
                        CornerSpec.Measured(TurnDirection.LEFT, Angle(70_530)),
                        right,
                        CornerSpec.Measured(TurnDirection.RIGHT, Angle(109_471)),
                        left,
                    ),
            )
        assertNotEquals(mixedRoom().inputFingerprint(), changed.inputFingerprint())
    }

    @Test
    fun `given changed diagonal when hashed then fingerprint differs`() {
        val base = mixedRoom(diagonal("a", 0, 2, 4000L)).inputFingerprint()
        assertNotEquals(base, mixedRoom().inputFingerprint())
        assertNotEquals(base, mixedRoom(diagonal("a", 0, 2, 4001L)).inputFingerprint())
        assertNotEquals(base, mixedRoom(diagonal("a", 1, 3, 4000L)).inputFingerprint())
    }

    @Test
    fun `given matching fingerprint when solver version differs then overlay stays current`() {
        val fingerprint = square.inputFingerprint()
        val values = List(4) { 1.0 }.toImmutableList()
        assertTrue(square.isAdjustmentCurrent(ClosureAdjustment(fingerprint, 1, values, values)))
        assertTrue(square.isAdjustmentCurrent(ClosureAdjustment(fingerprint, 99, values, values)))
    }

    @Test
    fun `given stale fingerprint when checked then overlay is not current`() {
        val values = List(4) { 1.0 }.toImmutableList()
        val stale = ClosureAdjustment(square.inputFingerprint() + 1L, 1, values, values)
        assertFalse(square.isAdjustmentCurrent(stale))
    }
}
