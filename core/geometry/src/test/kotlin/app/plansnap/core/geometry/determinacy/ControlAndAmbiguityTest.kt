package app.plansnap.core.geometry.determinacy

import app.plansnap.core.common.units.Angle
import app.plansnap.core.common.units.TurnDirection
import app.plansnap.core.geometry.adjustment.AdjustedShape
import app.plansnap.core.geometry.adjustment.AdjustmentMode
import app.plansnap.core.geometry.adjustment.AdjustmentOutcome
import app.plansnap.core.geometry.adjustment.DefaultClosureAdjuster
import app.plansnap.core.geometry.adjustment.ObservationRef
import app.plansnap.core.geometry.cancellation.CancellationSignal
import app.plansnap.core.geometry.primitives.GeometryTolerances
import app.plansnap.core.geometry.primitives.Polygon2
import app.plansnap.core.geometry.primitives.Vec2
import app.plansnap.core.geometry.solver.ShapeSolution
import app.plansnap.core.geometry.solver.SolverStart
import app.plansnap.core.geometry.statistics.ChiSquareLimits
import app.plansnap.core.geometry.traverse.CornerInput
import app.plansnap.core.geometry.traverse.ShapeInput
import app.plansnap.core.geometry.traverse.rightAngled
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class ControlAndAmbiguityTest {
    private val analyzer = DefaultDeterminacyAnalyzer()
    private val none = CancellationSignal.NONE
    private val square = Polygon2(listOf(Vec2(0.0, 0.0), Vec2(4000.0, 0.0), Vec2(4000.0, 4000.0), Vec2(0.0, 4000.0)))
    private val squareInput = rightAngled(4000, 4000, 4000, 4000)

    @Test
    fun `given redundancy zero then no single measurement is listed`() {
        val control = analyzer.analyzeControl(rectangleInput, rectangleShape, 0, LIMIT_MM, none)

        assertEquals(MeasurementControl(0, emptyList()), control)
    }

    @Test
    fun `given measurement error path with declared corners then every listed noncentrality is below the threshold`() {
        val control = analyzer.analyzeControl(rectangleInput, rectangleShape, REDUNDANCY, LIMIT_MM, none)
        val threshold = ChiSquareLimits.controlThreshold(REDUNDANCY)

        assertEquals(REDUNDANCY, control.redundancy)
        assertTrue(control.uncontrolled.all { it.noncentrality < threshold })
        // Declared corners are observations in this mode, so they are never recommended as a new angle.
        assertTrue(control.uncontrolled.none { it.recommendation is MeasurementRecommendation.CornerAngle })
    }

    @Test
    fun `given large limit then every measurement is controllable`() {
        val control = analyzer.analyzeControl(rectangleInput, rectangleShape, REDUNDANCY, LARGE_LIMIT_MM, none)

        assertEquals(emptyList<UncontrolledMeasurement>(), control.uncontrolled)
    }

    @Test
    fun `given small limit then the noncentrality follows the formula with the mm equivalent of angles`() {
        val limit = 1.0
        val control = analyzer.analyzeControl(rectangleInput, rectangleShape, REDUNDANCY, limit, none)
        val perimeter = rectangleInput.lengthsMm.sum().toDouble()
        val sigmaAngleMm =
            StrictMath.toRadians(GeometryTolerances.SIGMA_RIGHT_ANGLE_DEG) * perimeter / (2.0 * StrictMath.PI)

        assertEquals(rectangleShape.redundancies.size, control.uncontrolled.size)
        control.uncontrolled.forEach { entry ->
            val redundancy = rectangleShape.redundancies.first { it.observation == entry.observation }.redundancy
            val angle = entry.observation is ObservationRef.CornerAngle
            val sigma = if (angle) sigmaAngleMm else GeometryTolerances.SIGMA_LENGTH_MM
            val expected =
                (GeometryTolerances.CONTROL_ERROR_FACTOR * limit).let { it * it } * redundancy / (sigma * sigma)
            assertEquals(expected, entry.noncentrality, expected * 1e-12)
        }
    }

    @Test
    fun `given invalid control arguments then they are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            analyzer.analyzeControl(rectangleInput, rectangleShape, -1, LIMIT_MM, none)
        }
        assertThrows(IllegalArgumentException::class.java) {
            analyzer.analyzeControl(rectangleInput, rectangleShape, 1, 0.0, none)
        }
        assertThrows(IllegalArgumentException::class.java) {
            analyzer.analyzeControl(rightAngled(4000, 3000, 4000), rectangleShape, 1, LIMIT_MM, none)
        }
    }

    @Test
    fun `given no alternative then there is no ambiguity`() {
        assertNull(analyzer.analyzeAmbiguity(squareInput, square, emptyList(), LIMIT_MM, none))
    }

    @Test
    fun `given alternative exactly at the limit then there is no ambiguity`() {
        val moved = Polygon2(square.vertices.mapIndexed { i, v -> if (i == 2) v + Vec2(100.0, 0.0) else v })
        val distance = shapeDistanceMm(square, moved)

        assertNull(analyzer.analyzeAmbiguity(squareInput, square, listOf(solution(moved)), distance, none))
        assertTrue(
            analyzer.analyzeAmbiguity(squareInput, square, listOf(solution(moved)), distance - 1e-6, none) != null,
        )
    }

    @Test
    fun `given two equally far alternatives then the first in selection order is reported`() {
        val far = Polygon2(square.vertices.mapIndexed { i, v -> if (i == 2) v + Vec2(900.0, 0.0) else v })
        val shifted = Polygon2(far.vertices.map { it + Vec2(5.0, -7.0) })

        val ambiguity =
            analyzer.analyzeAmbiguity(
                squareInput,
                square,
                listOf(solution(far), solution(shifted)),
                LIMIT_MM,
                none,
            )

        assertEquals(far, ambiguity?.alternative)
        // V_0 keeps its turn, so the diagonal through the moved corner separates both shapes.
        assertEquals(MeasurementRecommendation.DiagonalBetween(0, 2), ambiguity?.recommendation)
    }

    @Test
    fun `given measured closing corner then the diagonal with the largest difference is recommended`() {
        val input =
            ShapeInput(
                List(4) { 4000L },
                listOf(measured) + List(3) { CornerInput.DeclaredRight(TurnDirection.LEFT) },
            )
        val far = Polygon2(square.vertices.mapIndexed { i, v -> if (i == 3) v + Vec2(0.0, 900.0) else v })

        val ambiguity = analyzer.analyzeAmbiguity(input, square, listOf(solution(far)), LIMIT_MM, none)

        assertEquals(MeasurementRecommendation.DiagonalBetween(1, 3), ambiguity?.recommendation)
    }

    @Test
    fun `given triangle with measured closing corner then no measurement separates the shapes`() {
        val input = ShapeInput(listOf(4000L, 4000L, 5657L), listOf(measured, measured, measured))
        val triangle = Polygon2(listOf(Vec2(0.0, 0.0), Vec2(4000.0, 0.0), Vec2(4000.0, 4000.0)))
        val other = Polygon2(listOf(Vec2(0.0, 0.0), Vec2(4000.0, 0.0), Vec2(5000.0, 3000.0)))

        val ambiguity = analyzer.analyzeAmbiguity(input, triangle, listOf(solution(other)), LIMIT_MM, none)

        assertNull(ambiguity?.recommendation)
        assertEquals(shapeDistanceMm(triangle, other), ambiguity!!.distanceMm, 1e-9)
    }

    @Test
    fun `given invalid ambiguity arguments then they are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            analyzer.analyzeAmbiguity(squareInput, square, emptyList(), 0.0, none)
        }
        val triangle = Polygon2(square.vertices.take(3))
        assertThrows(IllegalArgumentException::class.java) {
            analyzer.analyzeAmbiguity(squareInput, triangle, emptyList(), LIMIT_MM, none)
        }
    }

    @Test
    fun `given determinacy sources then trigonometry and roots only use StrictMath`() {
        val sources = File(SOURCES).listFiles { file -> file.extension == "kt" }.orEmpty()
        assertTrue(sources.size >= MIN_SOURCES, "no sources found in ${File(SOURCES).absolutePath}")
        sources.forEach { file ->
            val match = FORBIDDEN_MATH.find(file.readText())
            assertNull(match, "non-strict math in ${file.name}: ${match?.value}")
        }
    }

    private fun solution(outline: Polygon2): ShapeSolution =
        ShapeSolution(List(outline.vertices.size) { 0.0 }, outline, 0.0, SolverStart.TARGET_TURNS, true)

    private companion object {
        const val LIMIT_MM = 141.5
        const val LARGE_LIMIT_MM = 1.0e6
        const val REDUNDANCY = 3
        const val MIN_SOURCES = 6
        const val SOURCES = "src/main/kotlin/app/plansnap/core/geometry/determinacy"
        val measured = CornerInput.Measured(TurnDirection.LEFT, Angle.RIGHT)

        /** Rectangle with a gap of 150 mm, all corners declared: measurement error path with `r = 2`. */
        val rectangleInput: ShapeInput = rightAngled(4150, 3000, 4000, 3000)

        val rectangleShape: AdjustedShape =
            DefaultClosureAdjuster()
                .adjust(
                    rectangleInput,
                    AdjustmentMode.MEASUREMENT_ERROR,
                    listOf(StrictMath.PI / 2.0, StrictMath.PI / 2.0, StrictMath.PI / 2.0, StrictMath.PI / 2.0),
                    CancellationSignal.NONE,
                ).let { (it as AdjustmentOutcome.Adjusted).shape }

        /** Non-strict trigonometry, roots or powers from Kotlin or `java.lang.Math`. */
        val FORBIDDEN_MATH =
            Regex(
                """kotlin\.math\.(\*|sin|cos|tan|asin|acos|atan|atan2|sqrt|hypot|exp|ln|log|pow|cbrt)\b|""" +
                    """(?<!Strict)\bMath\.(sin|cos|tan|asin|acos|atan|atan2|sqrt|hypot|exp|log|pow|cbrt)\(""",
            )
    }
}
