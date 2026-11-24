package app.plansnap.core.geometry.adjustment

import app.plansnap.core.domain.evaluation.AdjustmentConfirmation
import app.plansnap.core.domain.evaluation.ProposalReason
import app.plansnap.core.geometry.adjustment.AdjustmentTestSupport.adjustOrFail
import app.plansnap.core.geometry.adjustment.AdjustmentTestSupport.assertRelative
import app.plansnap.core.geometry.adjustment.AdjustmentTestSupport.gapMm
import app.plansnap.core.geometry.adjustment.AdjustmentTestSupport.isSimple
import app.plansnap.core.geometry.adjustment.AdjustmentTestSupport.outline
import app.plansnap.core.geometry.adjustment.AdjustmentTestSupport.perimeter
import app.plansnap.core.geometry.adjustment.AdjustmentTestSupport.radians
import app.plansnap.core.geometry.adjustment.AdjustmentTestSupport.rawLengths
import app.plansnap.core.geometry.adjustment.AdjustmentTestSupport.targetStart
import app.plansnap.core.geometry.adjustment.AdjustmentTestSupport.violatedCorners
import app.plansnap.core.geometry.determinacy.shapeDistanceMm
import app.plansnap.core.geometry.primitives.ClosureTolerance
import app.plansnap.core.geometry.primitives.GeometryTolerances
import app.plansnap.core.geometry.statistics.ChiSquareLevel
import app.plansnap.core.geometry.statistics.ChiSquareLimits
import app.plansnap.core.geometry.traverse.ClosureClass
import app.plansnap.core.geometry.traverse.CornerInput
import app.plansnap.core.geometry.traverse.ShapeInput
import app.plansnap.core.geometry.traverse.toShapeInput
import app.plansnap.core.model.ConflictReason
import app.plansnap.core.model.ShapeStatus
import app.plansnap.testing.fixtures.rooms.ExpectedAmbiguity
import app.plansnap.testing.fixtures.rooms.OverdeterminedCase
import app.plansnap.testing.fixtures.rooms.OverdeterminedCases
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory

/**
 * Confirms every case of [OverdeterminedCases] on the production adjuster (ADR 0018). Each case runs the path that the
 * reference prototype takes. The decision steps outside T-GEO-006 (rank, solver, ambiguity search) use the expected
 * values of the fixture, all adjustment values come from [DefaultClosureAdjuster].
 */
class ClosureAdjusterCatalogTest {
    private val tolerance = ClosureTolerance()

    @Test
    fun `given catalog then it has thirty distinct cases and each has a reference run`() {
        assertEquals(CATALOG_SIZE, OverdeterminedCases.ALL.size)
        assertEquals(
            CATALOG_SIZE,
            OverdeterminedCases.ALL
                .map { it.key }
                .toSet()
                .size,
        )
        assertEquals(OverdeterminedCases.ALL.toSet(), CatalogReferenceRuns.RUNS.keys)
    }

    @TestFactory
    fun `given catalog case when evaluated with the production adjuster then status and values match`() =
        OverdeterminedCases.ALL.map { case ->
            DynamicTest.dynamicTest(case.key) { check(case, CatalogReferenceRuns.RUNS.getValue(case)) }
        }

    private fun check(
        case: OverdeterminedCase,
        run: ReferenceRun,
    ) {
        val input = case.room.toShapeInput()
        val closure = AdjustmentTestSupport.traverse.closureError(input)
        val closureClass = AdjustmentTestSupport.traverse.classify(input, closure, tolerance)
        when (run) {
            ReferenceRun.Exact -> checkExact(case, input, closureClass)
            is ReferenceRun.BranchA -> checkBranchA(case, input, closureClass, run)
            is ReferenceRun.Adjust -> checkAdjusted(case, input, closureClass, run)
        }
    }

