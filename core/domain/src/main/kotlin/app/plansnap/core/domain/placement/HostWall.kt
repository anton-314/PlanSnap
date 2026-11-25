package app.plansnap.core.domain.placement

import app.plansnap.core.domain.evaluation.EvaluatedShape
import app.plansnap.core.geometry.primitives.Vec2
import app.plansnap.core.model.DockLink
import app.plansnap.core.model.RoomPlacement

/**
 * Host side of a dock: the displayed host wall in world coordinates (DOMAIN sections 8 and 10).
 *
 * @property start world position of the host corner `V_i`, where the host wall starts.
 * @property direction world unit vector along the host wall, from `V_i` to `V_{i+1}`.
 * @property outwardNormal world unit vector of the outward normal `n_i = s_F · (sin φ_i, −cos φ_i)`.
 * @property thicknessMm thickness of the host wall, the separating wall thickness (ADR 0013).
 */
internal class HostWall(
    val start: Vec2,
    val direction: Vec2,
    val outwardNormal: Vec2,
    val thicknessMm: Double,
)

/**
 * Returns the host wall [wallIndex] of the displayed [shape] placed by [placement] with the outward thickness
 * [thicknessMm].
 */
internal fun hostWall(
    shape: EvaluatedShape,
    placement: RoomPlacement,
    wallIndex: Int,
    thicknessMm: Double,
): HostWall {
    val (start, end) = wallEnds(shape, wallIndex)
    val local = end - start
    val direction = placement.directionToWorld(local * (1.0 / local.length()))
    val sign = shape.winding.sign.toDouble()
    return HostWall(
        start = placement.toWorld(start),
        direction = direction,
        outwardNormal = Vec2(direction.y, -direction.x) * sign,
        thicknessMm = thicknessMm,
    )
}

/**
 * Computes the docked placement of a room (DOMAIN section 10).
 *
 * The own wall [ownWallIndex] of [ownShape] is turned antiparallel to [host]. Its start point `V_j` is placed at
 * `start + alongMm · direction + thickness · outwardNormal`, so the inner contours are exactly the host wall thickness
 * apart. [alongMm] is the effective shift along the host wall from `V_i`, already including the opening offset when
 * the link refers to an opening. The rotation is normalized to `(−π, π]`.
 */
internal fun dockedPlacement(
    host: HostWall,
    alongMm: Double,
    ownShape: EvaluatedShape,
    ownWallIndex: Int,
    link: DockLink,
): RoomPlacement {
    val (ownStart, ownEnd) = wallEnds(ownShape, ownWallIndex)
    val ownDirection = ownEnd - ownStart
    val target = host.start + host.direction * alongMm + host.outwardNormal * host.thicknessMm
    val targetAngle = StrictMath.atan2(-host.direction.y, -host.direction.x)
    val rotation = normalizedAngle(targetAngle - StrictMath.atan2(ownDirection.y, ownDirection.x))
    val origin = target - ownStart.rotated(rotation)
    // Adding 0.0 turns a negative zero into a positive one, so equal geometry yields equal placements.
    return RoomPlacement(origin.x + 0.0, origin.y + 0.0, rotation + 0.0, link)
}

/** Start `V_i` and end `V_{i+1}` of wall [index] of [shape] in room-local coordinates. */
private fun wallEnds(
    shape: EvaluatedShape,
    index: Int,
): Pair<Vec2, Vec2> {
    val vertices = shape.outline.vertices
    return vertices[index] to vertices[(index + 1) % vertices.size]
}

/** Maps an angle from `(−2π, 2π)` to `(−π, π]`. */
private fun normalizedAngle(radians: Double): Double =
    when {
        radians > Math.PI -> radians - 2.0 * Math.PI
        radians <= -Math.PI -> radians + 2.0 * Math.PI
        else -> radians
    }
