package app.plansnap.core.drawing.scene

import app.plansnap.core.geometry.primitives.GeometryTolerances
import app.plansnap.core.geometry.primitives.Vec2
import app.plansnap.core.model.RoomPlacement
import app.plansnap.core.model.WallId
import kotlinx.collections.immutable.persistentListOf
import kotlin.math.PI

/** Rigid placement `p_world = R(rotation) · p_local + origin` of DOMAIN section 2. */
internal class WorldTransform(
    private val placement: RoomPlacement,
) {
    private val origin = Vec2(placement.originX, placement.originY)

    /** Maps a room-local point to world coordinates. */
    fun apply(local: Vec2): Vec2 = local.rotated(placement.rotationRad) + origin
}

/**
 * Wall `w_i` in world coordinates, running from [start] to [end].
 *
 * @property direction unit vector from [start] to [end].
 * @property outward unit outward normal `n_i = s · (d.y, −d.x)` (DOMAIN section 8).
 * @property thicknessMm drawn outward thickness, zero for a wall drawn as a line.
 * @property lengthMm length shown in the dimension label.
 */
internal data class WallFrame(
    val index: Int,
    val wallId: WallId,
    val start: Vec2,
    val end: Vec2,
    val direction: Vec2,
    val outward: Vec2,
    val thicknessMm: Double,
    val lengthMm: Double,
) {
    /** Point at [distanceMm] from [start] along the wall, shifted outward by [outwardMm]. */
    fun at(
        distanceMm: Double,
        outwardMm: Double = 0.0,
    ): Vec2 = start + direction * distanceMm + outward * outwardMm
}

/**
 * Builds one [WallFrame] per wall. Wall `i` runs from `points[i]` to `points[(i + 1) % points.size]`, so a closed
 * outline wraps around and an open chain with one more point than walls does not.
 *
 * @param wallIds id of each wall.
 * @param windingSign orientation `s`, `+1` or `−1`.
 * @param thicknessesMm drawn thickness of each wall.
 * @param lengthsMm label length of each wall.
 */
internal fun wallFrames(
    wallIds: List<WallId>,
    points: List<Vec2>,
    windingSign: Int,
    thicknessesMm: List<Double>,
    lengthsMm: List<Double>,
): List<WallFrame> =
    wallIds.mapIndexed { i, id ->
        val start = points[i]
        val end = points[(i + 1) % points.size]
        val edge = end - start
        val direction = edge * (1.0 / edge.length())
        WallFrame(
            index = i,
            wallId = id,
            start = start,
            end = end,
            direction = direction,
            outward = Vec2(windingSign * direction.y, -windingSign * direction.x),
            thicknessMm = thicknessesMm[i],
            lengthMm = lengthsMm[i],
        )
    }

/**
 * Text rotation along [direction] in `(−π/2 + ε, π/2 + ε]`, turned by half a turn if needed so that text never reads
 * upside down, with `ε =` [GeometryTolerances.NEAR_COLLINEAR_SIN]. A direction within `ε` radians of `−y` thus
 * reads like `+y`, so that text along every nearly vertical wall reads upward, whatever the rounding of its end points.
 */
internal fun uprightAngle(direction: Vec2): Double {
    val angle = StrictMath.atan2(direction.y, direction.x)
    val tolerance = GeometryTolerances.NEAR_COLLINEAR_SIN
    return when {
        angle > PI / 2.0 + tolerance -> angle - PI
        angle <= -PI / 2.0 + tolerance -> angle + PI
        else -> angle
    }
}

/** Open polyline from [from] to [to]. */
internal fun segment(
    from: Vec2,
    to: Vec2,
    style: SceneStyle,
    target: SceneTarget?,
): SceneElement.Polyline = SceneElement.Polyline(persistentListOf(from, to), closed = false, style, target)

/** Midpoint of [a] and [b]. */
internal fun midpoint(
    a: Vec2,
    b: Vec2,
): Vec2 = Vec2((a.x + b.x) / 2.0, (a.y + b.y) / 2.0)
