package app.plansnap.core.domain.evaluation

import app.plansnap.core.common.runtime.DispatcherProvider
import app.plansnap.core.geometry.adjustment.AdjustedShape
import app.plansnap.core.geometry.adjustment.AdjustmentMode
import app.plansnap.core.geometry.adjustment.AdjustmentOutcome
import app.plansnap.core.geometry.adjustment.ClosureAdjuster
import app.plansnap.core.geometry.adjustment.DefaultClosureAdjuster
import app.plansnap.core.geometry.cancellation.CancellationSignal
import app.plansnap.core.geometry.determinacy.ConstraintAnalysis
import app.plansnap.core.geometry.determinacy.DefaultDeterminacyAnalyzer
import app.plansnap.core.geometry.determinacy.Determinacy
import app.plansnap.core.geometry.determinacy.DeterminacyAnalyzer
import app.plansnap.core.geometry.determinacy.MeasurementControl
import app.plansnap.core.geometry.determinacy.ShapeAmbiguity
import app.plansnap.core.geometry.primitives.ClosureTolerance
import app.plansnap.core.geometry.primitives.Polygon2
import app.plansnap.core.geometry.primitives.Vec2
import app.plansnap.core.geometry.solver.DefaultShapeSolver
import app.plansnap.core.geometry.solver.HardCondition
import app.plansnap.core.geometry.solver.ShapeSolution
import app.plansnap.core.geometry.solver.ShapeSolver
import app.plansnap.core.geometry.solver.SolverOutcome
import app.plansnap.core.geometry.traverse.ClosureClass
import app.plansnap.core.geometry.traverse.ClosureError
import app.plansnap.core.geometry.traverse.DefaultTraverse
import app.plansnap.core.geometry.traverse.ShapeInput
import app.plansnap.core.geometry.traverse.Traverse
import app.plansnap.core.geometry.traverse.TurnTargets

/**
 * Production components that record every call that computes something about the raw input. Walks are not recorded,
 * because drawing a stored overlay is no recomputation. [adjustOverride] and [solveOverride] replace the outcome of the
 * real adjustment or solver to reach paths that consistent input cannot reach.
 */
internal class RecordingComponents(
    private val adjustOverride: ((AdjustmentMode) -> AdjustmentOutcome?)? = null,
    private val solveOverride: (() -> SolverOutcome)? = null,
    private val onCall: (CancellationSignal) -> Unit = {},
) {
    /** Names of the recorded calls in call order. */
    val calls: MutableList<String> = mutableListOf()

    /** Modes of the adjustments in call order. */
    val modes: MutableList<AdjustmentMode> = mutableListOf()

    private val realTraverse = DefaultTraverse()
    private val realSolver = DefaultShapeSolver()
    private val realAdjuster = DefaultClosureAdjuster()
    private val realAnalyzer = DefaultDeterminacyAnalyzer()

    /** Evaluator on these components. */
    fun evaluator(dispatchers: DispatcherProvider = DirectDispatchers): DefaultRoomEvaluator =
        DefaultRoomEvaluator(dispatchers, traverse, solver, adjuster, analyzer)

    private fun record(
        name: String,
        signal: CancellationSignal? = null,
    ) {
        calls += name
        signal?.let(onCall)
    }

    private val traverse =
        object : Traverse {
            override fun targets(input: ShapeInput): TurnTargets {
                record("targets")
                return realTraverse.targets(input)
            }

            override fun walk(
                lengthsMm: List<Double>,
                turnsRad: List<Double>,
            ): List<Vec2> = realTraverse.walk(lengthsMm, turnsRad)

            override fun closureError(input: ShapeInput): ClosureError {
                record("closureError")
                return realTraverse.closureError(input)
            }

            override fun classify(
                input: ShapeInput,
                error: ClosureError,
                tolerance: ClosureTolerance,
            ): ClosureClass {
                record("classify")
                return realTraverse.classify(input, error, tolerance)
            }
        }

    private val solver =
        ShapeSolver { input: ShapeInput, conditions: List<HardCondition>, cancellation: CancellationSignal ->
            record("solve", cancellation)
            solveOverride?.invoke() ?: realSolver.solve(input, conditions, cancellation)
        }

    private val adjuster =
        object : ClosureAdjuster {
            override val version: Int get() = realAdjuster.version

            override fun adjust(
                input: ShapeInput,
                mode: AdjustmentMode,
                startTurnsRad: List<Double>,
                cancellation: CancellationSignal,
            ): AdjustmentOutcome {
                record("adjust", cancellation)
                modes += mode
                return adjustOverride?.invoke(mode) ?: realAdjuster.adjust(input, mode, startTurnsRad, cancellation)
            }
        }

    private val analyzer =
        object : DeterminacyAnalyzer {
            override fun analyzeConstraints(
                input: ShapeInput,
                turnsRad: List<Double>,
                cancellation: CancellationSignal,
            ): ConstraintAnalysis {
                record("analyzeConstraints", cancellation)
                return realAnalyzer.analyzeConstraints(input, turnsRad, cancellation)
            }

            override fun analyze(
                input: ShapeInput,
                lengthsMm: List<Double>,
                turnsRad: List<Double>,
                cancellation: CancellationSignal,
            ): Determinacy {
                record("analyze", cancellation)
                return realAnalyzer.analyze(input, lengthsMm, turnsRad, cancellation)
            }

            override fun analyzeAmbiguity(
                input: ShapeInput,
                outline: Polygon2,
                alternatives: List<ShapeSolution>,
                limitMm: Double,
                cancellation: CancellationSignal,
            ): ShapeAmbiguity? {
                record("analyzeAmbiguity", cancellation)
                return realAnalyzer.analyzeAmbiguity(input, outline, alternatives, limitMm, cancellation)
            }

            override fun analyzeControl(
                input: ShapeInput,
                shape: AdjustedShape,
                redundancy: Int,
                limitMm: Double,
                cancellation: CancellationSignal,
            ): MeasurementControl {
                record("analyzeControl", cancellation)
                return realAnalyzer.analyzeControl(input, shape, redundancy, limitMm, cancellation)
            }
        }
}