    private fun checkExact(
        case: OverdeterminedCase,
        input: ShapeInput,
        closureClass: ClosureClass,
    ) {
        assertEquals(ClosureClass.EXACT, closureClass)
        val simple = AdjustmentTestSupport.analyzer.isSimple(outline(rawLengths(input), targetStart(input)))
        val status = if (simple) ShapeStatus.CONFIRMED else ShapeStatus.CONFLICT
        assertEquals(case.expectedStatus, status)
        assertEquals(case.expectedConflict, ConflictReason.NO_SIMPLE_SHAPE.takeIf { !simple })
    }

    /** Branch A does not adjust. The adjuster confirms that each solver shape meets every input exactly. */
    private fun checkBranchA(
        case: OverdeterminedCase,
        input: ShapeInput,
        closureClass: ClosureClass,
        run: ReferenceRun.BranchA,
    ) {
        assertEquals(ClosureClass.SHAPE_DEVIATION, closureClass)
        val rank = checkNotNull(case.expectedRank) { "branch A needs a rank: ${case.key}" }
        assertEquals(rank.conditions, rank.routingRank, "branch A requires m = ρ")
        val ambiguity = case.expectedAmbiguity
        when {
            ambiguity != null -> {
                checkAmbiguousBranchA(case, input, ambiguity)
            }

            run.solutionTurnsRad != null -> {
                val shape = assertExactFit(input, run.solutionTurnsRad)
                assertTrue(rank.missing > 0 && case.expectedProposalReason == ProposalReason.UNDERDETERMINED)
                assertTrue(isSimple(shape))
            }

            else -> {
                checkTriangleDirections(case, input)
            }
        }
    }

    private fun checkAmbiguousBranchA(
        case: OverdeterminedCase,
        input: ShapeInput,
        ambiguity: ExpectedAmbiguity,
    ) {
        val selected = assertExactFit(input, radians(ambiguity.selectedTurnsDeg))
        val alternative = assertExactFit(input, radians(ambiguity.alternativeTurnsDeg))
        listOf(selected, alternative).forEach { shape ->
            assertTrue(isSimple(shape))
            assertTrue(violatedCorners(input, shape.adjustedTurnsRad).isEmpty())
        }
        val distance = shapeDistanceMm(outline(selected), outline(alternative))
        assertEquals(ambiguity.distanceMm, distance, ExpectedAmbiguity.DISTANCE_TOLERANCE_MM)
        assertTrue(distance > tolerance.limitMm(perimeter(input)))
        assertEquals(ShapeStatus.PROPOSED, case.expectedStatus)
        assertEquals(ProposalReason.AMBIGUOUS_SHAPE, case.expectedProposalReason)
    }

    /** Both mirror images of the only triangle with these lengths violate one declared direction. */
    private fun checkTriangleDirections(
        case: OverdeterminedCase,
        input: ShapeInput,
    ) {
        val (a, b, c) = rawLengths(input)
        val turnAtV1 = StrictMath.PI - StrictMath.acos((a * a + b * b - c * c) / (2.0 * a * b))
        val turnAtV2 = StrictMath.PI - StrictMath.acos((b * b + c * c - a * a) / (2.0 * b * c))
        listOf(1.0, -1.0).forEach { sign ->
            val turns =
                listOf(
                    sign * 2.0 * StrictMath.PI - sign * (turnAtV1 + turnAtV2),
                    sign * turnAtV1,
                    sign * turnAtV2,
                )
            val shape = assertExactFit(input, turns)
            assertFalse(violatedCorners(input, shape.adjustedTurnsRad).isEmpty())
        }
        assertEquals(ConflictReason.TURN_DIRECTION_VIOLATED, case.expectedConflict)
    }

    /** Adjusts in `SHAPE_DEVIATION` from [turns] and asserts that no observation needs a correction. */
    private fun assertExactFit(
        input: ShapeInput,
        turns: List<Double>,
    ): AdjustedShape {
        val shape = adjustOrFail(input, AdjustmentMode.SHAPE_DEVIATION, turns)
        assertTrue(shape.maxCorrectionMm <= GeometryTolerances.EXACT_CLOSURE_MM, "correction ${shape.maxCorrectionMm}")
        assertTrue(gapMm(shape) <= GeometryTolerances.SOLVER_RESIDUAL_MM)
        return shape
    }

