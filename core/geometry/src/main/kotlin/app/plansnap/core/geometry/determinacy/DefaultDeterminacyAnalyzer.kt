package app.plansnap.core.geometry.determinacy

import app.plansnap.core.geometry.adjustment.AdjustedShape
import app.plansnap.core.geometry.cancellation.CancellationSignal
import app.plansnap.core.geometry.primitives.GeometryTolerances
import app.plansnap.core.geometry.primitives.Polygon2
import app.plansnap.core.geometry.solver.ShapeSolution
import app.plansnap.core.geometry.solver.SolverProblem
import app.plansnap.core.geometry.traverse.ShapeInput

/**
 * Stateless [DeterminacyAnalyzer] after DOMAIN sections 5.4 to 5.6 and ADR 0018, ported from the reference prototype
 * in `docs/research/evaluation-prototype/flow.py`.
 *
 * Ranks come from Gaussian elimination with complete pivoting on the Jacobian of the hard conditions by the free turns
 * `u_frei`, with the rows in the fixed order of DOMAIN section 5.4. A diagonal between adjacent corners counts in `m`
 * but never in a rank. Every result is bit-identical for equal arguments: dense matrices with a fixed loop order, no
 * random numbers and [StrictMath] for all trigonometry and roots (DOMAIN section 11).
 */
class DefaultDeterminacyAnalyzer : DeterminacyAnalyzer {
    /**
     * Routing analysis without solving (see [DeterminacyAnalyzer.analyzeConstraints]).
     *
     * @throws IllegalArgumentException if [turnsRad] does not have one finite value per wall.
     */
    override fun analyzeConstraints(
        input: ShapeInput,
        turnsRad: List<Double>,
        cancellation: CancellationSignal,
    ): ConstraintAnalysis {
        requireTurns(input, turnsRad)
        val problem = SolverProblem(input)
        val conditionCount = CLOSURE_CONDITIONS + (if (problem.closingMeasured) 1 else 0) + input.diagonals.size
        val free = problem.free
        if (free.isEmpty()) return ConstraintAnalysis(0, conditionCount, emptyList())
        val rows = problem.rows.map { RankRow.Condition(it) }
        val lengths = problem.lengths
        val target = turnsRad.toDoubleArray()
        var jacobian = rankJacobian(input, free, rows, WalkedChain(lengths, target))
        val targetRank = rankOf(jacobian, GeometryTolerances.RANK_TOLERANCE, cancellation)
        if (targetRank < conditionCount) {
            val probe = target.copyOf()
            for (i in free) probe[i] = target[i] + GeometryTolerances.RANK_PROBE_RAD * i / input.wallCount
            val probeJacobian = rankJacobian(input, free, rows, WalkedChain(lengths, probe))
            if (rankOf(probeJacobian, GeometryTolerances.RANK_TOLERANCE, cancellation) > targetRank) {
                jacobian = probeJacobian
            }
        }
        val selection = independentRows(jacobian, GeometryTolerances.RANK_TOLERANCE, cancellation)
        return ConstraintAnalysis(free.size, conditionCount, selection.map { problem.rows[it] })
    }

    /**
     * Rank at a closed shape with its recommendation (see [DeterminacyAnalyzer.analyze]).
     *
     * While measurements are missing, the recommendation is the longest diagonal between non-adjacent corners that is
     * not measured yet and raises the rank, ties within `SOLVER_RESIDUAL_MM` to the smallest `(a, b)`. Only if no
     * diagonal raises the rank, it is the angle at the first corner by index that is not measured and lowers
     * `missing`: at the closing corner a new hard condition, at an inner corner the removal of its free variable.
     *
     * @throws IllegalArgumentException if [lengthsMm] or [turnsRad] does not have one finite value per wall, or a
     * length is not positive.
     */
    override fun analyze(
        input: ShapeInput,
        lengthsMm: List<Double>,
        turnsRad: List<Double>,
        cancellation: CancellationSignal,
    ): Determinacy {
        requireTurns(input, turnsRad)
        require(lengthsMm.size == input.wallCount && lengthsMm.all { it.isFinite() && it > 0.0 }) {
            "Expected ${input.wallCount} positive finite lengths"
        }
        val problem = SolverProblem(input)
        val free = problem.free
        if (free.isEmpty()) return Determinacy(0, 0, null)
        val chain = WalkedChain(lengthsMm.toDoubleArray(), turnsRad.toDoubleArray())
        val solutionRank = SolutionRank(input, problem, chain, cancellation)
        val recommendation = if (solutionRank.rank < free.size) solutionRank.recommendation() else null
        return Determinacy(free.size, solutionRank.rank, recommendation)
    }

    /**
     * Ambiguity of a determined room (see [DeterminacyAnalyzer.analyzeAmbiguity]).
     *
     * The turn `τ_0` of both shapes for the recommendation is measured at their outlines, between the edges into and
     * out of `V_0`.
     *
     * @throws IllegalArgumentException if [limitMm] is not positive and finite, or an outline does not have one vertex
     * per wall.
     */
    override fun analyzeAmbiguity(
        input: ShapeInput,
        outline: Polygon2,
        alternatives: List<ShapeSolution>,
        limitMm: Double,
        cancellation: CancellationSignal,
    ): ShapeAmbiguity? = AmbiguityAnalysis(input, outline, limitMm).farthest(alternatives, cancellation)

    /**
     * Controllability of the measurements (see [DeterminacyAnalyzer.analyzeControl]).
     *
     * The mode of the adjustment follows from [shape]: declared right angles with a redundancy number were
     * observations (`MEASUREMENT_ERROR`). Candidates for a recommended measurement are the diagonals between
     * non-adjacent corners that are not measured, longest at the adjusted shape first, ties within
     * `SOLVER_RESIDUAL_MM` to the smallest `(a, b)`, then the corners without an observation in the adjustment by
     * index, measured with `σ = SIGMA_MEASURED_ANGLE_DEG`. A candidate is evaluated at the adjusted shape with the
     * redundancy numbers of the extended adjustment.
     *
     * @throws IllegalArgumentException if [redundancy] is negative, [limitMm] is not positive and finite, or [shape]
     * does not belong to [input].
     */
    override fun analyzeControl(
        input: ShapeInput,
        shape: AdjustedShape,
        redundancy: Int,
        limitMm: Double,
        cancellation: CancellationSignal,
    ): MeasurementControl {
        require(redundancy >= 0) { "Redundancy must not be negative: $redundancy" }
        require(limitMm.isFinite() && limitMm > 0.0) { "Invalid limit: $limitMm" }
        require(shape.adjustedLengthsMm.size == input.wallCount) { "Shape does not belong to the input" }
        require(shape.adjustedDiagonalsMm.size == input.diagonals.size) { "Shape does not belong to the input" }
        if (redundancy == 0) return MeasurementControl(0, emptyList())
        return MeasurementControl(
            redundancy,
            ControlAnalysis(input, shape, limitMm).uncontrolled(redundancy, cancellation),
        )
    }

    private companion object {
        const val CLOSURE_CONDITIONS = 2

        fun requireTurns(
            input: ShapeInput,
            turnsRad: List<Double>,
        ) {
            require(turnsRad.size == input.wallCount && turnsRad.all { it.isFinite() }) {
                "Expected ${input.wallCount} finite turns"
            }
        }
    }
}
