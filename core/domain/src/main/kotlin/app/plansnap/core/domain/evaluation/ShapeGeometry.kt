package app.plansnap.core.domain.evaluation

import app.plansnap.core.common.units.Angle
import app.plansnap.core.geometry.adjustment.AdjustedShape
import app.plansnap.core.geometry.adjustment.AdjustmentMode
import app.plansnap.core.geometry.adjustment.AdjustmentOutcome
import app.plansnap.core.geometry.adjustment.ClosureAdjuster
import app.plansnap.core.geometry.polygon.PolygonAnalyzer
import app.plansnap.core.geometry.primitives.GeometryTolerances
import app.plansnap.core.geometry.primitives.Polygon2
import app.plansnap.core.geometry.primitives.Vec2
import app.plansnap.core.geometry.primitives.Winding
import app.plansnap.core.geometry.traverse.CornerInput
import app.plansnap.core.geometry.traverse.ShapeInput
import app.plansnap.core.geometry.traverse.Traverse
import app.plansnap.core.model.ClosureAdjustment
import app.plansnap.core.model.ConflictReason
import kotlinx.collections.immutable.toImmutableList

/** Walks effective lengths and turns and builds the displayed shape (DOMAIN sections 3 and 7). */
internal class ShapeBuilder(
    private val traverse: Traverse,
    private val polygons: PolygonAnalyzer,
) {
    /** Closed outline `V_0 … V_{n-1}` of [lengthsMm] and [turnsRad]. */
    fun outline(
        lengthsMm: List<Double>,
        turnsRad: List<Double>,
    ): Polygon2 = Polygon2(traverse.walk(lengthsMm, turnsRad).dropLast(1))

    /** Whether [outline] is simple with `MIN_FEATURE_MM` (DOMAIN section 7). */
    fun isSimple(outline: Polygon2): Boolean = polygons.isSimple(outline, GeometryTolerances.MIN_FEATURE_MM)

    /** Displayed shape of a simple [outline] walked from [lengthsMm] and [turnsRad]. */
    fun shape(
        outline: Polygon2,
        lengthsMm: List<Double>,
        turnsRad: List<Double>,
    ): EvaluatedShape {
        val signedArea = polygons.signedAreaMm2(outline)
        return EvaluatedShape(
            outline = outline,
            winding = if (signedArea >= 0.0) Winding.COUNTER_CLOCKWISE else Winding.CLOCKWISE,
            lengthsMm = lengthsMm.toImmutableList(),
            turnsRad = turnsRad.toImmutableList(),
            areaMm2 = StrictMath.abs(signedArea),
            perimeterMm = lengthsMm.sum(),
        )
    }

    /**
     * Shape of a stored overlay if it is structurally valid (DOMAIN section 6), otherwise `null`.
     *
     * Lengths and turns are positive and finite by construction of [ClosureAdjustment]. The overlay is valid if its
     * walk stays finite, its gap is at most `EXACT_CLOSURE_MM` and its outline is simple. An overlay from an untrusted
     * document whose walk, perimeter or area leaves the finite range is not valid either.
     */
    fun overlayShape(adjustment: ClosureAdjustment): EvaluatedShape? {
        val lengths = adjustment.adjustedLengthsMm
        val turns = adjustment.adjustedTurnsRad
        val points = finiteWalk(lengths, turns) ?: return null
        val outline = Polygon2(points.dropLast(1))
        val closes = points.last().length() <= GeometryTolerances.EXACT_CLOSURE_MM
        val finite = lengths.sum().isFinite() && polygons.signedAreaMm2(outline).isFinite()
        return if (closes && finite && isSimple(outline)) shape(outline, lengths, turns) else null
    }

    private fun finiteWalk(
        lengths: List<Double>,
        turns: List<Double>,
    ): List<Vec2>? =
        try {
            traverse.walk(lengths, turns)
        } catch (_: IllegalArgumentException) {
            // A point outside the finite range cannot be represented, so the overlay cannot close.
            null
        }
}

/**
 * Closure adjustment followed by the simplicity check (DOMAIN section 6), shared by the measurement error path of
 * DOMAIN section 4 and the steps B2 and B3 of DOMAIN section 5.5.
 */
internal class AdjustmentStep(
    private val adjuster: ClosureAdjuster,
    private val shapes: ShapeBuilder,
) {
    /** Version of the adjuster, recorded in every proposed overlay. */
    val version: Int get() = adjuster.version

    /** Outcome of [run]. */
    sealed interface Outcome

    /** Converged adjustment with its simple preview [shape]. */
    data class Done(
        val adjusted: AdjustedShape,
        val shape: EvaluatedShape,
    ) : Outcome

    /** `ADJUSTMENT_NOT_CONVERGED` or `NO_SIMPLE_SHAPE`. */
    data class Failed(
        val reason: ConflictReason,
    ) : Outcome

    /** Adjusts the input of [room] in [mode] from the linearization point [start]. */
    fun run(
        room: ClosedRoom,
        mode: AdjustmentMode,
        start: List<Double>,
    ): Outcome {
        val outcome = adjuster.adjust(room.input, mode, start, room.signal)
        if (outcome !is AdjustmentOutcome.Adjusted) return Failed(ConflictReason.ADJUSTMENT_NOT_CONVERGED)
        val adjusted = outcome.shape
        val outline = shapes.outline(adjusted.adjustedLengthsMm, adjusted.adjustedTurnsRad)
        return if (shapes.isSimple(outline)) {
            Done(adjusted, shapes.shape(outline, adjusted.adjustedLengthsMm, adjusted.adjustedTurnsRad))
        } else {
            Failed(ConflictReason.NO_SIMPLE_SHAPE)
        }
    }
}

