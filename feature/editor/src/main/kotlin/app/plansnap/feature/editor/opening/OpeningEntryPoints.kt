package app.plansnap.feature.editor.opening

import androidx.annotation.StringRes
import app.plansnap.core.drawing.scene.SceneTarget
import app.plansnap.core.model.OpeningType
import app.plansnap.feature.editor.R
import app.plansnap.feature.editor.canvas.PlanTap
import app.plansnap.feature.editor.state.EditorIntent
import app.plansnap.feature.editor.state.EditorUiState

/**
 * The opening intent for a tap on the plan, `null` if the tap is not about openings. The canvas already applied the
 * hit priority of DOMAIN section 11 (opening before corner before wall), so this only translates the target:
 *
 * - an opening opens its form,
 * - a wall opens the list of its openings, from which one is added or changed,
 * - anything else, and a target that no longer exists in the project, is not handled here.
 *
 * The result never replaces the selection: the caller still sends `CoreIntent.SelectRoom` for the tapped room and
 * then this intent, so the room that receives the input is the room that was tapped.
 */
fun openingTapIntent(
    tap: PlanTap,
    state: EditorUiState,
): EditorIntent? {
    val target = tap.target
    val room = target?.let { state.project?.rooms?.firstOrNull { room -> room.id == it.roomId } }
    return when {
        room == null -> {
            null
        }

        target is SceneTarget.Opening -> {
            target.takeIf { room.openings.any { it.id == target.openingId } }?.let {
                OpeningIntent.Edit(room.id, it.openingId)
            }
        }

        target is SceneTarget.Wall -> {
            target.takeIf { room.walls.any { it.id == target.wallId } }?.let {
                OpeningIntent.OpenWall(room.id, it.wallId)
            }
        }

        else -> {
            null
        }
    }
}

/**
 * An entry of the overflow menu of the top bar.
 *
 * @property label string resource of the visible text, also its spoken label.
 * @property intent what the entry sends.
 * @property enabled whether the entry can be used now.
 */
data class OpeningMenuEntry(
    @StringRes val label: Int,
    val intent: EditorIntent,
    val enabled: Boolean,
)

/**
 * Menu entries that add an opening to the selected room: door, window and passage. The opening starts on the last
 * wall of the room, the form moves it to another wall. The entries are disabled while the selected room has no wall.
 */
fun openingMenuEntries(state: EditorUiState): List<OpeningMenuEntry> {
    val enabled = state.selectedRoom?.walls?.isNotEmpty() == true
    return OpeningType.entries.map { type ->
        OpeningMenuEntry(addLabel(type), OpeningIntent.AddToSelectedRoom(type), enabled)
    }
}

@StringRes
internal fun addLabel(type: OpeningType): Int =
    when (type) {
        OpeningType.DOOR -> R.string.opening_add_door
        OpeningType.WINDOW -> R.string.opening_add_window
        OpeningType.PASSAGE -> R.string.opening_add_passage
    }
