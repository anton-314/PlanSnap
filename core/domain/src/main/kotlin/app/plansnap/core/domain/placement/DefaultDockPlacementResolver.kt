package app.plansnap.core.domain.placement

import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.result.DomainResult
import app.plansnap.core.common.result.flatMap
import app.plansnap.core.common.result.fold
import app.plansnap.core.domain.command.DockPlacementResolver
import app.plansnap.core.domain.evaluation.EvaluatedShape
import app.plansnap.core.domain.evaluation.RoomEvaluator
import app.plansnap.core.geometry.primitives.ClosureTolerance
import app.plansnap.core.model.DockLink
import app.plansnap.core.model.Project
import app.plansnap.core.model.Room
import app.plansnap.core.model.RoomId
import app.plansnap.core.model.RoomPlacement
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * [DockPlacementResolver] of DOMAIN section 10 and ADR 0015.
 *
 * Docked rooms are resolved in [DockTopology.resolutionOrder], so a room is always aligned to the already resolved
 * placement of its host. The displayed outline from [evaluator] is authoritative. The own wall is turned antiparallel
 * to the host wall and its start point `V_j` lies [DockLink.hostOffset] along the host wall from `V_i`, or from the
 * start of the host opening if [DockLink.hostOpeningId] is set, and exactly the host wall thickness outside the host
 * inner contour. A room whose host or own evaluation has no shape (`OPEN`, `CONFLICT`) keeps its last placement
 * including the dock link.
 *
 * Only rooms that take part in a dock link are evaluated, each at most once per call. The result is deterministic and
 * idempotent: resolving a resolved project returns an equal project.
 *
 * @param evaluator evaluator of the displayed outlines. It runs on the default dispatcher and is cancellable.
 */
class DefaultDockPlacementResolver(
    private val evaluator: RoomEvaluator,
) : DockPlacementResolver {
    /**
     * Returns [project] with resolved placements of all docked rooms.
     *
     * @return the project, the same instance if no placement changed, or the first failure: an invalid dock link or
     * `DOCK_CYCLE` from [DockTopology.resolutionOrder], or an evaluation failure.
     * @throws kotlinx.coroutines.CancellationException if cancelled.
     */
    override suspend fun resolve(
        project: Project,
        tolerance: ClosureTolerance,
    ): DomainResult<Project> =
        DockTopology.resolutionOrder(project).flatMap { order ->
            resolveInOrder(project, order, ShapeCache(evaluator, tolerance))
        }

    private suspend fun resolveInOrder(
        project: Project,
        order: List<Room>,
        shapes: ShapeCache,
    ): DomainResult<Project> {
        val roomsById = project.rooms.associateBy { it.id }
        val placements = HashMap<RoomId, RoomPlacement>()
        for (room in order) {
            currentCoroutineContext().ensureActive()
            val link = checkNotNull(room.placement.dock) { "Resolution order contains only docked rooms" }
            val host = roomsById.getValue(link.hostRoomId)
            val failure = shapes.load(host) ?: shapes.load(room)
            if (failure != null) return DomainResult.Failure(failure)
            val hostShape = shapes[host.id]
            val ownShape = shapes[room.id]
            if (hostShape != null && ownShape != null) {
                val hostPlacement = placements[host.id] ?: host.placement
                placements[room.id] = placementOf(host, hostShape, hostPlacement, room, ownShape)
            }
        }
        return DomainResult.Success(project.withPlacements(placements))
    }

    private fun placementOf(
        host: Room,
        hostShape: EvaluatedShape,
        hostPlacement: RoomPlacement,
        room: Room,
        ownShape: EvaluatedShape,
    ): RoomPlacement {
        val link = checkNotNull(room.placement.dock) { "Only docked rooms are placed" }
        val hostWallIndex = host.walls.indexOfFirst { it.id == link.hostWallId }
        val ownWallIndex = room.walls.indexOfFirst { it.id == link.ownWallId }
        val openingOffsetMm =
            link.hostOpeningId
                ?.let { id ->
                    host.openings
                        .first { it.id == id }
                        .offset.mm
                        .toDouble()
                }
                ?: 0.0
        val wall =
            hostWall(
                hostShape,
                hostPlacement,
                hostWallIndex,
                host.walls[hostWallIndex]
                    .thickness.mm
                    .toDouble(),
            )
        return dockedPlacement(wall, openingOffsetMm + link.hostOffset.mm.toDouble(), ownShape, ownWallIndex, link)
    }

    /** Evaluates each room at most once. A cached `null` stands for an evaluation without shape. */
    private class ShapeCache(
        private val evaluator: RoomEvaluator,
        private val tolerance: ClosureTolerance,
    ) {
        private val shapes = HashMap<RoomId, EvaluatedShape?>()

        /** Displayed shape of a loaded room, `null` for an evaluation without shape. */
        operator fun get(id: RoomId): EvaluatedShape? = shapes[id]

        /** Evaluates [room] unless it is already loaded. Returns the evaluation failure, or `null`. */
        suspend fun load(room: Room): DomainError? {
            if (shapes.containsKey(room.id)) return null
            return evaluator.evaluate(room, tolerance).fold(
                onSuccess = { evaluation ->
                    val shape = evaluation.shape
                    check(shape == null || shape.outline.vertices.size == room.walls.size) {
                        "The displayed outline of ${room.id.value} needs one vertex per wall"
                    }
                    shapes[room.id] = shape
                    null
                },
                onFailure = { it },
            )
        }
    }
}

/** Returns this project with the given placements, the same instance if none of them changes a room. */
private fun Project.withPlacements(placements: Map<RoomId, RoomPlacement>): Project {
    val changed = rooms.any { room -> placements[room.id]?.let { it != room.placement } == true }
    if (!changed) return this
    return copy(
        rooms =
            rooms
                .map { room ->
                    val placement = placements[room.id]
                    if (placement == null || placement == room.placement) room else room.copy(placement = placement)
                }.toImmutableList(),
    )
}
