package app.plansnap.core.geometry.determinacy

import app.plansnap.core.geometry.cancellation.CancellationSignal
import app.plansnap.core.geometry.primitives.GeometryTolerances
import app.plansnap.core.geometry.primitives.Polygon2
import app.plansnap.core.geometry.primitives.Vec2
import app.plansnap.core.geometry.solver.ShapeSolution
import app.plansnap.core.geometry.solver.SolverProblem
import app.plansnap.core.geometry.traverse.CornerInput
import app.plansnap.core.geometry.traverse.ShapeInput

/** Comparison of the displayed shape with the other solver results (DOMAIN section 5.6). */
internal class AmbiguityAnalysis(
    private val input: ShapeInput,
    private val outline: Polygon2,
    private val limitMm: Double,
) {
    init {
        require(limitMm.isFinite() && limitMm > 0.0) { "Invalid limit: $limitMm" }
        require(outline.vertices.size == input.wallCount) { "Expected one vertex per wall" }
    }

    /** The alternative farthest from [outline] beyond [limitMm], ties within `SOLVER_RESIDUAL_MM` to the first. */
    fun farthest(
        alternatives: List<ShapeSolution>,
        cancellation: CancellationSignal,
    ): ShapeAmbiguity? {
        val distances =
            alternatives.map { alternative ->
                cancellation.checkpoint()
                require(alternative.outline.vertices.size == input.wallCount) { "Expected one vertex per wall" }
                alternative to shapeDistanceMm(outline, alternative.outline)
            }
        val far = distances.filter { it.second > limitMm }
        val top = far.maxOfOrNull { it.second } ?: return null
        val (alternative, distance) = far.first { it.second >= top - GeometryTolerances.SOLVER_RESIDUAL_MM }
        return ShapeAmbiguity(alternative.outline, distance, limitMm, recommendation(alternative.outline))
    }

    /**
     * The free closing corner if its turns differ by more than [limitMm] as arc, otherwise the unmeasured diagonal
     * with the largest length difference above `EXACT_CLOSURE_MM`, ties within `SOLVER_RESIDUAL_MM` to the smallest
     * `(a, b)`.
     */
    private fun recommendation(other: Polygon2): MeasurementRecommendation? {
        val perimeter = input.lengthsMm.sum().toDouble()
        val closingArc =
            StrictMath.abs(SolverProblem.wrap(closingTurn(outline) - closingTurn(other))) * perimeter / FULL_TURN
        return if (input.corners[0] == CornerInput.Free && closingArc > limitMm) {
            MeasurementRecommendation.CornerAngle(0)
        } else {
            separatingDiagonal(other)
        }
    }

    private fun separatingDiagonal(other: Polygon2): MeasurementRecommendation.DiagonalBetween? {
        val measured = input.diagonals.map { minOf(it.fromCorner, it.toCorner) to maxOf(it.fromCorner, it.toCorner) }
        val gaps =
            nonAdjacentPairs(input.wallCount)
                .filterNot { it in measured }
                .map { (a, b) -> (a to b) to StrictMath.abs(length(outline, a, b) - length(other, a, b)) }
        val top = gaps.maxOfOrNull { it.second }
        if (top == null || top <= GeometryTolerances.EXACT_CLOSURE_MM) return null
        val (a, b) = gaps.first { it.second >= top - GeometryTolerances.SOLVER_RESIDUAL_MM }.first
        return MeasurementRecommendation.DiagonalBetween(a, b)
    }

    private companion object {
        const val FULL_TURN = 2.0 * StrictMath.PI

        /** Turn at `V_0` between the edge into it and the edge out of it. */
        fun closingTurn(shape: Polygon2): Double {
            val vertices = shape.vertices
            val incoming = vertices[0] - vertices[vertices.size - 1]
            val outgoing = vertices[1] - vertices[0]
            return StrictMath.atan2(incoming.cross(outgoing), incoming.dot(outgoing))
        }

        fun length(
            shape: Polygon2,
            a: Int,
            b: Int,
        ): Double = (shape.vertices[b] - shape.vertices[a]).let(Vec2::length)
    }
}
