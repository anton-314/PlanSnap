package app.plansnap.core.common.units

import java.math.BigDecimal
import kotlin.math.PI

/** Direction of a turn at a corner, with [sign] `d` from DOMAIN section 3. */
enum class TurnDirection(
    val sign: Int,
) {
    /** Counter-clockwise turn, `d = +1`. */
    LEFT(1),

    /** Clockwise turn, `d = -1`. */
    RIGHT(-1),
}

/**
 * Raw angle in whole thousandths of a degree (ADR 0002).
 *
 * The type holds any [Int]. Arithmetic throws [ArithmeticException] on overflow.
 */
@JvmInline
value class Angle(
    val milliDegrees: Int,
) : Comparable<Angle> {
    /** Returns the exact sum. */
    operator fun plus(other: Angle): Angle = Angle(Math.addExact(milliDegrees, other.milliDegrees))

    /** Returns the exact difference. */
    operator fun minus(other: Angle): Angle = Angle(Math.subtractExact(milliDegrees, other.milliDegrees))

    /** Returns the exact negation. */
    operator fun unaryMinus(): Angle = Angle(Math.negateExact(milliDegrees))

    override fun compareTo(other: Angle): Int = milliDegrees.compareTo(other.milliDegrees)

    /** Returns the angle in radians. */
    fun toRadians(): Double = milliDegrees.toDouble() * PI / STRAIGHT_MILLI_DEGREES.toDouble()

    /** Returns the equivalent angle in `(-180°, 180°]`, computed exactly in integers. */
    fun normalized(): Angle {
        val positive = Math.floorMod(milliDegrees, FULL_MILLI_DEGREES)
        return Angle(if (positive > STRAIGHT_MILLI_DEGREES) positive - FULL_MILLI_DEGREES else positive)
    }

    /** Whether this is a valid entered corner angle `α` with `0° < α ≤ 180°` (DOMAIN section 3). */
    fun isValidCornerAngle(): Boolean = milliDegrees in 1..STRAIGHT_MILLI_DEGREES

    /**
     * Converts this entered corner angle `α` to the turn `τ = d · (180° − α)` (DOMAIN section 3).
     *
     * @throws IllegalArgumentException if this is not a valid corner angle.
     */
    fun toTurn(direction: TurnDirection): Angle {
        require(isValidCornerAngle()) { "Corner angle out of range: $milliDegrees" }
        return Angle(direction.sign * (STRAIGHT_MILLI_DEGREES - milliDegrees))
    }

    /** Factories and constants. */
    companion object {
        private const val STRAIGHT_MILLI_DEGREES = 180_000
        private const val FULL_MILLI_DEGREES = 360_000
        private const val MILLI_PER_DEGREE = 1_000L

        /** 0°. */
        val ZERO: Angle = Angle(0)

        /** 90°. */
        val RIGHT: Angle = Angle(90_000)

        /** 180°. */
        val STRAIGHT: Angle = Angle(STRAIGHT_MILLI_DEGREES)

        /** 360°. */
        val FULL: Angle = Angle(FULL_MILLI_DEGREES)

        /**
         * Converts decimal degrees without rounding.
         *
         * @return the angle, or `null` if the value has more than three decimals or exceeds the [Int] range.
         */
        fun ofDegreesExactOrNull(degrees: BigDecimal): Angle? =
            try {
                Angle(degrees.multiply(BigDecimal.valueOf(MILLI_PER_DEGREE)).intValueExact())
            } catch (_: ArithmeticException) {
                null
            }

        /**
         * Rounds computed radians to a raw angle, with halves rounded up.
         *
         * @return the angle, or `null` for NaN, infinity or a value outside the [Int] range.
         */
        fun roundedFromRadiansOrNull(radians: Double): Angle? {
            val scaled = radians * STRAIGHT_MILLI_DEGREES.toDouble() / PI
            val rounded = if (scaled.isFinite()) Math.round(scaled) else Long.MAX_VALUE
            return if (rounded in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) Angle(rounded.toInt()) else null
        }
    }
}
