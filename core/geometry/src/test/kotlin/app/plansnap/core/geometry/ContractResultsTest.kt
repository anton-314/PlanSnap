package app.plansnap.core.geometry

import app.plansnap.core.common.units.Angle
import app.plansnap.core.geometry.adjustment.AdjustedShape
import app.plansnap.core.geometry.adjustment.AdjustmentOutcome
import app.plansnap.core.geometry.adjustment.ObservationRef
import app.plansnap.core.geometry.bands.BandLayout
import app.plansnap.core.geometry.bands.CornerBand
import app.plansnap.core.geometry.bands.CornerJoin
import app.plansnap.core.geometry.bands.WallBand
import app.plansnap.core.geometry.polygon.PolygonAnalyzer
import app.plansnap.core.geometry.primitives.GeometryTolerances
import app.plansnap.core.geometry.primitives.Polygon2
import app.plansnap.core.geometry.primitives.Vec2
import app.plansnap.core.geometry.primitives.Winding
import app.plansnap.core.geometry.solver.ShapeSolution
import app.plansnap.core.geometry.solver.SolverOutcome
import app.plansnap.core.geometry.solver.SolverStart
import app.plansnap.core.geometry.traverse.ClosureClass
import app.plansnap.core.geometry.traverse.TurnTargets
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class ContractResultsTest {
    private val triangle = Polygon2(listOf(Vec2.ZERO, Vec2(1000.0, 0.0), Vec2(0.0, 1000.0)))

    /** Records the threshold so the contract default of [PolygonAnalyzer.isSimple] can be verified. */
    private class RecordingAnalyzer : PolygonAnalyzer {
        var receivedMinFeature: Double = Double.NaN

        override fun signedAreaMm2(polygon: Polygon2): Double = 0.0

        override fun areaMm2(polygon: Polygon2): Double = 0.0

        override fun perimeterMm(polygon: Polygon2): Double = 0.0

        override fun winding(polygon: Polygon2): Winding? = null

        override fun isSimple(
            polygon: Polygon2,
            minFeatureMm: Double,
        ): Boolean {
            receivedMinFeature = minFeatureMm
            return true
        }
    }

    @Test
    fun `given analyzer when simplicity uses default then central min feature applies`() {
        val analyzer = RecordingAnalyzer()
        analyzer.isSimple(triangle)
        assertEquals(GeometryTolerances.MIN_FEATURE_MM, analyzer.receivedMinFeature, 0.0)
    }

    @Test
    fun `given turn targets when read then values are kept`() {
        val targets = TurnTargets(Winding.CLOCKWISE, listOf(Angle.RIGHT, -Angle.RIGHT))
        assertEquals(Winding.CLOCKWISE, targets.winding)
        assertEquals(listOf(Angle.RIGHT, -Angle.RIGHT), targets.targetTurns)
    }

    @Test
    fun `given solver outcomes when compared then payload decides`() {
        val solution = ShapeSolution(listOf(1.0, 1.0, 1.0), triangle, 0.5, SolverStart.CHORD_POLYGON, true)
        assertEquals(solution, SolverOutcome.Solved(solution).solution)
        assertNotEquals(SolverOutcome.Solved(solution), SolverOutcome.NoSimpleShape)
    }

    @Test
    fun `given adjustment outcomes when compared then payload decides`() {
        val worst = ObservationRef.DiagonalLength(0)
        val shape = AdjustedShape(listOf(1.0, 1.0, 1.0), listOf(1.0, 1.0, 1.0), listOf(2.0), 0.25, worst)
        assertEquals(shape, AdjustmentOutcome.Adjusted(shape).shape)
        assertEquals(worst, shape.worstObservation)
        assertNotEquals(AdjustmentOutcome.Adjusted(shape), AdjustmentOutcome.NotConverged)
    }

    @Test
    fun `given observation references when read then index is kept`() {
        assertEquals(2, ObservationRef.WallLength(2).index)
        assertEquals(3, ObservationRef.CornerAngle(3).index)
        assertEquals(4, ObservationRef.DiagonalLength(4).index)
        val wall: Any = ObservationRef.WallLength(1)
        val corner: Any = ObservationRef.CornerAngle(1)
        assertNotEquals(wall, corner)
    }

    @Test
    fun `given band layout when read then bands and corners are kept`() {
        val line = WallBand.Line(0, Vec2.ZERO, Vec2(1000.0, 0.0))
        val filled = WallBand.Filled(1, triangle)
        val corner = CornerBand(1, CornerJoin.BEVEL, triangle)
        val layout = BandLayout(listOf(line, filled), listOf(corner))
        assertEquals(listOf(0, 1), layout.bands.map { it.wallIndex })
        assertEquals(Vec2(1000.0, 0.0), line.end)
        assertEquals(Vec2.ZERO, line.start)
        assertEquals(triangle, filled.shape)
        assertEquals(listOf(corner), layout.corners)
    }

    @Test
    fun `given contract enums when read then value names are stable`() {
        assertEquals(
            listOf("TARGET_TURNS", "MEAN_TURNS", "CHORD_POLYGON"),
            SolverStart.entries.map { it.name },
        )
        assertEquals(listOf("EXACT", "MEASUREMENT_ERROR", "SHAPE_DEVIATION"), ClosureClass.entries.map { it.name })
        assertEquals(listOf("COLLINEAR", "MITER", "BEVEL", "CONCAVE"), CornerJoin.entries.map { it.name })
    }
}
