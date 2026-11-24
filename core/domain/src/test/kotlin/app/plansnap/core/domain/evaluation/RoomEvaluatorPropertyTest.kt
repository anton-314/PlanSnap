package app.plansnap.core.domain.evaluation

import app.plansnap.core.common.units.Length
import app.plansnap.core.common.units.TurnDirection
import app.plansnap.core.geometry.primitives.GeometryTolerances
import app.plansnap.core.model.CornerSpec
import app.plansnap.core.model.Room
import app.plansnap.core.model.RoomPlacement
import app.plansnap.core.model.ShapeStatus
import app.plansnap.testing.fixtures.generators.RoomGenerator
import io.kotest.common.ExperimentalKotest
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.checkAll
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Properties of the evaluation: determinism and invariance of the status (DOMAIN sections 4 and 11). */
class RoomEvaluatorPropertyTest {
    private val first = DefaultRoomEvaluator(DirectDispatchers)
    private val second = DefaultRoomEvaluator(DirectDispatchers)

    @Test
    fun `given any generated room when evaluated twice then the evaluations are bit identical`() =
        runBlocking<Unit> {
            val statuses = mutableListOf<ShapeStatus>()
            checkAll(ITERATIONS, config(SEED_DETERMINISM), perturbedRooms(DEVIATION_FACTOR)) { room ->
                val a = first.evaluate(room, DEFAULT_TOLERANCE).success()
                val b = second.evaluate(room, DEFAULT_TOLERANCE).success()
                // Data class equality compares every Double by its bits.
                assertEquals(a, b, "$room")
                statuses += a.status
            }
            assertEquals(ITERATIONS, statuses.size)
            // The perturbation reaches the exact, measurement error and shape deviation paths.
            assertTrue(
                statuses.containsAll(listOf(ShapeStatus.CONFIRMED, ShapeStatus.NEEDS_ADJUSTMENT)),
                "${statuses.toSet()}",
            )
            assertTrue(statuses.any { it == ShapeStatus.PROPOSED || it == ShapeStatus.CONFLICT }, "${statuses.toSet()}")
        }

    @Test
    fun `given any generated room when it is moved or turned in the plan then the evaluation is unchanged`() =
        runBlocking<Unit> {
            var checked = 0
            checkAll(ITERATIONS, config(SEED_PLACEMENT), placedRooms()) { (room, placed) ->
                assertEquals(first.evaluate(room, DEFAULT_TOLERANCE), first.evaluate(placed, DEFAULT_TOLERANCE))
                checked++
            }
            assertEquals(ITERATIONS, checked)
        }

    @Test
    fun `given a room with every corner entered when the start corner moves then the status is unchanged`() =
        runBlocking<Unit> {
            val statuses = mutableListOf<ShapeStatus>()
            checkAll(ITERATIONS, config(SEED_START), perturbedRooms(DEVIATION_FACTOR, closedFixed = true)) { room ->
                val expected = first.evaluate(room, DEFAULT_TOLERANCE).success().status
                for (shift in 1 until room.walls.size) {
                    val shifted = room.startingAt(shift)
                    assertEquals(
                        expected,
                        first.evaluate(shifted, DEFAULT_TOLERANCE).success().status,
                        "$shift of $room",
                    )
                }
                statuses += expected
            }
            assertEquals(ITERATIONS, statuses.size)
            assertTrue(
                statuses.containsAll(listOf(ShapeStatus.CONFIRMED, ShapeStatus.NEEDS_ADJUSTMENT)),
                "${statuses.toSet()}",
            )
            assertTrue(ShapeStatus.PROPOSED in statuses, "${statuses.toSet()}")
        }

    /**
     * Generated rooms with one wall changed by up to `factor · T`. With [closedFixed] the closing corner is entered, so
     * that every corner can become the start.
     */
    private fun perturbedRooms(
        factor: Double,
        closedFixed: Boolean = false,
    ): Arb<Room> =
        arbitrary { rs ->
            val generator = RoomGenerator(rs.random.nextLong())
            val base = if (closedFixed) generator.histogram(rs.random.nextInt(1, MAX_COLUMNS + 1)) else generator.any()
            val room = if (closedFixed) base.room.withClosingCornerEntered() else base.room
            val limit = DEFAULT_TOLERANCE.limitMm(room.walls.sumOf { it.length.mm }.toDouble())
            val maxDelta = (factor * limit).toLong()
            val delta = rs.random.nextLong(-maxDelta, maxDelta + 1)
            val wall = rs.random.nextInt(room.walls.size)
            val length = (room.walls[wall].length.mm + delta).coerceAtLeast(GeometryTolerances.MIN_WALL_MM)
            val walls = room.walls.mapIndexed { i, w -> if (i == wall) w.copy(length = Length(length)) else w }
            room.copy(walls = walls.toImmutableList())
        }

    private fun placedRooms(): Arb<Pair<Room, Room>> =
        arbitrary { rs ->
            val room = RoomGenerator(rs.random.nextLong()).any().room
            val placement =
                RoomPlacement(
                    originX = rs.random.nextDouble(-PLAN_EXTENT_MM, PLAN_EXTENT_MM),
                    originY = rs.random.nextDouble(-PLAN_EXTENT_MM, PLAN_EXTENT_MM),
                    rotationRad = rs.random.nextDouble(-StrictMath.PI, StrictMath.PI),
                    dock = null,
                )
            room to room.copy(placement = placement)
        }

    /** The generated rooms are counter-clockwise, so the free corner `V_0` is a declared left turn. */
    private fun Room.withClosingCornerEntered(): Room =
        copy(corners = (listOf(CornerSpec.DeclaredRight(TurnDirection.LEFT)) + corners.drop(1)).toImmutableList())

    private fun Room.startingAt(shift: Int): Room =
        copy(
            walls = (walls.drop(shift) + walls.take(shift)).toImmutableList(),
            corners = (corners.drop(shift) + corners.take(shift)).toImmutableList(),
        )

    private companion object {
        const val ITERATIONS = 1_000
        const val SEED_DETERMINISM = 7_101L
        const val SEED_PLACEMENT = 7_102L
        const val SEED_START = 7_103L
        const val MAX_COLUMNS = 3
        const val DEVIATION_FACTOR = 3.0
        const val PLAN_EXTENT_MM = 1e6

        /** Fixed seed, so every run checks the same rooms (TESTING section 2). */
        @OptIn(ExperimentalKotest::class)
        fun config(seed: Long): PropTestConfig = PropTestConfig(seed = seed)
    }
}
