package app.plansnap.core.domain.command

import app.plansnap.core.common.result.DomainResult
import app.plansnap.core.common.result.DomainRule
import app.plansnap.core.common.result.EntityKind
import app.plansnap.core.geometry.primitives.GeometryTolerances
import app.plansnap.core.model.CornerSpec
import app.plansnap.core.model.Project
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentList

internal fun addWall(
    project: Project,
    edit: ProjectEdit.AddWall,
): DomainResult<Project> =
    project.mapRoom(edit.roomId) { room ->
        val error =
            when {
                room.isClosed -> {
                    rule(DomainRule.ROOM_ALREADY_CLOSED)
                }

                room.walls.size >= GeometryTolerances.MAX_WALLS_PER_ROOM -> {
                    rule(DomainRule.TOO_MANY_WALLS)
                }

                room.walls.any { it.id == edit.wall.id } -> {
                    rule(DomainRule.DUPLICATE_ID)
                }

                room.walls.isNotEmpty() && edit.startCorner is CornerSpec.Unspecified -> {
                    rule(DomainRule.CORNER_MUST_BE_SPECIFIED)
                }

                else -> {
                    wallLengthError(edit.wall.length) ?: thicknessError(edit.wall.thickness)
                }
            }
        error.otherwise {
            room.copy(
                walls = room.walls.toPersistentList().adding(edit.wall),
                corners = room.corners.toPersistentList().adding(edit.startCorner),
            )
        }
    }

internal fun editWall(
    project: Project,
    edit: ProjectEdit.EditWall,
): DomainResult<Project> =
    project.mapRoom(edit.roomId) { room ->
        val index = room.walls.indexOfFirst { it.id == edit.wallId }
        val error =
            if (index < 0) notFound(EntityKind.WALL, edit.wallId.value) else wallLengthError(edit.length)
        error.otherwise {
            room.copy(
                walls = room.walls.toPersistentList().replacingAt(index, room.walls[index].copy(length = edit.length)),
            )
        }
    }

internal fun removeLastWall(
    project: Project,
    edit: ProjectEdit.RemoveLastWall,
): DomainResult<Project> =
    project.mapRoom(edit.roomId) { room ->
        val last = room.walls.lastOrNull()
        val lastCorner = room.walls.size - 1
        val error =
            when {
                last == null -> {
                    rule(DomainRule.NO_WALL_TO_REMOVE)
                }

                room.diagonals.any { it.fromCorner == lastCorner || it.toCorner == lastCorner } -> {
                    rule(DomainRule.CORNER_HAS_DIAGONALS)
                }

                room.openings.any { it.wallId == last.id } -> {
                    rule(DomainRule.WALL_HAS_OPENINGS)
                }

                project.hasDockOnWall(room, last.id) -> {
                    rule(DomainRule.WALL_HAS_DOCKED_ROOMS)
                }

                else -> {
                    null
                }
            }
        error.otherwise {
            room.copy(
                walls = room.walls.dropLast(1).toImmutableList(),
                corners = room.corners.dropLast(1).toImmutableList(),
                isClosed = false,
                adjustment = null,
            )
        }
    }

internal fun setCorner(
    project: Project,
    edit: ProjectEdit.SetCorner,
): DomainResult<Project> =
    project.mapRoom(edit.roomId) { room ->
        val error =
            when {
                edit.cornerIndex !in room.corners.indices -> {
                    notFound(EntityKind.CORNER, edit.cornerIndex.toString())
                }

                edit.cornerIndex > 0 && edit.corner is CornerSpec.Unspecified -> {
                    rule(DomainRule.CORNER_MUST_BE_SPECIFIED)
                }

                else -> {
                    null
                }
            }
        error.otherwise {
            room.copy(corners = room.corners.toPersistentList().replacingAt(edit.cornerIndex, edit.corner))
        }
    }

internal fun addDiagonal(
    project: Project,
    edit: ProjectEdit.AddDiagonal,
): DomainResult<Project> =
    project.mapRoom(edit.roomId) { room ->
        val diagonal = edit.diagonal
        val error =
            when {
                diagonal.fromCorner >= room.walls.size || diagonal.toCorner >= room.walls.size -> {
                    rule(DomainRule.DIAGONAL_INVALID_CORNERS)
                }

                room.diagonals.any { it.id == diagonal.id } -> {
                    rule(DomainRule.DUPLICATE_ID)
                }

                else -> {
                    null
                }
            }
        error.otherwise { room.copy(diagonals = room.diagonals.toPersistentList().adding(diagonal)) }
    }

internal fun removeDiagonal(
    project: Project,
    edit: ProjectEdit.RemoveDiagonal,
): DomainResult<Project> =
    project.mapRoom(edit.roomId) { room ->
        val error =
            if (room.diagonals.none { it.id == edit.diagonalId }) {
                notFound(EntityKind.DIAGONAL, edit.diagonalId.value)
            } else {
                null
            }
        error.otherwise { room.copy(diagonals = room.diagonals.filter { it.id != edit.diagonalId }.toImmutableList()) }
    }

internal fun closeRoom(
    project: Project,
    edit: ProjectEdit.CloseRoom,
): DomainResult<Project> =
    project.mapRoom(edit.roomId) { room ->
        val error =
            when {
                room.isClosed -> rule(DomainRule.ROOM_ALREADY_CLOSED)
                room.walls.size < MIN_CLOSED_WALLS -> rule(DomainRule.TOO_FEW_WALLS)
                else -> null
            }
        error.otherwise { room.copy(isClosed = true) }
    }

internal fun setThickness(
    project: Project,
    edit: ProjectEdit.SetThickness,
): DomainResult<Project> =
    project.mapRoom(edit.roomId) { room ->
        val index = room.walls.indexOfFirst { it.id == edit.wallId }
        val error =
            if (index < 0) notFound(EntityKind.WALL, edit.wallId.value) else thicknessError(edit.thickness)
        error.otherwise {
            room.copy(
                walls =
                    room.walls.toPersistentList().replacingAt(
                        index,
                        room.walls[index].copy(thickness = edit.thickness),
                    ),
            )
        }
    }

internal fun revertAdjustment(
    project: Project,
    edit: ProjectEdit.RevertAdjustment,
): DomainResult<Project> = project.mapRoom(edit.roomId) { DomainResult.Success(it.copy(adjustment = null)) }

private const val MIN_CLOSED_WALLS = 3
