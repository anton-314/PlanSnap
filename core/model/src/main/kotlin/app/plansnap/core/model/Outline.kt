package app.plansnap.core.model

import app.plansnap.core.common.units.Angle
import app.plansnap.core.common.units.Length
import app.plansnap.core.common.units.TurnDirection
import kotlinx.collections.immutable.ImmutableList

/**
 * Wall `w_i` of the inner contour (ADR 0003). The limits from `GeometryTolerances` are checked by `:core:domain`.
 *
 * @property length raw inner length, positive.
 * @property thickness outward thickness, not negative.
 */
data class Wall(
    val id: WallId,
    val length: Length,
    val thickness: Length,
) {
    init {
        require(length > Length.ZERO) { "Wall length must be positive: ${length.mm}" }
        require(thickness >= Length.ZERO) { "Wall thickness must not be negative: ${thickness.mm}" }
    }
}

/** Raw input at a corner (DOMAIN section 3). */
sealed interface CornerSpec {
    /** Entered turn direction, `null` if the corner is unspecified. */
    val direction: TurnDirection?

    /** Declared right angle, equal to `α = 90°`. */
    data class DeclaredRight(
        override val direction: TurnDirection,
    ) : CornerSpec

    /** Measured corner with the entered angle `α` in `(0°, 180°]`. */
    data class Measured(
        override val direction: TurnDirection,
        val angle: Angle,
    ) : CornerSpec {
        init {
            require(angle.isValidCornerAngle()) { "Corner angle out of range: ${angle.milliDegrees}" }
        }
    }

    /** No entered value. Only permitted at the closing corner `V_0`. */
    data object Unspecified : CornerSpec {
        override val direction: TurnDirection? = null
    }
}

/**
 * Measured diagonal between two different corners.
 *
 * @property length raw length, positive.
 */
data class Diagonal(
    val id: DiagonalId,
    val fromCorner: Int,
    val toCorner: Int,
    val length: Length,
) {
    init {
        require(fromCorner >= 0 && toCorner >= 0) { "Corner index must not be negative" }
        require(fromCorner != toCorner) { "Diagonal must connect two different corners" }
        require(length > Length.ZERO) { "Diagonal length must be positive: ${length.mm}" }
    }
}

/** Derived shape status of a room (DOMAIN section 4). */
enum class ShapeStatus {
    /** Fewer than three walls or not closed. Not exportable. */
    OPEN,

    /** Shape is unique and exactly closed. */
    CONFIRMED,

    /** Small gap, adjustment awaits confirmation. */
    NEEDS_ADJUSTMENT,

    /** Adjustment is confirmed and matches the raw values. */
    ADJUSTED,

    /** Shape is a proposal, additional measurements are missing. */
    PROPOSED,

    /** Measurements are geometrically impossible. Not exportable. */
    CONFLICT,
}

/** Reason for [ShapeStatus.CONFLICT] (DOMAIN sections 4, 5.3 and 5.5). */
enum class ConflictReason {
    /** One wall is at least as long as all others together. */
    WALL_TOO_LONG,

    /** A diagonal violates the triangle bounds of its chains. */
    DIAGONAL_IMPOSSIBLE,

    /** No start value yields a valid simple outline. */
    NO_SIMPLE_SHAPE,

    /** Overdetermined measurements cannot be reconciled within the tolerance. */
    MEASUREMENTS_INCONSISTENT,

    /** The closure adjustment did not converge, so no overlay exists (DOMAIN section 6). */
    ADJUSTMENT_NOT_CONVERGED,
}

/**
 * Confirmed closure adjustment stored as an overlay, never written into raw values (DOMAIN section 6).
 *
 * The overlay is valid while its fingerprint matches [Room.inputFingerprint].
 * A different [solverVersion] does not invalidate it.
 *
 * @property inputFingerprint fingerprint of the raw input at adjustment time.
 * @property solverVersion adjuster version, positive, kept for traceability.
 * @property adjustedLengthsMm adjusted wall lengths, positive and finite.
 * @property adjustedTurnsRad adjusted turns `τ_0 … τ_{n-1}`, finite, one per wall.
 */
data class ClosureAdjustment(
    val inputFingerprint: Long,
    val solverVersion: Int,
    val adjustedLengthsMm: ImmutableList<Double>,
    val adjustedTurnsRad: ImmutableList<Double>,
) {
    init {
        require(solverVersion > 0) { "Solver version must be positive: $solverVersion" }
        require(adjustedLengthsMm.size == adjustedTurnsRad.size) { "Expected one turn per wall" }
        require(adjustedLengthsMm.all { it.isFinite() && it > 0.0 }) { "Adjusted lengths must be positive" }
        require(adjustedTurnsRad.all { it.isFinite() }) { "Adjusted turns must be finite" }
    }
}
