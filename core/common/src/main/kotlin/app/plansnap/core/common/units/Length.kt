package app.plansnap.core.common.units

/**
 * Raw length in whole millimeters (ADR 0002).
 *
 * The type holds any [Long], including zero and negative values, so that differences can be expressed.
 * Range rules belong to the consuming model. Arithmetic throws [ArithmeticException] on overflow.
 */
@JvmInline
value class Length(
    val mm: Long,
) : Comparable<Length> {
    /** Returns the exact sum. */
    operator fun plus(other: Length): Length = Length(Math.addExact(mm, other.mm))

    /** Returns the exact difference. */
    operator fun minus(other: Length): Length = Length(Math.subtractExact(mm, other.mm))

    /** Returns the exact negation. */
    operator fun unaryMinus(): Length = Length(Math.negateExact(mm))

    override fun compareTo(other: Length): Int = mm.compareTo(other.mm)

    /** Returns the value as computed millimeters for geometry. Values beyond 2^53 may lose precision. */
    fun toDoubleMm(): Double = mm.toDouble()

    /** Factories and constants. */
    companion object {
        /** Zero length. */
        val ZERO: Length = Length(0L)

        private const val LONG_RANGE_LIMIT = 9.223372036854775807E18

        /**
         * Rounds computed millimeters to a raw length, with halves rounded up.
         *
         * @return the length, or `null` for NaN, infinity or a value outside the [Long] range.
         */
        fun roundedFromMmOrNull(value: Double): Length? {
            if (!value.isFinite() || value < -LONG_RANGE_LIMIT || value >= LONG_RANGE_LIMIT) return null
            return Length(Math.round(value))
        }
    }
}
