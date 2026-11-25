package app.plansnap.core.domain.placement

import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.result.DomainResult
import app.plansnap.core.common.result.DomainRule
import app.plansnap.core.common.result.EntityKind
import app.plansnap.core.model.DockLink
import app.plansnap.core.model.Project
import app.plansnap.core.model.Room
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/**
 * Structure of the dock links of a project (DOMAIN section 10, ADR 0013).
 *
 * Every room has at most one host, so the links form a forest unless they contain a cycle. The checks are shared by
 * the placement resolver and by project validation.
 */
object DockTopology {
    /**
     * Checks one dock [link] of [room] against [project] in the order of DOMAIN section 10.
     *
     * @return `null` for a valid link, otherwise the first violation: `DOCK_TO_SELF`, `NotFound` for a missing host
     * room, host wall, own wall or host opening, then `DOCK_OPENING_NOT_ON_WALL`. Cycles are checked by
     * [resolutionOrder], because they are a property of all links together.
     */
    fun linkError(
        project: Project,
        room: Room,
        link: DockLink,
    ): DomainError? {
        val host = project.rooms.firstOrNull { it.id == link.hostRoomId }
        val openingId = link.hostOpeningId
        val hostOpening = host?.openings?.firstOrNull { it.id == openingId }
        return when {
            link.hostRoomId == room.id -> {
                DomainError.RuleViolation(DomainRule.DOCK_TO_SELF)
            }

            host == null -> {
                DomainError.NotFound(EntityKind.ROOM, link.hostRoomId.value)
            }

            host.walls.none { it.id == link.hostWallId } -> {
                DomainError.NotFound(EntityKind.WALL, link.hostWallId.value)
            }

            room.walls.none { it.id == link.ownWallId } -> {
                DomainError.NotFound(EntityKind.WALL, link.ownWallId.value)
            }

            openingId != null && hostOpening == null -> {
                DomainError.NotFound(EntityKind.OPENING, openingId.value)
            }

            hostOpening != null && hostOpening.wallId != link.hostWallId -> {
                DomainError.RuleViolation(DomainRule.DOCK_OPENING_NOT_ON_WALL)
            }

            else -> {
                null
            }
        }
    }

    /**
     * Returns the docked rooms of [project] in resolution order: every host comes before the rooms docked to it.
     *
     * The order is deterministic. Rooms are sorted by their depth in the dock forest and, at equal depth, by their
     * position in [Project.rooms]. Rooms without a dock link are not part of the result.
     *
     * @return the order, or the first violation in room order from [linkError], or `DOCK_CYCLE` if the links contain
     * a cycle.
     */
    fun resolutionOrder(project: Project): DomainResult<ImmutableList<Room>> {
        val rooms = project.rooms
        val firstError =
            rooms.firstNotNullOfOrNull { room -> room.placement.dock?.let { linkError(project, room, it) } }
        val depths = if (firstError == null) depthsOf(rooms) else null
        return when {
            firstError != null -> {
                DomainResult.Failure(firstError)
            }

            depths == null -> {
                DomainResult.Failure(DomainError.RuleViolation(DomainRule.DOCK_CYCLE))
            }

            else -> {
                val order =
                    rooms.indices
                        .filter { rooms[it].placement.dock != null }
                        .sortedWith(compareBy<Int> { depths[it] }.thenBy { it })
                        .map { rooms[it] }
                DomainResult.Success(order.toImmutableList())
            }
        }
    }

    /**
     * Depth of every room in the dock forest, 0 for a room without host. Requires valid host references.
     *
     * @return the depths, or `null` if the links contain a cycle.
     */
    private fun depthsOf(rooms: List<Room>): IntArray? {
        val indexOf = rooms.withIndex().associate { it.value.id to it.index }
        val hostIndex =
            IntArray(rooms.size) { i -> rooms[i].placement.dock?.let { indexOf.getValue(it.hostRoomId) } ?: NO_HOST }
        val depths = IntArray(rooms.size) { UNKNOWN }
        for (start in rooms.indices) {
            val path = ArrayList<Int>()
            val onPath = HashSet<Int>()
            var current = start
            while (current != NO_HOST && depths[current] == UNKNOWN) {
                if (!onPath.add(current)) return null
                path.add(current)
                current = hostIndex[current]
            }
            var depth = if (current == NO_HOST) NO_HOST else depths[current]
            for (index in path.asReversed()) {
                depth += 1
                depths[index] = depth
            }
        }
        return depths
    }

    private const val UNKNOWN = -2
    private const val NO_HOST = -1
}
