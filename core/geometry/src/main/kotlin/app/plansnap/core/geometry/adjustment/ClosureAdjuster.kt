package app.plansnap.core.geometry.adjustment

import app.plansnap.core.geometry.cancellation.CancellationSignal
import app.plansnap.core.geometry.traverse.ShapeInput

/** Reference to one observation of the adjustment. */
sealed interface ObservationRef {
    /** Length of the wall at [index]. */
    data class WallLength(
        val index: Int,
    ) : ObservationRef

    /** Angle of the corner at [index]. */
    data class CornerAngle(
        val index: Int,
    ) : ObservationRef

    /** Diagonal at [index] in the input order. */
    data class DiagonalLength(
        val index: Int,
    ) : ObservationRef
}

/**
 * Redundancy number of one observation (DOMAIN section 5.5).
 *
 * @property redundancy share `r_i` of an error that shows up in the residuals, in `[0, 1]`.
 */
data class ObservationRedundancy(
    val observation: ObservationRef,
    val redundancy: Double,
) {
    init {
        require(redundancy.isFinite() && redundancy in 0.0..1.0) { "Redundancy out of range: $redundancy" }
    }
}

/**
 * Result of a weighted least-squares adjustment (DOMAIN section 6).
 *
 * @property adjustedLengthsMm adjusted wall lengths, positive and finite.
 * @property adjustedTurnsRad adjusted turns `τ_0 … τ_{n-1}`, finite.
 * @property adjustedDiagonalsMm adjusted diagonals in input order, positive and finite.
 * @property maxCorrectionMm largest correction over all observations in mm: `|ΔL_i|`, `|ΔD_k|` and `|Δτ_i| · U / (2π)`.
 * Corners without an observation do not count (DOMAIN section 6). Compared with `EXACT_CLOSURE_MM` in DOMAIN
 * section 5.5.
 * @property worstObservation observation with the largest normalized residual, `null` if all residuals are zero. Never
 * a corner without an observation.
 * @property correctionSumMm sum norm `S` over the same mm-equivalent corrections as [maxCorrectionMm].
 * @property normalizedSquareSum `q² = Σ (v_i / σ_i)²` over all observations.
 * @property largestCorrection observation with the largest mm-equivalent correction. Ties within `SOLVER_RESIDUAL_MM`
 * go to the first in the order walls, corners, diagonals. `null` if no correction exceeds `SOLVER_RESIDUAL_MM`.
 * @property redundancies redundancy numbers of all observations: walls, observed corners, then diagonals.
 */
data class AdjustedShape(
    val adjustedLengthsMm: List<Double>,
    val adjustedTurnsRad: List<Double>,
    val adjustedDiagonalsMm: List<Double>,
    val maxCorrectionMm: Double,
    val worstObservation: ObservationRef?,
    val correctionSumMm: Double,
    val normalizedSquareSum: Double,
    val largestCorrection: ObservationRef?,
    val redundancies: List<ObservationRedundancy>,
) {
    init {
        require(adjustedLengthsMm.size == adjustedTurnsRad.size) { "Expected one turn per wall" }
        require(adjustedLengthsMm.all { it.isFinite() && it > 0.0 }) { "Adjusted lengths must be positive" }
        require(adjustedTurnsRad.all { it.isFinite() }) { "Adjusted turns must be finite" }
        require(adjustedDiagonalsMm.all { it.isFinite() && it > 0.0 }) { "Adjusted diagonals must be positive" }
        require(maxCorrectionMm.isFinite() && maxCorrectionMm >= 0.0) { "Invalid correction: $maxCorrectionMm" }
        require(correctionSumMm.isFinite() && correctionSumMm >= maxCorrectionMm) {
            "Correction sum must be at least the largest correction: $correctionSumMm"
        }
        require(normalizedSquareSum.isFinite() && normalizedSquareSum >= 0.0) {
            "Invalid normalized square sum: $normalizedSquareSum"
        }
        require(redundancies.distinctBy { it.observation }.size == redundancies.size) {
            "Expected at most one redundancy per observation"
        }
    }
}

/** Role of declared right angles in the adjustment (DOMAIN section 6, ADR 0018). */
enum class AdjustmentMode {
    /** Small gap with `e ≤ T`. Declared right angles are observations with their own standard deviation. */
    MEASUREMENT_ERROR,

    /** Overdetermined shape deviation (DOMAIN section 5.5). Declared right angles carry no observation. */
    SHAPE_DEVIATION,
}

/** Result of the closure adjustment. */
sealed interface AdjustmentOutcome {
    /** The iteration converged. */
    data class Adjusted(
        val shape: AdjustedShape,
    ) : AdjustmentOutcome

    /** The residual limit was not reached within the permitted steps: `CONFLICT(ADJUSTMENT_NOT_CONVERGED)`. */
    data object NotConverged : AdjustmentOutcome
}

/** Gauss-Helmert closure adjustment (DOMAIN section 6, ADR 0005). Never changes raw values. */
interface ClosureAdjuster {
    /** Version recorded in the stored overlay for traceability. */
    val version: Int

    /**
     * Adjusts [input] in [mode]. Equal arguments yield a bit-identical outcome.
     *
     * The observations stay the reference `x_0` of the correction. Variables without an observation have no reference
     * and are not pulled back to their target. [startTurnsRad] is only the first linearization point `x_1` of the turns
     * (DOMAIN section 6).
     *
     * @param startTurnsRad finite turns `τ_0 … τ_{n-1}`, one per wall.
     * @throws IllegalArgumentException if [startTurnsRad] has the wrong size or a non-finite value.
     * @throws java.util.concurrent.CancellationException if [cancellation] cancels the computation.
     */
    fun adjust(
        input: ShapeInput,
        mode: AdjustmentMode,
        startTurnsRad: List<Double>,
        cancellation: CancellationSignal,
    ): AdjustmentOutcome
}
