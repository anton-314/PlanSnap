package app.plansnap.core.domain.evaluation

import app.plansnap.core.common.result.DomainResult
import app.plansnap.core.geometry.adjustment.ObservationRef
import app.plansnap.core.geometry.determinacy.Determinacy
import app.plansnap.core.geometry.primitives.ClosureTolerance
import app.plansnap.core.geometry.primitives.Polygon2
import app.plansnap.core.geometry.primitives.Vec2
import app.plansnap.core.geometry.primitives.Winding
import app.plansnap.core.geometry.traverse.ClosureError
import app.plansnap.core.model.ClosureAdjustment
import app.plansnap.core.model.ConflictReason
import app.plansnap.core.model.DiagonalId
import app.plansnap.core.model.OpeningId
import app.plansnap.core.model.OpeningStatus
import app.plansnap.core.model.Room
import app.plansnap.core.model.RoomId
import app.plansnap.core.model.ShapeStatus
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap

/** Whether a room with this status appears in PDF, PNG and SVG (DOMAIN sections 4 and 12). */
val ShapeStatus.isExportable: Boolean
    get() = this != ShapeStatus.OPEN && this != ShapeStatus.CONFLICT

/** Whether a room with this status carries the mark "shape not confirmed" (REQ-EXP-006). */
val ShapeStatus.isUnconfirmed: Boolean
    get() = this == ShapeStatus.NEEDS_ADJUSTMENT || this == ShapeStatus.PROPOSED

/**
 * Displayed closed shape in room-local coordinates.
 *
 * @property outline inner contour.
 * @property winding orientation of [outline].
 * @property lengthsMm effective wall lengths, one per vertex, positive and finite.
 * @property turnsRad effective turns `τ_0 … τ_{n-1}`, one per vertex, finite.
 * @property areaMm2 unrounded inner area, not negative and finite.
 * @property perimeterMm unrounded perimeter, positive and finite.
 */
data class EvaluatedShape(
    val outline: Polygon2,
    val winding: Winding,
    val lengthsMm: ImmutableList<Double>,
    val turnsRad: ImmutableList<Double>,
    val areaMm2: Double,
    val perimeterMm: Double,
) {
    init {
        val n = outline.vertices.size
        require(lengthsMm.size == n) { "Expected one length per vertex" }
        require(turnsRad.size == n) { "Expected one turn per vertex" }
        require(lengthsMm.all { it.isFinite() && it > 0.0 }) { "Effective lengths must be positive" }
        require(turnsRad.all { it.isFinite() }) { "Turns must be finite" }
        require(areaMm2.isFinite() && areaMm2 >= 0.0) { "Invalid area: $areaMm2" }
        require(perimeterMm.isFinite() && perimeterMm > 0.0) { "Invalid perimeter: $perimeterMm" }
    }
}

/** Result of evaluating one room (DOMAIN section 4). Each variant corresponds to exactly one [ShapeStatus]. */
sealed interface RoomEvaluation {
    /** Evaluated room. */
    val roomId: RoomId

    /** Derived status. */
    val status: ShapeStatus

    /** Displayed closed shape, `null` for [Open] and [Conflict]. */
    val shape: EvaluatedShape?

    /** Derived status of every opening of the room (DOMAIN section 9). */
    val openingStatuses: ImmutableMap<OpeningId, OpeningStatus>

    /**
     * Fewer than three walls or not closed.
     *
     * @property chain walked points of the open chain in room-local coordinates, starting at `V_0`, never empty.
     */
    data class Open(
        override val roomId: RoomId,
        val chain: ImmutableList<Vec2>,
        override val openingStatuses: ImmutableMap<OpeningId, OpeningStatus>,
    ) : RoomEvaluation {
        init {
            require(chain.isNotEmpty()) { "An open chain starts at V_0" }
        }

        override val status: ShapeStatus get() = ShapeStatus.OPEN
        override val shape: EvaluatedShape? get() = null
    }

