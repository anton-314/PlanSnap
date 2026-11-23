package app.plansnap.core.geometry.determinacy

import app.plansnap.core.geometry.solver.ShapeSolution
import app.plansnap.core.geometry.traverse.ShapeInput

/** Additional measurement recommended to determine a shape (DOMAIN section 5.4). */
sealed interface MeasurementRecommendation {
    /** Measure the diagonal between corners [fromCorner] and [toCorner], with `fromCorner < toCorner`. */
    data class DiagonalBetween(
        val fromCorner: Int,
        val toCorner: Int,
    ) : MeasurementRecommendation {
        init {
            require(fromCorner in 0 until toCorner) { "Expected 0 <= fromCorner < toCorner" }
        }
    }

    /** Measure the angle at [corner]. */
    data class CornerAngle(
        val corner: Int,
    ) : MeasurementRecommendation {
        init {
            require(corner >= 0) { "Corner index must not be negative" }
        }
    }
}

/**
 * Determinacy of a solved shape.
 *
 * @property freeVariables `|u_frei|`, the variables left after eliminating measured corners.
 * @property rank rank `ρ_J`, between 0 and [freeVariables].
 * @property isOverdetermined whether more independent hard conditions exist than variables (DOMAIN section 5.5).
 * @property recommendation next measurement. Only permitted while [missing] is positive.
 */
data class Determinacy(
    val freeVariables: Int,
    val rank: Int,
    val isOverdetermined: Boolean,
    val recommendation: MeasurementRecommendation?,
) {
    init {
        require(freeVariables >= 0) { "Free variables must not be negative" }
        require(rank in 0..freeVariables) { "Rank out of range: $rank" }
        require(recommendation == null || rank < freeVariables) { "A determined shape needs no recommendation" }
    }

    /** Number of missing measurements, `|u_frei| − ρ_J`. */
    val missing: Int get() = freeVariables - rank
}

/** Rank analysis of the hard conditions at a solution (DOMAIN sections 5.4 and 5.5). */
fun interface DeterminacyAnalyzer {
    /** Analyzes [input] at [solution]. */
    fun analyze(
        input: ShapeInput,
        solution: ShapeSolution,
    ): Determinacy
}
