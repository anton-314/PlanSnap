package app.plansnap.core.geometry.solver

import app.plansnap.core.geometry.cancellation.CancellationSignal
import app.plansnap.core.geometry.primitives.GeometryTolerances
import app.plansnap.core.geometry.primitives.Polygon2
import app.plansnap.core.geometry.traverse.ShapeInput

/**
 * Stateless [ShapeSolver] after DOMAIN sections 5.1 to 5.3 and 5.6, ported from the reference prototype in
 * `docs/research/evaluation-prototype/flow.py` with the production constants of `GeometryTolerances`.
 *
 * Every start value runs twice, directly and after the start refinement. If [solve] receives fewer conditions than
 * there are rows, the results are refined over all rows and serve only as linearization points. Results are
 * bit-identical for equal arguments: there are no random numbers, all trigonometry and roots use [StrictMath], and
 * every loop runs in a fixed order (DOMAIN section 11).
 */
class DefaultShapeSolver : ShapeSolver {
    /**
     * Solves [input] under [conditions] (see [ShapeSolver.solve]).
     *
     * [conditions] may come in any order, the solver uses the fixed row order of DOMAIN section 5.4. Without a free
     * variable, that is with every inner corner measured, there is nothing to solve and the result is
     * [SolverOutcome.NoSimpleShape] (step B1 then starts at the observations).
     *
     * @throws IllegalArgumentException if [conditions] contains a duplicate or a condition that is not a row of
     * [input]: a diagonal index out of range, a diagonal between adjacent corners or a closing corner that is not
     * measured.
     * @throws java.util.concurrent.CancellationException if [cancellation] cancels the computation.
     */
    override fun solve(
        input: ShapeInput,
        conditions: List<HardCondition>,
        cancellation: CancellationSignal,
    ): SolverOutcome {
        val problem = SolverProblem(input)
        require(conditions.distinct().size == conditions.size) { "Conditions must be distinct: $conditions" }
        val unknown = conditions.filterNot { it in problem.rows }
        require(unknown.isEmpty()) { "Conditions are not rows of the input: $unknown" }
        val selected = problem.rows.filter { it in conditions }
        val ranked =
            if (problem.free.isEmpty()) {
                emptyList()
            } else {
                collect(
                    problem,
                    selected,
                    cancellation,
                ).sortedWith(SELECTION_ORDER)
            }
        return if (ranked.isEmpty()) {
            SolverOutcome.NoSimpleShape
        } else {
            val alternatives =
                ranked
                    .drop(1)
                    .filter { it.solution.honorsDirections && it.hardResidualMm <= GeometryTolerances.EXACT_CLOSURE_MM }
                    .map { it.solution }
            SolverOutcome.Solved(ranked.first().solution, alternatives)
        }
    }

    /** Runs every start directly and after the start refinement and returns the valid results in start order. */
    private fun collect(
        problem: SolverProblem,
        selected: List<HardCondition>,
        cancellation: CancellationSignal,
    ): List<Candidate> {
        val sqp = SqpIteration(problem, selected, cancellation)
        val refinement = StartRefinement(problem, cancellation)
        val overdetermined = selected.size < problem.rows.size
        val closed = HardCondition.ClosureX in selected && HardCondition.ClosureY in selected
        val candidates = ArrayList<Candidate>()

        fun consider(
            turns: DoubleArray,
            start: SolverStart,
        ) {
            candidate(problem, selected, closed, turns, start)?.let { candidates += it }
        }
        for ((start, turns) in SolverStarts.of(problem)) {
            cancellation.checkpoint()
            sqp.solve(turns)?.let { consider(if (overdetermined) refinement.refine(it) else it, start) }
            val refined = refinement.refine(turns)
            if (overdetermined) {
                consider(refined, start)
            } else {
                sqp.solve(refined)?.let { consider(it, start) }
            }
        }
        return candidates
    }

    /**
     * Builds a candidate, or `null` if its closed outline is not simple. The turns are always finite: the SQP only
     * returns feasible points, and the refinement only accepts a decreasing, hence finite, residual norm.
     */
    private fun candidate(
        problem: SolverProblem,
        selected: List<HardCondition>,
        closed: Boolean,
        rawTurns: DoubleArray,
        start: SolverStart,
    ): Candidate? {
        val turns = problem.candidateTurns(rawTurns)
        val chain = problem.walk(turns)
        if (closed && !problem.isSimple(chain)) return null
        val cost = problem.cost(turns)
        val violates = problem.violatesDirections(turns)
        val solution = ShapeSolution(turns.toList(), Polygon2(chain.vertices()), cost, start, !violates)
        return Candidate(
            solution = solution,
            violatesDirections = violates,
            outsideResidualSteps =
                steps(problem.outsideResidualMm(selected, turns, chain), GeometryTolerances.EXACT_CLOSURE_MM),
            costSteps = steps(cost, GeometryTolerances.COST_TIE),
            roundedVariables =
                DoubleArray(problem.free.size) {
                    steps(turns[problem.free[it]], GeometryTolerances.SOLUTION_ROUNDING_RAD)
                },
            hardResidualMm = problem.hardResidualMm(turns, chain),
        )
    }

    /** A valid result with its selection keys of DOMAIN section 5.3. */
    private class Candidate(
        val solution: ShapeSolution,
        val violatesDirections: Boolean,
        val outsideResidualSteps: Double,
        val costSteps: Double,
        val roundedVariables: DoubleArray,
        val hardResidualMm: Double,
    )

    private companion object {
        /** Directions kept first, then the residual outside `I`, the cost and the rounded variables. */
        val SELECTION_ORDER: Comparator<Candidate> =
            compareBy<Candidate>({ it.violatesDirections }, { it.outsideResidualSteps }, { it.costSteps })
                .then { a, b -> compareLexicographically(a.roundedVariables, b.roundedVariables) }

        /** [value] rounded to whole multiples of [unit]. Adding zero turns `-0.0` into `0.0` for the comparison. */
        fun steps(
            value: Double,
            unit: Double,
        ): Double = StrictMath.rint(value / unit) + 0.0

        fun compareLexicographically(
            a: DoubleArray,
            b: DoubleArray,
        ): Int {
            for (i in a.indices) {
                val order = a[i].compareTo(b[i])
                if (order != 0) return order
            }
            return 0
        }
    }
}
