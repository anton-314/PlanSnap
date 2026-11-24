package app.plansnap.core.domain.evaluation

import app.plansnap.core.geometry.determinacy.shapeDistanceMm
import app.plansnap.core.geometry.primitives.Polygon2
import app.plansnap.core.geometry.traverse.DefaultTraverse
import app.plansnap.testing.fixtures.rooms.ExpectedAmbiguity
import app.plansnap.testing.fixtures.rooms.OverdeterminedCase
import app.plansnap.testing.fixtures.rooms.OverdeterminedCases
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

/** All cases of [OverdeterminedCases] through the production evaluator (ADR 0018, T-DOM-001). */
class OverdeterminedCatalogEvaluationTest {
    private val traverse = DefaultTraverse()

    @Test
    fun `given the catalog when every case is evaluated then all 30 match status and reason`() {
        val matching =
            OverdeterminedCases.ALL.count { case ->
                val evaluation = evaluated(case.room)
                evaluation.status == case.expectedStatus &&
                    conflictOf(evaluation) == case.expectedConflict &&
                    proposalOf(evaluation) == case.expectedProposalReason &&
                    confirmationOf(evaluation) == case.expectedConfirmation
            }
        assertEquals(CATALOG_SIZE, OverdeterminedCases.ALL.size)
        assertEquals(CATALOG_SIZE, matching)
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    fun `given a catalog case when evaluated then status reason and confirmation match`(case: OverdeterminedCase) {
        val evaluation = evaluated(case.room)
        assertEquals(case.expectedStatus, evaluation.status, case.key)
        assertEquals(case.expectedConflict, conflictOf(evaluation), case.key)
        assertEquals(case.expectedProposalReason, proposalOf(evaluation), case.key)
        assertEquals(case.expectedConfirmation, confirmationOf(evaluation), case.key)
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    fun `given a catalog case with expected corrections when evaluated then they match`(case: OverdeterminedCase) {
        val expected = case.expectedCorrection ?: return
        val evaluation = evaluated(case.room)
        val (sum, q2) =
            when (evaluation) {
                is RoomEvaluation.Proposed -> {
                    evaluation.correction?.let {
                        it.correctionSumMm to it.normalizedSquareSum
                    }
                }

                is RoomEvaluation.NeedsAdjustment -> {
                    evaluation.proposal.correctionSumMm to null
                }

                else -> {
                    null
                }
            } ?: return
        assertRelative(expected.sumMm, sum, expected.toleranceRelative, "${case.key} S")
        q2?.let { assertRelative(expected.normalizedSquareSum, it, expected.toleranceRelative, "${case.key} q²") }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    fun `given a catalog case with adjusted lengths when evaluated then the shape shows them`(
        case: OverdeterminedCase,
    ) {
        val shape = evaluated(case.room).shape
        case.expectedAdjustedLengthsMm.forEach { (wall, mm) ->
            assertEquals(mm, shape!!.lengthsMm[wall], LENGTH_TOLERANCE_MM, case.key)
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    fun `given an ambiguous catalog case when evaluated then the second shape matches`(case: OverdeterminedCase) {
        val expected = case.expectedAmbiguity ?: return
        val evaluation = evaluated(case.room) as RoomEvaluation.Proposed
        val ambiguity = evaluation.ambiguity!!
        assertEquals(expected.distanceMm, ambiguity.distanceMm, ExpectedAmbiguity.DISTANCE_TOLERANCE_MM, case.key)
        assertEquals(expected.recommendation, evaluation.recommendation, case.key)
        val raw = case.room.walls.map { it.length.mm.toDouble() }
        val turns = expected.alternativeTurnsDeg.map(StrictMath::toRadians)
        val alternative = Polygon2(traverse.walk(raw, turns).dropLast(1))
        assertTrue(shapeDistanceMm(alternative, ambiguity.alternative) <= ExpectedAmbiguity.DISTANCE_TOLERANCE_MM)
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

    private companion object {
        const val CATALOG_SIZE = 30
        const val LENGTH_TOLERANCE_MM = 0.01

        @JvmStatic
        fun cases(): List<OverdeterminedCase> = OverdeterminedCases.ALL
    }
}
