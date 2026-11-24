package app.plansnap.testing.fixtures.rooms

import app.plansnap.core.domain.evaluation.ProposalReason
import app.plansnap.core.geometry.adjustment.ObservationRef
import app.plansnap.core.geometry.determinacy.MeasurementRecommendation
import app.plansnap.core.geometry.primitives.Vec2
import app.plansnap.core.model.CornerSpec
import app.plansnap.core.model.Room
import app.plansnap.core.model.ShapeStatus
import app.plansnap.testing.fixtures.IndependentGeometry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** Checks the ambiguous fixtures with independent formulas, never with production geometry. */
class AmbiguousCasesTest {
    private val cases = OverdeterminedCases.ALL.filter { it.expectedAmbiguity != null }

    @Test
    fun `given catalog then it holds the three ambiguous cases`() {
        assertEquals(
            listOf(
                OverdeterminedCases.AMBIGUOUS_SELECTED_OTHER_SHAPE,
                OverdeterminedCases.AMBIGUOUS_SELECTED_TRUE_SHAPE,
                OverdeterminedCases.AMBIGUOUS_NEEDS_ADJUSTMENT,
            ),
            cases,
        )
    }

    @Test
    fun `given ambiguous cases then both shapes satisfy every input`() {
        cases.forEach { case ->
            val ambiguity = checkNotNull(case.expectedAmbiguity)
            assertSatisfiesInputs(
                "${case.key} displayed",
                case.room,
                displayedLengths(case),
                ambiguity.selectedTurnsDeg,
            )
            assertSatisfiesInputs(
                "${case.key} alternative",
                case.room,
                rawLengths(case.room),
                ambiguity.alternativeTurnsDeg,
            )
        }
    }

    @Test
    fun `given ambiguous cases then the shape distance exceeds the limit`() {
        cases.forEach { case ->
            val ambiguity = checkNotNull(case.expectedAmbiguity)
            val distance =
                alignedDistance(
                    walk(displayedLengths(case), ambiguity.selectedTurnsDeg),
                    walk(rawLengths(case.room), ambiguity.alternativeTurnsDeg),
                )

            assertEquals(ambiguity.distanceMm, distance, ExpectedAmbiguity.DISTANCE_TOLERANCE_MM, case.key)
            assertTrue(distance > limitMm(case.room), case.key)
        }
    }

    @Test
    fun `given ambiguous cases then the rank is determined and the reason is ambiguous`() {
        cases.forEach { case ->
            assertEquals(ShapeStatus.PROPOSED, case.expectedStatus, case.key)
            assertEquals(ProposalReason.AMBIGUOUS_SHAPE, case.expectedProposalReason, case.key)
            assertEquals(0, case.expectedRank?.missing, case.key)
        }
    }

    @Test
    fun `given free closing corner that differs then it is the recommendation`() {
        val free = cases.filter { it.room.corners[0] == CornerSpec.Unspecified }

        assertEquals(2, free.size)
        free.forEach { case ->
            val ambiguity = checkNotNull(case.expectedAmbiguity)
            val difference = abs(wrapDeg(ambiguity.selectedTurnsDeg[0] - ambiguity.alternativeTurnsDeg[0]))

            assertTrue(Math.toRadians(difference) * perimeterMm(case.room) / FULL_TURN_RAD > limitMm(case.room))
            assertEquals(MeasurementRecommendation.CornerAngle(0), ambiguity.recommendation, case.key)
            assertFalse(ambiguity.selectedIsTrueShape, case.key)
        }
    }

