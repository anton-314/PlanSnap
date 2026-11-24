package app.plansnap.core.geometry.determinacy.rig

import app.plansnap.core.domain.evaluation.ProposalReason
import app.plansnap.core.geometry.determinacy.shapeDistanceMm
import app.plansnap.core.geometry.primitives.Polygon2
import app.plansnap.core.geometry.primitives.Vec2
import app.plansnap.core.geometry.solver.SolverProblem
import app.plansnap.core.geometry.solver.violatesDirections
import app.plansnap.core.geometry.traverse.CornerInput
import app.plansnap.core.geometry.traverse.DefaultTraverse
import app.plansnap.core.geometry.traverse.ShapeInput
import app.plansnap.core.model.ConflictReason
import app.plansnap.core.model.ShapeStatus

/**
 * Findings of one rig case, as the counters of `stress.one`. Keys without the prefix `info-` are deviations, keys with
 * it are statistics.
 */
internal class RigTally {
    val counts: MutableMap<String, Int> = sortedMapOf()
    val samples: MutableMap<String, String> = sortedMapOf()

    fun count(
        key: String,
        case: RigCase? = null,
    ) {
        counts.merge(key, 1, Int::plus)
        if (case != null) samples.putIfAbsent(key, RigCases.describe(case.input))
    }

    fun addAll(other: RigTally) {
        other.counts.forEach { (key, value) -> counts.merge(key, value, Int::plus) }
        other.samples.forEach { (key, value) -> samples.putIfAbsent(key, value) }
    }

    /** Sum of the counters whose key starts with [prefix]. */
    fun sum(prefix: String): Int = counts.filterKeys { it.startsWith(prefix) }.values.sum()
}

/** Checks of `stress.py` against the known truth of each class. */
internal object RigChecks {
    private val traverse = DefaultTraverse()

    /** Runs [case] through [evaluator] and records status and findings, a crash included. */
    fun run(
        case: RigCase,
        evaluator: RigEvaluator,
    ): RigTally {
        val tally = RigTally()
        val tag = case.tag
        tally.count("$tag:total")
        try {
            val result = evaluator.evaluate(case.input)
            tally.count("$tag:status:${result.status}")
            if (result.proposal == ProposalReason.AMBIGUOUS_SHAPE) {
                tally.count("$tag:info-proposed-ambiguous")
                if (result.ambiguity?.recommendation == null) tally.count("$tag:info-ambiguous-without-recommendation")
            }
            when {
                case.gapMm != null -> checkGap(case, result, tally)
                case.truth != null -> checkTrue(case, case.truth, result, tally)
                tag == "K2" -> checkDirections(case, result, tally)
                else -> checkUnderdetermined(case, result, tally)
            }
        } catch (expected: RuntimeException) {
            tally.count("$tag:crash:${expected.javaClass.simpleName}", case)
        }
        return tally
    }

    private fun checkTrue(
        case: RigCase,
        truth: List<Vec2>,
        result: RigResult,
        tally: RigTally,
    ) {
        val tag = case.tag
        val input = case.input
        val limit = limit(input)
        if (result.status == ShapeStatus.CONFLICT) {
            if (roundingImpossible(input, result)) {
                tally.count("$tag:rounding-impossible")
            } else {
                tally.count("$tag:false-conflict:${result.conflict}:${result.path}", case)
            }
            return
        }
        val points = points(result)
        if (!RigGeometry.isSimple(points)) {
            tally.count("$tag:non-simple:${result.status}", case)
            return
        }
        if (result.proposal == ProposalReason.AMBIGUOUS_SHAPE) {
            val shown = if (distance(points, truth) > limit) "shown-differs" else "shown-matches"
            tally.count("$tag:info-ambiguous:${result.path}:instead-${result.instead}:$shown")
        }
        if (result.status == ShapeStatus.CONFIRMED && distance(points, truth) > limit) {
            if (meetsInputs(result, input)) {
                tally.count("$tag:ambiguous-other-solution", case)
            } else {
                tally.count("$tag:wrong-confirmed:${result.path}", case)
            }
        }
    }

