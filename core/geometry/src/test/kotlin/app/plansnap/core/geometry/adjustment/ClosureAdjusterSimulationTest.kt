package app.plansnap.core.geometry.adjustment

import app.plansnap.core.common.units.Angle
import app.plansnap.core.common.units.TurnDirection
import app.plansnap.core.geometry.adjustment.AdjustmentTestSupport.adjustOrFail
import app.plansnap.core.geometry.adjustment.AdjustmentTestSupport.perimeter
import app.plansnap.core.geometry.adjustment.AdjustmentTestSupport.targetStart
import app.plansnap.core.geometry.adjustment.AdjustmentTestSupport.traverse
import app.plansnap.core.geometry.primitives.ClosureTolerance
import app.plansnap.core.geometry.statistics.ChiSquareLevel
import app.plansnap.core.geometry.statistics.ChiSquareLimits
import app.plansnap.core.geometry.traverse.ClosureClass
import app.plansnap.core.geometry.traverse.CornerInput
import app.plansnap.core.geometry.traverse.ShapeInput
import app.plansnap.testing.fixtures.random.SeededRandom
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Locale

/**
 * Recomputes the statistical limits of ADR 0018 on the production adjuster, with the scenario definitions of the
 * addendum CR-W1-01 to ADR 0018.
 *
 * The rooms are the true shapes of the NH3 cases in `OverdeterminedCases` (rectangle, L, U, cross) with every corner
 * measured as 90°. Each sample adds normal noise with the σ of DOMAIN section 6 and rounds to whole millimeters and
 * millidegrees. `T` is the default limit. Every sample runs through the routing of DOMAIN section 4: a room with
 * `e ≤ T` takes the measurement-error path with a simple confirmation and no χ² test, only a room with `e > T` reaches
 * the adjustment of branch B (DOMAIN section 5.5) with `r = 3`. The full scenario set of ADR 0018 (13 scenarios, 77
 * wall cases) is not part of the repository, so this is a seeded subset with the four named rooms.
 */
class ClosureAdjusterSimulationTest {
    private val tolerance = ClosureTolerance()

    @Test
    fun `given consistent rooms with noise when evaluated then conflict and confirmation rates match ADR 0018`() {
        SCENARIOS.forEach { scenario ->
            val random = SeededRandom(SEED + scenario.name.hashCode())
            val counts = IntArray(Band.entries.size)
            repeat(NOISE_SAMPLES) { counts[classify(noisyInput(scenario, random, wall = -1, errorMm = 0.0)).ordinal]++ }
            val rates = counts.map { 100.0 * it / NOISE_SAMPLES }
            report(
                "%s: measurement-error path %.2f %%, false conflict %.2f %%, explicit %.2f %%, large simple %.2f %%",
                scenario.name,
                rates[Band.MEASUREMENT_ERROR.ordinal],
                rates[Band.CONFLICT.ordinal],
                rates[Band.EXPLICIT.ordinal],
                rates[Band.LARGE_SIMPLE.ordinal],
            )
            // q² follows χ²(3) for consistent rooms, and S > T holds for nearly every q² above χ²(3; 0.99).
            assertTrue(rates[Band.CONFLICT.ordinal] in FALSE_CONFLICT_PERCENT_RANGE, scenario.name)
            assertNear(scenario.explicitPercent, rates[Band.EXPLICIT.ordinal], "${scenario.name} explicit")
            assertNear(scenario.largeSimplePercent, rates[Band.LARGE_SIMPLE.ordinal], "${scenario.name} large simple")
        }
    }

    @Test
    fun `given one wall wrong by three times the limit when evaluated then the conflict rule detects it`() {
        var detectedWalls = 0
        var walls = 0
        SCENARIOS.forEach { scenario ->
            val limit = tolerance.limitMm(scenario.lengths.sum().toDouble())
            val random = SeededRandom(SEED + 1 + scenario.name.hashCode())
            val rates =
                scenario.lengths.indices.map { wall ->
                    val hits =
                        (0 until WALL_SAMPLES).count {
                            classify(noisyInput(scenario, random, wall, ERROR_FACTOR * limit)) == Band.CONFLICT
                        }
                    100.0 * hits / WALL_SAMPLES
                }
            walls += rates.size
            detectedWalls += rates.count { it >= DETECTION_PERCENT }
            report(
                "%s: detection of 3 T per wall %s",
                scenario.name,
                rates.joinToString { "%.1f".format(Locale.ROOT, it) },
            )
        }
        report("walls detected with at least 95 %%: %d of %d", detectedWalls, walls)
        // Every wall of these rooms is controllable, λ(3T) ≥ λ₀(3), so each one must reach the power of 95 %.
        assertEquals(walls, detectedWalls)
    }

