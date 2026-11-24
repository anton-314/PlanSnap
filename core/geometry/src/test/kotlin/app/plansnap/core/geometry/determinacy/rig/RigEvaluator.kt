package app.plansnap.core.geometry.determinacy.rig

import app.plansnap.core.domain.evaluation.AdjustmentConfirmation
import app.plansnap.core.domain.evaluation.ProposalReason
import app.plansnap.core.geometry.adjustment.AdjustedShape
import app.plansnap.core.geometry.adjustment.AdjustmentMode
import app.plansnap.core.geometry.adjustment.AdjustmentOutcome
import app.plansnap.core.geometry.adjustment.DefaultClosureAdjuster
import app.plansnap.core.geometry.cancellation.CancellationSignal
import app.plansnap.core.geometry.determinacy.ConstraintAnalysis
import app.plansnap.core.geometry.determinacy.DefaultDeterminacyAnalyzer
import app.plansnap.core.geometry.determinacy.Determinacy
import app.plansnap.core.geometry.determinacy.MeasurementControl
import app.plansnap.core.geometry.determinacy.ShapeAmbiguity
import app.plansnap.core.geometry.polygon.DefaultPolygonAnalyzer
import app.plansnap.core.geometry.primitives.ClosureTolerance
import app.plansnap.core.geometry.primitives.GeometryTolerances
import app.plansnap.core.geometry.primitives.Polygon2
import app.plansnap.core.geometry.solver.DefaultShapeSolver
import app.plansnap.core.geometry.solver.ShapeSolution
import app.plansnap.core.geometry.solver.SolverOutcome
import app.plansnap.core.geometry.solver.SolverProblem
import app.plansnap.core.geometry.solver.violatesDirections
import app.plansnap.core.geometry.statistics.ChiSquareLevel
import app.plansnap.core.geometry.statistics.ChiSquareLimits
import app.plansnap.core.geometry.traverse.ClosureClass
import app.plansnap.core.geometry.traverse.CornerInput
import app.plansnap.core.geometry.traverse.DefaultTraverse
import app.plansnap.core.geometry.traverse.ShapeInput
import app.plansnap.core.model.ConflictReason
import app.plansnap.core.model.ShapeStatus

/**
 * Result of one rig evaluation.
 *
 * @property path step of DOMAIN sections 4 and 5.5 that decided, as in `flow.py`.
 * @property lengthsMm lengths of the displayed shape, `null` without a shape.
 * @property turnsRad turns of the displayed shape, `null` without a shape.
 * @property redundancy `r` of the path, `null` if the path has none.
 * @property instead status the room would have without the ambiguity rule, only for [ProposalReason.AMBIGUOUS_SHAPE].
 */
internal data class RigResult(
    val status: ShapeStatus,
    val path: String,
    val conflict: ConflictReason? = null,
    val proposal: ProposalReason? = null,
    val lengthsMm: List<Double>? = null,
    val turnsRad: List<Double>? = null,
    val constraints: ConstraintAnalysis? = null,
    val determinacy: Determinacy? = null,
    val redundancy: Int? = null,
    val shape: AdjustedShape? = null,
    val confirmation: AdjustmentConfirmation? = null,
    val control: MeasurementControl? = null,
    val ambiguity: ShapeAmbiguity? = null,
    val instead: ShapeStatus? = null,
)

/**
 * Test-side port of `flow.evaluate` (DOMAIN sections 4 and 5.5) on the production components: traverse, solver,
 * adjustment and determinacy analysis. It stands in for the evaluation of `:core:domain` until T-DOM-001.
 */
