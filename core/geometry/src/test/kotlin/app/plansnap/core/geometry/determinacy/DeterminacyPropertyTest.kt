package app.plansnap.core.geometry.determinacy

import app.plansnap.core.common.units.Angle
import app.plansnap.core.common.units.TurnDirection
import app.plansnap.core.geometry.cancellation.CancellationSignal
import app.plansnap.core.geometry.primitives.Vec2
import app.plansnap.core.geometry.solver.SolverProblem
import app.plansnap.core.geometry.traverse.CornerInput
import app.plansnap.core.geometry.traverse.DefaultTraverse
import app.plansnap.core.geometry.traverse.DiagonalInput
import app.plansnap.core.geometry.traverse.ShapeInput
import io.kotest.common.ExperimentalKotest
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DeterminacyPropertyTest {
    private val analyzer = DefaultDeterminacyAnalyzer()
    private val traverse = DefaultTraverse()
    private val none = CancellationSignal.NONE

    @Test
    fun `given closed shape when the start corner moves then missing is unchanged`() =
        runBlocking<Unit> {
            checkAll(ITERATIONS, config(SEED_SHIFT), rooms) { generated ->
                val room = generated.declaredClosing()
                val base = room.analyze()
                for (shift in 1 until room.n) {
                    val shifted = room.shifted(shift).analyze()
                    assertEquals(base.missing, shifted.missing, "shift $shift of $room")
                    // A measured V_0 is a hard condition, not an eliminated variable: v and ρ both grow.
                    val closingMeasured = if (shift in room.measuredCorners) 1 else 0
                    assertEquals(base.rank + closingMeasured, shifted.rank, "shift $shift of $room")
                }
            }
        }

    @Test
    fun `given closed shape when it is mirrored then rank and missing are unchanged`() =
        runBlocking<Unit> {
            checkAll(ITERATIONS, config(SEED_MIRROR), rooms) { room ->
                val base = room.analyze()
                val mirrored = room.mirrored().analyze()
                assertEquals(base.rank, mirrored.rank, "$room")
                assertEquals(base.missing, mirrored.missing, "$room")
            }
        }

    @Test
    fun `given underdetermined shape when the recommended measurement is added then one measurement less is missing`() =
        runBlocking<Unit> {
            var checked = 0
            checkAll(ITERATIONS, config(SEED_RECOMMENDATION), rooms) { room ->
                val determinacy = room.analyze()
                val recommendation = determinacy.recommendation ?: return@checkAll
                val extended = room.with(recommendation).analyze()
                assertEquals(determinacy.missing - 1, extended.missing, "$recommendation for $room")
                checked++
            }
            assertTrue(checked > ITERATIONS / 2, "only $checked rooms had a recommendation")
        }

    @Test
    fun `given determined shape then there is no recommendation and missing is v minus rank`() =
        runBlocking<Unit> {
            checkAll(ITERATIONS, config(SEED_INVARIANTS), rooms) { room ->
                val determinacy = room.analyze()
                assertEquals(determinacy.freeVariables - determinacy.rank, determinacy.missing)
                assertEquals(determinacy.missing > 0, determinacy.recommendation != null, "$room")
            }
        }

    @Test
    fun `given routing analysis then the selection follows the fixed row order and missing is v minus rho`() =
        runBlocking<Unit> {
            checkAll(ITERATIONS, config(SEED_ROUTING), rooms) { room ->
                val input = room.input()
                val analysis = analyzer.analyzeConstraints(input, targets(input), none)
                val rows = SolverProblem(input).rows
                assertEquals(rows.filter { it in analysis.independentSelection }, analysis.independentSelection)
                assertEquals(analysis.freeVariables - analysis.independentConditions, analysis.missing)
                assertEquals(analysis.conditionCount > analysis.independentConditions, analysis.isOverdetermined)
            }
        }

    @Test
    fun `given equal arguments then every analysis is bit identical`() =
        runBlocking<Unit> {
            checkAll(ITERATIONS, config(SEED_DETERMINISM), rooms) { room ->
                val input = room.input()
                val other = DefaultDeterminacyAnalyzer()
                assertEquals(
                    analyzer.analyzeConstraints(input, targets(input), none),
                    other.analyzeConstraints(input, targets(input), none),
                )
                assertEquals(room.analyze(), room.analyze(other))
            }
        }

    private fun targets(input: ShapeInput): List<Double> = traverse.targets(input).targetTurns.map { it.toRadians() }

    /**
     * A closed true shape with corner kinds and measured diagonals. [measuredCorners] are measured with their true
     * angle, the other corners after `V_0` are declared right angles in their true direction.
     */
    private inner class Room(
        val points: List<Vec2>,
        val freeClosing: Boolean,
        val measuredCorners: Set<Int>,
        val diagonals: List<Pair<Int, Int>>,
    ) {
        val n: Int get() = points.size
        val lengths: List<Double> get() = List(n) { (points[(it + 1) % n] - points[it]).length() }
        val turns: List<Double>
            get() =
                List(n) { i ->
                    val a = points[i] - points[(i + n - 1) % n]
                    val b = points[(i + 1) % n] - points[i]
                    StrictMath.atan2(a.cross(b), a.dot(b))
                }

        fun input(): ShapeInput {
            val turns = turns
            val corners =
                List(n) { i ->
                    when {
                        i == 0 && freeClosing -> {
                            CornerInput.Free
                        }

                        i in measuredCorners -> {
                            measuredCorner(turns[i])
                        }

                        else -> {
                            CornerInput.DeclaredRight(
                                if (turns[i] >=
                                    0.0
                                ) {
                                    TurnDirection.LEFT
                                } else {
                                    TurnDirection.RIGHT
                                },
                            )
                        }
                    }
                }
            val walls = lengths.map { maxOf(MIN_WALL, StrictMath.rint(it).toLong()) }
            val measured =
                diagonals.map { (a, b) ->
                    DiagonalInput(a, b, StrictMath.rint((points[b] - points[a]).length()).toLong())
                }
            return ShapeInput(walls, corners, measured)
        }

        fun analyze(analyzer: DefaultDeterminacyAnalyzer = this@DeterminacyPropertyTest.analyzer): Determinacy =
            analyzer.analyze(input(), lengths, turns, none)

        /** The same room with a declared closing corner, so that every corner may become `V_0`. */
        fun declaredClosing(): Room = Room(points, false, measuredCorners, diagonals)

        /** The same room started at corner [k]. Only valid with a declared closing corner. */
        fun shifted(k: Int): Room {
            fun moved(i: Int) = (i - k + n) % n
            return Room(
                points.drop(k) + points.take(k),
                freeClosing = false,
                measuredCorners = measuredCorners.map(::moved).toSet(),
                diagonals = diagonals.map { (a, b) -> minOf(moved(a), moved(b)) to maxOf(moved(a), moved(b)) },
            )
        }

        fun mirrored(): Room = Room(points.map { Vec2(it.x, -it.y) }, freeClosing, measuredCorners, diagonals)

        fun with(recommendation: MeasurementRecommendation): Room =
            when (recommendation) {
                is MeasurementRecommendation.DiagonalBetween -> {
                    Room(
                        points,
                        freeClosing,
                        measuredCorners,
                        diagonals + (recommendation.fromCorner to recommendation.toCorner),
                    )
                }

                is MeasurementRecommendation.CornerAngle -> {
                    Room(
                        points,
                        freeClosing && recommendation.corner != 0,
                        measuredCorners + recommendation.corner,
                        diagonals,
                    )
                }
            }

        override fun toString(): String =
            "Room(points=$points, free=$freeClosing, measured=$measuredCorners, diagonals=$diagonals)"
    }

    private fun measuredCorner(turn: Double): CornerInput.Measured {
        val alpha = StrictMath.rint(180_000.0 - StrictMath.abs(StrictMath.toDegrees(turn)) * 1000.0).toInt()
        val direction = if (turn >= 0.0) TurnDirection.LEFT else TurnDirection.RIGHT
        return CornerInput.Measured(direction, Angle(alpha.coerceIn(1, 180_000)))
    }

    /** Star-shaped polygons with 4 to 9 corners, some measured corners and up to two diagonals. */
    private val rooms: Arb<Room> =
        arbitrary { rs ->
            val random = rs.random
            val n = random.nextInt(MIN_CORNERS, MAX_CORNERS + 1)
            val sector = 2.0 * StrictMath.PI / n
            val points =
                List(n) { i ->
                    val angle = i * sector + JITTER * sector * (2.0 * random.nextDouble() - 1.0)
                    Vec2(random.nextDouble(MIN_RADIUS, MAX_RADIUS), 0.0).rotated(angle)
                }
            val measured = (1 until n).filter { random.nextDouble() < MEASURED_SHARE }.toSet()
            val pairs = nonAdjacentPairs(n).shuffled(random)
            Room(points, random.nextBoolean(), measured, pairs.take(random.nextInt(0, MAX_DIAGONALS + 1)))
        }

    private companion object {
        const val ITERATIONS = 200
        const val SEED_SHIFT = 5_001L
        const val SEED_MIRROR = 5_002L
        const val SEED_RECOMMENDATION = 5_003L
        const val SEED_INVARIANTS = 5_004L
        const val SEED_ROUTING = 5_005L
        const val SEED_DETERMINISM = 5_006L
        const val MIN_CORNERS = 4
        const val MAX_CORNERS = 9
        const val MAX_DIAGONALS = 2
        const val MEASURED_SHARE = 0.25
        const val JITTER = 0.3
        const val MIN_RADIUS = 1_500.0
        const val MAX_RADIUS = 6_000.0
        const val MIN_WALL = 10L

        /** Fixed seed so every run checks the same cases (TESTING section 2). */
        @OptIn(ExperimentalKotest::class)
        fun config(seed: Long) = PropTestConfig(seed = seed)
    }
}
