package app.plansnap.core.geometry.solver

import app.plansnap.core.geometry.solver.SolverProblem.Companion.FULL_TURN
import app.plansnap.core.geometry.solver.SolverProblem.Companion.HALF_TURN
import app.plansnap.core.geometry.traverse.CornerInput

/** The fixed start values S1 to S4 of DOMAIN section 5.3 as full turn arrays. */
internal object SolverStarts {
    /** Bisection steps for the radius of the chord polygon. */
    private const val CHORD_BISECTION_STEPS = 100

    /** Returns the start values in their mandatory order. S4 is missing if it is not defined. */
    fun of(problem: SolverProblem): List<Pair<SolverStart, DoubleArray>> {
        val starts =
            mutableListOf(
                SolverStart.TARGET_TURNS to problem.targets.copyOf(),
                SolverStart.MEAN_TURNS to meanTurns(problem),
                SolverStart.CHORD_POLYGON to chordTurns(problem),
            )
        directionTurns(problem)?.let { starts += SolverStart.DIRECTION_TURNS to it }
        return starts
    }

    /** S2: mean of `τ̂_i` and the regular polygon turn `s · 2π / n`. */
    fun meanTurns(problem: SolverProblem): DoubleArray {
        val regular = problem.sign * FULL_TURN / problem.n
        return DoubleArray(problem.n) { (problem.targets[it] + regular) / 2.0 }
    }

    /**
     * S3: turns of the polygon inscribed in a circle with the raw lengths as chords. The radius `R` solves
     * `Σ 2·asin(L_i / (2R)) = 2π` by bisection. If the center lies outside, the arc of the longest wall becomes
     * `2π − 2·asin(L_max / (2R))`.
     */
    fun chordTurns(problem: SolverProblem): DoubleArray {
        val lengths = problem.lengths
        val longest = lengths.max()
        val longestIndex = lengths.indexOfFirst { it >= longest }
        var low = longest / 2.0
        var high = problem.perimeter
        val outside = arcs(lengths, low, longestIndex, outside = false).sum() < FULL_TURN
        repeat(CHORD_BISECTION_STEPS) {
            val middle = (low + high) / 2.0
            if ((arcs(lengths, middle, longestIndex, outside).sum() > FULL_TURN) != outside) {
                low = middle
            } else {
                high = middle
            }
        }
        val arcs = arcs(lengths, high, longestIndex, outside)
        val n = problem.n
        return DoubleArray(n) { i -> problem.sign * (arcs[(i + n - 1) % n] + arcs[i]) / 2.0 }
    }

    /**
     * S4: `τ_i = d_i · β` at the free corners with `β = (2π·s − Σ τ̂_measured − τ̂_0) / Σ d_i`. Returns `null` if the
     * denominator is zero or `β` is not in `(0, π)`.
     */
    fun directionTurns(problem: SolverProblem): DoubleArray? {
        val directions = problem.free.map { (problem.input.corners[it] as CornerInput.DeclaredRight).direction.sign }
        val weight = directions.sum()
        var measured = 0.0
        for (i in 1 until problem.n) {
            if (i !in problem.free) measured += problem.targets[i]
        }
        // A zero denominator gives an infinite or NaN β, which fails the range check as well.
        val beta = (FULL_TURN * problem.sign - measured - problem.targets[0]) / weight.toDouble()
        if (!(beta > 0.0 && beta < HALF_TURN)) return null
        val turns = problem.targets.copyOf()
        problem.free.forEachIndexed { k, i -> turns[i] = directions[k] * beta }
        return turns
    }

    private fun arcs(
        lengths: DoubleArray,
        radius: Double,
        longestIndex: Int,
        outside: Boolean,
    ): DoubleArray {
        val arcs = DoubleArray(lengths.size) { 2.0 * StrictMath.asin(minOf(1.0, lengths[it] / (2.0 * radius))) }
        if (outside) arcs[longestIndex] = FULL_TURN - arcs[longestIndex]
        return arcs
    }
}
