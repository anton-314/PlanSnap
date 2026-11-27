package app.plansnap.feature.editor.a11y

import android.content.res.Resources
import androidx.compose.runtime.Immutable
import app.plansnap.core.common.units.DecimalAngleFormatter
import app.plansnap.core.common.units.LengthUnit
import app.plansnap.core.domain.evaluation.RoomEvaluation
import app.plansnap.core.model.CornerSpec
import app.plansnap.core.model.Diagonal
import app.plansnap.core.model.Opening
import app.plansnap.core.model.OpeningStatus
import app.plansnap.core.model.Room
import app.plansnap.core.model.RoomId
import app.plansnap.core.model.ShapeStatus
import app.plansnap.feature.editor.R
import app.plansnap.feature.editor.details.RoomTypeCatalog
import app.plansnap.feature.editor.opening.OpeningIntent
import app.plansnap.feature.editor.opening.lengthLabel
import app.plansnap.feature.editor.opening.typeRes
import app.plansnap.feature.editor.shape.areaText
import app.plansnap.feature.editor.shape.conflictText
import app.plansnap.feature.editor.shape.errorMessage
import app.plansnap.feature.editor.shape.lengthText
import app.plansnap.feature.editor.shape.recommendationText
import app.plansnap.feature.editor.state.CoreIntent
import app.plansnap.feature.editor.state.EditorIntent
import app.plansnap.feature.editor.state.EditorUiState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/** What an entry of the plan outline stands for, in the order a room lists them. */
internal enum class OutlineKind { ROOM, WALL, OPENING, DIAGONAL }

/**
 * One entry of the plan outline, the text alternative to the plan (UX section 7).
 *
 * @property key unique and stable across states.
 * @property kind element the entry describes.
 * @property lines texts of the entry, the first one names it. Every value the plan shows is in words here.
 * @property status status of a room, `null` for other entries and while a room has no evaluation.
 * @property selected whether the entry is the room that receives the input.
 * @property intents what activating the entry does, the same as a tap on that element of the plan. Empty for an entry
 * the plan does not let the user tap.
 */
@Immutable
internal data class OutlineEntry(
    val key: String,
    val kind: OutlineKind,
    val lines: ImmutableList<String>,
    val status: ShapeStatus? = null,
    val selected: Boolean = false,
    val intents: ImmutableList<EditorIntent> = persistentListOf(),
)

/**
 * Builds the outline of the confirmed project: every room with its status and area, followed by its walls, openings
 * and diagonals with their dimensions. It only puts into words what the evaluation and the model already hold; wall
 * lengths are those of the displayed shape, as the dimensions of the plan are, and the entered length is added where
 * it differs.
 *
 * @param state state of the editor.
 * @param resources texts of the language the user sees.
 */
internal fun planOutline(
    state: EditorUiState,
    resources: Resources,
): ImmutableList<OutlineEntry> {
    val builder = OutlineBuilder(state, resources)
    return state.project
        ?.rooms
        .orEmpty()
        .flatMapIndexed { index, room -> builder.room(room, index) }
        .toImmutableList()
}

