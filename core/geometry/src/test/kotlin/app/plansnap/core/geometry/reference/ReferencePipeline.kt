package app.plansnap.core.geometry.reference

import app.plansnap.core.geometry.adjustment.AdjustedShape
import app.plansnap.core.geometry.adjustment.AdjustmentMode
import app.plansnap.core.geometry.adjustment.AdjustmentOutcome
import app.plansnap.core.geometry.adjustment.DefaultClosureAdjuster
import app.plansnap.core.geometry.cancellation.CancellationSignal
import app.plansnap.core.geometry.polygon.DefaultPolygonAnalyzer
import app.plansnap.core.geometry.primitives.ClosureTolerance
import app.plansnap.core.geometry.primitives.GeometryTolerances
import app.plansnap.core.geometry.primitives.Polygon2
import app.plansnap.core.geometry.solver.DefaultShapeSolver
import app.plansnap.core.geometry.solver.DenseLinearAlgebra
import app.plansnap.core.geometry.solver.SolverOutcome
import app.plansnap.core.geometry.solver.SolverProblem
import app.plansnap.core.geometry.solver.allRows
import app.plansnap.core.geometry.solver.jacobian
import app.plansnap.core.geometry.traverse.ClosureClass
import app.plansnap.core.geometry.traverse.CornerInput
import app.plansnap.core.geometry.traverse.DefaultTraverse
import app.plansnap.core.geometry.traverse.ShapeInput
import app.plansnap.core.geometry.traverse.toShapeInput
import app.plansnap.core.model.ShapeStatus
import app.plansnap.testing.fixtures.rooms.ReferenceRoom

/** How the pipeline obtained the displayed outline of a reference room. */
internal enum class ShapeSource {
    /** Closure within `EXACT_CLOSURE_MM`: the raw values walked as entered. */
    WALKED,

    /** Small gap: closure adjustment in measurement error mode. */
    ADJUSTED,

    /** Shape deviation of a room that is not overdetermined: solver result (step A1). */
    SOLVED,
}

/** Result of [ReferencePipeline.run]. */
internal data class PipelineResult(
    val input: ShapeInput,
    val closureClass: ClosureClass,
    val source: ShapeSource,
    val outline: Polygon2,
    val maxCorrectionMm: Double,
    val status: ShapeStatus,
)

/** The merged production pipeline traverse, solver, adjustment, as DOMAIN section 4 chains them for a room. */
internal object ReferencePipeline {
    private val traverse = DefaultTraverse()
    private val adjuster = DefaultClosureAdjuster()
    private val solver = DefaultShapeSolver()
    private val analyzer = DefaultPolygonAnalyzer()

    /** Runs [room] through closure classification and the matching production step. */
    fun run(room: ReferenceRoom): PipelineResult {
        val input = room.room.toShapeInput()
        val closureClass = closureClass(input)
        return when (closureClass) {
            ClosureClass.EXACT -> {
                val turns = targetStart(input)
                val outline = outline(input.lengthsMm.map { it.toDouble() }, turns)
                PipelineResult(input, closureClass, ShapeSource.WALKED, outline, 0.0, confirmedIfSimple(outline))
            }

            ClosureClass.MEASUREMENT_ERROR -> {
                val shape = adjust(input, AdjustmentMode.MEASUREMENT_ERROR)
                val outline = outline(shape.adjustedLengthsMm, shape.adjustedTurnsRad)
                val status = if (analyzer.isSimple(outline)) ShapeStatus.NEEDS_ADJUSTMENT else ShapeStatus.CONFLICT
                PipelineResult(input, closureClass, ShapeSource.ADJUSTED, outline, shape.maxCorrectionMm, status)
            }

            ClosureClass.SHAPE_DEVIATION -> {
                val outcome = solver.solve(input, allRows(input), CancellationSignal.NONE)
                val solved = outcome as? SolverOutcome.Solved ?: error("Solver found no shape for ${room.key}")
                val outline = solved.solution.outline
                val status =
                    if (missing(input, solved.solution.turnsRad) > 0) {
                        ShapeStatus.PROPOSED
                    } else {
                        confirmedIfSimple(outline)
                    }
                PipelineResult(input, closureClass, ShapeSource.SOLVED, outline, 0.0, status)
            }
        }
    }

    /** Closure class of [input] with the default tolerance (DOMAIN section 4, step 5). */
    fun closureClass(input: ShapeInput): ClosureClass =
        traverse.classify(input, traverse.closureError(input), ClosureTolerance())

    private fun confirmedIfSimple(outline: Polygon2): ShapeStatus =
        if (analyzer.isSimple(outline)) ShapeStatus.CONFIRMED else ShapeStatus.CONFLICT

    /** Missing measurements `v − ρ` at the solution with turns [turnsRad] (DOMAIN section 5.4). */
    private fun missing(
        input: ShapeInput,
        turnsRad: List<Double>,
    ): Int {
        val problem = SolverProblem(input)
        val matrix = problem.jacobian(problem.rows, problem.walk(turnsRad.toDoubleArray()))
        val rank = DenseLinearAlgebra.independentRows(matrix, GeometryTolerances.RANK_TOLERANCE).size
        return problem.free.size - rank
    }

    /** Adjusts [input] from its target turns and fails if the iteration does not converge. */
    fun adjust(
        input: ShapeInput,
        mode: AdjustmentMode,
    ): AdjustedShape =
        when (val outcome = adjuster.adjust(input, mode, targetStart(input), CancellationSignal.NONE)) {
            is AdjustmentOutcome.Adjusted -> outcome.shape
            AdjustmentOutcome.NotConverged -> error("Adjustment did not converge for $input")
        }

    /** Target turns, the free closing corner replaced by the derived turn so that the turns sum to `±2π`. */
    fun targetStart(input: ShapeInput): List<Double> {
        val targets = traverse.targets(input)
        val turns = targets.targetTurns.map { it.toRadians() }.toMutableList()
        if (input.corners.first() == CornerInput.Free) {
            turns[0] = 2.0 * StrictMath.PI * targets.winding.sign - turns.drop(1).sum()
        }
        return turns
    }

    /** Outline `V_0 … V_{n-1}` of the chain walked with [lengthsMm] and [turnsRad]. */
    fun outline(
        lengthsMm: List<Double>,
        turnsRad: List<Double>,
    ): Polygon2 = Polygon2(traverse.walk(lengthsMm, turnsRad).dropLast(1))

    /** Whether [outline] is simple with the default minimum feature size. */
    fun isSimple(outline: Polygon2): Boolean = analyzer.isSimple(outline)
}
