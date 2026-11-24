package app.plansnap.core.geometry.determinacy

import app.plansnap.core.common.units.Angle
import app.plansnap.core.common.units.TurnDirection
import app.plansnap.core.geometry.cancellation.CancellationSignal
import app.plansnap.core.geometry.solver.HardCondition
import app.plansnap.core.geometry.traverse.CornerInput
import app.plansnap.core.geometry.traverse.DefaultTraverse
import app.plansnap.core.geometry.traverse.DiagonalInput
import app.plansnap.core.geometry.traverse.ShapeInput
import app.plansnap.core.geometry.traverse.rightAngled
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CancellationException

class DefaultDeterminacyAnalyzerTest {
    private val analyzer = DefaultDeterminacyAnalyzer()
    private val traverse = DefaultTraverse()
    private val none = CancellationSignal.NONE
    private val rectangle = listOf(4000.0, 3000.0, 4000.0, 3000.0)
    private val rectangleTurns = listOf(HALF_PI, HALF_PI, HALF_PI, HALF_PI)

    @Test
    fun `given quadrilateral without additional measurements then v 3 m 2 rho 2`() {
        val analysis = constraints(rightAngled(4000, 3000, 4000, 3000))

        assertEquals(3, analysis.freeVariables)
        assertEquals(2, analysis.conditionCount)
        assertEquals(listOf(HardCondition.ClosureX, HardCondition.ClosureY), analysis.independentSelection)
        assertEquals(1, analysis.missing)
        assertFalse(analysis.isOverdetermined)
    }

    @Test
    fun `given quadrilateral with one diagonal then v 3 m 3 rho 3`() {
        val analysis = constraints(rightAngled(4000, 3000, 4000, 3000, diagonals = listOf(DiagonalInput(0, 2, 5000))))

        assertEquals(
            Triple(3, 3, 3),
            Triple(analysis.freeVariables, analysis.conditionCount, analysis.independentConditions),
        )
        assertEquals(HardCondition.DiagonalLength(0), analysis.independentSelection.last())
        assertFalse(analysis.isOverdetermined)
    }

    @Test
    fun `given quadrilateral with two diagonals then it is overdetermined without solving`() {
        val diagonals = listOf(DiagonalInput(1, 3, 5000), DiagonalInput(0, 2, 5000))
        val analysis = constraints(rightAngled(4000, 3000, 4000, 3000, diagonals = diagonals))

        assertEquals(
            Triple(3, 4, 3),
            Triple(analysis.freeVariables, analysis.conditionCount, analysis.independentConditions),
        )
        assertTrue(analysis.isOverdetermined)
        // Pivot order: closure, then the diagonals by (min, max), so (0, 2) at input index 1 comes first.
        assertEquals(HardCondition.DiagonalLength(1), analysis.independentSelection.last())
    }

    @Test
    fun `given room with every corner measured then v 0 rho 0 and overdetermined`() {
        val corners = List(4) { CornerInput.Measured(TurnDirection.LEFT, Angle.RIGHT) }
        val analysis = constraints(ShapeInput(listOf(4000, 3000, 4000, 3000), corners))

        assertEquals(
            Triple(0, 3, 0),
            Triple(analysis.freeVariables, analysis.conditionCount, analysis.independentConditions),
        )
        assertTrue(analysis.isOverdetermined)
        assertEquals(0, analysis.missing)
    }

    @Test
    fun `given pentagon with the same diagonal twice then it is overdetermined and one measurement is missing`() {
        val diagonals = listOf(DiagonalInput(0, 2, 5000), DiagonalInput(0, 2, 5000))
        val analysis = constraints(rightAngled(4000, 3000, 2500, 2500, 3000, diagonals = diagonals))

        assertEquals(
            Triple(4, 4, 3),
            Triple(analysis.freeVariables, analysis.conditionCount, analysis.independentConditions),
        )
        assertTrue(analysis.isOverdetermined)
        assertEquals(1, analysis.missing)
        assertEquals(HardCondition.DiagonalLength(0), analysis.independentSelection.last())
    }

    @Test
    fun `given diagonal between adjacent corners then it counts in m but never in the selection`() {
        val analysis = constraints(rightAngled(4000, 3000, 4000, 3000, diagonals = listOf(DiagonalInput(3, 0, 3000))))

        assertEquals(3, analysis.conditionCount)
        assertEquals(listOf(HardCondition.ClosureX, HardCondition.ClosureY), analysis.independentSelection)
        assertTrue(analysis.isOverdetermined)
    }

    @Test
    fun `given singular target turns then the probe point decides and no overdetermination is reported`() {
        val input = rightAngled(4000, 3000, 4000, 3000)
        val straight = listOf(0.0, 0.0, 0.0, 0.0)

        val analysis = analyzer.analyzeConstraints(input, straight, none)

        assertEquals(2, analysis.independentConditions)
        assertFalse(analysis.isOverdetermined)
    }

