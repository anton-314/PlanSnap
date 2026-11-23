package app.plansnap.core.domain.snap

import app.plansnap.core.geometry.primitives.Vec2
import kotlinx.collections.immutable.ImmutableList

/** Kind of snap candidate, declared by descending priority (DOMAIN section 10). */
enum class SnapKind {
    /** Room corner. */
    CORNER,

    /** Wall line. */
    WALL,

    /** Grid point. */
    GRID,
}

/** Wall line in world coordinates. */
data class SnapSegment(
    val start: Vec2,
    val end: Vec2,
)

/**
 * Snap request in world coordinates.
 *
 * @property point input point.
 * @property maxDistanceMm snap distance, positive and finite, converted from 16 dp by the caller.
 * @property gridStepMm grid step, positive and finite, `null` without a grid.
 * @property enabled if `false`, the input is returned unchanged.
 */
data class SnapQuery(
    val point: Vec2,
    val maxDistanceMm: Double,
    val gridStepMm: Double?,
    val enabled: Boolean,
) {
    init {
        require(maxDistanceMm.isFinite() && maxDistanceMm > 0.0) { "Invalid snap distance: $maxDistanceMm" }
        require(gridStepMm == null || (gridStepMm.isFinite() && gridStepMm > 0.0)) { "Invalid grid step: $gridStepMm" }
    }
}

/** Snap candidates in world coordinates. */
data class SnapTargets(
    val corners: ImmutableList<Vec2>,
    val walls: ImmutableList<SnapSegment>,
)

/**
 * Snap outcome.
 *
 * @property point resulting point.
 * @property kind matched candidate, `null` if the input stayed unchanged.
 */
data class SnapResult(
    val point: Vec2,
    val kind: SnapKind?,
)

/** Pure snapping with priority corner, wall, grid (DOMAIN section 10, REQ-UX-010). */
fun interface Snapper {
    /** Snaps [query] to [targets]. */
    fun snap(
        query: SnapQuery,
        targets: SnapTargets,
    ): SnapResult
}
