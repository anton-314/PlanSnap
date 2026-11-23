package app.plansnap.testing.fixtures.rooms

import app.plansnap.core.common.units.TurnDirection
import app.plansnap.core.domain.evaluation.AdjustmentConfirmation
import app.plansnap.core.geometry.primitives.ClosureTolerance
import app.plansnap.core.geometry.statistics.ChiSquareLevel
import app.plansnap.core.geometry.statistics.ChiSquareLimits
import app.plansnap.core.model.ConflictReason
import app.plansnap.core.model.CornerSpec
import app.plansnap.core.model.Room
import app.plansnap.core.model.ShapeStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OverdeterminedCasesTest {
    private val tolerance = ClosureTolerance()

    @Test
    fun `given catalog then every finding has its named case with a unique key`() {
        val keys = OverdeterminedCases.ALL.map { it.key }

        assertEquals(keys.toSet().size, keys.size)
        assertEquals(16, keys.size)
        listOf("NH1", "NH2", "NH3", "NM6", "Decision 3", "Decision 4", "Decision 5").forEach { finding ->
            assertTrue(OverdeterminedCases.ALL.any { finding in it.finding }, finding)
        }
    }

    @Test
    fun `given every case then the room is closed and has one corner per wall`() {
        OverdeterminedCases.ALL.forEach {
            assertTrue(it.room.isClosed, it.key)
            assertEquals(it.room.walls.size, it.room.corners.size, it.key)
        }
    }

    @Test
    fun `given pentagon case then raw values match the review`() {
        val case = OverdeterminedCases.PENTAGON_REPEATED_DIAGONAL

        assertEquals(listOf(4000L, 3000L, 2500L, 2500L, 3000L), lengths(case.room))
        assertEquals(listOf(5000L, 5000L), case.room.diagonals.map { it.length.mm })
        assertEquals(ShapeStatus.PROPOSED, case.expectedStatus)
        assertEquals(1, case.expectedRank?.missing)
    }

    @Test
    fun `given hexagon case then raw values match the review`() {
        val room = OverdeterminedCases.HEXAGON_ALL_DIAGONALS.room

        assertEquals(listOf(4404L, 2614L, 2150L, 4671L, 3690L, 5090L), lengths(room))
        assertEquals(
            listOf(6105L, 7543L, 8181L, 4718L, 7842L, 6603L, 6090L, 6137L, 5981L),
            room.diagonals.map { it.length.mm },
        )
        assertTrue(room.corners.drop(1).all { it == CornerSpec.DeclaredRight(TurnDirection.LEFT) })
    }

    @Test
    fun `given wrong wall cases then corners close exactly and only the first wall carries the error`() {
        val errors =
            mapOf(
                OverdeterminedCases.RECTANGLE_WRONG_WALL to 380L,
                OverdeterminedCases.L_SHAPE_WRONG_WALL to 380L,
                OverdeterminedCases.U_SHAPE_WRONG_WALL to 630L,
                OverdeterminedCases.CROSS_WRONG_WALL to 910L,
            )
        errors.forEach { (case, error) ->
            val (x, y) = rectilinearGap(case.room)
            assertEquals(error, x, case.key)
            assertEquals(0L, y, case.key)
            assertEquals(4, netLeftTurns(case.room), case.key)
            assertEquals(ConflictReason.MEASUREMENTS_INCONSISTENT, case.expectedConflict, case.key)
        }
    }

    @Test
    fun `given wrong wall cases then expected statistics exceed both limits`() {
        listOf(
            OverdeterminedCases.RECTANGLE_WRONG_WALL,
            OverdeterminedCases.L_SHAPE_WRONG_WALL,
            OverdeterminedCases.U_SHAPE_WRONG_WALL,
            OverdeterminedCases.CROSS_WRONG_WALL,
            OverdeterminedCases.RECTANGLE_LARGE_CONFLICT,
        ).forEach { case ->
            val correction = checkNotNull(case.expectedCorrection)
            assertTrue(correction.sumMm > limit(case.room), case.key)
            assertTrue(correction.normalizedSquareSum > ChiSquareLimits.quantile(ChiSquareLevel.CONFLICT, 3), case.key)
        }
    }

    @Test
    fun `given band cases then expected statistics fall on the documented side of each limit`() {
        val lower = ChiSquareLimits.quantile(ChiSquareLevel.EXPLICIT_CONFIRMATION, 3)
        val upper = ChiSquareLimits.quantile(ChiSquareLevel.CONFLICT, 3)
        val simple = OverdeterminedCases.RECTANGLE_LARGE_SIMPLE
        val explicit = OverdeterminedCases.RECTANGLE_LARGE_EXPLICIT

        assertTrue(checkNotNull(simple.expectedCorrection).sumMm > limit(simple.room))
        assertTrue(checkNotNull(simple.expectedCorrection).normalizedSquareSum <= lower)
        assertEquals(AdjustmentConfirmation.SIMPLE, simple.expectedConfirmation)
        assertTrue(checkNotNull(explicit.expectedCorrection).sumMm > limit(explicit.room))
        assertTrue(checkNotNull(explicit.expectedCorrection).normalizedSquareSum > lower)
        assertTrue(checkNotNull(explicit.expectedCorrection).normalizedSquareSum <= upper)
        assertEquals(AdjustmentConfirmation.EXPLICIT, explicit.expectedConfirmation)
    }

    @Test
    fun `given gap case then the gap is below the limit`() {
        val case = OverdeterminedCases.GAP_30_MM
        val (x, y) = rectilinearGap(case.room)

        assertEquals(30L, x)
        assertEquals(0L, y)
        assertTrue(x < limit(case.room))
        assertEquals(AdjustmentConfirmation.SIMPLE, case.expectedConfirmation)
    }

    @Test
    fun `given repeated wall cases then the diagonal connects adjacent corners`() {
        listOf(
            OverdeterminedCases.QUAD_REPEATED_WALL,
            OverdeterminedCases.QUAD_REPEATED_WALL_1_MM,
            OverdeterminedCases.QUAD_REPEATED_WALL_CONTRADICTION,
        ).forEach { case ->
            val diagonal = case.room.diagonals.single()
            assertEquals(0, diagonal.fromCorner, case.key)
            assertEquals(3, diagonal.toCorner, case.key)
            assertEquals(1, case.expectedRank?.missing, case.key)
        }
    }

    @Test
    fun `given direction case then the declared turns cannot belong to a triangle`() {
        val room = OverdeterminedCases.TRIANGLE_WRONG_DIRECTION.room

        assertEquals(CornerSpec.DeclaredRight(TurnDirection.LEFT), room.corners[1])
        assertEquals(CornerSpec.DeclaredRight(TurnDirection.RIGHT), room.corners[2])
        assertEquals(ShapeStatus.CONFLICT, OverdeterminedCases.TRIANGLE_WRONG_DIRECTION.expectedStatus)
    }

    @Test
    fun `given uncontrolled case then the status stays confirmed and measurements are listed`() {
        val case = OverdeterminedCases.HEXAGON_UNCONTROLLED

        assertEquals(ShapeStatus.CONFIRMED, case.expectedStatus)
        assertEquals(4, case.expectedUncontrolled.size)
        assertFalse(case.expectedUncontrolled.values.any { it == null })
    }

    @Test
    fun `given inconsistent expectations when a case is created then it is rejected`() {
        val room = OverdeterminedCases.GAP_30_MM.room
        assertThrows(IllegalArgumentException::class.java) {
            OverdeterminedCase("bad", "x", room, ShapeStatus.CONFLICT)
        }
        assertThrows(IllegalArgumentException::class.java) {
            OverdeterminedCase("bad", "x", room, ShapeStatus.NEEDS_ADJUSTMENT)
        }
        assertThrows(IllegalArgumentException::class.java) {
            OverdeterminedCase(
                "bad",
                "x",
                room,
                ShapeStatus.CONFIRMED,
                expectedConfirmation = AdjustmentConfirmation.SIMPLE,
            )
        }
    }

    private fun lengths(room: Room): List<Long> = room.walls.map { it.length.mm }

    private fun limit(room: Room): Double = tolerance.limitMm(lengths(room).sum().toDouble())

    /** Walks a room whose corners are all 90 degrees in integer steps, independent of production geometry. */
    private fun rectilinearGap(room: Room): Pair<Long, Long> {
        var dx = 1L
        var dy = 0L
        var x = 0L
        var y = 0L
        room.walls.forEachIndexed { i, wall ->
            if (i > 0) {
                val left = room.corners[i].direction == TurnDirection.LEFT
                val turnedX = if (left) -dy else dy
                val turnedY = if (left) dx else -dx
                dx = turnedX
                dy = turnedY
            }
            x += dx * wall.length.mm
            y += dy * wall.length.mm
        }
        return x to y
    }

    private fun netLeftTurns(room: Room): Int =
        room.corners.count { it.direction == TurnDirection.LEFT } -
            room.corners.count { it.direction == TurnDirection.RIGHT }
}
