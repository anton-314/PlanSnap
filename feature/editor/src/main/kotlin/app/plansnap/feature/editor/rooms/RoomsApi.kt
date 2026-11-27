package app.plansnap.feature.editor.rooms

import androidx.annotation.StringRes
import app.plansnap.core.drawing.scene.SceneTarget
import app.plansnap.core.model.OpeningId
import app.plansnap.core.model.RoomId
import app.plansnap.core.model.WallId
import app.plansnap.feature.editor.R
import app.plansnap.feature.editor.canvas.PlanTap
import app.plansnap.feature.editor.canvas.RoomGestureCallbacks
import app.plansnap.feature.editor.canvas.RoomTransformPreview
import app.plansnap.feature.editor.state.EditorIntent
import app.plansnap.feature.editor.state.EditorUiState

// What the screen needs of the rooms function besides the sheet: the callbacks and the preview for the canvas, the
// meaning of a tap, and the entries of the menu.

/**
 * The long-press callbacks for `PlanCanvas.roomGestures`. Remember the result, it does not change with the state:
 * `val gestures = remember(onIntent) { roomGestureCallbacks(onIntent) }`.
 */
fun roomGestureCallbacks(onIntent: (EditorIntent) -> Unit): RoomGestureCallbacks =
    RoomGestureCallbacks(
        onStart = { onIntent(RoomsIntent.GestureStarted(it)) },
        onUpdate = { onIntent(RoomsIntent.GestureMoved(it)) },
        onEnd = { onIntent(RoomsIntent.GestureEnded) },
        onCancel = { onIntent(RoomsIntent.GestureCancelled) },
    )

/**
 * The preview for `PlanCanvas.roomPreview`: the rooms that are being dragged with the transform of the gesture,
 * `null` if no gesture runs. It is derived from the state, so it exists exactly as long as the gesture.
 */
fun roomsCanvasPreview(state: EditorUiState): RoomTransformPreview? =
    state.section(RoomsState::class)?.drag?.let {
        RoomTransformPreview(it.movingIds, it.translation, it.rotationRad, it.pivot)
    }

/**
 * Whether the plan shows the grid, from the settings. `PlanCanvas.showGrid = roomsShowGrid(state)`.
 */
fun roomsShowGrid(state: EditorUiState): Boolean = state.settings.gridEnabled

/**
 * The meaning of a tap for the rooms function, or `null` if the tap is not its concern and the screen handles it as
 * usual (selecting a room).
 *
 * Only while the host of a dock is being chosen on the plan, taps belong here: a wall or an opening of another room
 * becomes the host, every other tap changes nothing, so the user keeps their place.
 */
fun roomsTapIntent(
    tap: PlanTap,
    state: EditorUiState,
): EditorIntent? {
    val dock = state.section(RoomsState::class)?.dock?.takeIf { it.picking } ?: return null
    return when (val target = tap.target) {
        is SceneTarget.Wall -> {
            hostIntent(dock, target.roomId, target.wallId, null)
        }

        is SceneTarget.Opening -> {
            val opening =
                state.project
                    ?.rooms
                    ?.firstOrNull { it.id == target.roomId }
                    ?.openings
                    ?.firstOrNull { it.id == target.openingId }
            opening?.let { hostIntent(dock, target.roomId, it.wallId, it.id) }
        }

        else -> {
            null
        }
    } ?: RoomsIntent.PickIgnored
}

private fun hostIntent(
    dock: DockDraft,
    roomId: RoomId,
    wallId: WallId,
    openingId: OpeningId?,
): EditorIntent? = if (roomId == dock.guestId) null else RoomsIntent.SelectHost(roomId, wallId, openingId)

/**
 * An entry of the top-bar menu as data: the screen shows [label], greys it out if not [enabled], draws a check mark
 * if [checked] is set, and sends [intent] on tap.
 */
data class RoomsMenuItem(
    @get:StringRes val label: Int,
    val enabled: Boolean,
    val checked: Boolean?,
    val intent: RoomsIntent,
)

/** The menu entries of the rooms function: add a room, dock the selected room, grid and snapping. */
fun roomsMenuItems(state: EditorUiState): List<RoomsMenuItem> =
    listOf(
        RoomsMenuItem(R.string.rooms_menu_add, state.project != null, null, RoomsIntent.AddRoom),
        RoomsMenuItem(R.string.rooms_menu_dock, DockDrafts.open(state) != null, null, RoomsIntent.OpenDock),
        RoomsMenuItem(
            R.string.rooms_menu_grid,
            true,
            state.settings.gridEnabled,
            RoomsIntent.SetGrid(!state.settings.gridEnabled),
        ),
        RoomsMenuItem(
            R.string.rooms_menu_snap,
            true,
            state.settings.snapEnabled,
            RoomsIntent.SetSnap(!state.settings.snapEnabled),
        ),
    )