@Suppress("ReturnCount") // Each step of DOMAIN sections 4 and 5.5 returns as soon as it decides, as in `flow.py`.
internal class RigEvaluator(
    private val tolerance: ClosureTolerance = ClosureTolerance(),
) {
    private val traverse = DefaultTraverse()
    private val solver = DefaultShapeSolver()
    private val adjuster = DefaultClosureAdjuster()
    private val analyzer = DefaultDeterminacyAnalyzer()
    private val polygons = DefaultPolygonAnalyzer()
    private val none = CancellationSignal.NONE

    /** Evaluates [input] after the steps of DOMAIN section 4. */
    fun evaluate(input: ShapeInput): RigResult {
        RigNecessary.violation(input)?.let { return RigResult(ShapeStatus.CONFLICT, "N", conflict = it) }
        val raw = input.lengthsMm.map { it.toDouble() }
        val limit = tolerance.limitMm(raw.sum())
        val target = targetShapeTurns(input)
        return when (traverse.classify(input, traverse.closureError(input), tolerance)) {
            ClosureClass.EXACT -> exact(raw, target)
            ClosureClass.MEASUREMENT_ERROR -> measurementError(input, target, limit)
            ClosureClass.SHAPE_DEVIATION -> shapeDeviation(input, target, limit)
        }
    }

    private fun exact(
        raw: List<Double>,
        target: List<Double>,
    ): RigResult =
        if (polygons.isSimple(outline(raw, target))) {
            RigResult(ShapeStatus.CONFIRMED, "E", lengthsMm = raw, turnsRad = target)
        } else {
            RigResult(ShapeStatus.CONFLICT, "E", ConflictReason.NO_SIMPLE_SHAPE)
        }

    private fun measurementError(
        input: ShapeInput,
        target: List<Double>,
        limit: Double,
    ): RigResult {
        val outcome = adjuster.adjust(input, AdjustmentMode.MEASUREMENT_ERROR, target, none)
        val shape = (outcome as? AdjustmentOutcome.Adjusted)?.shape
        return when {
            shape == null -> {
                RigResult(ShapeStatus.CONFLICT, "M", ConflictReason.ADJUSTMENT_NOT_CONVERGED)
            }

            !polygons.isSimple(outline(shape)) -> {
                RigResult(ShapeStatus.CONFLICT, "M", ConflictReason.NO_SIMPLE_SHAPE)
            }

            else -> {
                val closingFixed = if (input.corners[0] == CornerInput.Free) 0 else 1
                val r = 2 + closingFixed + input.diagonals.size
                RigResult(
                    ShapeStatus.NEEDS_ADJUSTMENT,
                    "M",
                    lengthsMm = shape.adjustedLengthsMm,
                    turnsRad = shape.adjustedTurnsRad,
                    redundancy = r,
                    shape = shape,
                    confirmation = AdjustmentConfirmation.SIMPLE,
                    control = analyzer.analyzeControl(input, shape, r, limit, none),
                )
            }
        }
    }

    private fun shapeDeviation(
        input: ShapeInput,
        target: List<Double>,
        limit: Double,
    ): RigResult {
        val targets = traverse.targets(input).targetTurns.map { it.toRadians() }
        val analysis = analyzer.analyzeConstraints(input, targets, none)
        val branch = BranchB(input, analysis, limit)
        if (analysis.conditionCount == analysis.independentConditions) {
            val solved = solver.solve(input, analysis.independentSelection, none) as? SolverOutcome.Solved
            return if (solved == null) branch.run(target, "A>B") else branchA(input, analysis, solved, limit)
        }
        if (analysis.freeVariables == 0) return branch.run(target, "B")
        val solved =
            solver.solve(input, analysis.independentSelection, none) as? SolverOutcome.Solved
                ?: return branch.run(target, "B1>B")
        val start = solved.solution.turnsRad.toMutableList()
        start[0] = closingDerived(input, start)
        return branch.run(start, "B", solved.alternatives)
    }

    private fun branchA(
        input: ShapeInput,
        analysis: ConstraintAnalysis,
        solved: SolverOutcome.Solved,
        limit: Double,
    ): RigResult {
        val raw = input.lengthsMm.map { it.toDouble() }
        val solution = solved.solution
        val determinacy = analyzer.analyze(input, raw, solution.turnsRad, none)
        val base =
            RigResult(
                ShapeStatus.CONFIRMED,
                "A5",
                lengthsMm = raw,
                turnsRad = solution.turnsRad,
                constraints = analysis,
                determinacy = determinacy,
                redundancy = analysis.conditionCount - determinacy.rank,
            )
        if (determinacy.missing > 0) {
            return base.copy(status = ShapeStatus.PROPOSED, path = "A2", proposal = ProposalReason.UNDERDETERMINED)
        }
        if (violates(input, solution.turnsRad)) {
            return base.copy(
                status = ShapeStatus.CONFLICT,
                path = "A3",
                conflict = ConflictReason.TURN_DIRECTION_VIOLATED,
            )
        }
        val ambiguity = analyzer.analyzeAmbiguity(input, solution.outline, solved.alternatives, limit, none)
        return if (ambiguity == null) {
            base
        } else {
            base.copy(
                status = ShapeStatus.PROPOSED,
                path = "A4",
                proposal = ProposalReason.AMBIGUOUS_SHAPE,
                ambiguity = ambiguity,
                instead = ShapeStatus.CONFIRMED,
            )
        }
    }

    /** Steps B2 to B9 for one start. */
    private inner class BranchB(
        private val input: ShapeInput,
        private val analysis: ConstraintAnalysis,
        private val limit: Double,
    ) {
        fun run(
            start: List<Double>,
            path: String,
            alternatives: List<ShapeSolution> = emptyList(),
        ): RigResult {
            val outcome = adjuster.adjust(input, AdjustmentMode.SHAPE_DEVIATION, start, none)
            val shape =
                (outcome as? AdjustmentOutcome.Adjusted)?.shape
                    ?: return RigResult(ShapeStatus.CONFLICT, "$path/B2", ConflictReason.ADJUSTMENT_NOT_CONVERGED)
            if (!polygons.isSimple(outline(shape))) {
                return RigResult(ShapeStatus.CONFLICT, "$path/B3", ConflictReason.NO_SIMPLE_SHAPE)
            }
            val determinacy = analyzer.analyze(input, shape.adjustedLengthsMm, shape.adjustedTurnsRad, none)
            val r = analysis.conditionCount - determinacy.rank
            val base =
                RigResult(
                    ShapeStatus.CONFIRMED,
                    "$path/B8",
                    lengthsMm = shape.adjustedLengthsMm,
                    turnsRad = shape.adjustedTurnsRad,
                    constraints = analysis,
                    determinacy = determinacy,
                    redundancy = r,
                    shape = shape,
                )
            return decide(base, shape, determinacy, r, alternatives)
        }

        private fun decide(
            base: RigResult,
            shape: AdjustedShape,
            determinacy: Determinacy,
            r: Int,
            alternatives: List<ShapeSolution>,
        ): RigResult {
            val path = base.path.removeSuffix("/B8")
            val large = r >= 1 && shape.correctionSumMm > limit
            val q2 = shape.normalizedSquareSum
            if (large && q2 > ChiSquareLimits.quantile(ChiSquareLevel.CONFLICT, r)) {
                return base.copy(ShapeStatus.CONFLICT, "$path/B5", ConflictReason.MEASUREMENTS_INCONSISTENT)
            }
            if (determinacy.missing == 0 && violates(input, shape.adjustedTurnsRad)) {
                return base.copy(ShapeStatus.CONFLICT, "$path/B6", ConflictReason.TURN_DIRECTION_VIOLATED)
            }
            if (determinacy.missing > 0) {
                return base.copy(
                    status = ShapeStatus.PROPOSED,
                    path = "$path/B7",
                    proposal = ProposalReason.UNDERDETERMINED,
                )
            }
            val exact = shape.maxCorrectionMm <= GeometryTolerances.EXACT_CLOSURE_MM
            val ambiguity = analyzer.analyzeAmbiguity(input, outline(shape), alternatives, limit, none)
            if (ambiguity != null) {
                return base.copy(
                    status = ShapeStatus.PROPOSED,
                    path = "$path/B7",
                    proposal = ProposalReason.AMBIGUOUS_SHAPE,
                    ambiguity = ambiguity,
                    instead = if (exact) ShapeStatus.CONFIRMED else ShapeStatus.NEEDS_ADJUSTMENT,
                )
            }
            val control = analyzer.analyzeControl(input, shape, r, limit, none)
            if (exact) return base.copy(control = control)
            val explicit = large && q2 > ChiSquareLimits.quantile(ChiSquareLevel.EXPLICIT_CONFIRMATION, r)
            return base.copy(
                status = ShapeStatus.NEEDS_ADJUSTMENT,
                path = "$path/B9",
                confirmation = if (explicit) AdjustmentConfirmation.EXPLICIT else AdjustmentConfirmation.SIMPLE,
                control = control,
            )
        }
    }

    private fun violates(
        input: ShapeInput,
        turns: List<Double>,
    ): Boolean = SolverProblem(input).violatesDirections(turns.toDoubleArray())

    private fun targetShapeTurns(input: ShapeInput): List<Double> {
        val turns =
            traverse
                .targets(input)
                .targetTurns
                .map { it.toRadians() }
                .toMutableList()
        if (input.corners[0] == CornerInput.Free) turns[0] = closingDerived(input, turns)
        return turns
    }

    private fun closingDerived(
        input: ShapeInput,
        turns: List<Double>,
    ): Double = 2.0 * StrictMath.PI * traverse.targets(input).winding.sign - turns.drop(1).sum()

    private fun outline(
        lengths: List<Double>,
        turns: List<Double>,
    ): Polygon2 = Polygon2(traverse.walk(lengths, turns).dropLast(1))

    private fun outline(shape: AdjustedShape): Polygon2 = outline(shape.adjustedLengthsMm, shape.adjustedTurnsRad)
}