    @Test
    fun `given closed rectangle then one measurement is missing and the first longest diagonal is recommended`() {
        val determinacy = analyzer.analyze(rightAngled(4000, 3000, 4000, 3000), rectangle, rectangleTurns, none)

        assertEquals(3, determinacy.freeVariables)
        assertEquals(2, determinacy.rank)
        assertEquals(1, determinacy.missing)
        assertEquals(MeasurementRecommendation.DiagonalBetween(0, 2), determinacy.recommendation)
    }

    @Test
    fun `given determined rectangle with a diagonal then missing is zero`() {
        val input = rightAngled(4000, 3000, 4000, 3000, diagonals = listOf(DiagonalInput(1, 3, 5000)))

        val determinacy = analyzer.analyze(input, rectangle, rectangleTurns, none)

        assertEquals(Determinacy(3, 3, null), determinacy)
    }

    @Test
    fun `given every corner measured then nothing is free`() {
        val corners = List(4) { CornerInput.Measured(TurnDirection.LEFT, Angle.RIGHT) }

        val determinacy =
            analyzer.analyze(
                ShapeInput(listOf(4000, 3000, 4000, 3000), corners),
                rectangle,
                rectangleTurns,
                none,
            )

        assertEquals(Determinacy(0, 0, null), determinacy)
    }

    @Test
    fun `given collinear triangle without any diagonal then the closing corner angle is recommended`() {
        val input = ShapeInput(listOf(1000, 500, 500), listOf(CornerInput.Free, left, left))

        val determinacy = analyzer.analyze(input, listOf(1000.0, 500.0, 500.0), listOf(0.0, 0.0, 0.0), none)

        assertEquals(1, determinacy.rank)
        assertEquals(MeasurementRecommendation.CornerAngle(0), determinacy.recommendation)
    }

    @Test
    fun `given measured closing corner and no helpful diagonal then the first free inner corner is recommended`() {
        val measured = CornerInput.Measured(TurnDirection.LEFT, Angle.RIGHT)
        val input = ShapeInput(List(4) { 1000L }, listOf(measured, left, left, left))

        val determinacy = analyzer.analyze(input, List(4) { 1000.0 }, List(4) { 0.0 }, none)

        assertEquals(2, determinacy.rank)
        assertEquals(MeasurementRecommendation.CornerAngle(1), determinacy.recommendation)
    }

    @Test
    fun `given invalid arguments then they are rejected`() {
        val input = rightAngled(4000, 3000, 4000, 3000)

        assertThrows(
            IllegalArgumentException::class.java,
        ) { analyzer.analyzeConstraints(input, rectangleTurns.drop(1), none) }
        assertThrows(IllegalArgumentException::class.java) {
            analyzer.analyzeConstraints(input, listOf(0.0, Double.NaN, 0.0, 0.0), none)
        }
        assertThrows(
            IllegalArgumentException::class.java,
        ) { analyzer.analyze(input, rectangle.drop(1), rectangleTurns, none) }
        assertThrows(IllegalArgumentException::class.java) {
            analyzer.analyze(input, listOf(4000.0, 0.0, 4000.0, 3000.0), rectangleTurns, none)
        }
        assertThrows(IllegalArgumentException::class.java) {
            analyzer.analyze(input, listOf(4000.0, Double.POSITIVE_INFINITY, 4000.0, 3000.0), rectangleTurns, none)
        }
    }

    @Test
    fun `given cancelled signal then every analysis stops`() {
        val cancelled = CancellationSignal { throw CancellationException("cancelled") }
        val input = rightAngled(4000, 3000, 4000, 3000, diagonals = listOf(DiagonalInput(0, 2, 5000)))

        assertThrows(
            CancellationException::class.java,
        ) { analyzer.analyzeConstraints(input, rectangleTurns, cancelled) }
        assertThrows(
            CancellationException::class.java,
        ) { analyzer.analyze(input, rectangle, rectangleTurns, cancelled) }
    }

    @Test
    fun `given target turns from the traverse then the routing matches DOMAIN examples bit for bit on repetition`() {
        val input =
            rightAngled(
                5000,
                3000,
                3000,
                3000,
                diagonals = listOf(DiagonalInput(0, 2, 4899), DiagonalInput(1, 3, 4899)),
            )
        val targets = traverse.targets(input).targetTurns.map { it.toRadians() }

        val first = DefaultDeterminacyAnalyzer().analyzeConstraints(input, targets, none)
        val second = DefaultDeterminacyAnalyzer().analyzeConstraints(input, targets, none)

        assertEquals(first, second)
        assertEquals(Triple(3, 4, 3), Triple(first.freeVariables, first.conditionCount, first.independentConditions))
    }

    private fun constraints(input: ShapeInput): ConstraintAnalysis =
        analyzer.analyzeConstraints(input, traverse.targets(input).targetTurns.map { it.toRadians() }, none)

    private companion object {
        const val HALF_PI = StrictMath.PI / 2.0
        val left = CornerInput.DeclaredRight(TurnDirection.LEFT)
    }
}
