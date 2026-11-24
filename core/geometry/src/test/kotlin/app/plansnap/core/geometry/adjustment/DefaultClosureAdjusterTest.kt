package app.plansnap.core.geometry.adjustment

import app.plansnap.core.common.units.Angle
import app.plansnap.core.common.units.TurnDirection
import app.plansnap.core.geometry.adjustment.AdjustmentTestSupport.adjustOrFail
import app.plansnap.core.geometry.adjustment.AdjustmentTestSupport.adjuster
import app.plansnap.core.geometry.adjustment.AdjustmentTestSupport.gapMm
import app.plansnap.core.geometry.adjustment.AdjustmentTestSupport.outline
import app.plansnap.core.geometry.adjustment.AdjustmentTestSupport.targetStart
import app.plansnap.core.geometry.cancellation.CancellationSignal
import app.plansnap.core.geometry.primitives.GeometryTolerances
import app.plansnap.core.geometry.traverse.CornerInput
import app.plansnap.core.geometry.traverse.DiagonalInput
import app.plansnap.core.geometry.traverse.ShapeInput
import app.plansnap.core.geometry.traverse.rightAngled
import app.plansnap.core.geometry.traverse.turnCorner
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CancellationException

class DefaultClosureAdjusterTest {
    private val trapezoid =
        rightAngled(5000, 3000, 3000, 3000, diagonals = listOf(DiagonalInput(0, 2, 4899), DiagonalInput(1, 3, 4899)))

    @Test
    fun `given adjuster then version is one`() {
        assertEquals(1, adjuster.version)
    }

    @Test
    fun `given trapezoid 3-3-3-5 m with two diagonals when adjusted for shape deviation then it converges`() {
        // DOMAIN section 5.3: interior angles arccos(1/3) at both ends of the 5 m wall, area 8√2 m².
        val acute = StrictMath.acos(1.0 / 3.0)
        val expected = listOf(StrictMath.PI - acute, StrictMath.PI - acute, acute, acute)
        val starts =
            listOf(
                targetStart(trapezoid),
                expected,
                List(4) { StrictMath.toRadians(100.0) },
                List(4) { 1.1 * StrictMath.PI / 2.0 },
            )
        starts.forEach { start ->
            val shape = adjustOrFail(trapezoid, AdjustmentMode.SHAPE_DEVIATION, start)
            assertTrue(gapMm(shape) <= GeometryTolerances.SOLVER_RESIDUAL_MM)
            expected.forEachIndexed { i, turn -> assertEquals(turn, shape.adjustedTurnsRad[i], TRAPEZOID_TURN_RAD) }
            val area = AdjustmentTestSupport.analyzer.areaMm2(outline(shape))
            assertEquals(8.0 * StrictMath.sqrt(2.0) * 1e6, area, TRAPEZOID_AREA_MM2)
            // The diagonals are 0.02 mm longer than the true 4898.98 mm, so every correction stays tiny.
            assertTrue(shape.maxCorrectionMm < GeometryTolerances.EXACT_CLOSURE_MM)
            // Declared corners carry no observation in this mode, so only walls and diagonals are reported.
            assertEquals(6, shape.redundancies.size)
            assertTrue(shape.redundancies.none { it.observation is ObservationRef.CornerAngle })
        }
    }

    @Test
    fun `given wall measured twice when adjusted then both observations meet at their mean`() {
        val input = rightAngled(5000, 3000, 3000, 3000, diagonals = listOf(DiagonalInput(0, 3, 3050)))
        val shape = adjustOrFail(input, AdjustmentMode.SHAPE_DEVIATION, targetStart(input))
        // Equal σ: both observations of w_3 move by half the difference of 50 mm.
        assertEquals(3025.0, shape.adjustedLengthsMm[3], ANALYTIC_MM)
        assertEquals(3025.0, shape.adjustedDiagonalsMm[0], ANALYTIC_MM)
        assertEquals(50.0, shape.correctionSumMm, ANALYTIC_MM)
        assertEquals(25.0, shape.maxCorrectionMm, ANALYTIC_MM)
        assertEquals(2.0 * (25.0 / 5.0) * (25.0 / 5.0), shape.normalizedSquareSum, ANALYTIC_Q2)
        assertEquals(ObservationRef.WallLength(3), shape.largestCorrection)
        assertEquals(ObservationRef.WallLength(3), shape.worstObservation)
        // Each of the two observations has redundancy 1/2, all other walls have no cross-check.
        val redundancy = shape.redundancies.associate { it.observation to it.redundancy }
        assertEquals(0.5, redundancy.getValue(ObservationRef.WallLength(3)), FREE_SIGMA_REDUNDANCY)
        assertEquals(0.5, redundancy.getValue(ObservationRef.DiagonalLength(0)), FREE_SIGMA_REDUNDANCY)
        listOf(
            0,
            1,
            2,
        ).forEach { assertEquals(0.0, redundancy.getValue(ObservationRef.WallLength(it)), FREE_SIGMA_REDUNDANCY) }
    }