    @Test
    fun `given measured closing corner then the diagonal with the largest difference is recommended`() {
        val case = OverdeterminedCases.AMBIGUOUS_SELECTED_TRUE_SHAPE
        val ambiguity = checkNotNull(case.expectedAmbiguity)
        val selected = walk(displayedLengths(case), ambiguity.selectedTurnsDeg)
        val alternative = walk(rawLengths(case.room), ambiguity.alternativeTurnsDeg)
        val measured =
            case.room.diagonals.map {
                minOf(
                    it.fromCorner,
                    it.toCorner,
                ) to maxOf(it.fromCorner, it.toCorner)
            }
        val n = selected.size
        val candidates =
            (0 until n)
                .flatMap { a -> (a + 2 until n).map { b -> a to b } }
                .filter { (a, b) -> !isAdjacent(a, b, n) && (a to b) !in measured }
        val best =
            candidates.maxBy { (a, b) ->
                abs(distance(selected[a], selected[b]) - distance(alternative[a], alternative[b]))
            }

        assertTrue(case.room.corners[0] is CornerSpec.Measured)
        assertEquals(MeasurementRecommendation.DiagonalBetween(best.first, best.second), ambiguity.recommendation)
        assertTrue(ambiguity.selectedIsTrueShape)
    }

    @Test
    fun `given ambiguous room that needs an adjustment then the proposal carries its corrections`() {
        val case = OverdeterminedCases.AMBIGUOUS_NEEDS_ADJUSTMENT
        val repeated = case.room.diagonals.single { isAdjacent(it.fromCorner, it.toCorner, case.room.walls.size) }
        val raw =
            case.room.walls[0]
                .length.mm
                .toDouble()
        val adjusted = displayedLengths(case)[0]
        val correction = checkNotNull(case.expectedCorrection)

        assertEquals(0 to 1, repeated.fromCorner to repeated.toCorner)
        assertEquals(abs(repeated.length.mm - raw), correction.sumMm, EXACT_MM)
        assertEquals(correction.sumMm / 2, abs(adjusted - raw), EXACT_MM)
        assertTrue(abs(adjusted - raw) > EXACT_MM, "Without the rule the room would need an adjustment")
        assertTrue(correction.sumMm <= limitMm(case.room), "The correction stays a measurement error")
        assertEquals(ObservationRef.WallLength(0), case.expectedLargestCorrection)
        assertEquals(1, case.expectedRank?.redundancy)
        assertNotNull(case.expectedAmbiguity)
    }

    @Test
    fun `given every proposed case then reason and missing agree`() {
        OverdeterminedCases.ALL.filter { it.expectedStatus == ShapeStatus.PROPOSED }.forEach { case ->
            val missing = checkNotNull(case.expectedRank).missing
            val expected = if (missing > 0) ProposalReason.UNDERDETERMINED else ProposalReason.AMBIGUOUS_SHAPE
            assertEquals(expected, case.expectedProposalReason, case.key)
        }
    }

    @Test
    fun `given contradicting proposal expectations when a case is created then it is rejected`() {
        val valid = OverdeterminedCases.AMBIGUOUS_SELECTED_OTHER_SHAPE

        assertThrows(IllegalArgumentException::class.java) { valid.copy(expectedProposalReason = null) }
        assertThrows(IllegalArgumentException::class.java) { valid.copy(expectedAmbiguity = null) }
        assertThrows(IllegalArgumentException::class.java) {
            valid.copy(expectedProposalReason = ProposalReason.UNDERDETERMINED)
        }
        assertThrows(IllegalArgumentException::class.java) {
            valid.copy(expectedRank = valid.expectedRank?.copy(missing = 1))
        }
    }