    private fun checkAdjusted(
        case: OverdeterminedCase,
        input: ShapeInput,
        closureClass: ClosureClass,
        run: ReferenceRun.Adjust,
    ) {
        val expectedClass =
            if (run.mode ==
                AdjustmentMode.MEASUREMENT_ERROR
            ) {
                ClosureClass.MEASUREMENT_ERROR
            } else {
                ClosureClass.SHAPE_DEVIATION
            }
        assertEquals(expectedClass, closureClass)
        val shape = adjustOrFail(input, run.mode, run.startTurnsRad ?: targetStart(input))
        assertTrue(gapMm(shape) <= GeometryTolerances.SOLVER_RESIDUAL_MM, "gap ${gapMm(shape)}")
        val decision =
            if (run.mode ==
                AdjustmentMode.MEASUREMENT_ERROR
            ) {
                measurementErrorDecision(shape)
            } else {
                branchB(case, input, shape)
            }
        assertEquals(case.expectedStatus, decision.status)
        assertEquals(case.expectedConflict, decision.conflict)
        assertEquals(case.expectedProposalReason, decision.proposal)
        assertEquals(case.expectedConfirmation, decision.confirmation)
        checkValues(case, input, shape)
    }

    private fun checkValues(
        case: OverdeterminedCase,
        input: ShapeInput,
        shape: AdjustedShape,
    ) {
        case.expectedCorrection?.let {
            assertRelative(it.sumMm, shape.correctionSumMm, it.toleranceRelative, "S")
            assertRelative(it.normalizedSquareSum, shape.normalizedSquareSum, it.toleranceRelative, "q²")
        }
        case.expectedLargestCorrection?.let { assertEquals(it, shape.largestCorrection) }
        case.expectedAdjustedLengthsMm.forEach { (wall, mm) ->
            assertEquals(mm, shape.adjustedLengthsMm[wall], GeometryTolerances.SOLVER_RESIDUAL_MM)
        }
        case.expectedAmbiguity?.let { ambiguity ->
            radians(ambiguity.selectedTurnsDeg).forEachIndexed { i, expected ->
                assertEquals(0.0, wrapAngle(shape.adjustedTurnsRad[i] - expected), TURN_TOLERANCE_RAD)
            }
        }
        if (case.expectedUncontrolled.isNotEmpty()) {
            assertEquals(case.expectedUncontrolled.keys, uncontrolled(input, shape, case.expectedRank!!.redundancy))
        }
    }

    /** DOMAIN section 4, step 6, measurement error path. */
    private fun measurementErrorDecision(shape: AdjustedShape): Decision =
        if (isSimple(shape)) {
            Decision(ShapeStatus.NEEDS_ADJUSTMENT, confirmation = AdjustmentConfirmation.SIMPLE)
        } else {
            Decision(ShapeStatus.CONFLICT, conflict = ConflictReason.NO_SIMPLE_SHAPE)
        }