    @Test
    fun `given one wall wrong without noise when evaluated then the undetected band matches ADR 0018`() {
        SCENARIOS.forEach { scenario ->
            val limit = tolerance.limitMm(scenario.lengths.sum().toDouble())
            val band =
                scenario.lengths.indices.maxOf { wall ->
                    maxOf(detectionThreshold(scenario, wall, limit, 1), detectionThreshold(scenario, wall, limit, -1))
                }
            report("%s: undetected band up to %.3f T", scenario.name, band)
            assertEquals(scenario.undetectedBand, band, BAND_TOLERANCE, scenario.name)
        }
    }

    /**
     * Smallest error of [wall] in multiples of [limit], with the given [sign] and rounded to whole millimeters, from
     * which the room without noise ends in a conflict. Bisection on `[0, 3 T]`.
     */
    private fun detectionThreshold(
        scenario: Scenario,
        wall: Int,
        limit: Double,
        sign: Int,
    ): Double {
        var low = 0.0
        var high = ERROR_FACTOR
        repeat(BISECTION_STEPS) {
            val middle = (low + high) / 2.0
            val input = exactInput(scenario, wall, sign * Math.round(middle * limit))
            if (classify(input) == Band.CONFLICT) high = middle else low = middle
        }
        return high
    }

    /** Status band of [input] after DOMAIN section 4 and the conflict rule of DOMAIN section 5.5 for `r = 3`. */
    private fun classify(input: ShapeInput): Band {
        if (traverse.classify(input, traverse.closureError(input), tolerance) != ClosureClass.SHAPE_DEVIATION) {
            return Band.MEASUREMENT_ERROR
        }
        val shape = adjustOrFail(input, AdjustmentMode.SHAPE_DEVIATION, targetStart(input))
        val q2 = shape.normalizedSquareSum
        return when {
            shape.correctionSumMm <= tolerance.limitMm(perimeter(input)) -> Band.SMALL
            q2 > ChiSquareLimits.quantile(ChiSquareLevel.CONFLICT, REDUNDANCY) -> Band.CONFLICT
            q2 > ChiSquareLimits.quantile(ChiSquareLevel.EXPLICIT_CONFIRMATION, REDUNDANCY) -> Band.EXPLICIT
            else -> Band.LARGE_SIMPLE
        }
    }

    private fun noisyInput(
        scenario: Scenario,
        random: SeededRandom,
        wall: Int,
        errorMm: Double,
    ): ShapeInput {
        val lengths =
            scenario.lengths.mapIndexed { i, mm ->
                val error = if (i == wall) errorMm else 0.0
                Math.round(mm + error + SIGMA_LENGTH_MM * gaussian(random))
            }
        val corners =
            scenario.directions.map { direction ->
                val noisy = RIGHT_MILLI_DEGREES + SIGMA_ANGLE_MILLI_DEGREES * gaussian(random)
                CornerInput.Measured(direction, Angle(Math.round(noisy).toInt()))
            }
        return ShapeInput(lengths, corners)
    }

    private fun exactInput(
        scenario: Scenario,
        wall: Int,
        errorMm: Long,
    ): ShapeInput =
        ShapeInput(
            scenario.lengths.mapIndexed { i, mm -> if (i == wall) mm + errorMm else mm },
            scenario.directions.map { CornerInput.Measured(it, Angle.RIGHT) },
        )

    /** Outcome after DOMAIN sections 4 and 5.5. Rare branch-B outcomes (B3, B6, B8) do not occur in these rooms. */
    private enum class Band {
        /** `e ≤ T`: measurement-error path, simple confirmation, no χ² test. */
        MEASUREMENT_ERROR,