    /** Hard conditions of DOMAIN section 5.6. A diagonal between adjacent corners repeats a wall and is not one. */
    private fun assertSatisfiesInputs(
        key: String,
        room: Room,
        lengths: List<Double>,
        turnsDeg: List<Double>,
    ) {
        val chain = chain(lengths, turnsDeg)
        val points = chain.dropLast(1)
        val end = chain.last()
        assertTrue(hypot(end.x, end.y) <= EXACT_MM, "$key closure")
        assertTrue(IndependentGeometry.isSimple(points), "$key simple")
        room.diagonals.filterNot { isAdjacent(it.fromCorner, it.toCorner, points.size) }.forEach { diagonal ->
            val length = distance(points[diagonal.fromCorner], points[diagonal.toCorner])
            assertTrue(abs(length - diagonal.length.mm) <= EXACT_MM, "$key diagonal ${diagonal.id}")
        }
        room.corners.forEachIndexed { i, corner ->
            val direction = corner.direction?.sign ?: return@forEachIndexed
            assertTrue(direction * wrapDeg(turnsDeg[i]) >= -DIRECTION_TOLERANCE_DEG, "$key direction $i")
            if (corner is CornerSpec.Measured) {
                val measured = direction * (STRAIGHT_DEG - corner.angle.milliDegrees / MILLI_PER_DEGREE)
                assertEquals(measured, wrapDeg(turnsDeg[i]), ANGLE_TOLERANCE_DEG, "$key measured $i")
            }
        }
    }

    private fun rawLengths(room: Room): List<Double> = room.walls.map { it.length.mm.toDouble() }

    private fun displayedLengths(case: OverdeterminedCase): List<Double> =
        rawLengths(case.room).mapIndexed { i, raw -> case.expectedAdjustedLengthsMm[i] ?: raw }

    private fun walk(
        lengths: List<Double>,
        turnsDeg: List<Double>,
    ): List<Vec2> = chain(lengths, turnsDeg).dropLast(1)

    private fun chain(
        lengths: List<Double>,
        turnsDeg: List<Double>,
    ): List<Vec2> {
        var heading = 0.0
        var x = 0.0
        var y = 0.0
        val points = mutableListOf(Vec2.ZERO)
        lengths.forEachIndexed { i, length ->
            if (i > 0) heading += Math.toRadians(turnsDeg[i])
            x += length * cos(heading)
            y += length * sin(heading)
            points += Vec2(x, y)
        }
        return points
    }

    private fun alignedDistance(
        first: List<Vec2>,
        second: List<Vec2>,
    ): Double {
        val a = centered(first)
        val b = centered(second)
        val dot = a.indices.sumOf { a[it].x * b[it].x + a[it].y * b[it].y }
        val cross = a.indices.sumOf { a[it].x * b[it].y - a[it].y * b[it].x }
        val angle = atan2(cross, dot)
        return a.indices.maxOf {
            hypot(
                cos(angle) * a[it].x - sin(angle) * a[it].y - b[it].x,
                sin(angle) * a[it].x + cos(angle) * a[it].y - b[it].y,
            )
        }
    }

    private fun centered(points: List<Vec2>): List<Vec2> {
        val cx = points.sumOf { it.x } / points.size
        val cy = points.sumOf { it.y } / points.size
        return points.map { Vec2(it.x - cx, it.y - cy) }
    }

    private fun distance(
        a: Vec2,
        b: Vec2,
    ): Double = hypot(a.x - b.x, a.y - b.y)

    private fun isAdjacent(
        a: Int,
        b: Int,
        n: Int,
    ): Boolean = abs(a - b) == 1 || abs(a - b) == n - 1

    private fun wrapDeg(value: Double): Double {
        val turned = (value + STRAIGHT_DEG).mod(2 * STRAIGHT_DEG) - STRAIGHT_DEG
        return if (turned == -STRAIGHT_DEG) STRAIGHT_DEG else turned
    }

    private fun perimeterMm(room: Room): Double = room.walls.sumOf { it.length.mm }.toDouble()

    private fun limitMm(room: Room): Double = maxOf(ABSOLUTE_LIMIT_MM, RELATIVE_LIMIT * perimeterMm(room))

    private companion object {
        const val EXACT_MM = 1.0
        const val ABSOLUTE_LIMIT_MM = 50.0
        const val RELATIVE_LIMIT = 0.01
        const val STRAIGHT_DEG = 180.0
        const val MILLI_PER_DEGREE = 1000.0
        const val DIRECTION_TOLERANCE_DEG = 2.0
        const val ANGLE_TOLERANCE_DEG = 0.05
        const val FULL_TURN_RAD = 2 * Math.PI
    }
}
