package app.plansnap.core.geometry.determinacy

import app.plansnap.core.geometry.adjustment.AdjustedShape
import app.plansnap.core.geometry.adjustment.AdjustmentMode
import app.plansnap.core.geometry.adjustment.AdjustmentProblem
import app.plansnap.core.geometry.adjustment.NormalSystem
import app.plansnap.core.geometry.adjustment.ObservationRef
import app.plansnap.core.geometry.cancellation.CancellationSignal
import app.plansnap.core.geometry.primitives.GeometryTolerances
import app.plansnap.core.geometry.statistics.ChiSquareLimits
import app.plansnap.core.geometry.traverse.CornerInput
import app.plansnap.core.geometry.traverse.DefaultTraverse
import app.plansnap.core.geometry.traverse.DiagonalInput
import app.plansnap.core.geometry.traverse.ShapeInput

/**
 * Controllability of the measurements of an adjusted shape (DOMAIN section 5.5).
 *
 * A measurement is not controllable if `λ_i(3T) = (CONTROL_ERROR_FACTOR · T)² · r_i / σ_i² < λ₀(r)`, with `σ_i` of a
 * corner as mm equivalent `σ · U / (2π)`. Redundancy numbers of a candidate measurement come from the adjustment
 * extended by that measurement at the adjusted shape: `r_i = σ_i² · a_iᵀ (A Q Aᵀ)⁻¹ a_i`.
 */
