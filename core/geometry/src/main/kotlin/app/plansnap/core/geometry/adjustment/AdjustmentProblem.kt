package app.plansnap.core.geometry.adjustment

import app.plansnap.core.common.units.Angle
import app.plansnap.core.geometry.primitives.GeometryTolerances
import app.plansnap.core.geometry.traverse.CornerInput
import app.plansnap.core.geometry.traverse.ShapeInput
import app.plansnap.core.geometry.traverse.TurnTargets

/**
 * Observations, weights and conditions of the closure adjustment (DOMAIN section 6).
 *
 * The variable vector is `x = (L_0 … L_{n-1}, τ_0 … τ_{n-1}, D_0 … D_{m-1})`. The conditions are `C = (c_x, c_y,
 * wrap(Σ τ_i − 2π·s), d_0 − D_0, …)`, where `d_k` is the walked corner distance or, for a diagonal between adjacent
 * corners, the length of the wall between them.
 */
internal class AdjustmentProblem(
    input: ShapeInput,
    mode: AdjustmentMode,
    targets: TurnTargets,
) {
    /** Number of walls `n`. */
    val wallCount: Int = input.wallCount

    /** Number of diagonals. */
    val diagonalCount: Int = input.diagonals.size

    /** Perimeter `U` of the raw lengths in mm. */
    val perimeterMm: Double = input.lengthsMm.sum().toDouble()

    /** Observations `x_0`. A corner without observation holds its target, the free closing corner `τ_0,derived`. */
    val observations: DoubleArray

    /** Diagonal of `Q`: `σ²` per variable, angles in rad². */
    val variances: DoubleArray

    /** Whether the variable at an index has an observation. Walls and diagonals always have one. */
    val observed: BooleanArray

    private val windingSign: Int = targets.winding.sign
    private val diagonalFrom = IntArray(diagonalCount) { input.diagonals[it].run { minOf(fromCorner, toCorner) } }
    private val diagonalTo = IntArray(diagonalCount) { input.diagonals[it].run { maxOf(fromCorner, toCorner) } }

    /** Wall repeated by a diagonal between adjacent corners, otherwise [NOT_ADJACENT]. */
    private val repeatedWall = IntArray(diagonalCount) { repeatedWallOf(diagonalFrom[it], diagonalTo[it], wallCount) }

    init {
        val size = variableCount
        observations = DoubleArray(size)
        variances = DoubleArray(size)
        observed = BooleanArray(size) { true }
        for (i in 0 until wallCount) {
            observations[i] = input.lengthsMm[i].toDouble()
            variances[i] = GeometryTolerances.SIGMA_LENGTH_MM * GeometryTolerances.SIGMA_LENGTH_MM
            val sigma = turnSigma(input.corners[i], mode)
            val turn = turnIndex(i)
            observations[turn] = observedTurn(input.corners[i], i, targets).toRadians()
            observed[turn] = sigma != null
            val effective = sigma ?: GeometryTolerances.SIGMA_FREE_RAD
            variances[turn] = effective * effective
        }
        for (k in 0 until diagonalCount) {
            observations[diagonalIndex(k)] = input.diagonals[k].lengthMm.toDouble()
            variances[diagonalIndex(k)] = GeometryTolerances.SIGMA_DIAGONAL_MM * GeometryTolerances.SIGMA_DIAGONAL_MM
        }
    }

    /** Length of `x`. */
    val variableCount: Int get() = 2 * wallCount + diagonalCount

    /** Number of conditions. */
    val conditionCount: Int get() = FIXED_CONDITIONS + diagonalCount

    /** Index of `τ_i` in `x`. */
    fun turnIndex(corner: Int): Int = wallCount + corner

    /** Index of `D_k` in `x`. */
    fun diagonalIndex(diagonal: Int): Int = 2 * wallCount + diagonal

    /** Reference `x̃_k`: the observation for observed variables, [x] for the others (DOMAIN section 6). */
    fun reference(x: DoubleArray): DoubleArray = DoubleArray(x.size) { if (observed[it]) observations[it] else x[it] }

    /**
     * Largest residual of [conditions]: the Euclidean closure gap `|(c_x, c_y)|` in mm, the wrapped turn sum and every
     * diagonal row. Measuring the gap as one vector keeps the closure gap itself within the limit (DOMAIN sections 1
     * and 4), not only its components.
     */
    fun residual(conditions: DoubleArray): Double {
        var worst = StrictMath.hypot(conditions[ROW_X], conditions[ROW_Y])
        for (row in ROW_TURNS until conditionCount) worst = maxOf(worst, StrictMath.abs(conditions[row]))
        return worst
    }

    /** Evaluates `C(x)`. */
    fun conditions(x: DoubleArray): DoubleArray {
        val chain = Chain(x)
        val c = DoubleArray(conditionCount)
        c[ROW_X] = chain.x[wallCount]
        c[ROW_Y] = chain.y[wallCount]
        var turnSum = 0.0
        for (i in 0 until wallCount) turnSum += x[turnIndex(i)]
        c[ROW_TURNS] = wrapAngle(turnSum - FULL_TURN * windingSign)
        for (k in 0 until diagonalCount) {
            val wall = repeatedWall[k]
            val distance = if (wall == NOT_ADJACENT) chain.distance(diagonalFrom[k], diagonalTo[k]) else x[wall]
            c[FIXED_CONDITIONS + k] = distance - x[diagonalIndex(k)]
        }
        return c
    }

    /** Evaluates `A = ∂C/∂x`, one row per condition. */
    fun jacobian(x: DoubleArray): Array<DoubleArray> {
        val chain = Chain(x)
        val a = Array(conditionCount) { DoubleArray(variableCount) }
        val endX = chain.x[wallCount]
        val endY = chain.y[wallCount]
        for (i in 0 until wallCount) {
            a[ROW_X][i] = chain.cos[i]
            a[ROW_Y][i] = chain.sin[i]
            a[ROW_TURNS][turnIndex(i)] = 1.0
            if (i > 0) {
                a[ROW_X][turnIndex(i)] = -(endY - chain.y[i])
                a[ROW_Y][turnIndex(i)] = endX - chain.x[i]
            }
        }
        for (k in 0 until diagonalCount) {
            val row = a[FIXED_CONDITIONS + k]
            row[diagonalIndex(k)] = -1.0
            val wall = repeatedWall[k]
            if (wall == NOT_ADJACENT) diagonalRow(row, chain, diagonalFrom[k], diagonalTo[k]) else row[wall] = 1.0
        }
        return a
    }

    /** Partial derivatives of the walked distance `|V_b − V_a|`, `a < b`, by lengths and turns. */
    private fun diagonalRow(
        row: DoubleArray,
        chain: Chain,
        a: Int,
        b: Int,
    ) {
        val dx = chain.x[b] - chain.x[a]
        val dy = chain.y[b] - chain.y[a]
        val distance = StrictMath.hypot(dx, dy)
        if (!(distance > 0.0)) return
        val ux = dx / distance
        val uy = dy / distance
        for (i in a until b) row[i] = ux * chain.cos[i] + uy * chain.sin[i]
        for (j in 1 until b) {
            val pivot = if (j <= a) a else j
            val vx = chain.x[b] - chain.x[pivot]
            val vy = chain.y[b] - chain.y[pivot]
            row[turnIndex(j)] = -ux * vy + uy * vx
        }
    }

    /** Headings and corner points of the chain walked with the lengths and turns of `x`. */
    private inner class Chain(
        values: DoubleArray,
    ) {
        val cos = DoubleArray(wallCount)
        val sin = DoubleArray(wallCount)
        val x = DoubleArray(wallCount + 1)
        val y = DoubleArray(wallCount + 1)

        init {
            var heading = 0.0
            for (i in 0 until wallCount) {
                if (i > 0) heading += values[turnIndex(i)]
                cos[i] = StrictMath.cos(heading)
                sin[i] = StrictMath.sin(heading)
                x[i + 1] = x[i] + values[i] * cos[i]
                y[i + 1] = y[i] + values[i] * sin[i]
            }
        }

        fun distance(
            a: Int,
            b: Int,
        ): Double = StrictMath.hypot(x[b] - x[a], y[b] - y[a])
    }

    private companion object {
        const val ROW_X = 0
        const val ROW_Y = 1
        const val ROW_TURNS = 2
        const val FIXED_CONDITIONS = 3
        const val NOT_ADJACENT = -1
        const val FULL_TURN = 2.0 * StrictMath.PI
        val SIGMA_RIGHT_ANGLE_RAD: Double = StrictMath.toRadians(GeometryTolerances.SIGMA_RIGHT_ANGLE_DEG)
        val SIGMA_MEASURED_ANGLE_RAD: Double = StrictMath.toRadians(GeometryTolerances.SIGMA_MEASURED_ANGLE_DEG)

        /** `σ` of a corner observation in rad, `null` for a corner without observation in [mode]. */
        fun turnSigma(
            corner: CornerInput,
            mode: AdjustmentMode,
        ): Double? =
            when (corner) {
                is CornerInput.Measured -> {
                    SIGMA_MEASURED_ANGLE_RAD
                }

                is CornerInput.DeclaredRight -> {
                    SIGMA_RIGHT_ANGLE_RAD.takeIf { mode == AdjustmentMode.MEASUREMENT_ERROR }
                }

                CornerInput.Free -> {
                    null
                }
            }

        /**
         * Target `τ̂_i` of [corner], or `τ_0,derived = 360° · s − Σ_{i≥1} τ̂_i` for a free closing corner, exact in
         * millidegrees (DOMAIN section 6).
         */
        fun observedTurn(
            corner: CornerInput,
            index: Int,
            targets: TurnTargets,
        ): Angle =
            if (corner == CornerInput.Free) {
                val full = Angle(targets.winding.sign * Angle.FULL.milliDegrees)
                targets.targetTurns.drop(1).fold(full) { rest, turn -> rest - turn }
            } else {
                targets.targetTurns[index]
            }

        /** Wall `w_i` between adjacent corners `a < b`, or [NOT_ADJACENT]. */
        fun repeatedWallOf(
            a: Int,
            b: Int,
            n: Int,
        ): Int =
            when {
                b == a + 1 -> a
                a == 0 && b == n - 1 -> n - 1
                else -> NOT_ADJACENT
            }
    }
}

/** Wraps an angle in radians to `(−π, π]`. */
internal fun wrapAngle(radians: Double): Double {
    val shifted = StrictMath.PI - radians
    val turns = StrictMath.floor(shifted / (2.0 * StrictMath.PI))
    return StrictMath.PI - (shifted - turns * 2.0 * StrictMath.PI)
}