    @Test
    fun `given consistent repetition when adjusted then nothing is corrected and no observation is reported`() {
        val input = rightAngled(5000, 3000, 3000, 3000, diagonals = listOf(DiagonalInput(3, 0, 3000)))
        val shape = adjustOrFail(input, AdjustmentMode.SHAPE_DEVIATION, solvedTrapezoidTurns())
        assertEquals(0.0, shape.maxCorrectionMm, ANALYTIC_MM)
        assertNull(shape.largestCorrection)
        assertNull(shape.worstObservation)
        assertEquals(0.0, shape.normalizedSquareSum, ANALYTIC_Q2)
    }

    @Test
    fun `given gap of 30 mm when adjusted as measurement error then declared corners are observations`() {
        val input = rightAngled(4030, 3000, 4000, 3000)
        val shape = adjustOrFail(input, AdjustmentMode.MEASUREMENT_ERROR, targetStart(input))
        assertTrue(gapMm(shape) <= GeometryTolerances.SOLVER_RESIDUAL_MM)
        // Prototype reference (docs/research/evaluation-prototype): S = 30.135366, q² = 0.830445.
        assertEquals(30.135366, shape.correctionSumMm, PROTOTYPE_MM)
        assertEquals(0.830445, shape.normalizedSquareSum, PROTOTYPE_Q2)
        assertEquals(ObservationRef.CornerAngle(3), shape.largestCorrection)
        val corners = shape.redundancies.map { it.observation }.filterIsInstance<ObservationRef.CornerAngle>()
        // The free closing corner never has an observation.
        assertEquals(listOf(1, 2, 3).map { ObservationRef.CornerAngle(it) }, corners)
        assertEquals(input.wallCount + 3, shape.redundancies.size)
        // Three conditions, but the free closing corner absorbs the turn sum: r = 2 (DOMAIN section 4, step 6).
        assertEquals(2.0, shape.redundancies.sumOf { it.redundancy }, FREE_SIGMA_REDUNDANCY)
    }

    @Test
    fun `given gap of 30 mm when adjusted as shape deviation then unobserved turns absorb the gap`() {
        // Declared corners carry no observation here, and a quadrilateral with free turns closes with any lengths.
        val input = rightAngled(4030, 3000, 4000, 3000)
        val start = targetStart(input)
        val shape = adjustOrFail(input, AdjustmentMode.SHAPE_DEVIATION, start)
        assertTrue(gapMm(shape) <= GeometryTolerances.SOLVER_RESIDUAL_MM)
        assertTrue(shape.maxCorrectionMm < GeometryTolerances.SOLVER_RESIDUAL_MM, "${shape.maxCorrectionMm}")
        assertNull(shape.largestCorrection)
        // The turns moved away from 90° and were not pulled back to their targets.
        assertTrue(shape.adjustedTurnsRad.indices.any { StrictMath.abs(shape.adjustedTurnsRad[it] - start[it]) > 1e-3 })
        assertEquals(input.wallCount, shape.redundancies.size)
    }

    @Test
    fun `given measured corners in both modes then their observations do not depend on the mode`() {
        val input =
            ShapeInput(
                lengthsMm = listOf(4150, 3000, 4000, 3000),
                corners = List(4) { CornerInput.Measured(TurnDirection.LEFT, Angle.RIGHT) },
            )
        val error = adjustOrFail(input, AdjustmentMode.MEASUREMENT_ERROR, targetStart(input))
        val shape = adjustOrFail(input, AdjustmentMode.SHAPE_DEVIATION, targetStart(input))
        assertEquals(error, shape)
        // DOMAIN section 5.5 example: S = 223.94, q² = 8.062.
        assertEquals(223.94, shape.correctionSumMm, BAND_MM)
        assertEquals(8.062, shape.normalizedSquareSum, BAND_Q2)
        assertEquals(ObservationRef.CornerAngle(0), shape.largestCorrection)
    }