    /** Steps B3 to B9 of DOMAIN section 5.5 with rank and alternatives from the fixture. */
    private fun branchB(
        case: OverdeterminedCase,
        input: ShapeInput,
        shape: AdjustedShape,
    ): Decision {
        val rank = case.expectedRank!!
        val r = rank.redundancy
        val limit = tolerance.limitMm(perimeter(input))
        val large = r >= 1 && shape.correctionSumMm > limit
        val q2 = shape.normalizedSquareSum
        return when {
            !isSimple(shape) -> {
                Decision(ShapeStatus.CONFLICT, conflict = ConflictReason.NO_SIMPLE_SHAPE)
            }

            large && q2 > ChiSquareLimits.quantile(ChiSquareLevel.CONFLICT, r) -> {
                Decision(ShapeStatus.CONFLICT, conflict = ConflictReason.MEASUREMENTS_INCONSISTENT)
            }

            rank.missing == 0 && violatedCorners(input, shape.adjustedTurnsRad).isNotEmpty() -> {
                Decision(ShapeStatus.CONFLICT, conflict = ConflictReason.TURN_DIRECTION_VIOLATED)
            }

            rank.missing > 0 -> {
                Decision(ShapeStatus.PROPOSED, proposal = ProposalReason.UNDERDETERMINED)
            }

            isAmbiguous(case, input, shape, limit) -> {
                Decision(ShapeStatus.PROPOSED, proposal = ProposalReason.AMBIGUOUS_SHAPE)
            }

            shape.maxCorrectionMm <= GeometryTolerances.EXACT_CLOSURE_MM -> {
                Decision(ShapeStatus.CONFIRMED)
            }

            else -> {
                val explicit = large && q2 > ChiSquareLimits.quantile(ChiSquareLevel.EXPLICIT_CONFIRMATION, r)
                val confirmation = if (explicit) AdjustmentConfirmation.EXPLICIT else AdjustmentConfirmation.SIMPLE
                Decision(ShapeStatus.NEEDS_ADJUSTMENT, confirmation = confirmation)
            }
        }
    }

    /** Step B7 with the alternative of the fixture on the raw lengths (DOMAIN section 5.6). */
    private fun isAmbiguous(
        case: OverdeterminedCase,
        input: ShapeInput,
        shape: AdjustedShape,
        limit: Double,
    ): Boolean {
        val ambiguity = case.expectedAmbiguity ?: return false
        val alternative = outline(rawLengths(input), radians(ambiguity.alternativeTurnsDeg))
        val distance = shapeDistanceMm(outline(shape), alternative)
        assertEquals(ambiguity.distanceMm, distance, ExpectedAmbiguity.DISTANCE_TOLERANCE_MM)
        return distance > limit
    }

    /** Measurements with `λ_i(3T) < λ₀(r)` (DOMAIN section 5.5, control). */
    private fun uncontrolled(
        input: ShapeInput,
        shape: AdjustedShape,
        redundancy: Int,
    ): Set<ObservationRef> {
        val u = perimeter(input)
        val error = GeometryTolerances.CONTROL_ERROR_FACTOR * tolerance.limitMm(u)
        return shape.redundancies
            .filter { entry ->
                val sigma = sigmaMm(input, entry.observation, u)
                error * error * entry.redundancy / (sigma * sigma) < ChiSquareLimits.controlThreshold(redundancy)
            }.map { it.observation }
            .toSet()
    }

    private fun sigmaMm(
        input: ShapeInput,
        observation: ObservationRef,
        perimeterMm: Double,
    ): Double =
        when (observation) {
            is ObservationRef.WallLength -> {
                GeometryTolerances.SIGMA_LENGTH_MM
            }

            is ObservationRef.DiagonalLength -> {
                GeometryTolerances.SIGMA_DIAGONAL_MM
            }

            is ObservationRef.CornerAngle -> {
                val corner = input.corners[observation.index]
                val degrees =
                    if (corner is CornerInput.Measured) {
                        GeometryTolerances.SIGMA_MEASURED_ANGLE_DEG
                    } else {
                        GeometryTolerances.SIGMA_RIGHT_ANGLE_DEG
                    }
                StrictMath.toRadians(degrees) * perimeterMm / (2.0 * StrictMath.PI)
            }
        }

    private data class Decision(
        val status: ShapeStatus,
        val conflict: ConflictReason? = null,
        val proposal: ProposalReason? = null,
        val confirmation: AdjustmentConfirmation? = null,
    )

    private companion object {
        const val CATALOG_SIZE = 30
        const val TURN_TOLERANCE_RAD = 2e-6
    }
}
