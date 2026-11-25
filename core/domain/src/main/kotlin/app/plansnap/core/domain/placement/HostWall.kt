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
 * @property windingSign winding `s_W` of the displayed host outline, `+1` or `-1`.
 */
internal class HostWall(
    val start: Vec2,
    val direction: Vec2,
    val outwardNormal: Vec2,
    val thicknessMm: Double,
    val windingSign: Int,
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
    val sign = shape.winding.sign
    return HostWall(
        start = placement.toWorld(start),
        direction = direction,
        outwardNormal = Vec2(direction.y, -direction.x) * sign.toDouble(),
        thicknessMm = thicknessMm,
        windingSign = sign,
    )
}

/**
 * Computes the docked placement of a room (DOMAIN section 10, CR-W2-03).
 *
 * The own wall [ownWallIndex] of [ownShape] runs along `e_j = −s_W · s_G · e_i`, antiparallel to [host] for equal
 * windings and parallel for different ones, so the interior of the room always lies on the outward side of the host
 * wall. The room is only rotated, never mirrored. Along the host wall the own wall covers `[alongMm, alongMm + L_j]`:
 * its end nearest to `V_i`, `V_{j+1}` for equal and `V_j` for different windings, is placed at
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
    val sameWinding = host.windingSign == ownShape.winding.sign
    val targetDirection = if (sameWinding) host.direction * -1.0 else host.direction
    val anchor = if (sameWinding) ownEnd else ownStart
    val target = host.start + host.direction * alongMm + host.outwardNormal * host.thicknessMm
    val targetAngle = StrictMath.atan2(targetDirection.y, targetDirection.x)
    val rotation = normalizedAngle(targetAngle - StrictMath.atan2(ownDirection.y, ownDirection.x))
    val origin = target - anchor.rotated(rotation)
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
