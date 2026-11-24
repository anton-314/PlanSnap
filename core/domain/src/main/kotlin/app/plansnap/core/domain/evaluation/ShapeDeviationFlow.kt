package app.plansnap.core.domain.evaluation

import app.plansnap.core.geometry.adjustment.AdjustedShape
import app.plansnap.core.geometry.adjustment.AdjustmentMode
import app.plansnap.core.geometry.determinacy.ConstraintAnalysis
import app.plansnap.core.geometry.determinacy.DeterminacyAnalyzer
import app.plansnap.core.geometry.determinacy.MeasurementControl
import app.plansnap.core.geometry.primitives.GeometryTolerances
import app.plansnap.core.geometry.solver.ShapeSolution
import app.plansnap.core.geometry.solver.ShapeSolver
import app.plansnap.core.geometry.solver.SolverOutcome
import app.plansnap.core.geometry.statistics.ChiSquareLevel
import app.plansnap.core.geometry.statistics.ChiSquareLimits
import app.plansnap.core.model.ConflictReason

/**
 * Decision flow for a shape deviation `e > T` (DOMAIN section 5.5, ADR 0018).
 *
 * Routing by `analyzeConstraints` at the target turns: `m = ρ` starts at A1, `m > ρ` at B1. Every `NoSimpleShape` of
 * A1 or B1 continues at B2 with the start at the target turns. The first step that applies decides.
 */
