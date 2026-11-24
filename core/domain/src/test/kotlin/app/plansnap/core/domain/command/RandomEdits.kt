package app.plansnap.core.domain.command

import app.plansnap.core.common.units.Length
import app.plansnap.core.domain.evaluation.AdjustmentConfirmation
import app.plansnap.core.domain.evaluation.AdjustmentProposal
import app.plansnap.core.geometry.adjustment.ObservationRef
import app.plansnap.core.model.AreaFactor
import app.plansnap.core.model.ClosureAdjustment
import app.plansnap.core.model.Diagonal
import app.plansnap.core.model.DiagonalId
import app.plansnap.core.model.DockLink
import app.plansnap.core.model.OpeningId
import app.plansnap.core.model.Project
import app.plansnap.core.model.Room
import app.plansnap.core.model.Wall
import app.plansnap.core.model.WallId
import app.plansnap.testing.fixtures.random.SeededRandom
import kotlinx.collections.immutable.persistentListOf

/**
 * Seeded generator of edits for the current project. Most edits are valid, a part deliberately violates a rule or
 * refers to a missing entity, so that sequences contain successes and failures.
 */
class RandomEdits(
    private val random: SeededRandom,
) {
    private var counter = 0

    private fun fresh(prefix: String): String = "$prefix-${counter++}"

    private fun <T> pick(items: List<T>): T = items[random.nextInt(items.size)]

    /** Returns the next edit for [project]. */
    fun next(project: Project): ProjectEdit {
        val room = project.rooms.takeIf { it.isNotEmpty() }?.let { pick(it) }
        return if (room == null) addRoom() else roomEdit(project, room)
    }

    private fun addRoom(): ProjectEdit = ProjectEdit.AddRoom(openRoom(fresh("room"), random.nextInt(0..4)))

    private fun roomEdit(
        project: Project,
        room: Room,
    ): ProjectEdit =
        when (random.nextInt(KINDS)) {
            0 -> wallEdit(room)
            1 -> structureEdit(room)
            2 -> openingEdit(room)
            3 -> roomLevelEdit(project, room)
            else -> adjustmentEdit(room)
        }

    private fun wallEdit(room: Room): ProjectEdit {
        val wallId = room.walls.takeIf { it.isNotEmpty() }?.let { pick(it).id } ?: WallId("none")
        return when (random.nextInt(WALL_KINDS)) {
            0 -> {
                ProjectEdit.AddWall(
                    room.id,
                    Wall(WallId(fresh("w")), Length(random.nextInt(0..5000).toLong()), Length(120)),
                    measuredRight(),
                )
            }

            1 -> {
                ProjectEdit.EditWall(room.id, wallId, Length(random.nextInt(0..5000).toLong()))
            }

            2 -> {
                ProjectEdit.RemoveLastWall(room.id)
            }

            else -> {
                ProjectEdit.SetThickness(room.id, wallId, Length(random.nextInt(-10..1100).toLong()))
            }
        }
    }

    private fun structureEdit(room: Room): ProjectEdit {
        val corners = room.walls.size + 1
        return when (random.nextInt(STRUCTURE_KINDS)) {
            0 -> {
                ProjectEdit.SetCorner(room.id, random.nextInt(0..corners), measuredRight())
            }

            1 -> {
                ProjectEdit.AddDiagonal(room.id, diagonal(corners))
            }

            2 -> {
                room.diagonals.takeIf { it.isNotEmpty() }?.let { ProjectEdit.RemoveDiagonal(room.id, pick(it).id) }
                    ?: ProjectEdit.RemoveDiagonal(room.id, DiagonalId("none"))
            }

            else -> {
                ProjectEdit.CloseRoom(room.id)
            }
        }
    }

    private fun diagonal(corners: Int): Diagonal {
        val from = random.nextInt(0..corners)
        val to = (from + 1 + random.nextInt(0..corners)) % (corners + 1)
        return Diagonal(
            DiagonalId(fresh("d")),
            from,
            if (to ==
                from
            ) {
                from + 1
            } else {
                to
            },
            Length(random.nextInt(1..6000).toLong()),
        )
    }

    private fun openingEdit(room: Room): ProjectEdit {
        val wall = room.walls.takeIf { it.isNotEmpty() }?.let { pick(it).id } ?: WallId("none")
        val opening =
            door(
                fresh("op"),
                wall,
                offset = random.nextInt(0..3000).toLong(),
                width =
                    random.nextInt(100..1500).toLong(),
            )
        val existing = room.openings.takeIf { it.isNotEmpty() }?.let { pick(it) }
        return when (random.nextInt(OPENING_KINDS)) {
            0 -> ProjectEdit.AddOpening(room.id, opening)
            1 -> ProjectEdit.EditOpening(room.id, (existing ?: opening).copy(offset = opening.offset, wallId = wall))
            else -> ProjectEdit.RemoveOpening(room.id, existing?.id ?: OpeningId("none"))
        }
    }

    private fun roomLevelEdit(
        project: Project,
        room: Room,
    ): ProjectEdit =
        when (random.nextInt(ROOM_KINDS)) {
            0 -> {
                addRoom()
            }

            1 -> {
                ProjectEdit.RemoveRoom(room.id)
            }

            2 -> {
                ProjectEdit.MoveRoom(
                    room.id,
                    random.nextInt(-5000..5000).toDouble(),
                    random.nextInt(-5000..5000).toDouble(),
                    0.5,
                )
            }

            3 -> {
                dock(project, room)
            }

            4 -> {
                ProjectEdit.UpdateRoomDetails(
                    room.id,
                    RoomDetails(
                        "n${random.nextInt(0..9)}",
                        null,
                        "",
                        Length(random.nextInt(-1..3000).toLong()).takeIf { random.nextBoolean() },
                        AreaFactor(
                            random.nextInt(0..100),
                        ),
                    ),
                )
            }

            else -> {
                ProjectEdit.SetProjectHeight(Length(random.nextInt(-100..3000).toLong()))
            }
        }

    private fun dock(
        project: Project,
        room: Room,
    ): ProjectEdit {
        val host = pick(project.rooms)
        val hostWall = host.walls.takeIf { it.isNotEmpty() }?.let { pick(it).id } ?: WallId("none")
        val ownWall = room.walls.takeIf { it.isNotEmpty() }?.let { pick(it).id } ?: WallId("none")
        val opening = host.openings.takeIf { it.isNotEmpty() && random.nextBoolean() }?.let { pick(it).id }
        return ProjectEdit.DockRoom(
            room.id,
            DockLink(host.id, hostWall, ownWall, Length(random.nextInt(-500..500).toLong()), opening),
        )
    }

    private fun adjustmentEdit(room: Room): ProjectEdit =
        if (random.nextBoolean()) {
            ProjectEdit.RevertAdjustment(room.id)
        } else {
            val adjustment = ClosureAdjustment(0L, 1, persistentListOf(1.0), persistentListOf(0.0))
            val proposal =
                AdjustmentProposal(adjustment, 1.0, 1.0, ObservationRef.WallLength(0), AdjustmentConfirmation.SIMPLE)
            ProjectEdit.AcceptAdjustment(room.id, proposal, AdjustmentConfirmation.EXPLICIT)
        }

    private companion object {
        const val KINDS = 5
        const val WALL_KINDS = 4
        const val STRUCTURE_KINDS = 4
        const val OPENING_KINDS = 3
        const val ROOM_KINDS = 6
    }
}
