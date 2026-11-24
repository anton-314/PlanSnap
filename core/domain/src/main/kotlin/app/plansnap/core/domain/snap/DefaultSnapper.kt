package app.plansnap.core.domain.snap

import app.plansnap.core.geometry.primitives.Vec2
import kotlin.math.floor
import kotlin.math.hypot

/**
 * Default [Snapper] with priority corner, wall, grid (DOMAIN section 10, REQ-UX-010).
 *
 * A candidate qualifies when its distance to the query point is at most `maxDistanceMm` (inclusive).
 * The first qualifying kind in priority order wins, even if a lower-priority candidate is closer.
 * Within a kind the nearest candidate wins; exact ties go to the candidate listed first.
 * Grid points are the multiples of the step on both axes; a coordinate exactly between two grid
 * lines rounds towards positive infinity. The result is a pure function of its inputs.
 */
class DefaultSnapper : Snapper {
    override fun snap(
        query: SnapQuery,
        targets: SnapTargets,
    ): SnapResult {
        if (!query.enabled) return SnapResult(query.point, null)
        val found =
            nearestCorner(query, targets)?.let { SnapResult(it, SnapKind.CORNER) }
                ?: nearestWall(query, targets)?.let { SnapResult(it, SnapKind.WALL) }
                ?: gridPoint(query)?.let { SnapResult(it, SnapKind.GRID) }
        return found ?: SnapResult(query.point, null)
    }

    private fun nearestCorner(
        query: SnapQuery,
        targets: SnapTargets,
    ): Vec2? = nearestWithin(query, targets.corners)

    private fun nearestWall(
        query: SnapQuery,
        targets: SnapTargets,
    ): Vec2? = nearestWithin(query, targets.walls.map { closestOnSegment(query.point, it) })

    private fun nearestWithin(
        query: SnapQuery,
        candidates: List<Vec2>,
    ): Vec2? =
        candidates
            .map { it to distance(query.point, it) }
            .filter { it.second <= query.maxDistanceMm }
            .minByOrNull { it.second }
            ?.first

    private fun gridPoint(query: SnapQuery): Vec2? {
        val step = query.gridStepMm ?: return null
        val candidate = Vec2(roundToStep(query.point.x, step), roundToStep(query.point.y, step))
        return candidate.takeIf { distance(query.point, it) <= query.maxDistanceMm }
    }

    private fun roundToStep(
        value: Double,
        step: Double,
    ): Double = floor(value / step + HALF) * step

    private fun closestOnSegment(
        point: Vec2,
        segment: SnapSegment,
    ): Vec2 {
        val direction = segment.end - segment.start
        val lengthSquared = direction.dot(direction)
        if (lengthSquared == 0.0) return segment.start
        val t = ((point - segment.start).dot(direction) / lengthSquared).coerceIn(0.0, 1.0)
        return segment.start + direction * t
    }

    private fun distance(
        a: Vec2,
        b: Vec2,
    ): Double = hypot(a.x - b.x, a.y - b.y)

    private companion object {
        const val HALF = 0.5
    }
}
