package app.plansnap.core.domain.validation

import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.result.DomainResult
import app.plansnap.core.common.result.DomainRule
import app.plansnap.core.common.result.map
import app.plansnap.core.domain.command.thicknessError
import app.plansnap.core.domain.command.wallLengthError
import app.plansnap.core.domain.placement.DockTopology
import app.plansnap.core.geometry.primitives.GeometryTolerances
import app.plansnap.core.model.Opening
import app.plansnap.core.model.Project
import app.plansnap.core.model.Room

/**
 * Validation of DOMAIN section 11 for projects from an untrusted source (ADR 0017).
 *
 * Per room in project order the first violation of these checks is reported:
 * 1. more than `MAX_WALLS_PER_ROOM` walls: `RuleViolation(TOO_MANY_WALLS)`,
 * 2. per wall in index order a length outside `MIN_WALL_MM..MAX_WALL_MM` or a thickness outside
 *    `0..MAX_THICKNESS_MM`: `InvalidValue`,
 * 3. per opening in index order an overlap with an earlier opening on the same wall: `OPENING_OVERLAP`. Openings that
 *    touch do not overlap. An opening that no longer fits its wall is not a violation, it is a derived status
 *    (DOMAIN section 9),
 * 4. the dock link of the room through `DockTopology.linkError`.
 *
 * After all rooms the dock links are checked together for cycles through `DockTopology.resolutionOrder`.
 */
class DefaultProjectValidator : ProjectValidator {
    override fun validate(project: Project): DomainResult<Unit> {
        val roomError = project.rooms.firstNotNullOfOrNull { roomError(project, it) }
        return if (roomError != null) {
            DomainResult.Failure(roomError)
        } else {
            DockTopology.resolutionOrder(project).map { }
        }
    }

    private fun roomError(
        project: Project,
        room: Room,
    ): DomainError? =
        wallError(room)
            ?: overlapError(room)
            ?: room.placement.dock?.let { DockTopology.linkError(project, room, it) }

    private fun wallError(room: Room): DomainError? =
        if (room.walls.size > GeometryTolerances.MAX_WALLS_PER_ROOM) {
            DomainError.RuleViolation(DomainRule.TOO_MANY_WALLS)
        } else {
            room.walls.firstNotNullOfOrNull { wallLengthError(it.length) ?: thicknessError(it.thickness) }
        }

    private fun overlapError(room: Room): DomainError? {
        val overlapping =
            room.openings.withIndex().any { (index, opening) ->
                room.openings.take(index).any { opening.overlaps(it) }
            }
        return if (overlapping) DomainError.RuleViolation(DomainRule.OPENING_OVERLAP) else null
    }

    private fun Opening.overlaps(other: Opening): Boolean =
        wallId == other.wallId && offset < other.endOffset && other.offset < endOffset
}
