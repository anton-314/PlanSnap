package app.plansnap.core.domain.evaluation

import app.plansnap.core.common.result.DomainResult
import app.plansnap.core.common.runtime.DispatcherProvider
import app.plansnap.core.common.units.Angle
import app.plansnap.core.common.units.Length
import app.plansnap.core.common.units.TurnDirection
import app.plansnap.core.geometry.primitives.ClosureTolerance
import app.plansnap.core.model.ConflictReason
import app.plansnap.core.model.CornerSpec
import app.plansnap.core.model.Diagonal
import app.plansnap.core.model.DiagonalId
import app.plansnap.core.model.Room
import app.plansnap.core.model.RoomId
import app.plansnap.core.model.Wall
import app.plansnap.core.model.WallId
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertTrue

/** Runs every role on [Dispatchers.Unconfined], so an evaluation runs in the calling thread. */
internal object DirectDispatchers : DispatcherProvider {
    override val main: CoroutineDispatcher get() = Dispatchers.Unconfined
    override val default: CoroutineDispatcher get() = Dispatchers.Unconfined
    override val io: CoroutineDispatcher get() = Dispatchers.Unconfined
}

/** Default tolerance `T = max(50 mm, 1 % · U)`. */
internal val DEFAULT_TOLERANCE = ClosureTolerance()

/** Evaluates [room] with the production evaluator and returns the successful evaluation. */
internal fun evaluated(
    room: Room,
    tolerance: ClosureTolerance = DEFAULT_TOLERANCE,
    evaluator: RoomEvaluator = DefaultRoomEvaluator(DirectDispatchers),
): RoomEvaluation = runBlocking { evaluator.evaluate(room, tolerance) }.success()

/** Returns the value of a success, otherwise fails the test. */
internal fun <T> DomainResult<T>.success(): T {
    assertTrue(this is DomainResult.Success, "Expected success: $this")
    return (this as DomainResult.Success).value
}

/** Conflict reason of [evaluation], `null` for every other variant. */
internal fun conflictOf(evaluation: RoomEvaluation): ConflictReason? = (evaluation as? RoomEvaluation.Conflict)?.reason

/** Proposal reason of [evaluation], `null` for every other variant. */
internal fun proposalOf(evaluation: RoomEvaluation): ProposalReason? = (evaluation as? RoomEvaluation.Proposed)?.reason

/** Required confirmation of [evaluation], `null` for every variant except [RoomEvaluation.NeedsAdjustment]. */
internal fun confirmationOf(evaluation: RoomEvaluation): AdjustmentConfirmation? =
    (evaluation as? RoomEvaluation.NeedsAdjustment)?.proposal?.requiredConfirmation

/**
 * Builds a room from compact corner tokens: `F` unspecified, `L` or `R` declared right, `M<milliDegrees>` measured left
 * and `m<milliDegrees>` measured right. Diagonals are `from-to=mm`.
 */
internal fun room(
    lengths: String,
    corners: String,
    diagonals: String = "",
    closed: Boolean = true,
    id: String = "room",
): Room =
    Room(
        id = RoomId(id),
        walls =
            lengths
                .split(' ')
                .filter(String::isNotEmpty)
                .mapIndexed { i, mm -> Wall(WallId("$id-w$i"), Length(mm.toLong()), Length(THICKNESS_MM)) }
                .toImmutableList(),
        corners =
            corners
                .split(' ')
                .filter(String::isNotEmpty)
                .map(::cornerOf)
                .toImmutableList(),
        diagonals =
            diagonals
                .split(' ')
                .filter(String::isNotEmpty)
                .mapIndexed { i, spec ->
                    val (from, to, mm) = spec.split('-', '=')
                    Diagonal(DiagonalId("$id-d$i"), from.toInt(), to.toInt(), Length(mm.toLong()))
                }.toImmutableList(),
        isClosed = closed,
    )

private fun cornerOf(token: String): CornerSpec =
    when (token[0]) {
        'F' -> CornerSpec.Unspecified
        'L' -> CornerSpec.DeclaredRight(TurnDirection.LEFT)
        'R' -> CornerSpec.DeclaredRight(TurnDirection.RIGHT)
        'M' -> CornerSpec.Measured(TurnDirection.LEFT, Angle(token.drop(1).toInt()))
        else -> CornerSpec.Measured(TurnDirection.RIGHT, Angle(token.drop(1).toInt()))
    }

private const val THICKNESS_MM = 120L