    private fun checkDirections(
        case: RigCase,
        result: RigResult,
        tally: RigTally,
    ) {
        val shown = result.status in setOf(ShapeStatus.CONFIRMED, ShapeStatus.NEEDS_ADJUSTMENT, ShapeStatus.PROPOSED)
        if (shown && !RigGeometry.isSimple(points(result))) {
            tally.count("K2:non-simple:${result.status}:${result.path}", case)
        }
        if (result.status == ShapeStatus.CONFIRMED && violates(case.input, result)) {
            tally.count("K2:confirmed-direction:${result.path}", case)
        }
    }

    private fun checkUnderdetermined(
        case: RigCase,
        result: RigResult,
        tally: RigTally,
    ) {
        if (result.status == ShapeStatus.CONFIRMED) tally.count("K3:confirmed-underdetermined:${result.path}", case)
        val shown = result.status == ShapeStatus.NEEDS_ADJUSTMENT || result.status == ShapeStatus.PROPOSED
        if (shown && !RigGeometry.isSimple(points(result))) tally.count("K3:non-simple:${result.status}", case)
    }

    private fun checkGap(
        case: RigCase,
        result: RigResult,
        tally: RigTally,
    ) {
        val gap = requireNotNull(case.gapMm)
        val wanted =
            when {
                gap <= 1 -> ShapeStatus.CONFIRMED
                gap <= limit(case.input) -> ShapeStatus.NEEDS_ADJUSTMENT
                else -> null
            }
        if (wanted != null && result.status != wanted) tally.count("K4-gap:gap-status:$gap:${result.status}", case)
        if (wanted == null && result.status == ShapeStatus.CONFIRMED && violates(case.input, result)) {
            tally.count("K4-gap:direction", case)
        }
    }

    /** `stress.meets_inputs`: lengths and diagonals within 1.5 mm, measured corners within 0.05°, directions kept. */
    private fun meetsInputs(
        result: RigResult,
        input: ShapeInput,
    ): Boolean {
        val lengths = requireNotNull(result.lengthsMm)
        val turns = requireNotNull(result.turnsRad)
        val points = points(result)
        val lengthsMatch = lengths.indices.all { StrictMath.abs(lengths[it] - input.lengthsMm[it]) <= INPUT_MM }
        val diagonalsMatch =
            input.diagonals.all {
                StrictMath.abs((points[it.toCorner] - points[it.fromCorner]).length() - it.lengthMm) <= INPUT_MM
            }
        val cornersMatch =
            input.corners.indices.all { i ->
                val corner = input.corners[i]
                corner !is CornerInput.Measured ||
                    StrictMath.abs(SolverProblem.wrap(turns[i] - corner.angle.toTurn(corner.direction).toRadians())) <=
                    INPUT_RAD
            }
        return lengthsMatch && diagonalsMatch && cornersMatch && !violates(input, result)
    }

    /** A conflict from a necessary condition is no finding if the rounded values provably admit no shape. */
    private fun roundingImpossible(
        input: ShapeInput,
        result: RigResult,
    ): Boolean {
        val necessary =
            result.conflict == ConflictReason.WALL_TOO_LONG || result.conflict == ConflictReason.DIAGONAL_IMPOSSIBLE
        return necessary && RigNecessary.violation(input, ROUNDING_PER_WALL_MM * (input.wallCount + 1)) == null
    }

    private fun violates(
        input: ShapeInput,
        result: RigResult,
    ): Boolean = SolverProblem(input).violatesDirections(requireNotNull(result.turnsRad).toDoubleArray())

    private fun points(result: RigResult): List<Vec2> =
        traverse.walk(requireNotNull(result.lengthsMm), requireNotNull(result.turnsRad)).dropLast(1)

    private fun distance(
        points: List<Vec2>,
        truth: List<Vec2>,
    ): Double = shapeDistanceMm(Polygon2(points), Polygon2(truth))

    private fun limit(input: ShapeInput): Double = maxOf(LIMIT_ABS_MM, LIMIT_REL * input.lengthsMm.sum())

    private const val INPUT_MM = 1.5
    private val INPUT_RAD = StrictMath.toRadians(0.05)
    private const val ROUNDING_PER_WALL_MM = 0.5
    private const val LIMIT_ABS_MM = 50.0
    private const val LIMIT_REL = 0.01
}
