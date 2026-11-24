package app.plansnap.core.geometry.solver

import app.plansnap.core.geometry.polygon.DefaultPolygonAnalyzer
import app.plansnap.core.geometry.primitives.GeometryTolerances
import app.plansnap.core.geometry.primitives.Polygon2
import app.plansnap.core.geometry.primitives.Vec2
import app.plansnap.core.geometry.traverse.CornerInput
import app.plansnap.core.geometry.traverse.DefaultTraverse
import app.plansnap.core.geometry.traverse.DiagonalInput
import app.plansnap.core.geometry.traverse.ShapeInput

/** Walked chain `P_0 … P_n` in mm, `P_0` at the origin and `w_0` along +x. */
internal class Chain(
    val x: DoubleArray,
    val y: DoubleArray,
) {
    /** The corners `V_0 … V_{n-1}`, without the end point of the last wall. */
    fun vertices(): List<Vec2> = List(x.size - 1) { Vec2(x[it], y[it]) }
}

/**
 * The hard conditions and the objective of DOMAIN section 5.1 for one input.
 *
 * Turns are full arrays `τ_0 … τ_{n-1}` in radians. The variables `u` are the turns at [free]. Measured corners keep
 * their target, the walk ignores `τ_0`. Distance rows are divided by the perimeter `U`, so all rows have the same order
 * of magnitude. Trigonometry and roots use [StrictMath], the evaluation order is fixed (DOMAIN section 11).
 */
internal class SolverProblem(
    val input: ShapeInput,
) {
    /** Number of walls `n`. */
    val n: Int = input.wallCount

    /** Raw wall lengths in mm. */
    val lengths: DoubleArray = DoubleArray(n) { input.lengthsMm[it].toDouble() }

    /** Perimeter `U = Σ L_i` of the raw lengths, exact because the lengths are whole millimeters. */
    val perimeter: Double = input.lengthsMm.sum().toDouble()

    /** Orientation `s` from the targets (DOMAIN section 3). */
    val sign: Int

    /** Target turns `τ̂_i` in radians. */
    val targets: DoubleArray

    init {
        val turnTargets = DefaultTraverse().targets(input)
        sign = turnTargets.winding.sign
        targets = DoubleArray(n) { turnTargets.targetTurns[it].toRadians() }
    }

    /** Indices `i ≥ 1` of the corners that are not measured, the variables `u_frei` (DOMAIN section 5.4). */
    val free: IntArray = (1 until n).filter { input.corners[it] !is CornerInput.Measured }.toIntArray()

    /** Whether the closing corner `V_0` is measured and therefore a hard condition. */
    val closingMeasured: Boolean = input.corners[0] is CornerInput.Measured

    /**
     * All rows of DOMAIN section 5.4 in their fixed order: closure in x and y, measured closing corner, then the
     * diagonals between non-adjacent corners sorted by `(min, max, length, input index)`.
     */
    val rows: List<HardCondition> = buildRows()

    /** Returns the targets with the variables [u] inserted at [free]. */
    fun fullTurns(u: DoubleArray): DoubleArray {
        val turns = targets.copyOf()
        for (k in free.indices) turns[free[k]] = u[k]
        return turns
    }

    /** Returns the variables `u` of [turns]. */
    fun freeValues(turns: DoubleArray): DoubleArray = DoubleArray(free.size) { turns[free[it]] }

    /** Walks the raw lengths with [turns] in the order of `Traverse.walk`. */
    fun walk(turns: DoubleArray): Chain {
        val x = DoubleArray(n + 1)
        val y = DoubleArray(n + 1)
        var heading = 0.0
        for (i in 0 until n) {
            if (i > 0) heading += turns[i]
            x[i + 1] = x[i] + lengths[i] * StrictMath.cos(heading)
            y[i + 1] = y[i] + lengths[i] * StrictMath.sin(heading)
        }
        return Chain(x, y)
    }

    /** `τ_0,derived = 2π·s − Σ_{i≥1} τ_i`, not wrapped. */
    fun derivedClosingTurn(turns: DoubleArray): Double {
        var inner = 0.0
        for (i in 1 until n) inner += turns[i]
        return FULL_TURN * sign - inner
    }

    /** `wrap(2π·s − Σ_{i≥1} τ_i − τ̂_0)`, the deviation of the closing corner from its target. */
    fun closingResidual(turns: DoubleArray): Double = wrap(derivedClosingTurn(turns) - targets[0])

    /** Values `c(u)` of [selected] at [turns], distance rows divided by `U`. */
    fun residuals(
        selected: List<HardCondition>,
        turns: DoubleArray,
        chain: Chain,
    ): DoubleArray =
        DoubleArray(selected.size) { r ->
            when (val row = selected[r]) {
                HardCondition.ClosureX -> {
                    chain.x[n] / perimeter
                }

                HardCondition.ClosureY -> {
                    chain.y[n] / perimeter
                }

                HardCondition.ClosingCorner -> {
                    closingResidual(turns)
                }

                is HardCondition.DiagonalLength -> {
                    val diagonal = input.diagonals[row.index]
                    val length = diagonal.lengthMm.toDouble()
                    val distance = distance(chain, diagonal)
                    (distance * distance - length * length) / (2.0 * length) / perimeter
                }
            }
        }

    /** Whether the closed outline of [chain] is simple with [GeometryTolerances.MIN_FEATURE_MM] (DOMAIN section 7). */
    fun isSimple(chain: Chain): Boolean =
        ANALYZER.isSimple(Polygon2(chain.vertices()), GeometryTolerances.MIN_FEATURE_MM)

    /** Distance `|P_a P_b|` of the corners of [diagonal]. */
    fun distance(
        chain: Chain,
        diagonal: DiagonalInput,
    ): Double {
        val a = diagonal.fromCorner
        val b = diagonal.toCorner
        return StrictMath.hypot(chain.x[a] - chain.x[b], chain.y[a] - chain.y[b])
    }

    /** Whether [diagonal] connects adjacent corners and therefore only repeats a wall. */
    fun isAdjacent(diagonal: DiagonalInput): Boolean {
        val a = minOf(diagonal.fromCorner, diagonal.toCorner)
        val b = maxOf(diagonal.fromCorner, diagonal.toCorner)
        return b == a + 1 || (a == 0 && b == n - 1)
    }

    private fun buildRows(): List<HardCondition> {
        val diagonals =
            input.diagonals.indices
                .filterNot { isAdjacent(input.diagonals[it]) }
                .sortedWith(
                    compareBy<Int>(
                        { minOf(input.diagonals[it].fromCorner, input.diagonals[it].toCorner) },
                        { maxOf(input.diagonals[it].fromCorner, input.diagonals[it].toCorner) },
                        { input.diagonals[it].lengthMm },
                        { it },
                    ),
                ).map { HardCondition.DiagonalLength(it) }
        val closing = if (closingMeasured) listOf(HardCondition.ClosingCorner) else emptyList()
        return listOf(HardCondition.ClosureX, HardCondition.ClosureY) + closing + diagonals
    }

    companion object {
        /** `π`. */
        const val HALF_TURN: Double = StrictMath.PI

        /** `2π`. */
        const val FULL_TURN: Double = 2.0 * StrictMath.PI

        private val ANALYZER = DefaultPolygonAnalyzer()

        /** Wraps [angle] to `(−π, π]`, as `π − ((π − a) mod 2π)`. */
        fun wrap(angle: Double): Double = HALF_TURN - (HALF_TURN - angle).mod(FULL_TURN)
    }
}
