package app.plansnap.core.geometry.traverse

import app.plansnap.core.common.units.Angle
import app.plansnap.core.geometry.primitives.ClosureTolerance
import app.plansnap.core.geometry.primitives.GeometryTolerances
import app.plansnap.core.geometry.primitives.Vec2
import app.plansnap.core.geometry.primitives.Winding

/**
 * Stateless [Traverse] after DOMAIN sections 3 and 4.
 *
 * Every result is bit-identical on every device: target turns and headings are exact integers in millidegrees,
 * trigonometry and roots go through [StrictMath], and the evaluation order is fixed (DOMAIN section 11).
 * The target shape uses exact unit vectors for headings that are whole multiples of 90°, so outlines with declared
 * right angles and whole-millimeter walls have exactly representable corner points.
 */
class DefaultTraverse : Traverse {
    override fun targets(input: ShapeInput): TurnTargets {
        // ShapeInput guarantees that every corner after V_0 is fixed, so no inner turn is dropped here.
        val innerTurns = input.corners.drop(1).mapNotNull(::fixedTurn)
        val innerSum = innerTurns.fold(Angle.ZERO) { sum, turn -> sum + turn }
        val winding = if (innerSum >= Angle.ZERO) Winding.COUNTER_CLOCKWISE else Winding.CLOCKWISE
        val closingTurn = fixedTurn(input.corners.first()) ?: freeClosingTurn(winding, innerSum)
        return TurnTargets(winding, listOf(closingTurn) + innerTurns)
    }

    /** @throws IllegalArgumentException if [lengthsMm] and [turnsRad] differ in size or a point is not finite. */
    override fun walk(
        lengthsMm: List<Double>,
        turnsRad: List<Double>,
    ): List<Vec2> {
        require(lengthsMm.size == turnsRad.size) { "Expected one turn per wall" }
        val points = ArrayList<Vec2>(lengthsMm.size + 1)
        points += Vec2.ZERO
        var heading = 0.0
        for (i in lengthsMm.indices) {
            if (i > 0) heading += turnsRad[i]
            val length = lengthsMm[i]
            points += points.last() + Vec2(length * StrictMath.cos(heading), length * StrictMath.sin(heading))
        }
        return points
    }

    override fun closureError(input: ShapeInput): ClosureError {
        val targets = targets(input)
        val points = targetPoints(input, targets)
        val gap = points.last().length()
        val maxDiagonalResidual =
            input.diagonals.maxOfOrNull { diagonal ->
                val distance = (points[diagonal.toCorner] - points[diagonal.fromCorner]).length()
                StrictMath.abs(distance - diagonal.lengthMm.toDouble())
            } ?: 0.0
        return ClosureError(
            gapMm = gap,
            maxDiagonalResidualMm = maxDiagonalResidual,
            closingAngleResidualMm = closingAngleResidual(input, targets),
        )
    }

    override fun classify(
        input: ShapeInput,
        error: ClosureError,
        tolerance: ClosureTolerance,
    ): ClosureClass {
        val e = error.errorMm
        return when {
            e <= GeometryTolerances.EXACT_CLOSURE_MM -> ClosureClass.EXACT
            e <= tolerance.limitMm(perimeterMm(input)) -> ClosureClass.MEASUREMENT_ERROR
            else -> ClosureClass.SHAPE_DEVIATION
        }
    }

    /**
     * Walks the target shape: `lengthsMm.size + 1` points from `V_0` at the origin, with exact integer headings.
     * Corner `V_i` is the point at index `i`, the last point is the end of the final wall.
     */
    internal fun targetPoints(
        input: ShapeInput,
        targets: TurnTargets,
    ): List<Vec2> {
        val points = ArrayList<Vec2>(input.wallCount + 1)
        points += Vec2.ZERO
        var heading = 0
        for (i in 0 until input.wallCount) {
            if (i > 0) heading = Math.floorMod(heading + targets.targetTurns[i].milliDegrees, FULL_MILLI_DEGREES)
            points += points.last() + unitVector(heading) * input.lengthsMm[i].toDouble()
        }
        return points
    }

    /** Term `a = |wrap(τ̂_0,fixed − τ_0,derived)| · U / (2π)`, zero for a free closing corner. */
    private fun closingAngleResidual(
        input: ShapeInput,
        targets: TurnTargets,
    ): Double {
        val fixedClosing = fixedTurn(input.corners.first()) ?: return 0.0
        val derivedClosing = derivedClosingTurn(targets.winding, targets.targetTurns.drop(1))
        val deviation = (fixedClosing - derivedClosing).normalized().milliDegrees
        return StrictMath.abs(deviation).toDouble() * perimeterMm(input) / FULL_MILLI_DEGREES.toDouble()
    }

    private companion object {
        const val QUARTER_MILLI_DEGREES = 90_000
        const val FULL_MILLI_DEGREES = 360_000

        /** Candidates `{−90°, 0°, +90°}` of a free closing corner (DOMAIN section 3). */
        val CLOSING_CANDIDATES: List<Angle> = listOf(-Angle.RIGHT, Angle.ZERO, Angle.RIGHT)

        /** Exact unit vectors of the headings 0°, 90°, 180° and 270°. */
        val QUADRANT_UNITS: List<Vec2> = listOf(Vec2(1.0, 0.0), Vec2(0.0, 1.0), Vec2(-1.0, 0.0), Vec2(0.0, -1.0))

        /** Target turn of an entered corner, `null` for a free corner. */
        fun fixedTurn(corner: CornerInput): Angle? =
            when (corner) {
                is CornerInput.DeclaredRight -> Angle.RIGHT.toTurn(corner.direction)
                is CornerInput.Measured -> corner.angle.toTurn(corner.direction)
                CornerInput.Free -> null
            }

        /** `τ_0,derived = 360° · s − Σ_{i≥1} τ̂_i`. */
        fun derivedClosingTurn(
            winding: Winding,
            innerTurns: List<Angle>,
        ): Angle = innerTurns.fold(Angle(winding.sign * FULL_MILLI_DEGREES)) { rest, turn -> rest - turn }

        /**
         * Target of a free closing corner: the candidate with the smallest cyclic distance to `r`, ties broken by the
         * largest `s · τ̂_0`. Exact in millidegrees, so ties are detected exactly.
         */
        fun freeClosingTurn(
            winding: Winding,
            innerSum: Angle,
        ): Angle {
            val remainder = (Angle(winding.sign * FULL_MILLI_DEGREES) - innerSum).normalized()
            return CLOSING_CANDIDATES.minWith(
                compareBy<Angle> { StrictMath.abs((it - remainder).normalized().milliDegrees) }
                    .thenByDescending { winding.sign * it.milliDegrees },
            )
        }

        /** Unit vector of a heading in `[0°, 360°)` given in millidegrees. */
        fun unitVector(headingMilliDegrees: Int): Vec2 =
            if (headingMilliDegrees % QUARTER_MILLI_DEGREES == 0) {
                QUADRANT_UNITS[headingMilliDegrees / QUARTER_MILLI_DEGREES]
            } else {
                val radians = Angle(headingMilliDegrees).toRadians()
                Vec2(StrictMath.cos(radians), StrictMath.sin(radians))
            }

        /** Perimeter `U = Σ L_i` of the raw lengths in mm. */
        fun perimeterMm(input: ShapeInput): Double = input.lengthsMm.sum().toDouble()
    }
}
