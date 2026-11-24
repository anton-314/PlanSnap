package app.plansnap.core.geometry.adjustment

import app.plansnap.core.geometry.primitives.GeometryTolerances

/**
 * Builds the [AdjustedShape] of the converged variables [x] (DOMAIN sections 5.5 and 6).
 *
 * Observations are visited in the order walls, observed corners, diagonals. Corners without an observation never
 * count. [jacobian] and [system] belong to [x] and give the redundancy numbers
 * `r_i = (Q Aᵀ (A Q Aᵀ)⁻¹ A Q)_ii / σ_i² = σ_i² · a_iᵀ (A Q Aᵀ)⁻¹ a_i`, clamped to `[0, 1]` against rounding.
 */
internal fun adjustedShapeOf(
    problem: AdjustmentProblem,
    x: DoubleArray,
    jacobian: Array<DoubleArray>,
    system: NormalSystem,
): AdjustedShape {
    val mmPerRad = problem.perimeterMm / (2.0 * StrictMath.PI)
    val entries = ArrayList<Entry>(problem.variableCount)
    for (i in 0 until problem.wallCount) entries += entry(problem, x, i, ObservationRef.WallLength(i), 1.0)
    for (i in 0 until problem.wallCount) {
        val index = problem.turnIndex(i)
        if (problem.observed[index]) entries += entry(problem, x, index, ObservationRef.CornerAngle(i), mmPerRad)
    }
    for (k in 0 until problem.diagonalCount) {
        entries += entry(problem, x, problem.diagonalIndex(k), ObservationRef.DiagonalLength(k), 1.0)
    }
    var sum = 0.0
    var squares = 0.0
    var largest = 0.0
    var worstNormalized = 0.0
    for (e in entries) {
        sum += e.correctionMm
        squares += e.normalized * e.normalized
        largest = maxOf(largest, e.correctionMm)
        worstNormalized = maxOf(worstNormalized, e.normalized)
    }
    val largestRef =
        entries
            .firstOrNull { largest > GeometryTolerances.SOLVER_RESIDUAL_MM && it.correctionMm >= largest - TIE_MM }
            ?.observation
    val worstRef =
        entries.firstOrNull { worstNormalized > 0.0 && it.normalized >= worstNormalized - TIE_NORMALIZED }?.observation
    return AdjustedShape(
        adjustedLengthsMm = List(problem.wallCount) { x[it] },
        adjustedTurnsRad = List(problem.wallCount) { x[problem.turnIndex(it)] },
        adjustedDiagonalsMm = List(problem.diagonalCount) { x[problem.diagonalIndex(it)] },
        maxCorrectionMm = largest,
        worstObservation = worstRef,
        correctionSumMm = sum,
        normalizedSquareSum = squares,
        largestCorrection = largestRef,
        redundancies = entries.map { ObservationRedundancy(it.observation, redundancy(problem, jacobian, system, it)) },
    )
}

/** Ties of `largestCorrection` within `SOLVER_RESIDUAL_MM` go to the first observation (DOMAIN section 6). */
private const val TIE_MM = GeometryTolerances.SOLVER_RESIDUAL_MM

/**
 * Ties of `worstObservation`: the iteration stops at `SOLVER_RESIDUAL_MM`, so normalized residuals that differ by less
 * than `SOLVER_RESIDUAL_MM / SIGMA_LENGTH_MM` are equal, and the first observation in the order walls, corners,
 * diagonals wins.
 */
private const val TIE_NORMALIZED = GeometryTolerances.SOLVER_RESIDUAL_MM / GeometryTolerances.SIGMA_LENGTH_MM

/** One observation with its index in `x`, its correction in mm and its normalized residual `|v_i| / σ_i`. */
private class Entry(
    val observation: ObservationRef,
    val index: Int,
    val correctionMm: Double,
    val normalized: Double,
)

private fun entry(
    problem: AdjustmentProblem,
    x: DoubleArray,
    index: Int,
    observation: ObservationRef,
    mmPerUnit: Double,
): Entry {
    val raw = x[index] - problem.observations[index]
    val residual = StrictMath.abs(if (observation is ObservationRef.CornerAngle) wrapAngle(raw) else raw)
    return Entry(observation, index, residual * mmPerUnit, residual / StrictMath.sqrt(problem.variances[index]))
}

private fun redundancy(
    problem: AdjustmentProblem,
    jacobian: Array<DoubleArray>,
    system: NormalSystem,
    entry: Entry,
): Double {
    val column = DoubleArray(jacobian.size) { jacobian[it][entry.index] }
    val solved = system.solve(column)
    var product = 0.0
    for (row in column.indices) product += column[row] * solved[row]
    return (problem.variances[entry.index] * product).coerceIn(0.0, 1.0)
}