    @Test
    fun `given start turn shifted by two full turns when adjusted then angle corrections are wrapped`() {
        // Rectangle 4000 x 2000 with a straight measured closing corner in the middle of the bottom wall.
        val input =
            ShapeInput(
                lengthsMm = listOf(2000, 2000, 4000, 2000, 2000),
                corners =
                    listOf(
                        turnCorner(0),
                        turnCorner(90_000),
                        turnCorner(90_000),
                        turnCorner(90_000),
                        turnCorner(90_000),
                    ),
            )
        val shiftedStart = targetStart(input).mapIndexed { i, turn -> if (i == 2) turn + 4.0 * StrictMath.PI else turn }
        val shape = adjustOrFail(input, AdjustmentMode.SHAPE_DEVIATION, shiftedStart)
        assertTrue(shape.maxCorrectionMm <= GeometryTolerances.SOLVER_RESIDUAL_MM, "${shape.maxCorrectionMm}")
    }

    @Test
    fun `given start turns of wrong size or not finite when adjusted then it is rejected`() {
        val input = rightAngled(4000, 3000, 4000, 3000)
        assertThrows(IllegalArgumentException::class.java) {
            adjuster.adjust(input, AdjustmentMode.SHAPE_DEVIATION, listOf(1.0, 1.0, 1.0), CancellationSignal.NONE)
        }
        listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY).forEach { bad ->
            assertThrows(IllegalArgumentException::class.java) {
                adjuster.adjust(
                    input,
                    AdjustmentMode.SHAPE_DEVIATION,
                    listOf(1.0, 1.0, 1.0, bad),
                    CancellationSignal.NONE,
                )
            }
        }
    }

    @Test
    fun `given impossible diagonal when adjusted then it does not converge`() {
        // The diagonal is longer than the whole perimeter. With σ = 5 mm the walls would have to stretch without bound.
        val input = rightAngled(1000, 1000, 1000, 1000, diagonals = listOf(DiagonalInput(0, 2, 90_000)))
        val outcome =
            adjuster.adjust(
                input,
                AdjustmentMode.SHAPE_DEVIATION,
                targetStart(input),
                CancellationSignal.NONE,
            )
        assertSame(AdjustmentOutcome.NotConverged, outcome)
    }

    @Test
    fun `given cancelled signal when adjusted then the computation stops`() {
        val input = rightAngled(4030, 3000, 4000, 3000)
        var calls = 0
        val cancelAfterFirst =
            CancellationSignal {
                calls++
                if (calls > 1) throw CancellationException("cancelled")
            }
        assertThrows(CancellationException::class.java) {
            adjuster.adjust(input, AdjustmentMode.MEASUREMENT_ERROR, targetStart(input), cancelAfterFirst)
        }
        assertEquals(2, calls)
    }

    @Test
    fun `given running adjustment then it checks for cancellation in every iteration and elimination step`() {
        val input = rightAngled(4030, 3000, 4000, 3000)
        var calls = 0
        adjuster.adjust(input, AdjustmentMode.MEASUREMENT_ERROR, targetStart(input)) { calls++ }
        // Two steps with three pivots each, three convergence checks, and three pivots for the final redundancies.
        assertEquals(2 * 3 + 3 + 3, calls)
    }

    private fun solvedTrapezoidTurns(): List<Double> {
        val acute = StrictMath.acos(1.0 / 3.0)
        return listOf(StrictMath.PI - acute, StrictMath.PI - acute, acute, acute)
    }

    private companion object {
        const val TRAPEZOID_TURN_RAD = 1e-5
        const val TRAPEZOID_AREA_MM2 = 500.0
        const val ANALYTIC_MM = 1e-6
        const val ANALYTIC_Q2 = 1e-9
        const val FREE_SIGMA_REDUNDANCY = 1e-5
        const val PROTOTYPE_MM = 1e-4
        const val PROTOTYPE_Q2 = 1e-6
        const val BAND_MM = 0.01
        const val BAND_Q2 = 0.001
    }
}
