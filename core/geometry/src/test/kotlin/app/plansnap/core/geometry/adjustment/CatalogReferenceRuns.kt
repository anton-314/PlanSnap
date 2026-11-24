package app.plansnap.core.geometry.adjustment

import app.plansnap.testing.fixtures.rooms.OverdeterminedCase
import app.plansnap.testing.fixtures.rooms.OverdeterminedCases

/**
 * How the evaluation of DOMAIN sections 4 and 5.5 reaches each catalog case, as traced in the reference prototype
 * `docs/research/evaluation-prototype/flow.py`. Start turns from a solver run (steps A1 and B1) are the prototype's
 * values in radians, because T-GEO-006 must not depend on the solver. They are only the first linearization point, so
 * the production solver may deliver slightly different values without changing the adjusted result.
 */
internal sealed interface ReferenceRun {
    /** Step 6 of DOMAIN section 4 with `e ≤ EXACT_CLOSURE_MM`: no adjustment. */
    data object Exact : ReferenceRun

    /** Branch A of DOMAIN section 5.5: no adjustment. [solutionTurnsRad] is the solver result, `null` if analytic. */
    data class BranchA(
        val solutionTurnsRad: List<Double>?,
    ) : ReferenceRun

    /** Adjustment in [mode], from the target turns if [startTurnsRad] is `null`, else from the solver start. */
    data class Adjust(
        val mode: AdjustmentMode,
        val startTurnsRad: List<Double>? = null,
    ) : ReferenceRun
}

/** Reference runs of all catalog cases. */
internal object CatalogReferenceRuns {
    private val SHAPE_FROM_TARGETS = ReferenceRun.Adjust(AdjustmentMode.SHAPE_DEVIATION)

    private val QUAD_SOLVER_START =
        listOf(1.910633236241642, 1.9106332362371987, 1.2309594173540754, 1.2309594173466696)

    private fun fromSolver(vararg turns: Double) = ReferenceRun.Adjust(AdjustmentMode.SHAPE_DEVIATION, turns.toList())

    val RUNS: Map<OverdeterminedCase, ReferenceRun> =
        with(OverdeterminedCases) {
            mapOf(
                SELF_INTERSECTING_EXACT to ReferenceRun.Exact,
                TRAPEZOID_ROUNDED_ANGLES to
                    fromSolver(1.9106293854507488, 1.9106293854506766, 1.2309632681390805, 1.2309632681390805),
                TRAPEZOID_ROUNDED_ANGLES_CONTROL to
                    fromSolver(1.9106468387432631, 1.9106468387432018, 1.2309458148465608, 1.2309458148465608),
                // A1 has no simple result, so B2 starts at the target turns.
                PENTAGON_DEPENDENT_DIAGONAL to SHAPE_FROM_TARGETS,
                STRAIGHT_CORNER_LEFT to SHAPE_FROM_TARGETS,
                STRAIGHT_CORNER_RIGHT to SHAPE_FROM_TARGETS,
                NEARLY_STRAIGHT_CORNER_RIGHT to SHAPE_FROM_TARGETS,
                PROPOSED_WITH_CORRECTION to ReferenceRun.Adjust(AdjustmentMode.SHAPE_DEVIATION, QUAD_SOLVER_START),
                TRIANGLE_START_BRANCH to fromSolver(2.666094457648059, 1.0472673643666774, 2.5698234851648496),
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
                HEXAGON_UNOBSERVED_TURNS to
                    fromSolver(
                        -1.0527804120115967,
                        1.1165426992557734,
                        0.637414695368409,
                        0.7784677973295849,
                        1.7118014770635184,
                        3.091739050173897,
                    ),
                PENTAGON_REPEATED_DIAGONAL to
                    fromSolver(
                        1.3607049748633209,
                        1.5707963267948966,
                        1.195884124892516,
                        0.7783436212145455,
                        1.377456259414307,
                    ),
                HEXAGON_ALL_DIAGONALS to
                    fromSolver(
                        1.6088159492596352,
                        1.07033886084881,
                        0.27748939957393454,
                        1.0122605607071626,
                        1.561037910247893,
                        0.7532426265421507,
                    ),
                RECTANGLE_WRONG_WALL to SHAPE_FROM_TARGETS,
                L_SHAPE_WRONG_WALL to SHAPE_FROM_TARGETS,
                U_SHAPE_WRONG_WALL to SHAPE_FROM_TARGETS,
                CROSS_WRONG_WALL to SHAPE_FROM_TARGETS,
                GAP_30_MM to ReferenceRun.Adjust(AdjustmentMode.MEASUREMENT_ERROR),
                RECTANGLE_LARGE_SIMPLE to SHAPE_FROM_TARGETS,
                RECTANGLE_LARGE_EXPLICIT to SHAPE_FROM_TARGETS,
                RECTANGLE_LARGE_CONFLICT to SHAPE_FROM_TARGETS,
                TRAPEZOID_TWO_DIAGONALS to
                    fromSolver(1.9106331115882593, 1.9106321141843703, 1.230957547255754, 1.2309625341512023),
                QUAD_REPEATED_WALL to ReferenceRun.Adjust(AdjustmentMode.SHAPE_DEVIATION, QUAD_SOLVER_START),
                QUAD_REPEATED_WALL_1_MM to ReferenceRun.Adjust(AdjustmentMode.SHAPE_DEVIATION, QUAD_SOLVER_START),
                QUAD_REPEATED_WALL_CONTRADICTION to
                    ReferenceRun.Adjust(AdjustmentMode.SHAPE_DEVIATION, QUAD_SOLVER_START),
                TRIANGLE_WRONG_DIRECTION to ReferenceRun.BranchA(null),
                HEXAGON_UNCONTROLLED to
                    fromSolver(
                        1.2341501730537345,
                        1.1072604206876422,
                        1.0619965974956076,
                        0.9322989909492468,
                        0.97454646014636,
                        0.9729326648469951,
                    ),
                AMBIGUOUS_SELECTED_OTHER_SHAPE to ReferenceRun.BranchA(null),
                AMBIGUOUS_SELECTED_TRUE_SHAPE to ReferenceRun.BranchA(null),
                AMBIGUOUS_NEEDS_ADJUSTMENT to
                    fromSolver(
                        0.5606093047997387,
                        -1.8153823816036243,
                        -1.7007124791508477,
                        -1.6530886010264292,
                        -1.6746111501984238,
                    ),
            )
        }
}
