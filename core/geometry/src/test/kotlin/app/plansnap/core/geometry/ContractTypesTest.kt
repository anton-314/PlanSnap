package app.plansnap.core.geometry

import app.plansnap.core.common.units.Angle
import app.plansnap.core.common.units.TurnDirection
import app.plansnap.core.geometry.adjustment.AdjustedShape
import app.plansnap.core.geometry.bands.BandInput
import app.plansnap.core.geometry.bands.CornerBand
import app.plansnap.core.geometry.bands.CornerJoin
import app.plansnap.core.geometry.determinacy.ConstraintAnalysis
import app.plansnap.core.geometry.determinacy.Determinacy
import app.plansnap.core.geometry.determinacy.MeasurementRecommendation
import app.plansnap.core.geometry.primitives.GeometryTolerances
import app.plansnap.core.geometry.primitives.Polygon2
import app.plansnap.core.geometry.primitives.Vec2
import app.plansnap.core.geometry.primitives.Winding
import app.plansnap.core.geometry.solver.ShapeSolution
import app.plansnap.core.geometry.solver.SolverStart
import app.plansnap.core.geometry.traverse.ClosureError
import app.plansnap.core.geometry.traverse.CornerInput
import app.plansnap.core.geometry.traverse.DiagonalInput
import app.plansnap.core.geometry.traverse.ShapeInput
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ContractTypesTest {
    private val left = CornerInput.DeclaredRight(TurnDirection.LEFT)
    private val triangle = Polygon2(listOf(Vec2.ZERO, Vec2(1000.0, 0.0), Vec2(0.0, 1000.0)))

    private fun corners(n: Int): List<CornerInput> = listOf<CornerInput>(CornerInput.Free) + List(n - 1) { left }

    private fun assertRejected(block: () -> Unit) {
        assertThrows(IllegalArgumentException::class.java) { block() }
    }

    @Test
    fun `given valid outline when input is created then it is accepted`() {
        val input = ShapeInput(listOf(3000L, 3000L, 3000L, 5000L), corners(4), listOf(DiagonalInput(0, 2, 4000L)))
        assertEquals(4, input.wallCount)
        assertEquals(input, ShapeInput(input.lengthsMm, input.corners, input.diagonals))
        assertEquals(input.hashCode(), ShapeInput(input.lengthsMm, input.corners, input.diagonals).hashCode())
        assertNotEquals(input, ShapeInput(input.lengthsMm, input.corners))
        assertTrue(input.toString().contains("ShapeInput"))
    }

    @Test
    fun `given wall count limits when input is created then only valid counts pass`() {
        val max = GeometryTolerances.MAX_WALLS_PER_ROOM
        assertRejected { ShapeInput(List(2) { 1000L }, corners(2)) }
        assertDoesNotThrow { ShapeInput(List(3) { 1000L }, corners(3)) }
        assertDoesNotThrow { ShapeInput(List(max) { 1000L }, corners(max)) }
        assertRejected { ShapeInput(List(max + 1) { 1000L }, corners(max + 1)) }
    }

    @Test
    fun `given wall length limits when input is created then only valid lengths pass`() {
        listOf(
            0L,
            -1L,
            GeometryTolerances.MIN_WALL_MM - 1,
            GeometryTolerances.MAX_WALL_MM + 1,
            Long.MAX_VALUE,
        ).forEach {
            assertRejected { ShapeInput(listOf(it, 1000L, 1000L), corners(3)) }
        }
        assertDoesNotThrow {
            ShapeInput(listOf(GeometryTolerances.MIN_WALL_MM, GeometryTolerances.MAX_WALL_MM, 1000L), corners(3))
        }
    }

    @Test
    fun `given free corner after closing corner when input is created then it is rejected`() {
        assertRejected { ShapeInput(List(3) { 1000L }, listOf(left, CornerInput.Free, left)) }
        assertDoesNotThrow { ShapeInput(List(3) { 1000L }, listOf(left, left, left)) }
    }

    @Test
    fun `given structural mismatch when input is created then it is rejected`() {
        assertRejected { ShapeInput(List(3) { 1000L }, corners(4)) }
        assertRejected { ShapeInput(List(3) { 1000L }, corners(3), listOf(DiagonalInput(0, 3, 1000L))) }
        assertRejected { ShapeInput(List(3) { 1000L }, corners(3), listOf(DiagonalInput(3, 0, 1000L))) }
    }

    @Test
    fun `given mutable sources when input is created then later changes do not leak`() {
        val lengths = mutableListOf(1000L, 1000L, 1000L)
        val input = ShapeInput(lengths, corners(3))
        lengths[0] = 2000L
        assertEquals(1000L, input.lengthsMm[0])
    }

    @Test
    fun `given invalid diagonal when created then it is rejected`() {
        assertRejected { DiagonalInput(-1, 1, 1000L) }
        assertRejected { DiagonalInput(1, -1, 1000L) }
        assertRejected { DiagonalInput(1, 1, 1000L) }
        assertRejected { DiagonalInput(0, 1, 0L) }
        assertRejected { DiagonalInput(0, 1, -1L) }
    }

    @Test
    fun `given measured corner limits when created then only valid angles pass`() {
        assertRejected { CornerInput.Measured(TurnDirection.LEFT, Angle.ZERO) }
        assertRejected { CornerInput.Measured(TurnDirection.LEFT, Angle(180_001)) }
        assertDoesNotThrow { CornerInput.Measured(TurnDirection.RIGHT, Angle.STRAIGHT) }
    }

    @Test
    fun `given closure components when error is read then maximum is returned`() {
        assertEquals(3.0, ClosureError(1.0, 3.0, 2.0).errorMm, 0.0)
        assertEquals(3.0, ClosureError(3.0, 1.0, 2.0).errorMm, 0.0)
        assertEquals(3.0, ClosureError(1.0, 2.0, 3.0).errorMm, 0.0)
    }

    @Test
    fun `given invalid closure component when created then it is rejected`() {
        listOf(-1.0, Double.NaN, Double.POSITIVE_INFINITY).forEach {
            assertRejected { ClosureError(it, 0.0, 0.0) }
            assertRejected { ClosureError(0.0, it, 0.0) }
            assertRejected { ClosureError(0.0, 0.0, it) }
        }
    }

    @Test
    fun `given invalid solution when created then it is rejected`() {
        val turns = listOf(1.0, 1.0, 1.0)
        assertDoesNotThrow { ShapeSolution(turns, triangle, 0.0, SolverStart.TARGET_TURNS, true) }
        assertRejected { ShapeSolution(listOf(1.0), triangle, 0.0, SolverStart.TARGET_TURNS, true) }
        assertRejected { ShapeSolution(listOf(1.0, Double.NaN, 1.0), triangle, 0.0, SolverStart.MEAN_TURNS, true) }
        listOf(-1.0, Double.NaN, Double.POSITIVE_INFINITY).forEach {
            assertRejected { ShapeSolution(turns, triangle, it, SolverStart.CHORD_POLYGON, false) }
        }
    }

    @Test
    fun `given quadrilateral without extra measures when determinacy is read then one is missing`() {
        val determinacy = Determinacy(3, 2, MeasurementRecommendation.DiagonalBetween(0, 2))
        assertEquals(1, determinacy.missing)
    }

    @Test
    fun `given invalid determinacy when created then it is rejected`() {
        assertRejected { Determinacy(-1, 0, null) }
        assertRejected { Determinacy(2, 3, null) }
        assertRejected { Determinacy(2, -1, null) }
        assertRejected { Determinacy(2, 2, MeasurementRecommendation.CornerAngle(1)) }
        assertEquals(0, Determinacy(2, 2, null).missing)
    }

    @Test
    fun `given constraint counts when analyzed then overdetermination follows domain section 5_4`() {
        assertEquals(false, ConstraintAnalysis(3, 2, 2).isOverdetermined)
        assertEquals(false, ConstraintAnalysis(3, 3, 3).isOverdetermined)
        assertEquals(true, ConstraintAnalysis(3, 4, 3).isOverdetermined)
        assertEquals(true, ConstraintAnalysis(0, 2, 0).isOverdetermined)
        assertEquals(true, ConstraintAnalysis(0, 3, 0).isOverdetermined)
    }

    @Test
    fun `given invalid constraint analysis when created then it is rejected`() {
        assertRejected { ConstraintAnalysis(-1, 2, 0) }
        assertRejected { ConstraintAnalysis(3, 1, 1) }
        assertRejected { ConstraintAnalysis(3, 2, 3) }
        assertRejected { ConstraintAnalysis(1, 2, 2) }
        assertRejected { ConstraintAnalysis(3, 2, -1) }
    }

    @Test
    fun `given invalid recommendation when created then it is rejected`() {
        assertRejected { MeasurementRecommendation.DiagonalBetween(2, 1) }
        assertRejected { MeasurementRecommendation.DiagonalBetween(1, 1) }
        assertRejected { MeasurementRecommendation.DiagonalBetween(-1, 1) }
        assertRejected { MeasurementRecommendation.CornerAngle(-1) }
    }

    @Test
    fun `given invalid adjusted shape when created then it is rejected`() {
        val ok = listOf(1.0, 1.0, 1.0)
        assertDoesNotThrow { AdjustedShape(ok, ok, listOf(1.0), 0.0, null) }
        assertRejected { AdjustedShape(ok, listOf(1.0), emptyList(), 0.0, null) }
        assertRejected { AdjustedShape(listOf(1.0, 0.0, 1.0), ok, emptyList(), 0.0, null) }
        assertRejected { AdjustedShape(listOf(1.0, Double.NaN, 1.0), ok, emptyList(), 0.0, null) }
        assertRejected { AdjustedShape(ok, listOf(1.0, Double.NaN, 1.0), emptyList(), 0.0, null) }
        assertRejected { AdjustedShape(ok, ok, listOf(0.0), 0.0, null) }
        assertRejected { AdjustedShape(ok, ok, emptyList(), -1.0, null) }
        assertRejected { AdjustedShape(ok, ok, emptyList(), Double.NaN, null) }
    }

    @Test
    fun `given thickness limits when band input is created then only valid values pass`() {
        val winding = Winding.COUNTER_CLOCKWISE
        val valid = BandInput(triangle, winding, listOf(0L, 120L, GeometryTolerances.MAX_THICKNESS_MM))
        assertEquals(valid, BandInput(triangle, winding, valid.thicknessesMm))
        assertEquals(valid.hashCode(), BandInput(triangle, winding, valid.thicknessesMm).hashCode())
        assertTrue(valid.toString().contains("BandInput"))
        assertRejected { BandInput(triangle, winding, listOf(0L, 0L)) }
        assertRejected { BandInput(triangle, winding, listOf(-1L, 0L, 0L)) }
        assertRejected { BandInput(triangle, winding, listOf(GeometryTolerances.MAX_THICKNESS_MM + 1, 0L, 0L)) }
    }

    @Test
    fun `given corner join when created then fill exists exactly for bevel`() {
        assertDoesNotThrow { CornerBand(0, CornerJoin.BEVEL, triangle) }
        assertDoesNotThrow { CornerBand(0, CornerJoin.MITER, null) }
        assertRejected { CornerBand(0, CornerJoin.BEVEL, null) }
        assertRejected { CornerBand(0, CornerJoin.CONCAVE, triangle) }
        val quad = Polygon2(triangle.vertices + Vec2(-1.0, -1.0))
        assertRejected { CornerBand(0, CornerJoin.BEVEL, quad) }
    }
}
