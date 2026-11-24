package app.plansnap.core.geometry.adjustment

import app.plansnap.core.geometry.cancellation.CancellationSignal
import app.plansnap.core.geometry.primitives.GeometryTolerances
import app.plansnap.core.geometry.solver.DefaultShapeSolver
import app.plansnap.core.geometry.solver.DenseLinearAlgebra
import app.plansnap.core.geometry.solver.HardCondition
import app.plansnap.core.geometry.solver.SolverOutcome
import app.plansnap.core.geometry.solver.SolverProblem
import app.plansnap.core.geometry.solver.jacobian
import app.plansnap.core.geometry.traverse.ShapeInput
import app.plansnap.testing.fixtures.rooms.OverdeterminedCase
import app.plansnap.testing.fixtures.rooms.OverdeterminedCases

/**
 * How the evaluation of DOMAIN sections 4 and 5.5 reaches each catalog case, as traced in the reference prototype
 * `docs/research/evaluation-prototype/flow.py`. Start turns of step B1 come from the production [DefaultShapeSolver]
 * (see [CatalogReferenceRuns.solverStart]). They are only the first linearization point of the adjustment.
 */
internal sealed interface ReferenceRun {
    /** Step 6 of DOMAIN section 4 with `e ≤ EXACT_CLOSURE_MM`: no adjustment. */
    data object Exact : ReferenceRun

    /** Branch A of DOMAIN section 5.5: no adjustment. [solutionTurnsRad] is the solver result, `null` if analytic. */
    data class BranchA(
        val solutionTurnsRad: List<Double>?,
    ) : ReferenceRun

    /** Adjustment in [mode], from the start turns of [start]. */
    data class Adjust(
        val mode: AdjustmentMode,
        val start: StartTurns = StartTurns.TARGETS,
    ) : ReferenceRun
}

/** Origin of the first linearization point of an adjustment. */
internal enum class StartTurns {
    /** Target turns, step B2 after a solver without result and for rooms without free variables. */
    TARGETS,

    /** Solution of the production solver on the independent conditions (step B1). */
    SOLVER,
}

/** Reference runs of all catalog cases. */
internal object CatalogReferenceRuns {
    /**
     * Start turns of step B1 of DOMAIN section 5.5: the production solver on the independent conditions `I`, chosen
     * greedily in row order at the routing point (DOMAIN section 5.4, the better rank of `τ̂` and the probe point).
     * Without a free variable or without a simple solver result the start is the target turns (steps B1 and B2).
     */
    fun solverStart(input: ShapeInput): List<Double> {
        val selection = independentConditions(input)
        return when (val outcome = DefaultShapeSolver().solve(input, selection, CancellationSignal.NONE)) {
            is SolverOutcome.Solved -> outcome.solution.turnsRad
            SolverOutcome.NoSimpleShape -> AdjustmentTestSupport.targetStart(input)
        }
    }

    private fun independentConditions(input: ShapeInput): List<HardCondition> {
        val problem = SolverProblem(input)
        val atTargets = independentAt(problem, problem.targets)
        val probe =
            DoubleArray(problem.n) { i ->
                if (i in
                    problem.free
                ) {
                    problem.targets[i] + GeometryTolerances.RANK_PROBE_RAD * i / problem.n
                } else {
                    problem.targets[i]
                }
            }
        val atProbe = independentAt(problem, probe)
        return if (atProbe.size > atTargets.size) atProbe else atTargets
    }

    private fun independentAt(
        problem: SolverProblem,
        turns: DoubleArray,
    ): List<HardCondition> {
        if (problem.free.isEmpty()) return emptyList()
        val matrix = problem.jacobian(problem.rows, problem.walk(turns))
        return DenseLinearAlgebra
            .independentRows(matrix, GeometryTolerances.RANK_TOLERANCE)
            .map { problem.rows[it] }
    }

    private val SHAPE_FROM_TARGETS = ReferenceRun.Adjust(AdjustmentMode.SHAPE_DEVIATION)
    private val SHAPE_FROM_SOLVER = ReferenceRun.Adjust(AdjustmentMode.SHAPE_DEVIATION, StartTurns.SOLVER)

    val RUNS: Map<OverdeterminedCase, ReferenceRun> =
        with(OverdeterminedCases) {
            mapOf(
                SELF_INTERSECTING_EXACT to ReferenceRun.Exact,
                TRAPEZOID_ROUNDED_ANGLES to SHAPE_FROM_SOLVER,
                TRAPEZOID_ROUNDED_ANGLES_CONTROL to SHAPE_FROM_SOLVER,
                // A1 has no simple result, so B2 starts at the target turns.
                PENTAGON_DEPENDENT_DIAGONAL to SHAPE_FROM_TARGETS,
                STRAIGHT_CORNER_LEFT to SHAPE_FROM_TARGETS,
                STRAIGHT_CORNER_RIGHT to SHAPE_FROM_TARGETS,
                NEARLY_STRAIGHT_CORNER_RIGHT to SHAPE_FROM_TARGETS,
                PROPOSED_WITH_CORRECTION to SHAPE_FROM_SOLVER,
                TRIANGLE_START_BRANCH to SHAPE_FROM_SOLVER,
                HEXAGON_KEEPS_SIMPLE to
                    ReferenceRun.BranchA(
                        listOf(
                            2.3206449659047355,
                            0.2874510075735697,
                            1.6710480390294509,
                            1.1560532972457618,
                            0.7691514866190005,
                            0.07883651080706791,
                        ),
                    ),
                HEXAGON_UNOBSERVED_TURNS to SHAPE_FROM_SOLVER,
                PENTAGON_REPEATED_DIAGONAL to SHAPE_FROM_SOLVER,
                HEXAGON_ALL_DIAGONALS to SHAPE_FROM_SOLVER,
                RECTANGLE_WRONG_WALL to SHAPE_FROM_TARGETS,
                L_SHAPE_WRONG_WALL to SHAPE_FROM_TARGETS,
                U_SHAPE_WRONG_WALL to SHAPE_FROM_TARGETS,
                CROSS_WRONG_WALL to SHAPE_FROM_TARGETS,
                GAP_30_MM to ReferenceRun.Adjust(AdjustmentMode.MEASUREMENT_ERROR),
                RECTANGLE_LARGE_SIMPLE to SHAPE_FROM_TARGETS,
                RECTANGLE_LARGE_EXPLICIT to SHAPE_FROM_TARGETS,
                RECTANGLE_LARGE_CONFLICT to SHAPE_FROM_TARGETS,
                TRAPEZOID_TWO_DIAGONALS to SHAPE_FROM_SOLVER,
                QUAD_REPEATED_WALL to SHAPE_FROM_SOLVER,
                QUAD_REPEATED_WALL_1_MM to SHAPE_FROM_SOLVER,
                QUAD_REPEATED_WALL_CONTRADICTION to SHAPE_FROM_SOLVER,
                TRIANGLE_WRONG_DIRECTION to ReferenceRun.BranchA(null),
                HEXAGON_UNCONTROLLED to SHAPE_FROM_SOLVER,
                AMBIGUOUS_SELECTED_OTHER_SHAPE to ReferenceRun.BranchA(null),
                AMBIGUOUS_SELECTED_TRUE_SHAPE to ReferenceRun.BranchA(null),
                AMBIGUOUS_NEEDS_ADJUSTMENT to SHAPE_FROM_SOLVER,
            )
        }
}