internal class ControlAnalysis(
    private val input: ShapeInput,
    private val shape: AdjustedShape,
    limitMm: Double,
) {
    private val mode: AdjustmentMode =
        if (shape.redundancies.any { declaredRight(it.observation) }) {
            AdjustmentMode.MEASUREMENT_ERROR
        } else {
            AdjustmentMode.SHAPE_DEVIATION
        }
    private val targets = DefaultTraverse().targets(input)
    private val problem = AdjustmentProblem(input, mode, targets)
    private val x: DoubleArray =
        DoubleArray(problem.variableCount) { j ->
            when {
                j < problem.wallCount -> shape.adjustedLengthsMm[j]
                j < 2 * problem.wallCount -> shape.adjustedTurnsRad[j - problem.wallCount]
                else -> shape.adjustedDiagonalsMm[j - 2 * problem.wallCount]
            }
        }
    private val chain = WalkedChain(shape.adjustedLengthsMm.toDoubleArray(), shape.adjustedTurnsRad.toDoubleArray())
    private val errorSquared = (GeometryTolerances.CONTROL_ERROR_FACTOR * limitMm).let { it * it }
    private val mmPerRad = problem.perimeterMm / (2.0 * StrictMath.PI)

    /** The uncontrolled measurements in the order of [AdjustedShape.redundancies], each with its recommendation. */
    fun uncontrolled(
        redundancy: Int,
        cancellation: CancellationSignal,
    ): List<UncontrolledMeasurement> {
        val threshold = ChiSquareLimits.controlThreshold(redundancy)
        val weak =
            shape.redundancies.mapNotNull { entry ->
                val lambda = noncentrality(entry.observation, entry.redundancy)
                (entry.observation to lambda).takeIf { lambda < threshold }
            }
        if (weak.isEmpty()) return emptyList()
        val extendedThreshold = ChiSquareLimits.controlThreshold(redundancy + 1)
        val candidates = candidates(cancellation)
        return weak.map { (observation, lambda) ->
            val recommended =
                candidates.firstOrNull { (_, redundancies) ->
                    val extended = redundancies[observation] ?: 0.0
                    noncentrality(observation, extended) >= extendedThreshold
                }
            UncontrolledMeasurement(observation, lambda, recommended?.first)
        }
    }

    /** `λ(3T)` of [observation] with redundancy number [redundancy]. */
    private fun noncentrality(
        observation: ObservationRef,
        redundancy: Double,
    ): Double {
        val sigmaMm = StrictMath.sqrt(problem.variances[indexOf(observation)]) * unitMm(observation)
        return errorSquared * redundancy / (sigmaMm * sigmaMm)
    }

    /** Candidate measurements in their fixed order, each with the redundancy numbers of the existing observations. */
    private fun candidates(
        cancellation: CancellationSignal,
    ): List<Pair<MeasurementRecommendation, Map<ObservationRef, Double>>> {
        val result = ArrayList<Pair<MeasurementRecommendation, Map<ObservationRef, Double>>>()
        for (diagonal in diagonalCandidates()) {
            cancellation.checkpoint()
            diagonalRedundancies(diagonal, cancellation)?.let { result += diagonal to it }
        }
        for (corner in 0 until problem.wallCount) {
            if (problem.observed[problem.turnIndex(corner)]) continue
            cancellation.checkpoint()
            val variances = problem.variances.copyOf()
            variances[problem.turnIndex(corner)] = MEASURED_SIGMA_RAD * MEASURED_SIGMA_RAD
            redundancies(problem.jacobian(x), variances, cancellation)?.let {
                result += MeasurementRecommendation.CornerAngle(corner) to it
            }
        }
        return result
    }

    /**
     * Unmeasured diagonals between non-adjacent corners, longest first, ties within `SOLVER_RESIDUAL_MM` by `(a, b)`.
     */
    private fun diagonalCandidates(): List<MeasurementRecommendation.DiagonalBetween> {
        val measured = input.diagonals.map { minOf(it.fromCorner, it.toCorner) to maxOf(it.fromCorner, it.toCorner) }
        val open = nonAdjacentPairs(input.wallCount).filterNot { it in measured }.toMutableList()
        val ordered = ArrayList<MeasurementRecommendation.DiagonalBetween>()
        while (open.isNotEmpty()) {
            val longest = open.maxOf { (a, b) -> chain.distance(a, b) }
            val next = open.first { (a, b) -> chain.distance(a, b) >= longest - GeometryTolerances.SOLVER_RESIDUAL_MM }
            open.remove(next)
            ordered += MeasurementRecommendation.DiagonalBetween(next.first, next.second)
        }
        return ordered
    }

    /** Redundancy numbers after adding [diagonal] as an observation at its walked length. */
    private fun diagonalRedundancies(
        diagonal: MeasurementRecommendation.DiagonalBetween,
        cancellation: CancellationSignal,
    ): Map<ObservationRef, Double>? {
        val extendedX = x.copyOf(x.size + 1)
        val length = chain.distance(diagonal.fromCorner, diagonal.toCorner)
        extendedX[x.size] = length
        val roundedMm = maxOf(1L, StrictMath.rint(length).toLong())
        val extended =
            ShapeInput(
                input.lengthsMm,
                input.corners,
                input.diagonals + DiagonalInput(diagonal.fromCorner, diagonal.toCorner, roundedMm),
            )
        val extendedProblem = AdjustmentProblem(extended, mode, targets)
        return redundancies(extendedProblem.jacobian(extendedX), extendedProblem.variances, cancellation)
    }

    /** `r_i` of the existing observations for the condition rows [jacobian] and [variances], `null` if singular. */
    private fun redundancies(
        jacobian: Array<DoubleArray>,
        variances: DoubleArray,
        cancellation: CancellationSignal,
    ): Map<ObservationRef, Double>? {
        val system = NormalSystem.factor(jacobian, variances, cancellation) ?: return null
        return shape.redundancies.associate { entry ->
            val index = indexOf(entry.observation)
            val column = DoubleArray(jacobian.size) { jacobian[it][index] }
            val solved = system.solve(column)
            var product = 0.0
            for (row in column.indices) product += column[row] * solved[row]
            entry.observation to (variances[index] * product).coerceIn(0.0, 1.0)
        }
    }

    private fun indexOf(observation: ObservationRef): Int =
        when (observation) {
            is ObservationRef.WallLength -> observation.index
            is ObservationRef.CornerAngle -> problem.turnIndex(observation.index)
            is ObservationRef.DiagonalLength -> problem.diagonalIndex(observation.index)
        }

    private fun unitMm(observation: ObservationRef): Double =
        if (observation is ObservationRef.CornerAngle) mmPerRad else 1.0

    private fun declaredRight(observation: ObservationRef): Boolean =
        observation is ObservationRef.CornerAngle && input.corners[observation.index] is CornerInput.DeclaredRight

    private companion object {
        val MEASURED_SIGMA_RAD: Double = StrictMath.toRadians(GeometryTolerances.SIGMA_MEASURED_ANGLE_DEG)
    }
}
