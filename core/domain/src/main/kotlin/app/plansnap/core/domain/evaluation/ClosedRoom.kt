package app.plansnap.core.domain.evaluation

import app.plansnap.core.geometry.adjustment.AdjustedShape
import app.plansnap.core.geometry.adjustment.ObservationRef
import app.plansnap.core.geometry.cancellation.CancellationSignal
import app.plansnap.core.geometry.determinacy.Determinacy
import app.plansnap.core.geometry.determinacy.MeasurementControl
import app.plansnap.core.geometry.determinacy.ShapeAmbiguity
import app.plansnap.core.geometry.traverse.ClosureError
import app.plansnap.core.geometry.traverse.CornerInput
import app.plansnap.core.geometry.traverse.ShapeInput
import app.plansnap.core.geometry.traverse.TurnTargets
import app.plansnap.core.model.ClosureAdjustment
import app.plansnap.core.model.ConflictReason
import app.plansnap.core.model.Room
import app.plansnap.core.model.inputFingerprint
import kotlinx.collections.immutable.toImmutableList

/**
 * One closed room under evaluation with everything the steps of DOMAIN sections 4 and 5.5 share.
 *
 * @property room evaluated room, closed and within the value limits.
 * @property input model-free input of [room].
 * @property closureError error components of the target shape (DOMAIN section 4, step 4).
 * @property limitMm limit `T` of the room (DOMAIN section 4, step 5).
 * @param targets orientation and target turns of [input] (DOMAIN section 3).
 * @property signal cancellation of the running coroutine (ADR 0016).
 */
internal class ClosedRoom(
    val room: Room,
    val input: ShapeInput,
    val closureError: ClosureError,
    val limitMm: Double,
    targets: TurnTargets,
    val signal: CancellationSignal,
) {
    /** Orientation `s` from the target turns (DOMAIN section 3). */
    val windingSign: Int = targets.winding.sign

    /** Target turns `τ̂_0 … τ̂_{n-1}` in radians. */
    val targetTurnsRad: List<Double> = targets.targetTurns.map { it.toRadians() }

    /** Raw wall lengths in mm. */
    val rawLengthsMm: List<Double> = input.lengthsMm.map { it.toDouble() }

    /** Whether `V_0` is fixed, `Measured` or `DeclaredRight`. */
    val isClosingFixed: Boolean = input.corners.first() !is CornerInput.Free

    /**
     * Observations `x_0` as turns: the target turns, with `τ_0,derived` for a free closing corner (DOMAIN section 6).
     * It is the start of the adjustment in the measurement error path and the start at the target turns in step B2.
     */
    val observedTurnsRad: List<Double> =
        if (isClosingFixed) targetTurnsRad else ShapeRules.withDerivedClosing(windingSign, targetTurnsRad)

    /** Conflict without a shape. Opening statuses use the raw lengths. */
    fun conflict(
        reason: ConflictReason,
        diagonalIndex: Int? = null,
        worstObservation: ObservationRef? = null,
    ): RoomEvaluation.Conflict =
        RoomEvaluation.Conflict(
            roomId = room.id,
            reason = reason,
            diagonalId = diagonalIndex?.let { room.diagonals[it].id },
            worstObservation = worstObservation,
            openingStatuses = room.openingStatuses(rawLengthsMm),
        )

    /** Confirmed [shape] with [control], `null` without rank analysis. */
    fun confirmed(
        shape: EvaluatedShape,
        control: MeasurementControl?,
    ): RoomEvaluation.Confirmed =
        RoomEvaluation.Confirmed(room.id, shape, room.openingStatuses(shape.lengthsMm), control)

    /** Adjustment awaiting [confirmation], with the overlay of [adjusted] and its preview [shape]. */
    fun needsAdjustment(
        shape: EvaluatedShape,
        adjusted: AdjustedShape,
        version: Int,
        confirmation: AdjustmentConfirmation,
        control: MeasurementControl,
    ): RoomEvaluation.NeedsAdjustment {
        val overlay =
            ClosureAdjustment(
                inputFingerprint = room.inputFingerprint(),
                solverVersion = version,
                adjustedLengthsMm = adjusted.adjustedLengthsMm.toImmutableList(),
                adjustedTurnsRad = adjusted.adjustedTurnsRad.toImmutableList(),
            )
        val proposal =
            AdjustmentProposal(
                adjustment = overlay,
                correctionSumMm = adjusted.correctionSumMm,
                maxCorrectionMm = adjusted.maxCorrectionMm,
                largestCorrection = adjusted.largestCorrection,
                requiredConfirmation = confirmation,
            )
        return RoomEvaluation.NeedsAdjustment(
            roomId = room.id,
            shape = shape,
            closureError = closureError,
            proposal = proposal,
            openingStatuses = room.openingStatuses(shape.lengthsMm),
            measurementControl = control,
        )
    }

    /** Proposal of [shape] for [reason], with [correction] for a shape from step B7. */
    fun proposed(
        shape: EvaluatedShape,
        reason: ProposalReason,
        determinacy: Determinacy,
        ambiguity: ShapeAmbiguity?,
        correction: CorrectionSummary?,
    ): RoomEvaluation.Proposed =
        RoomEvaluation.Proposed(
            roomId = room.id,
            shape = shape,
            reason = reason,
            determinacy = determinacy,
            ambiguity = ambiguity,
            rawLengthsMm = input.lengthsMm.toImmutableList(),
            correction = correction,
            openingStatuses = room.openingStatuses(shape.lengthsMm),
        )
}
