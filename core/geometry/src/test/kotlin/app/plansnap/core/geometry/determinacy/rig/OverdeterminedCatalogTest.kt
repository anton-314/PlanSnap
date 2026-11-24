package app.plansnap.core.geometry.determinacy.rig

import app.plansnap.core.geometry.determinacy.shapeDistanceMm
import app.plansnap.core.geometry.primitives.Polygon2
import app.plansnap.core.geometry.solver.SolverProblem
import app.plansnap.core.geometry.statistics.ChiSquareLimits
import app.plansnap.core.geometry.traverse.DefaultTraverse
import app.plansnap.core.geometry.traverse.toShapeInput
import app.plansnap.testing.fixtures.rooms.ExpectedAmbiguity
import app.plansnap.testing.fixtures.rooms.OverdeterminedCase
import app.plansnap.testing.fixtures.rooms.OverdeterminedCases
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

/** All 30 cases of [OverdeterminedCases] through the evaluation flow on the production code (ADR 0018). */
class OverdeterminedCatalogTest {
    private val evaluator = RigEvaluator()
    private val traverse = DefaultTraverse()

    @Test
    fun `given the catalog then it has 30 cases`() {
        assertEquals(CATALOG_SIZE, OverdeterminedCases.ALL.size)
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    fun `given a catalog case then status and reason match`(case: OverdeterminedCase) {
        val result = evaluator.evaluate(case.room.toShapeInput())
        val where = "${case.key} at ${result.path}"
        assertEquals(case.expectedStatus, result.status, where)
        assertEquals(case.expectedConflict, result.conflict, where)
        assertEquals(case.expectedProposalReason, result.proposal, where)
        assertEquals(case.expectedConfirmation, result.confirmation, where)
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    fun `given a catalog case then rank and redundancy match`(case: OverdeterminedCase) {
        val expected = case.expectedRank ?: return
        val result = evaluator.evaluate(case.room.toShapeInput())
        val constraints = assertNotNull(result.constraints, case.key).let { result.constraints!! }
        val determinacy = result.determinacy!!
        assertEquals(expected.freeVariables, constraints.freeVariables, case.key)
        assertEquals(expected.conditions, constraints.conditionCount, case.key)
        assertEquals(expected.routingRank, constraints.independentConditions, case.key)
        assertEquals(expected.missing, determinacy.missing, case.key)
        assertEquals(expected.redundancy, result.redundancy, case.key)
        assertEquals(determinacy.missing > 0, determinacy.recommendation != null, case.key)
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    fun `given a catalog case then the corrections match`(case: OverdeterminedCase) {
        val result = evaluator.evaluate(case.room.toShapeInput())
        case.expectedCorrection?.let { expected ->
            val shape = assertNotNull(result.shape, case.key).let { result.shape!! }
            assertRelative(expected.sumMm, shape.correctionSumMm, expected.toleranceRelative, "${case.key} S")
            val q2 = shape.normalizedSquareSum
            assertRelative(expected.normalizedSquareSum, q2, expected.toleranceRelative, "${case.key} q²")
        }
        case.expectedLargestCorrection?.let { assertEquals(it, result.shape?.largestCorrection, case.key) }
        case.expectedAdjustedLengthsMm.forEach { (wall, mm) ->
            assertEquals(mm, result.shape!!.adjustedLengthsMm[wall], LENGTH_TOLERANCE_MM, case.key)
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    fun `given a catalog case then the uncontrolled measurements match`(case: OverdeterminedCase) {
        val result = evaluator.evaluate(case.room.toShapeInput())
        val control = result.control ?: return assertTrue(case.expectedUncontrolled.isEmpty(), case.key)
        assertEquals(result.redundancy, control.redundancy, case.key)
        val threshold = if (control.redundancy > 0) ChiSquareLimits.controlThreshold(control.redundancy) else 0.0
        assertTrue(control.uncontrolled.all { it.noncentrality < threshold }, case.key)
        // The catalog states the uncontrolled measurements only where a finding is about them.
        if (case.expectedUncontrolled.isNotEmpty()) {
            val actual = control.uncontrolled.associate { it.observation to it.recommendation }
            assertEquals(case.expectedUncontrolled, actual, case.key)
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    fun `given a catalog case then the second shape matches`(case: OverdeterminedCase) {
        val expected = case.expectedAmbiguity ?: return
        val input = case.room.toShapeInput()
        val result = evaluator.evaluate(input)
        val ambiguity = assertNotNull(result.ambiguity, case.key).let { result.ambiguity!! }
        assertEquals(expected.distanceMm, ambiguity.distanceMm, ExpectedAmbiguity.DISTANCE_TOLERANCE_MM, case.key)
        assertEquals(expected.recommendation, ambiguity.recommendation, case.key)
        val raw = input.lengthsMm.map { it.toDouble() }
        val alternative = Polygon2(traverse.walk(raw, radians(expected.alternativeTurnsDeg)).dropLast(1))
        assertTrue(shapeDistanceMm(alternative, ambiguity.alternative) <= ExpectedAmbiguity.DISTANCE_TOLERANCE_MM)
        val turns = result.turnsRad!!
        expected.selectedTurnsDeg.forEachIndexed { i, degrees ->
            val difference = SolverProblem.wrap(turns[i] - StrictMath.toRadians(degrees))
            assertEquals(0.0, StrictMath.toDegrees(difference), TURN_TOLERANCE_DEG, "${case.key} turn $i")
        }
    }

    private fun assertRelative(
        expected: Double,
        actual: Double,
        relative: Double,
        what: String,
    ) {
        assertTrue(
            StrictMath.abs(actual - expected) <= relative * StrictMath.abs(expected),
            "$what: $actual vs $expected",
        )
    }

    private fun radians(degrees: List<Double>): List<Double> = degrees.map(StrictMath::toRadians)

    private companion object {
        const val CATALOG_SIZE = 30
        const val LENGTH_TOLERANCE_MM = 0.01
        const val TURN_TOLERANCE_DEG = 1e-3

        @JvmStatic
        fun cases(): List<OverdeterminedCase> = OverdeterminedCases.ALL
    }
}