/** Necessary conditions of DOMAIN section 4, step 3, as `flow.necessary` with an optional rounding allowance. */
internal object RigNecessary {
    @Suppress("ReturnCount") // The first violated condition decides, as in DOMAIN section 4, step 3.
    fun violation(
        input: ShapeInput,
        relaxMm: Double = 0.0,
    ): ConflictReason? {
        val lengths = input.lengthsMm.map { it.toDouble() }
        val n = lengths.size
        val longest = lengths.max()
        if (longest >= lengths.sum() - longest - GeometryTolerances.MIN_FEATURE_MM) return ConflictReason.WALL_TOO_LONG
        for (diagonal in input.diagonals) {
            val a = minOf(diagonal.fromCorner, diagonal.toCorner)
            val b = maxOf(diagonal.fromCorner, diagonal.toCorner)
            if (b == a + 1 || (a == 0 && b == n - 1)) continue
            val d = diagonal.lengthMm.toDouble()
            for (chain in listOf(lengths.subList(a, b), lengths.subList(b, n) + lengths.subList(0, a))) {
                val total = chain.sum()
                if (d > total + relaxMm ||
                    d < 2 * chain.max() - total - relaxMm
                ) {
                    return ConflictReason.DIAGONAL_IMPOSSIBLE
                }
            }
        }
        return null
    }
}
