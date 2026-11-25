package app.plansnap.core.domain.evaluation

import app.plansnap.core.common.result.DomainResult
import app.plansnap.core.common.runtime.DispatcherProvider
import app.plansnap.core.geometry.adjustment.AdjustmentMode
import app.plansnap.core.geometry.adjustment.ClosureAdjuster
import app.plansnap.core.geometry.adjustment.DefaultClosureAdjuster
import app.plansnap.core.geometry.cancellation.CancellationSignal
import app.plansnap.core.geometry.determinacy.DefaultDeterminacyAnalyzer
import app.plansnap.core.geometry.determinacy.DeterminacyAnalyzer
import app.plansnap.core.geometry.polygon.DefaultPolygonAnalyzer
import app.plansnap.core.geometry.polygon.PolygonAnalyzer
import app.plansnap.core.geometry.primitives.ClosureTolerance
import app.plansnap.core.geometry.solver.DefaultShapeSolver
import app.plansnap.core.geometry.solver.ShapeSolver
import app.plansnap.core.geometry.traverse.ClosureClass
import app.plansnap.core.geometry.traverse.DefaultTraverse
import app.plansnap.core.geometry.traverse.Traverse
import app.plansnap.core.model.ConflictReason
import app.plansnap.core.model.Room
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.withContext

/**
 * [RoomEvaluator] after DOMAIN section 4 and the decision flow of DOMAIN section 5.5 (ADR 0018).
 *
 * The steps run in this order and the first that applies decides:
 *
 * 1. Value limits of `GeometryTolerances`: a wall length outside `MIN_WALL_MM..MAX_WALL_MM` or a thickness above
 *    `MAX_THICKNESS_MM` yields `InvalidValue`, more than `MAX_WALLS_PER_ROOM` walls `RuleViolation(TOO_MANY_WALLS)`.
 *    A room that is not closed is [RoomEvaluation.Open].
 * 2. A stored overlay with matching fingerprint that is structurally valid (DOMAIN section 6) yields
 *    [RoomEvaluation.Adjusted] at once. Nothing is recomputed, solver version and tolerance do not matter.
 * 3. Necessary conditions, then the target shape, the limit `T` and the branch into exact closure, measurement error
 *    and shape deviation.
 *
 * Every result is bit-identical for equal arguments: the geometry core is deterministic and this class keeps no state.
 * The work runs on [DispatcherProvider.default] and passes [currentCancellationSignal] to solver, adjustment and rank
 * analysis, so cancelling the calling coroutine stops the evaluation without a partial result (ADR 0016).
 *
 * @param dispatchers source of the default dispatcher.
 * @param traverse target turns, closure error and walks.
 * @param solver shape solver of DOMAIN sections 5.1 to 5.3.
 * @param adjuster closure adjustment of DOMAIN section 6. Its version is recorded in every proposed overlay.
 * @param analyzer rank, ambiguity and controllability analysis of DOMAIN sections 5.4 to 5.6.
 * @param polygons simplicity and area of DOMAIN section 7.
 */