/** Rules on the raw input that need no solver (DOMAIN sections 3 and 4). */
internal object ShapeRules {
    private val DIRECTION_TOLERANCE_RAD = StrictMath.toRadians(GeometryTolerances.TURN_DIRECTION_TOLERANCE_DEG)
    private const val HALF_TURN = StrictMath.PI
    private const val FULL_TURN = 2.0 * StrictMath.PI

    /**
     * Violated necessary condition of DOMAIN section 4, step 3, or `null`.
     *
     * @property reason `WALL_TOO_LONG` or `DIAGONAL_IMPOSSIBLE`.
     * @property diagonalIndex input index of the affected diagonal, present exactly for `DIAGONAL_IMPOSSIBLE`.
     */
    data class Violation(
        val reason: ConflictReason,
        val diagonalIndex: Int?,
    )

    /**
     * Checks `L_max < Σ L_i − L_max − MIN_FEATURE_MM` and, for every diagonal between non-adjacent corners in input
     * order, `2M − C ≤ D ≤ C` on both chains. Lengths are whole millimeters, so the sums are exact.
     */
    fun necessaryViolation(input: ShapeInput): Violation? {
        val lengths = input.lengthsMm
        val n = lengths.size
        val longest = lengths.max()
        val rest = lengths.sum() - longest
        if (longest.toDouble() >= rest.toDouble() - GeometryTolerances.MIN_FEATURE_MM) {
            return Violation(ConflictReason.WALL_TOO_LONG, null)
        }
        val impossible =
            input.diagonals.indices.firstOrNull { index ->
                val diagonal = input.diagonals[index]
                val a = minOf(diagonal.fromCorner, diagonal.toCorner)
                val b = maxOf(diagonal.fromCorner, diagonal.toCorner)
                val adjacent = b == a + 1 || (a == 0 && b == n - 1)
                val chains = listOf(lengths.subList(a, b), lengths.subList(b, n) + lengths.subList(0, a))
                !adjacent && chains.any { chain -> !admits(chain, diagonal.lengthMm) }
            }
        return impossible?.let { Violation(ConflictReason.DIAGONAL_IMPOSSIBLE, it) }
    }

    /** Whether a chain of total length `C` with longest link `M` can span [diagonalMm]: `2M − C ≤ D ≤ C`. */
    private fun admits(
        chain: List<Long>,
        diagonalMm: Long,
    ): Boolean {
        val total = chain.sum()
        return diagonalMm <= total && diagonalMm >= 2L * chain.max() - total
    }

    /**
     * Whether [turnsRad] violate a declared or measured direction: `d · wrap(τ) < −ε` with
     * `ε = TURN_DIRECTION_TOLERANCE_DEG` (DOMAIN section 3). A measured 180° corner and a free corner have no
     * direction.
     */
    fun violatesDirections(
        input: ShapeInput,
        turnsRad: List<Double>,
    ): Boolean =
        input.corners.indices.any { i ->
            val direction =
                when (val corner = input.corners[i]) {
                    is CornerInput.DeclaredRight -> corner.direction.sign
                    is CornerInput.Measured -> if (corner.angle == Angle.STRAIGHT) 0 else corner.direction.sign
                    CornerInput.Free -> 0
                }
            direction != 0 && direction * wrap(turnsRad[i]) < -DIRECTION_TOLERANCE_RAD
        }

    /** `τ_0,derived = 2π·s − Σ_{i≥1} τ_i` with the orientation [windingSign] `s`, not wrapped. */
    fun derivedClosingTurn(
        windingSign: Int,
        turnsRad: List<Double>,
    ): Double {
        var inner = 0.0
        for (i in 1 until turnsRad.size) inner += turnsRad[i]
        return FULL_TURN * windingSign - inner
    }

    /** [turnsRad] with `τ_0` replaced by [derivedClosingTurn]. */
    fun withDerivedClosing(
        windingSign: Int,
        turnsRad: List<Double>,
    ): List<Double> = listOf(derivedClosingTurn(windingSign, turnsRad)) + turnsRad.drop(1)

    /** Wraps [angle] to `(−π, π]` as `π − ((π − a) mod 2π)`. */
    private fun wrap(angle: Double): Double = HALF_TURN - (HALF_TURN - angle).mod(FULL_TURN)
}
