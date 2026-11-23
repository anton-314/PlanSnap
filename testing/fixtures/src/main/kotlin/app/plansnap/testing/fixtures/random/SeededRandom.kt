package app.plansnap.testing.fixtures.random

/**
 * Deterministic pseudo random source (SplitMix64). The same seed yields the same sequence on every JVM and
 * platform, so generated test data never depends on the real clock or on `java.util.Random` internals.
 */
class SeededRandom(
    seed: Long,
) {
    private var state: Long = seed

    /** Returns the next 64 random bits. */
    fun nextLong(): Long {
        state += GOLDEN_GAMMA
        var z = state
        z = (z xor (z ushr SHIFT_1)) * MIX_1
        z = (z xor (z ushr SHIFT_2)) * MIX_2
        return z xor (z ushr SHIFT_3)
    }

    /** Returns a value in `0 until bound`. */
    fun nextInt(bound: Int): Int {
        require(bound > 0) { "Bound must be positive: $bound" }
        return ((nextLong() ushr 1) % bound).toInt()
    }

    /** Returns a value in [range], both ends included. */
    fun nextInt(range: IntRange): Int {
        require(!range.isEmpty()) { "Range must not be empty" }
        return range.first + nextInt(range.last - range.first + 1)
    }

    /** Returns a multiple of [step] in [range], both ends included. The bounds must be multiples of [step]. */
    fun nextMultiple(
        range: IntRange,
        step: Int,
    ): Int {
        require(step > 0 && range.first % step == 0 && range.last % step == 0) { "Range must align with step $step" }
        return nextInt(range.first / step..range.last / step) * step
    }

    /** Returns `true` or `false` with equal probability. */
    fun nextBoolean(): Boolean = nextLong() < 0L

    /** Returns a value in `[0, 1)` with 53 bits of resolution. */
    fun nextDouble(): Double = (nextLong() ushr DOUBLE_SHIFT).toDouble() * DOUBLE_UNIT

    private companion object {
        const val GOLDEN_GAMMA: Long = -7046029254386353131L
        const val MIX_1: Long = -4658895280553007687L
        const val MIX_2: Long = -7723592293110705685L
        const val SHIFT_1 = 30
        const val SHIFT_2 = 27
        const val SHIFT_3 = 31
        const val DOUBLE_SHIFT = 11
        const val DOUBLE_UNIT = 1.0 / (1L shl 53)
    }
}