class DefaultRoomEvaluator(
    private val dispatchers: DispatcherProvider,
    private val traverse: Traverse = DefaultTraverse(),
    solver: ShapeSolver = DefaultShapeSolver(),
    adjuster: ClosureAdjuster = DefaultClosureAdjuster(),
    private val analyzer: DeterminacyAnalyzer = DefaultDeterminacyAnalyzer(),
    polygons: PolygonAnalyzer = DefaultPolygonAnalyzer(),
) : RoomEvaluator {
    private val shapes = ShapeBuilder(traverse, polygons)
    private val adjustment = AdjustmentStep(adjuster, shapes)
    private val shapeDeviation = ShapeDeviationFlow(solver, adjustment, analyzer, shapes)

    override suspend fun evaluate(
        room: Room,
        tolerance: ClosureTolerance,
    ): DomainResult<RoomEvaluation> =
        withContext(dispatchers.default) {
            val signal = currentCancellationSignal()
            signal.checkpoint()
            when (val violation = room.limitViolation()) {
                null -> DomainResult.Success(evaluateValid(room, tolerance, signal))
                else -> DomainResult.Failure(violation)
            }
        }

    /** Steps 1 and 2 of DOMAIN section 4 for a room within the value limits. */
    private fun evaluateValid(
        room: Room,
        tolerance: ClosureTolerance,
        signal: CancellationSignal,
    ): RoomEvaluation {
        if (!room.isClosed) return open(room)
        val overlay = shapes.acceptedOverlay(room)
        return overlay?.let { RoomEvaluation.Adjusted(room.id, it.shape, EffectiveWallLength.statuses(room, it)) }
            ?: recompute(room, tolerance, signal)
    }

    /** Steps 3 to 6 of DOMAIN section 4 on the raw values of a closed room. */
    private fun recompute(
        room: Room,
        tolerance: ClosureTolerance,
        signal: CancellationSignal,
    ): RoomEvaluation {
        val input = room.toShapeInput()
        val closed =
            ClosedRoom(
                room = room,
                input = input,
                closureError = traverse.closureError(input),
                limitMm = tolerance.limitMm(input.lengthsMm.sum().toDouble()),
                targets = traverse.targets(input),
                signal = signal,
            )
        val necessary = ShapeRules.necessaryViolation(input)
        return when {
            necessary != null -> {
                closed.conflict(necessary.reason, necessary.diagonalIndex)
            }

            else -> {
                when (traverse.classify(input, closed.closureError, tolerance)) {
                    ClosureClass.EXACT -> exact(closed)
                    ClosureClass.MEASUREMENT_ERROR -> measurementError(closed)
                    ClosureClass.SHAPE_DEVIATION -> shapeDeviation.evaluate(closed)
                }
            }
        }
    }

    /** Open chain walked from `V_0` with the raw lengths and the entered turns. */
    private fun open(room: Room): RoomEvaluation.Open {
        val lengths = room.walls.map { it.length.mm.toDouble() }
        return RoomEvaluation.Open(
            roomId = room.id,
            chain = traverse.walk(lengths, room.enteredTurnsRad()).toImmutableList(),
            openingStatuses = EffectiveWallLength.statuses(room, overlay = null),
        )
    }

    /** `e ≤ EXACT_CLOSURE_MM`: the target shape is confirmed if it is simple (DOMAIN section 4, step 6). */
    private fun exact(room: ClosedRoom): RoomEvaluation {
        val turns = ShapeRules.withDerivedClosing(room.windingSign, room.targetTurnsRad)
        val outline = shapes.outline(room.rawLengthsMm, turns)
        return if (shapes.isSimple(outline)) {
            room.confirmed(shapes.shape(outline, room.rawLengthsMm, turns), control = null)
        } else {
            room.conflict(ConflictReason.NO_SIMPLE_SHAPE)
        }
    }

    /**
     * `e ≤ T`: adjustment in `MEASUREMENT_ERROR` mode from the observations, always with simple confirmation. The
     * redundancy for the controllability is `r = 2 + [closing corner fixed] + number of diagonals`.
     */
    private fun measurementError(room: ClosedRoom): RoomEvaluation =
        when (val step = adjustment.run(room, AdjustmentMode.MEASUREMENT_ERROR, room.observedTurnsRad)) {
            is AdjustmentStep.Failed -> {
                room.conflict(step.reason)
            }

            is AdjustmentStep.Done -> {
                val redundancy = CLOSURE_CONDITIONS + (if (room.isClosingFixed) 1 else 0) + room.input.diagonals.size
                room.needsAdjustment(
                    shape = step.shape,
                    adjusted = step.adjusted,
                    version = adjustment.version,
                    confirmation = AdjustmentConfirmation.SIMPLE,
                    control = analyzer.analyzeControl(room.input, step.adjusted, redundancy, room.limitMm, room.signal),
                )
            }
        }

    private companion object {
        /** Closure in x and y. */
        const val CLOSURE_CONDITIONS = 2
    }
}
