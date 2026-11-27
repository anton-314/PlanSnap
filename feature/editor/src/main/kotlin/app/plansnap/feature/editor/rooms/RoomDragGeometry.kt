package app.plansnap.feature.editor.rooms

import app.plansnap.core.domain.evaluation.RoomEvaluation
import app.plansnap.core.domain.placement.toWorld
import app.plansnap.core.domain.snap.SnapSegment
import app.plansnap.core.domain.snap.SnapTargets
import app.plansnap.core.geometry.primitives.Vec2
import app.plansnap.core.model.Room
import app.plansnap.core.model.RoomId
import app.plansnap.feature.editor.state.EditorUiState
import kotlinx.collections.immutable.toImmutableList

// What a drag collects once when it starts: the rooms that follow, the corners, the pivot and the snap candidates.

/**
 * The dragged room and the docked rooms that follow it. The domain places a docked room anew only if its host and
 * the room itself have a shape (DOMAIN section 10); otherwise it keeps its place, and so does everything that
 * hangs on it.
 */
internal fun movingRooms(
    rooms: List<Room>,
    id: RoomId,
    state: EditorUiState,
): Set<RoomId> {
    fun hasShape(roomId: RoomId): Boolean = state.evaluations[roomId]?.shape != null

    val moving = linkedSetOf(id)
    do {
        val grown =
            rooms.filter { room ->
                val host = room.placement.dock?.hostRoomId
                room.id !in moving && host != null && host in moving && hasShape(host) && hasShape(room.id)
            }
        moving += grown.map { it.id }
    } while (grown.isNotEmpty())
    return moving
}

private fun localCorners(evaluation: RoomEvaluation?): List<Vec2> =
    evaluation?.shape?.outline?.vertices ?: (evaluation as? RoomEvaluation.Open)?.chain.orEmpty()

/** The corners of [room] in world coordinates, empty if its evaluation has none. */
internal fun worldCorners(
    room: Room,
    state: EditorUiState,
): List<Vec2> = localCorners(state.evaluations[room.id]).map { room.placement.toWorld(it) }

/** The center a twist turns around: of the drawn room, else of its corners, else its origin. */
internal fun pivotOf(
    room: Room,
    state: EditorUiState,
    corners: List<Vec2>,
): Vec2 {
    val bounds =
        state.scene
            ?.rooms
            ?.firstOrNull { it.roomId == room.id }
            ?.bounds
    return when {
        bounds != null -> Vec2((bounds.minX + bounds.maxX) / 2.0, (bounds.minY + bounds.maxY) / 2.0)
        corners.isNotEmpty() -> Vec2(corners.sumOf { it.x } / corners.size, corners.sumOf { it.y } / corners.size)
        else -> Vec2(room.placement.originX, room.placement.originY)
    }
}

/** The corners and wall lines of [rooms] as snap candidates. */
internal fun snapTargets(
    rooms: List<Room>,
    state: EditorUiState,
): SnapTargets {
    val corners = mutableListOf<Vec2>()
    val walls = mutableListOf<SnapSegment>()
    for (room in rooms) {
        val world = worldCorners(room, state)
        corners += world
        val closed = state.evaluations[room.id]?.shape != null
        val last = if (closed) world.size else world.size - 1
        for (i in 0 until last) walls += SnapSegment(world[i], world[(i + 1) % world.size])
    }
    return SnapTargets(corners.toImmutableList(), walls.toImmutableList())
}
