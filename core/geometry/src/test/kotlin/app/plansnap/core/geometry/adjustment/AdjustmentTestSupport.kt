package app.plansnap.core.geometry.adjustment

import app.plansnap.core.geometry.cancellation.CancellationSignal
import app.plansnap.core.geometry.polygon.DefaultPolygonAnalyzer
import app.plansnap.core.geometry.primitives.GeometryTolerances
import app.plansnap.core.geometry.primitives.Polygon2
import app.plansnap.core.geometry.traverse.CornerInput
import app.plansnap.core.geometry.traverse.DefaultTraverse
import app.plansnap.core.geometry.traverse.ShapeInput
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail

/** Shared helpers of the adjustment tests. */
internal object AdjustmentTestSupport {
    val adjuster = DefaultClosureAdjuster()
    val traverse = DefaultTraverse()
    val analyzer = DefaultPolygonAnalyzer()

    private const val STRAIGHT_MILLI_DEGREES = 180_000

    /** Target turns `τ̂` with the free closing corner replaced by `τ_0,derived` (DOMAIN section 5.5, step B2). */
    fun targetStart(input: ShapeInput): List<Double> {
        val targets = traverse.targets(input)
        val turns = targets.targetTurns.map { it.toRadians() }.toMutableList()
        if (input.corners.first() == CornerInput.Free) {
            turns[0] = 2.0 * StrictMath.PI * targets.winding.sign - turns.drop(1).sum()
        }
        return turns
    }

    /** Runs the adjustment and fails if it does not converge. */
    fun adjustOrFail(
        input: ShapeInput,
        mode: AdjustmentMode,
        start: List<Double>,
    ): AdjustedShape =
        when (val outcome = adjuster.adjust(input, mode, start, CancellationSignal.NONE)) {
            is AdjustmentOutcome.Adjusted -> outcome.shape
            AdjustmentOutcome.NotConverged -> fail("Adjustment did not converge for $input")
        }

    /** Corner points `V_0 … V_n` of the walked chain. */
    fun walk(
        lengths: List<Double>,
        turns: List<Double>,
    ) = traverse.walk(lengths, turns)

    /** Outline `V_0 … V_{n-1}` of the walked chain. */
    fun outline(
        lengths: List<Double>,
        turns: List<Double>,
    ): Polygon2 = Polygon2(walk(lengths, turns).dropLast(1))

    /** Outline of an adjusted shape. */
    fun outline(shape: AdjustedShape): Polygon2 = outline(shape.adjustedLengthsMm, shape.adjustedTurnsRad)

    /** Raw lengths of [input] in mm. */
    fun rawLengths(input: ShapeInput): List<Double> = input.lengthsMm.map { it.toDouble() }

    /** Perimeter `U` of the raw lengths. */
    fun perimeter(input: ShapeInput): Double = input.lengthsMm.sum().toDouble()

    /** Whether the adjusted outline is simple with `MIN_FEATURE_MM` (DOMAIN section 7). */
    fun isSimple(shape: AdjustedShape): Boolean = analyzer.isSimple(outline(shape))

    /** Corners whose entered direction is violated by [turns], with `ε = TURN_DIRECTION_TOLERANCE_DEG` (DOMAIN §3). */
    fun violatedCorners(
        input: ShapeInput,
        turns: List<Double>,
    ): List<Int> {
        val epsilon = StrictMath.toRadians(GeometryTolerances.TURN_DIRECTION_TOLERANCE_DEG)
        return input.corners.indices.filter { i ->
            val sign =
                when (val corner = input.corners[i]) {
                    is CornerInput.DeclaredRight -> {
                        corner.direction.sign
                    }

                    is CornerInput.Measured -> {
                        if (corner.angle.milliDegrees == STRAIGHT_MILLI_DEGREES) 0 else corner.direction.sign
                    }

                    CornerInput.Free -> {
                        0
                    }
                }
            sign != 0 && sign * wrapAngle(turns[i]) < -epsilon
        }
    }

    /** Euclidean closure gap `|V_n − V_0|` of the adjusted outline in mm. */
    fun gapMm(shape: AdjustedShape): Double = walk(shape.adjustedLengthsMm, shape.adjustedTurnsRad).last().length()

    /** Asserts `|actual − expected| ≤ relative · |expected|`. */
    fun assertRelative(
        expected: Double,
        actual: Double,
        relative: Double,
        what: String,
    ) {
        assertTrue(
            StrictMath.abs(actual - expected) <= relative * StrictMath.abs(expected),
            "$what: expected $expected ± ${relative * 100} %, got $actual",
        )
    }

    /** Turns in degrees to radians. */
    fun radians(degrees: List<Double>): List<Double> = degrees.map(StrictMath::toRadians)
}
