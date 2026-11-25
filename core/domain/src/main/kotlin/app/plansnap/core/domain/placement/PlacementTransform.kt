package app.plansnap.core.domain.placement

import app.plansnap.core.geometry.primitives.Vec2
import app.plansnap.core.model.RoomPlacement

/**
 * Maps a room-local point to world coordinates, `p_world = R(rotation) · p_local + origin` (DOMAIN section 2).
 *
 * The map is rigid: it only rotates and translates, so all lengths and angles of a room are preserved. Trigonometry
 * uses [StrictMath], so the result is bit-identical on every device.
 */
fun RoomPlacement.toWorld(local: Vec2): Vec2 = local.rotated(rotationRad) + Vec2(originX, originY)

/** Maps a room-local direction to world coordinates. Only the rotation applies, the origin does not. */
fun RoomPlacement.directionToWorld(local: Vec2): Vec2 = local.rotated(rotationRad)
