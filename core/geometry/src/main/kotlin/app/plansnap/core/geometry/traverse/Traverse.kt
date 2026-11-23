package app.plansnap.core.geometry.traverse

import app.plansnap.core.common.units.Angle
import app.plansnap.core.geometry.primitives.ClosureTolerance
import app.plansnap.core.geometry.primitives.Vec2
import app.plansnap.core.geometry.primitives.Winding

/**
 * Target turns of an outline (DOMAIN section 3).
 *
 * @property winding orientation `s`.
 * @property targetTurns target turns `τ̂_i` in index order, exact in millidegrees.
 */
data class TurnTargets(
    val winding: Winding,
    val targetTurns: List<Angle>,
)

/**
 * Error components of the target shape in mm (DOMAIN section 4, step 3). All values are finite and not negative.
 *
 * @property gapMm closure gap `g`.
 * @property maxDiagonalResidualMm largest diagonal residual `max r_k`, zero without diagonals.
 * @property closingAngleResidualMm closing-corner term `a`, zero unless the closing corner is measured.
 */
data class ClosureError(
    val gapMm: Double,
    val maxDiagonalResidualMm: Double,
    val closingAngleResidualMm: Double,
) {
    init {
        listOf(gapMm, maxDiagonalResidualMm, closingAngleResidualMm).forEach {
            require(it.isFinite() && it >= 0.0) { "Invalid closure error component: $it" }
        }
    }

    /** Overall error `e = max(g, max r_k, a)`. */
    val errorMm: Double get() = maxOf(gapMm, maxDiagonalResidualMm, closingAngleResidualMm)
}

/** Classification of a closure error (DOMAIN section 4, step 5). */
enum class ClosureClass {
    /** `e ≤ EXACT_CLOSURE_MM`. */
    EXACT,

    /** `e ≤ T`. */
    MEASUREMENT_ERROR,

    /** `e > T`. */
    SHAPE_DEVIATION,
}

/** Walks an outline from raw input (DOMAIN sections 3 and 4). */
interface Traverse {
    /** Returns orientation and target turns, using exact integer arithmetic in millidegrees. */
    fun targets(input: ShapeInput): TurnTargets

    /**
     * Walks a possibly open chain from `V_0` at the origin along +x.
     *
     * @param lengthsMm wall lengths.
     * @param turnsRad turns `τ_i`, same size as [lengthsMm]. The entry at index 0 is ignored.
     * @return `lengthsMm.size + 1` points, the last being the end of the final wall.
     */
    fun walk(
        lengthsMm: List<Long>,
        turnsRad: List<Double>,
    ): List<Vec2>

    /** Computes the error components of the target shape. */
    fun closureError(input: ShapeInput): ClosureError

    /** Classifies [error] for the perimeter of [input]. A value on a limit falls into the stricter class. */
    fun classify(
        input: ShapeInput,
        error: ClosureError,
        tolerance: ClosureTolerance,
    ): ClosureClass
}
