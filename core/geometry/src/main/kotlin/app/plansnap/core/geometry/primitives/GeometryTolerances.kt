package app.plansnap.core.geometry.primitives

/** Central limits and tolerances from DOMAIN section 1. No other place may define these values. */
object GeometryTolerances {
    /** Smallest wall length in mm. */
    const val MIN_WALL_MM: Long = 10L

    /** Largest wall length in mm. */
    const val MAX_WALL_MM: Long = 100_000L

    /** Largest wall thickness in mm. Zero is allowed. */
    const val MAX_THICKNESS_MM: Long = 1_000L

    /** Initial wall thickness in mm. */
    const val DEFAULT_THICKNESS_MM: Long = 120L

    /** Maximum number of walls per room. */
    const val MAX_WALLS_PER_ROOM: Int = 128

    /** Up to this error in mm an outline counts as exactly closed. */
    const val EXACT_CLOSURE_MM: Double = 1.0

    /** Permitted residual in mm after solving. */
    const val SOLVER_RESIDUAL_MM: Double = 0.01

    /** Termination threshold of the projected gradient. */
    const val SOLVER_GRADIENT: Double = 1e-9

    /** Solver iterations per start value. */
    const val SOLVER_MAX_ITERATIONS: Int = 200

    /** Relative pivot threshold of the rank determination. */
    const val RANK_TOLERANCE: Double = 1e-9

    /** Minimum distance in mm between non-adjacent edges. */
    const val MIN_FEATURE_MM: Double = 1.0

    /** Cost difference below which two solutions tie. */
    const val COST_TIE: Double = 1e-9

    /** Default absolute measurement error limit in mm. */
    const val DEFAULT_TOLERANCE_ABS_MM: Long = 50L

    /** Default measurement error limit relative to the perimeter. */
    const val DEFAULT_TOLERANCE_REL: Double = 0.01

    /** Regularization `μ` of the Hessian (DOMAIN section 5.2). */
    const val SOLVER_REGULARIZATION: Double = 1e-8

    /** Rounding step in radians for the lexicographic comparison (DOMAIN section 5.3). */
    const val SOLUTION_ROUNDING_RAD: Double = 1e-6

    /** Maximum steps of the closure adjustment (DOMAIN section 6). */
    const val ADJUSTMENT_MAX_ITERATIONS: Int = 50

    /** Standard deviation of a wall length in mm. */
    const val SIGMA_LENGTH_MM: Double = 5.0

    /** Standard deviation of a diagonal in mm. */
    const val SIGMA_DIAGONAL_MM: Double = 5.0

    /** Standard deviation of a declared right angle in degrees. */
    const val SIGMA_RIGHT_ANGLE_DEG: Double = 0.5

    /** Standard deviation of a measured angle in degrees. */
    const val SIGMA_MEASURED_ANGLE_DEG: Double = 1.0

    /** Fixed standard deviation in radians of a variable without observation. */
    const val SIGMA_FREE_RAD: Double = 10.0

    /** Below this `|sin τ|` a corner counts as nearly collinear (DOMAIN section 8). */
    const val NEAR_COLLINEAR_SIN: Double = 1e-6

    /** Miter limit as a multiple of the larger wall thickness (DOMAIN section 8). */
    const val MITER_LIMIT_FACTOR: Double = 4.0
}

/**
 * Compares two computed values within an absolute [tolerance], inclusive.
 *
 * NaN and infinite operands are never close to anything.
 *
 * @throws IllegalArgumentException if [tolerance] is negative, NaN or infinite.
 */
fun Double.isCloseTo(
    other: Double,
    tolerance: Double,
): Boolean {
    require(tolerance.isFinite() && tolerance >= 0.0) { "Invalid tolerance: $tolerance" }
    return isFinite() && other.isFinite() && kotlin.math.abs(this - other) <= tolerance
}

/**
 * User-adjustable limit between measurement error and shape deviation (DOMAIN section 4).
 *
 * @property absoluteMm absolute limit `T_abs` in mm, not negative.
 * @property relative limit `T_rel` relative to the perimeter, in `[0, 1]`.
 */
data class ClosureTolerance(
    val absoluteMm: Long = GeometryTolerances.DEFAULT_TOLERANCE_ABS_MM,
    val relative: Double = GeometryTolerances.DEFAULT_TOLERANCE_REL,
) {
    init {
        require(absoluteMm >= 0L) { "Absolute tolerance must not be negative: $absoluteMm" }
        require(relative.isFinite() && relative in 0.0..1.0) { "Relative tolerance out of range: $relative" }
    }

    /**
     * Returns `T = max(T_abs, T_rel · U)` for the perimeter `U` in mm.
     *
     * @throws IllegalArgumentException if [perimeterMm] is negative, NaN or infinite.
     */
    fun limitMm(perimeterMm: Double): Double {
        require(perimeterMm.isFinite() && perimeterMm >= 0.0) { "Invalid perimeter: $perimeterMm" }
        return maxOf(absoluteMm.toDouble(), relative * perimeterMm)
    }
}
