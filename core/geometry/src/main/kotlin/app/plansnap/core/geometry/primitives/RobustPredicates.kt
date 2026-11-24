package app.plansnap.core.geometry.primitives

import java.math.BigDecimal
import kotlin.math.abs
import kotlin.math.sign

/**
 * Exact geometric sign predicates on finite doubles.
 *
 * Each predicate first evaluates in double precision and accepts the sign only if it exceeds a forward error bound.
 * Otherwise it recomputes with [BigDecimal], which represents every finite double exactly. The result is therefore the
 * sign of the exact real value for every finite input, including overflow and underflow. These bounds describe IEEE-754
 * rounding only, they are not geometric tolerances.
 */
internal object RobustPredicates {
    /** Unit roundoff `u = 2^-53` of IEEE-754 double precision. */
    private val UNIT_ROUNDOFF: Double = StrictMath.ulp(1.0) / 2.0

    /** Linear coefficient of Shewchuk's error bound `ccwerrboundA = (3 + 16u) · u`. */
    private const val ORIENTATION_BOUND_LINEAR = 3.0

    /** Quadratic coefficient of Shewchuk's error bound `ccwerrboundA = (3 + 16u) · u`. */
    private const val ORIENTATION_BOUND_QUADRATIC = 16.0

    private val ORIENTATION_ERROR_BOUND: Double =
        (ORIENTATION_BOUND_LINEAR + ORIENTATION_BOUND_QUADRATIC * UNIT_ROUNDOFF) * UNIT_ROUNDOFF

    /**
     * Extra terms of the shoelace bound `2 · (n + SHOELACE_EXTRA_TERMS) · u · Σ(|x_i y_{i+1}| + |x_{i+1} y_i|)`: one
     * rounding per product, one per difference and one per addition, with a safety factor of two.
     */
    private const val SHOELACE_EXTRA_TERMS = 2

    private val HALF: BigDecimal = BigDecimal("0.5")

    /**
     * Returns the sign of `(b − a) × (c − a)`: `+1` if [a], [b], [c] turn counter-clockwise, `−1` if clockwise and `0`
     * if they are exactly collinear.
     */
    fun orientation(
        a: Vec2,
        b: Vec2,
        c: Vec2,
    ): Int {
        val left = (a.x - c.x) * (b.y - c.y)
        val right = (a.y - c.y) * (b.x - c.x)
        val det = left - right
        val bound = ORIENTATION_ERROR_BOUND * (abs(left) + abs(right))
        return if (isCertain(det, bound)) det.sign.toInt() else exactOrientation(a, b, c)
    }

    /** Returns the exact sign of the shoelace sum `Σ (x_i y_{i+1} − x_{i+1} y_i)` over the closed vertex ring. */
    fun shoelaceSign(vertices: List<Vec2>): Int {
        var sum = 0.0
        var magnitude = 0.0
        forEachEdge(vertices) { from, to ->
            val left = from.x * to.y
            val right = to.x * from.y
            sum += left - right
            magnitude += abs(left) + abs(right)
        }
        val bound = 2.0 * (vertices.size + SHOELACE_EXTRA_TERMS) * UNIT_ROUNDOFF * magnitude
        return if (isCertain(sum, bound)) sum.sign.toInt() else exactShoelace(vertices).signum()
    }

    /** Returns half of the exact shoelace sum, rounded once to the nearest double, possibly infinite. */
    fun exactSignedArea(vertices: List<Vec2>): Double = exactShoelace(vertices).multiply(HALF).toDouble()

    /**
     * Whether the double result [value] has the sign of the exact value, given its forward error [bound]. A bound
     * below [java.lang.Double.MIN_NORMAL] may hide subnormal rounding, a non-finite operand may hide overflow.
     */
    private fun isCertain(
        value: Double,
        bound: Double,
    ): Boolean = value.isFinite() && bound.isFinite() && bound >= java.lang.Double.MIN_NORMAL && abs(value) > bound

    private fun exactOrientation(
        a: Vec2,
        b: Vec2,
        c: Vec2,
    ): Int {
        val cx = BigDecimal(c.x)
        val cy = BigDecimal(c.y)
        val left = (BigDecimal(a.x) - cx) * (BigDecimal(b.y) - cy)
        val right = (BigDecimal(a.y) - cy) * (BigDecimal(b.x) - cx)
        return (left - right).signum()
    }

    private fun exactShoelace(vertices: List<Vec2>): BigDecimal {
        var sum = BigDecimal.ZERO
        forEachEdge(vertices) { from, to ->
            sum += BigDecimal(from.x) * BigDecimal(to.y) - BigDecimal(to.x) * BigDecimal(from.y)
        }
        return sum
    }

    private inline fun forEachEdge(
        vertices: List<Vec2>,
        action: (from: Vec2, to: Vec2) -> Unit,
    ) {
        for (i in vertices.indices) {
            action(vertices[i], vertices[(i + 1) % vertices.size])
        }
    }
}