private class OutlineBuilder(
    private val state: EditorUiState,
    private val resources: Resources,
) {
    private val unit: LengthUnit = state.settings.lengthUnit

    fun room(
        room: Room,
        index: Int,
    ): List<OutlineEntry> {
        val evaluation = state.evaluations[room.id]
        val name = room.name.ifBlank { resources.getString(R.string.details_room_fallback_name, index + 1) }
        return buildList {
            add(summary(room, name, evaluation))
            room.walls.indices.forEach { add(wall(room, name, it, evaluation)) }
            room.openings
                .sortedWith(compareBy<Opening> { opening -> wallIndex(room, opening) }.thenBy { it.offset })
                .forEach { add(opening(room, name, it, evaluation)) }
            room.diagonals.forEach { add(diagonal(room.id, name, it, evaluation)) }
        }
    }

    private fun summary(
        room: Room,
        name: String,
        evaluation: RoomEvaluation?,
    ): OutlineEntry {
        val type = room.typeKey?.let { RoomTypeCatalog.displayName(resources, it) }
        val title = if (type == null) name else resources.getString(R.string.details_row_title, name, type)
        val walls = room.walls.size
        val lines =
            buildList {
                add(title)
                add(statusText(room.id, evaluation))
                evaluation?.shape?.let {
                    add(resources.getString(R.string.details_row_area, areaText(it.areaMm2)))
                }
                evaluation?.let { explanation(room, it) }?.let(::addAll)
                add(
                    if (walls == 0) {
                        resources.getString(R.string.a11y_outline_no_walls)
                    } else {
                        resources.getQuantityString(R.plurals.a11y_outline_wall_count, walls, walls)
                    },
                )
            }
        return OutlineEntry(
            key = "room/${room.id.value}",
            kind = OutlineKind.ROOM,
            lines = lines.toImmutableList(),
            status = evaluation?.status,
            selected = room.id == state.selectedRoomId,
            intents = persistentListOf(CoreIntent.SelectRoom(room.id)),
        )
    }

    /** The status of the room in the words of the status card, or why there is none. */
    private fun statusText(
        roomId: RoomId,
        evaluation: RoomEvaluation?,
    ): String {
        val error = state.evaluationErrors[roomId]
        return when {
            error != null -> {
                resources.getString(R.string.shape_status_failed, resources.errorMessage(error))
            }

            evaluation == null -> {
                resources.getString(R.string.a11y_outline_pending)
            }

            else -> {
                resources.getString(statusRes(evaluation.status))
            }
        }
    }

    private fun explanation(
        room: Room,
        evaluation: RoomEvaluation,
    ): List<String> =
        when (evaluation) {
            is RoomEvaluation.NeedsAdjustment -> {
                val largest = resources.lengthText(evaluation.proposal.maxCorrectionMm, unit)
                listOf(resources.getString(R.string.shape_status_needs_adjustment, largest))
            }

            is RoomEvaluation.Proposed -> {
                listOf(resources.recommendationText(evaluation.recommendation))
            }

            is RoomEvaluation.Conflict -> {
                listOf(resources.conflictText(evaluation, room))
            }

            is RoomEvaluation.Open, is RoomEvaluation.Confirmed, is RoomEvaluation.Adjusted -> {
                emptyList()
            }
        }

    private fun wall(
        room: Room,
        roomName: String,
        index: Int,
        evaluation: RoomEvaluation?,
    ): OutlineEntry {
        val wall = room.walls[index]
        val entered = resources.lengthText(wall.length.mm.toDouble(), unit)
        val shown = evaluation?.shape?.let { resources.lengthText(it.lengthsMm[index], unit) } ?: entered
        val corner = room.corners[index]
        val lines =
            buildList {
                add(resources.getString(R.string.a11y_outline_wall, index + 1, roomName))
                add(resources.getString(R.string.a11y_outline_wall_length, shown))
                if (shown != entered) add(resources.getString(R.string.a11y_outline_wall_entered, entered))
                add(
                    resources.getString(
                        R.string.a11y_outline_wall_thickness,
                        resources.lengthText(wall.thickness.mm.toDouble(), unit),
                    ),
                )
                if (corner is CornerSpec.Measured) {
                    val degrees = DecimalAngleFormatter.format(corner.angle, resources.configuration.locales[0])
                    add(resources.getString(R.string.a11y_outline_corner_angle, index + 1, degrees))
                }
                evaluation?.let { add(resources.getString(statusRes(it.status))) }
            }
        return OutlineEntry(
            key = "wall/${room.id.value}/${wall.id.value}",
            kind = OutlineKind.WALL,
            lines = lines.toImmutableList(),
            intents = persistentListOf(CoreIntent.SelectRoom(room.id), OpeningIntent.OpenWall(room.id, wall.id)),
        )
    }

    private fun opening(
        room: Room,
        roomName: String,
        opening: Opening,
        evaluation: RoomEvaluation?,
    ): OutlineEntry {
        val type = resources.getString(typeRes(opening.type))
        val wallNumber = wallIndex(room, opening) + 1
        val lines =
            buildList {
                add(resources.getString(R.string.a11y_outline_opening, type, wallNumber, roomName))
                add(
                    resources.getString(
                        R.string.opening_row_summary,
                        type,
                        resources.lengthLabel(opening.width, unit),
                        resources.lengthLabel(opening.offset, unit),
                    ),
                )
                evaluation?.openingStatuses?.get(opening.id)?.let { add(resources.getString(fitRes(it))) }
            }
        return OutlineEntry(
            key = "opening/${room.id.value}/${opening.id.value}",
            kind = OutlineKind.OPENING,
            lines = lines.toImmutableList(),
            intents = persistentListOf(CoreIntent.SelectRoom(room.id), OpeningIntent.Edit(room.id, opening.id)),
        )
    }

    private fun diagonal(
        roomId: RoomId,
        roomName: String,
        diagonal: Diagonal,
        evaluation: RoomEvaluation?,
    ): OutlineEntry {
        val conflicting = (evaluation as? RoomEvaluation.Conflict)?.diagonalId == diagonal.id
        val lines =
            buildList {
                add(
                    resources.getString(
                        R.string.a11y_outline_diagonal,
                        diagonal.fromCorner + 1,
                        diagonal.toCorner + 1,
                        roomName,
                    ),
                )
                add(resources.lengthText(diagonal.length.mm.toDouble(), unit))
                if (conflicting) add(resources.getString(R.string.shape_status_conflict))
            }
        return OutlineEntry(
            key = "diagonal/${roomId.value}/${diagonal.id.value}",
            kind = OutlineKind.DIAGONAL,
            lines = lines.toImmutableList(),
        )
    }

    private fun wallIndex(
        room: Room,
        opening: Opening,
    ): Int = room.walls.indexOfFirst { it.id == opening.wallId }

    private fun statusRes(status: ShapeStatus): Int =
        when (status) {
            ShapeStatus.OPEN -> R.string.a11y_outline_open
            ShapeStatus.CONFIRMED -> R.string.shape_status_confirmed
            ShapeStatus.ADJUSTED -> R.string.shape_status_adjusted
            ShapeStatus.NEEDS_ADJUSTMENT, ShapeStatus.PROPOSED -> R.string.shape_status_unconfirmed
            ShapeStatus.CONFLICT -> R.string.shape_status_conflict
        }

    private fun fitRes(status: OpeningStatus): Int =
        when (status) {
            OpeningStatus.VALID -> R.string.opening_status_valid
            OpeningStatus.OUT_OF_BOUNDS -> R.string.opening_status_out_of_bounds
        }
}
