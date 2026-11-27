package app.plansnap.feature.editor.rooms

import app.plansnap.core.domain.command.ProjectEdit
import app.plansnap.core.domain.snap.DefaultSnapper
import app.plansnap.core.domain.snap.SnapKind
import app.plansnap.core.domain.snap.SnapQuery
import app.plansnap.core.domain.snap.Snapper
import app.plansnap.core.geometry.primitives.Vec2
import app.plansnap.core.model.Room
import app.plansnap.feature.editor.canvas.RoomGestureStart
import app.plansnap.feature.editor.canvas.RoomGestureUpdate
import app.plansnap.feature.editor.state.EditorUiState
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlin.math.PI
import kotlin.math.abs

/**
 * The arithmetic of the long-press gesture: preview with snapping while the finger moves, one `MoveRoom` at the end.
 * It never touches the model and keeps every geometric decision out of the canvas. Snapping goes through the
 * [Snapper] of the domain, targets are prepared once per gesture, and the per-event work is a loop over the corners of
 * the dragged room.
 */
internal class RoomDragEngine(
    private val snapper: Snapper = DefaultSnapper(),
) {
    /**
     * Starts a drag of [start].roomId in [state], or returns `null` if the project or the room is missing.
     *
     * The docked rooms that the domain places anew when the dragged room moves follow it in the preview. All other
     * rooms offer their corners and wall lines as snap targets.
     */
    fun begin(
        state: EditorUiState,
        start: RoomGestureStart,
    ): RoomDrag? {
        val rooms = state.project?.rooms
        val room = rooms?.firstOrNull { it.id == start.roomId }
        return if (rooms == null || room == null) {
            null
        } else {
            val moving = movingRooms(rooms, room.id, state)
            val corners = worldCorners(room, state)
            RoomDrag(
                roomId = room.id,
                movingIds = moving.toImmutableSet(),
                startPlacement = room.placement,
                pivot = pivotOf(room, state, corners),
                corners = corners.toImmutableList(),
                targets = snapTargets(rooms.filter { it.id !in moving }, state),
                snapDistanceMm = start.snapDistanceMm,
                gridStepMm = start.gridStepMm,
            )
        }
    }

    /**
     * The preview of [drag] after [update]. Snapping reads [snapEnabled] and [gridEnabled] at this moment, so a switch
     * of the setting takes effect with the next event. It only shifts the room, the twist stays as the fingers made
     * it.
     */
    fun update(
        drag: RoomDrag,
        update: RoomGestureUpdate,
        snapEnabled: Boolean,
        gridEnabled: Boolean,
    ): RoomDrag {
        val snapped =
            if (snapEnabled) {
                bestSnap(drag, update, if (gridEnabled) drag.gridStepMm else null)
            } else {
                null
            }
        val translation = snapped?.let { update.translationMm + it.shift } ?: update.translationMm
        return drag.withResult(translation, update.rotationRad, snapped?.kind)
    }

    /**
     * The one command that places the room where the preview shows it, `null` if the preview did not move at all, so
     * a touch without movement leaves no undo step and keeps the dock link.
     */
    fun finish(drag: RoomDrag): ProjectEdit.MoveRoom? {
        val theta = drag.rotationRad
        val moved = drag.translation.length() > MOVED_MM || abs(theta) > MOVED_RAD
        return if (moved) {
            val placement = drag.startPlacement
            val origin = transformed(Vec2(placement.originX, placement.originY), drag.pivot, theta, drag.translation)
            ProjectEdit.MoveRoom(
                roomId = drag.roomId,
                originX = origin.x,
                originY = origin.y,
                rotationRad = normalized(placement.rotationRad + theta),
            )
        } else {
            null
        }
    }

    /**
     * Asks the [Snapper] for every sampled corner of the dragged room and keeps the best answer: the higher priority
     * first, then the shorter way.
     */
    private fun bestSnap(
        drag: RoomDrag,
        update: RoomGestureUpdate,
        gridStepMm: Double?,
    ): Snap? {
        val stride = maxOf((drag.corners.size + MAX_SNAP_CORNERS - 1) / MAX_SNAP_CORNERS, 1)
        var best: Snap? = null
        for (i in drag.corners.indices step stride) {
            val point = transformed(drag.corners[i], drag.pivot, update.rotationRad, update.translationMm)
            val query = SnapQuery(point, drag.snapDistanceMm, gridStepMm, enabled = true)
            val result = snapper.snap(query, drag.targets)
            val kind = result.kind ?: continue
            val found = Snap(result.point - point, kind)
            val current = best
            if (current == null || found.isBetterThan(current)) best = found
        }
        return best
    }

    /** The rigid transform of the preview: a rotation around [pivot], then the [translation]. */
    private fun transformed(
        point: Vec2,
        pivot: Vec2,
        rotationRad: Double,
        translation: Vec2,
    ): Vec2 = (point - pivot).rotated(rotationRad) + pivot + translation

    private fun normalized(radians: Double): Double {
        var result = radians
        while (result > PI) result -= 2.0 * PI
        while (result <= -PI) result += 2.0 * PI
        return result
    }

    /** One snap answer: the [shift] that takes a corner to its target. */
    private class Snap(
        val shift: Vec2,
        val kind: SnapKind,
    ) {
        fun isBetterThan(other: Snap): Boolean =
            kind < other.kind || (kind == other.kind && shift.length() < other.shift.length())
    }

    /** Constants of the gesture. */
    companion object {
        /** At most this many corners of the dragged room are snapped per event. */
        const val MAX_SNAP_CORNERS: Int = 32

        private const val MOVED_MM = 0.5
        private const val MOVED_RAD = 1e-6
    }
}