internal class ShapeDeviationFlow(
    private val solver: ShapeSolver,
    private val adjustment: AdjustmentStep,
    private val analyzer: DeterminacyAnalyzer,
    private val shapes: ShapeBuilder,
) {
    /** Evaluates [room] with `e > T`. */
    fun evaluate(room: ClosedRoom): RoomEvaluation {
        val analysis = analyzer.analyzeConstraints(room.input, room.targetTurnsRad, room.signal)
        // A1 and B1. In branch B with v = 0 the observations are the start and the solver does not run.
        val skipSolver = analysis.isOverdetermined && analysis.freeVariables == 0
        val solved = if (skipSolver) null else solve(room, analysis)
        return when {
            solved == null -> {
                adjust(room, analysis, room.observedTurnsRad, emptyList())
            }

            !analysis.isOverdetermined -> {
                branchA(room, solved)
            }

            else -> {
                val start = ShapeRules.withDerivedClosing(room.windingSign, solved.solution.turnsRad)
                adjust(room, analysis, start, solved.alternatives)
            }
        }
    }

    private fun solve(
        room: ClosedRoom,
        analysis: ConstraintAnalysis,
    ): SolverOutcome.Solved? =
        solver.solve(room.input, analysis.independentSelection, room.signal) as? SolverOutcome.Solved

    /** Steps A2 to A5 on the solver result of A1, displayed with the raw lengths. */
    private fun branchA(
        room: ClosedRoom,
        solved: SolverOutcome.Solved,
    ): RoomEvaluation {
        val solution = solved.solution
        val determinacy = analyzer.analyze(room.input, room.rawLengthsMm, solution.turnsRad, room.signal)
        val turns = ShapeRules.withDerivedClosing(room.windingSign, solution.turnsRad)
        val shape = shapes.shape(solution.outline, room.rawLengthsMm, turns)
        return when {
            determinacy.missing > 0 -> {
                room.proposed(shape, ProposalReason.UNDERDETERMINED, determinacy, null, null)
            }

            ShapeRules.violatesDirections(room.input, turns) -> {
                room.conflict(ConflictReason.TURN_DIRECTION_VIOLATED)
            }

            else -> {
                val ambiguity =
                    analyzer.analyzeAmbiguity(
                        room.input,
                        solution.outline,
                        solved.alternatives,
                        room.limitMm,
                        room.signal,
                    )
                if (ambiguity == null) {
                    room.confirmed(shape, MeasurementControl(redundancy = 0, uncontrolled = emptyList()))
                } else {
                    room.proposed(shape, ProposalReason.AMBIGUOUS_SHAPE, determinacy, ambiguity, null)
                }
            }
        }
    }

    /** Steps B2 and B3: adjustment in `SHAPE_DEVIATION` mode from [start], then simplicity. */
    private fun adjust(
        room: ClosedRoom,
        analysis: ConstraintAnalysis,
        start: List<Double>,
        alternatives: List<ShapeSolution>,
    ): RoomEvaluation =
        when (val step = adjustment.run(room, AdjustmentMode.SHAPE_DEVIATION, start)) {
            is AdjustmentStep.Failed -> room.conflict(step.reason)
            is AdjustmentStep.Done -> decide(room, analysis, step.shape, step.adjusted, alternatives)
        }

    /** Steps B4 to B9 on the adjusted [shape]. */
    private fun decide(
        room: ClosedRoom,
        analysis: ConstraintAnalysis,
        shape: EvaluatedShape,
        adjusted: AdjustedShape,
        alternatives: List<ShapeSolution>,
    ): RoomEvaluation {
        val determinacy =
            analyzer.analyze(room.input, adjusted.adjustedLengthsMm, adjusted.adjustedTurnsRad, room.signal)
        val r = analysis.conditionCount - determinacy.rank
        val large = r >= 1 && adjusted.correctionSumMm > room.limitMm
        val q2 = adjusted.normalizedSquareSum
        val correction =
            CorrectionSummary(
                correctionSumMm = adjusted.correctionSumMm,
                normalizedSquareSum = q2.takeIf { r >= 1 },
                maxCorrectionMm = adjusted.maxCorrectionMm,
                largestCorrection = adjusted.largestCorrection,
            )
        // Section 5.6 runs only after the conflict test and the direction check, so it is evaluated on demand.
        val ambiguity by lazy(LazyThreadSafetyMode.NONE) {
            analyzer.analyzeAmbiguity(room.input, shape.outline, alternatives, room.limitMm, room.signal)
        }
        return when {
            large && q2 > ChiSquareLimits.quantile(ChiSquareLevel.CONFLICT, r) -> {
                room.conflict(ConflictReason.MEASUREMENTS_INCONSISTENT, worstObservation = adjusted.worstObservation)
            }

            determinacy.missing == 0 && ShapeRules.violatesDirections(room.input, adjusted.adjustedTurnsRad) -> {
                room.conflict(ConflictReason.TURN_DIRECTION_VIOLATED)
            }

            determinacy.missing > 0 -> {
                room.proposed(shape, ProposalReason.UNDERDETERMINED, determinacy, null, correction)
            }

            ambiguity != null -> {
                room.proposed(shape, ProposalReason.AMBIGUOUS_SHAPE, determinacy, ambiguity, correction)
            }

            else -> {
                confirmOrAdjust(room, shape, adjusted, r, large)
            }
        }
    }

    /** Steps B8 and B9 with the controllability of the measurements, which never changes the status. */
    private fun confirmOrAdjust(
        room: ClosedRoom,
        shape: EvaluatedShape,
        adjusted: AdjustedShape,
        r: Int,
        large: Boolean,
    ): RoomEvaluation {
        val control = analyzer.analyzeControl(room.input, adjusted, r, room.limitMm, room.signal)
        if (adjusted.maxCorrectionMm <= GeometryTolerances.EXACT_CLOSURE_MM) return room.confirmed(shape, control)
        val explicit =
            large && adjusted.normalizedSquareSum > ChiSquareLimits.quantile(ChiSquareLevel.EXPLICIT_CONFIRMATION, r)
        val confirmation = if (explicit) AdjustmentConfirmation.EXPLICIT else AdjustmentConfirmation.SIMPLE
        return room.needsAdjustment(shape, adjusted, adjustment.version, confirmation, control)
    }
}
