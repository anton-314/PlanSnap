package app.plansnap.feature.editor.history

import app.plansnap.core.common.units.Length
import app.plansnap.core.domain.command.ProjectEdit
import app.plansnap.core.model.DoorSwing
import app.plansnap.core.model.Opening
import app.plansnap.core.model.OpeningId
import app.plansnap.core.model.OpeningType
import app.plansnap.core.model.Project
import app.plansnap.core.model.Room
import app.plansnap.core.model.RoomId
import app.plansnap.core.model.Wall
import app.plansnap.core.model.WallId
import app.plansnap.feature.editor.state.EditorUiState
import app.plansnap.feature.editor.state.WallInput
import kotlin.random.Random

private const val EDIT_KINDS = 10
private const val NEW_ROOM_KIND = 0
private const val HEIGHT_KIND = 1

/** A door on the wall [wallId]. */
internal fun door(wallId: WallId): Opening =
    Opening(
        id = OpeningId("o1"),
        wallId = wallId,
        type = OpeningType.DOOR,
        offset = Length(500),
        width = Length(900),
        height = Length(2000),
        sillHeight = null,
        swing = DoorSwing.HINGE_LEFT_INWARD,
    )

/**
 * A random edit of [project]. Some edits are impossible on purpose, for example a wall for a closed room, so that
 * rejected commands are part of the sequence. Every new object is named [newId].
 */
internal fun randomEdit(
    random: Random,
    project: Project,
    newId: String,
): ProjectEdit {
    val kind = random.nextInt(EDIT_KINDS)
    val room = project.rooms.randomOrNull(random)
    return when {
        kind == HEIGHT_KIND -> ProjectEdit.SetProjectHeight(Length(random.nextLong(2200, 3200)))
        room == null || kind == NEW_ROOM_KIND -> ProjectEdit.AddRoom(Room(RoomId(newId)))
        else -> roomEdit(random, room, kind, newId)
    }
}

private fun roomEdit(
    random: Random,
    room: Room,
    kind: Int,
    newId: String,
): ProjectEdit {
    val wall = room.walls.randomOrNull(random)
    val length = Length(random.nextLong(500, 6000))
    return when {
        kind == 2 -> {
            ProjectEdit.RemoveLastWall(room.id)
        }

        kind == 3 -> {
            ProjectEdit.CloseRoom(room.id)
        }

        kind == 4 -> {
            ProjectEdit.RemoveRoom(room.id)
        }

        kind == 5 -> {
            ProjectEdit.MoveRoom(room.id, length.mm.toDouble(), -length.mm.toDouble(), 0.0)
        }

        kind == 6 && wall != null -> {
            ProjectEdit.EditWall(room.id, wall.id, length)
        }

        kind == 7 && wall != null -> {
            ProjectEdit.SetThickness(room.id, wall.id, Length(random.nextLong(0, 400)))
        }

        else -> {
            ProjectEdit.AddWall(
                room.id,
                Wall(WallId(newId), length, Length(120)),
                WallInput.startCorner(room, EditorUiState.DEFAULT_NEXT_CORNER),
            )
        }
    }
}
