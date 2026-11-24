package app.plansnap.core.geometry.solver

import app.plansnap.core.common.units.Angle
import app.plansnap.core.geometry.primitives.GeometryTolerances
import app.plansnap.core.geometry.solver.SolverProblem.Companion.FULL_TURN
import app.plansnap.core.geometry.solver.SolverProblem.Companion.HALF_TURN
import app.plansnap.core.geometry.solver.SolverProblem.Companion.wrap
import app.plansnap.core.geometry.traverse.CornerInput
import kotlin.math.abs

/** Direction tolerance `ε` of DOMAIN section 3 in radians. */
private val DIRECTION_TOLERANCE_RAD: Double = StrictMath.toRadians(GeometryTolerances.TURN_DIRECTION_TOLERANCE_DEG)

/** Whether the closing corner is a soft target in the objective: free or declared right. */
internal val SolverProblem.closingIsSoft: Boolean get() = !closingMeasured

/** Objective `f = Σ_{i ∈ W} wrap(τ_i − τ̂_i)²` over the free corners and a soft closing corner (DOMAIN section 5.1). */
internal fun SolverProblem.cost(turns: DoubleArray): Double {
    var sum = 0.0
    for (i in free) {
        val deviation = wrap(turns[i] - targets[i])
        sum += deviation * deviation
    }
    if (closingIsSoft) {
        val closing = closingResidual(turns)
        sum += closing * closing
    }
    return sum
}

/** Gradient `∇f` by the variables `u`. A soft closing corner depends on every inner turn with factor −1. */
internal fun SolverProblem.gradient(turns: DoubleArray): DoubleArray {
    val closing = if (closingIsSoft) 2.0 * closingResidual(turns) else 0.0
    return DoubleArray(free.size) { 2.0 * wrap(turns[free[it]] - targets[free[it]]) - closing }
}

/**
 * Turns of a candidate. Free turns outside `(−π, π]` are wrapped, so that equal shapes compare equal in the
 * lexicographic rule of DOMAIN section 5.3. Measured turns stay their exact targets. `τ_0` is the measured target or,
 * otherwise, `τ_0,derived` (DOMAIN section 3), also wrapped.
 */
internal fun SolverProblem.candidateTurns(turns: DoubleArray): DoubleArray {
    val candidate = turns.copyOf()
    for (i in free) candidate[i] = normalized(turns[i])
    candidate[0] = if (closingMeasured) targets[0] else normalized(derivedClosingTurn(candidate))
    return candidate
}

/** [angle] itself inside `(−π, π]`, so that a value in range keeps its bits, otherwise its wrapped value. */
private fun normalized(angle: Double): Double = if (angle > -HALF_TURN && angle <= HALF_TURN) angle else wrap(angle)

/** Largest residual in mm of the hard conditions outside [selected] (DOMAIN section 5.3, rule 2). */
internal fun SolverProblem.outsideResidualMm(
    selected: List<HardCondition>,
    turns: DoubleArray,
    chain: Chain,
): Double {
    var worst = 0.0
    if (closingMeasured && HardCondition.ClosingCorner !in selected) {
        worst = closingArcMm(turns)
    }
    for (row in rows) {
        if (row is HardCondition.DiagonalLength && row !in selected) {
            worst = maxOf(worst, diagonalResidualMm(row.index, chain))
        }
    }
    return worst
}

/**
 * Largest residual in mm of all hard conditions on the raw lengths: the gap, a measured closing corner as arc
 * `|Δτ_0| · U / (2π)` and every diagonal between non-adjacent corners (DOMAIN section 5.6).
 */
internal fun SolverProblem.hardResidualMm(
    turns: DoubleArray,
    chain: Chain,
): Double {
    var worst = StrictMath.hypot(chain.x[n], chain.y[n])
    if (closingMeasured) worst = maxOf(worst, closingArcMm(turns))
    for (row in rows) {
        if (row is HardCondition.DiagonalLength) worst = maxOf(worst, diagonalResidualMm(row.index, chain))
    }
    return worst
}

/**
 * Whether a corner with a direction `d` has `d · wrap(τ) < −ε` (DOMAIN section 3). Free corners and measured corners
 * with `α = 180°` have no direction.
 */
internal fun SolverProblem.violatesDirections(turns: DoubleArray): Boolean =
    input.corners.indices.any { i ->
        val direction =
            when (val corner = input.corners[i]) {
                is CornerInput.DeclaredRight -> corner.direction.sign
                is CornerInput.Measured -> if (corner.angle == Angle.STRAIGHT) 0 else corner.direction.sign
                CornerInput.Free -> 0
            }
        direction != 0 && direction * wrap(turns[i]) < -DIRECTION_TOLERANCE_RAD
    }

private fun SolverProblem.closingArcMm(turns: DoubleArray): Double = abs(closingResidual(turns)) * perimeter / FULL_TURN

private fun SolverProblem.diagonalResidualMm(
    index: Int,
    chain: Chain,
): Double {
    val diagonal = input.diagonals[index]
    return abs(distance(chain, diagonal) - diagonal.lengthMm.toDouble())
}