    /** Shape is unique and exactly closed. */
    data class Confirmed(
        override val roomId: RoomId,
        override val shape: EvaluatedShape,
        override val openingStatuses: ImmutableMap<OpeningId, OpeningStatus>,
    ) : RoomEvaluation {
        override val status: ShapeStatus get() = ShapeStatus.CONFIRMED
    }

    /**
     * Small gap whose adjustment awaits confirmation.
     *
     * @property shape adjustment preview.
     * @property closureError error components of the target shape.
     * @property proposedAdjustment overlay to pass to `AcceptAdjustment`, one entry per wall.
     */
    data class NeedsAdjustment(
        override val roomId: RoomId,
        override val shape: EvaluatedShape,
        val closureError: ClosureError,
        val proposedAdjustment: ClosureAdjustment,
        override val openingStatuses: ImmutableMap<OpeningId, OpeningStatus>,
    ) : RoomEvaluation {
        init {
            require(proposedAdjustment.adjustedLengthsMm.size == shape.outline.vertices.size) {
                "Expected one adjusted length per wall"
            }
        }

        override val status: ShapeStatus get() = ShapeStatus.NEEDS_ADJUSTMENT
    }

    /** Confirmed overlay that matches the raw values. */
    data class Adjusted(
        override val roomId: RoomId,
        override val shape: EvaluatedShape,
        override val openingStatuses: ImmutableMap<OpeningId, OpeningStatus>,
    ) : RoomEvaluation {
        override val status: ShapeStatus get() = ShapeStatus.ADJUSTED
    }

    /**
     * Proposed shape with missing measurements.
     *
     * @property determinacy rank analysis with at least one missing measurement and the recommended one.
     */
    data class Proposed(
        override val roomId: RoomId,
        override val shape: EvaluatedShape,
        val determinacy: Determinacy,
        override val openingStatuses: ImmutableMap<OpeningId, OpeningStatus>,
    ) : RoomEvaluation {
        init {
            require(determinacy.missing > 0) { "A proposal has at least one missing measurement" }
        }

        override val status: ShapeStatus get() = ShapeStatus.PROPOSED
    }

    /**
     * Geometrically impossible measurements.
     *
     * @property diagonalId affected diagonal, present exactly for [ConflictReason.DIAGONAL_IMPOSSIBLE].
     * @property worstObservation measurement with the largest normalized residual, only for
     * [ConflictReason.MEASUREMENTS_INCONSISTENT].
     */
    data class Conflict(
        override val roomId: RoomId,
        val reason: ConflictReason,
        val diagonalId: DiagonalId?,
        val worstObservation: ObservationRef?,
        override val openingStatuses: ImmutableMap<OpeningId, OpeningStatus>,
    ) : RoomEvaluation {
        init {
            require((reason == ConflictReason.DIAGONAL_IMPOSSIBLE) == (diagonalId != null)) {
                "A diagonal is required exactly for DIAGONAL_IMPOSSIBLE"
            }
            require(worstObservation == null || reason == ConflictReason.MEASUREMENTS_INCONSISTENT) {
                "A worst observation is only valid for MEASUREMENTS_INCONSISTENT"
            }
        }

        override val status: ShapeStatus get() = ShapeStatus.CONFLICT
        override val shape: EvaluatedShape? get() = null
    }
}

/** Evaluates a room as specified in DOMAIN section 4. Deterministic, main-safe and cancellable (ADR 0016). */
fun interface RoomEvaluator {
    /**
     * Evaluates [room] against [tolerance].
     *
     * Runs on the default dispatcher and passes [currentCancellationSignal] to solver and adjustment.
     *
     * @return the evaluation, or `DomainError.InvalidValue` if a value violates the limits of `GeometryTolerances`.
     * @throws kotlinx.coroutines.CancellationException if cancelled.
     */
    suspend fun evaluate(
        room: Room,
        tolerance: ClosureTolerance,
    ): DomainResult<RoomEvaluation>
}
