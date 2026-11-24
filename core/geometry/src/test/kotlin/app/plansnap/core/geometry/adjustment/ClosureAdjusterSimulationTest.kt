package app.plansnap.core.geometry.adjustment

import app.plansnap.core.common.units.Angle
import app.plansnap.core.common.units.TurnDirection
import app.plansnap.core.geometry.adjustment.AdjustmentTestSupport.adjustOrFail
import app.plansnap.core.geometry.adjustment.AdjustmentTestSupport.perimeter
import app.plansnap.core.geometry.adjustment.AdjustmentTestSupport.targetStart
import app.plansnap.core.geometry.primitives.ClosureTolerance
import app.plansnap.core.geometry.statistics.ChiSquareLevel
import app.plansnap.core.geometry.statistics.ChiSquareLimits
import app.plansnap.core.geometry.traverse.CornerInput
import app.plansnap.core.geometry.traverse.ShapeInput
import app.plansnap.testing.fixtures.random.SeededRandom
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Locale

/**
 * Recomputes the statistical limits of ADR 0018 on the production adjuster.
 *
 * The rooms are the true shapes of the NH3 cases in `OverdeterminedCases` (rectangle, L, U, cross) with every corner
 * measured as 90°. Each sample adds normal noise with the σ of DOMAIN section 6 and rounds to whole millimeters and
 * millidegrees. The redundancy is `r = 3`, `T` is the default limit. The test prints its figures, the assertions only
 * hold the statistical bounds that the conflict rule guarantees. The full scenario set of ADR 0018 (13 scenarios,
 * 77 wall cases) is not part of the repository, so this is a seeded subset with the four named rooms.
 */
class ClosureAdjusterSimulationTest {
    private val tolerance = ClosureTolerance()

    @Test
    fun `given consistent rooms with noise when adjusted then conflict and confirmation rates match ADR 0018`() {
        SCENARIOS.forEach { scenario ->
            val random = SeededRandom(SEED + scenario.name.hashCode())
            val counts = IntArray(Band.entries.size)
            repeat(NOISE_SAMPLES) { counts[classify(noisyInput(scenario, random, wall = -1, errorMm = 0.0)).ordinal]++ }
            val rates = counts.map { 100.0 * it / NOISE_SAMPLES }
            report(
                "%s: false conflict %.2f %%, explicit %.2f %%, large simple %.2f %%",
                scenario.name,
                rates[Band.CONFLICT.ordinal],
                rates[Band.EXPLICIT.ordinal],
                rates[Band.LARGE_SIMPLE.ordinal],
            )
            // q² follows χ²(3) for consistent rooms, so neither band can exceed its probability mass by much.
            assertTrue(rates[Band.CONFLICT.ordinal] <= MAX_FALSE_CONFLICT_PERCENT, scenario.name)
            assertTrue(rates[Band.EXPLICIT.ordinal] in EXPLICIT_PERCENT_RANGE, scenario.name)
        }
    }

    @Test
    fun `given one wall wrong by three times the limit when adjusted then the conflict rule detects it`() {
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
            assertTrue(rates.all { it >= MIN_DETECTION_PERCENT }, scenario.name)
        }
        report("walls detected with at least 95 %%: %d of %d", detectedWalls, walls)
        assertTrue(detectedWalls >= walls / 2)
    }

    @Test
    fun `given one wall wrong without noise when adjusted then the undetected band stays below twice the limit`() {
        SCENARIOS.forEach { scenario ->
            val limit = tolerance.limitMm(scenario.lengths.sum().toDouble())
            val band =
                scenario.lengths.indices.maxOf { wall ->
                    var low = 0.0
                    var high = ERROR_FACTOR
                    repeat(BISECTION_STEPS) {
                        val middle = (low + high) / 2.0
                        val input = exactInput(scenario, wall, Math.round(middle * limit))
                        if (classify(input) == Band.CONFLICT) high = middle else low = middle
                    }
                    high
                }
            report("%s: undetected band up to %.2f T", scenario.name, band)
            assertTrue(band < MAX_UNDETECTED_FACTOR, scenario.name)
        }
    }

    private fun classify(input: ShapeInput): Band {
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

    /** Outcome of the conflict rule of DOMAIN section 5.5 for `r = 3`. */
    private enum class Band { SMALL, LARGE_SIMPLE, EXPLICIT, CONFLICT }

    private data class Scenario(
        val name: String,
        val lengths: List<Long>,
        val directions: List<TurnDirection>,
    )

    private companion object {
        const val SEED = 20_261_124L
        const val NOISE_SAMPLES = 10_000
        const val WALL_SAMPLES = 1_000
        const val BISECTION_STEPS = 20
        const val REDUNDANCY = 3
        const val ERROR_FACTOR = 3.0
        const val SIGMA_LENGTH_MM = 5.0
        const val SIGMA_ANGLE_MILLI_DEGREES = 1_000.0
        const val RIGHT_MILLI_DEGREES = 90_000.0
        const val MAX_FALSE_CONFLICT_PERCENT = 1.5
        val EXPLICIT_PERCENT_RANGE = 2.0..6.0
        const val DETECTION_PERCENT = 95.0
        const val MIN_DETECTION_PERCENT = 50.0
        const val MAX_UNDETECTED_FACTOR = 2.0

        val SCENARIOS =
            listOf(
                scenario("rectangle", "4000 3000 4000 3000", "LLLL"),
                scenario("L", "6000 2000 3000 2000 3000 4000", "LLLRLL"),
                scenario("U", "6000 4000 2000 2500 2000 2500 2000 4000", "LLLLRRLL"),
                scenario("cross", "2000 2000 2000 2000 2000 2000 2000 2000 2000 2000 2000 2000", "LLRLLRLLRLLR"),
            )

        fun scenario(
            name: String,
            lengths: String,
            directions: String,
        ) = Scenario(
            name,
            lengths.split(' ').map(String::toLong),
            directions.map { if (it == 'L') TurnDirection.LEFT else TurnDirection.RIGHT },
        )

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