        /** `e > T` and `S ≤ T`: simple confirmation. */
        SMALL,

        /** `S > T` and `q² ≤ χ²(3; 0.95)`: large correction with a simple confirmation. */
        LARGE_SIMPLE,

        /** `S > T` and `χ²(3; 0.95) < q² ≤ χ²(3; 0.99)`: explicit confirmation. */
        EXPLICIT,

        /** `S > T` and `q² > χ²(3; 0.99)`: `CONFLICT(MEASUREMENTS_INCONSISTENT)`. */
        CONFLICT,
    }

    /**
     * One room with its reference values: rates in percent from the ADR 0018 table, [undetectedBand] in multiples of
     * `T` from the addendum CR-W1-01.
     */
    private data class Scenario(
        val name: String,
        val lengths: List<Long>,
        val directions: List<TurnDirection>,
        val explicitPercent: Double,
        val largeSimplePercent: Double,
        val undetectedBand: Double,
    )

    private companion object {
        const val SEED = 20_261_124L
        const val NOISE_SAMPLES = 40_000
        const val WALL_SAMPLES = 1_000
        const val BISECTION_STEPS = 20
        const val REDUNDANCY = 3
        const val ERROR_FACTOR = 3.0
        const val SIGMA_LENGTH_MM = 5.0
        const val SIGMA_ANGLE_MILLI_DEGREES = 1_000.0
        const val RIGHT_MILLI_DEGREES = 90_000.0
        const val DETECTION_PERCENT = 95.0

        /** χ²(3; 0.99) leaves 1 % of consistent rooms above the limit, 4 binomial σ of 40 000 samples are 0.2 %. */
        val FALSE_CONFLICT_PERCENT_RANGE = 0.6..1.4

        /** Margin in binomial standard deviations of [NOISE_SAMPLES]. */
        const val SIGMA_MARGIN = 4.0

        /** Extra margin in percentage points for the sampling error of the ADR reference values. */
        const val REFERENCE_MARGIN_PERCENT = 0.5

        /** Resolution of the band: one millimeter of error is at most 0.007 T for these rooms. */
        const val BAND_TOLERANCE = 0.01

        val SCENARIOS =
            listOf(
                scenario("rectangle", "4000 3000 4000 3000", "LLLL", 3.62, 11.88, 1.689),
                scenario("L", "6000 2000 3000 2000 3000 4000", "LLLRLL", 3.83, 23.79, 1.728),
                scenario("U", "6000 4000 2000 2500 2000 2500 2000 4000", "LLLLRRLL", 3.78, 27.12, 1.494),
                scenario(
                    "cross",
                    "2000 2000 2000 2000 2000 2000 2000 2000 2000 2000 2000 2000",
                    "LLRLLRLLRLLR",
                    3.98,
                    39.92,
                    1.635,
                ),
            )

        @Suppress("LongParameterList")
        fun scenario(
            name: String,
            lengths: String,
            directions: String,
            explicitPercent: Double,
            largeSimplePercent: Double,
            undetectedBand: Double,
        ) = Scenario(
            name,
            lengths.split(' ').map(String::toLong),
            directions.map { if (it == 'L') TurnDirection.LEFT else TurnDirection.RIGHT },
            explicitPercent,
            largeSimplePercent,
            undetectedBand,
        )

        /** Asserts that [actual] lies within the binomial and reference margin around [expected], both in percent. */
        fun assertNear(
            expected: Double,
            actual: Double,
            what: String,
        ) {
            val p = expected / 100.0
            val sampling = SIGMA_MARGIN * 100.0 * StrictMath.sqrt(p * (1.0 - p) / NOISE_SAMPLES)
            assertEquals(expected, actual, sampling + REFERENCE_MARGIN_PERCENT, what)
        }

        /** Standard normal value by Box-Muller from the seeded source. */
        fun gaussian(random: SeededRandom): Double {
            val u = 1.0 - random.nextDouble()
            return StrictMath.sqrt(-2.0 * StrictMath.log(u)) * StrictMath.cos(2.0 * StrictMath.PI * random.nextDouble())
        }

        fun report(
            format: String,
            vararg args: Any,
        ) = println("ADR 0018 simulation: " + format.format(Locale.ROOT, *args))
    }
}
