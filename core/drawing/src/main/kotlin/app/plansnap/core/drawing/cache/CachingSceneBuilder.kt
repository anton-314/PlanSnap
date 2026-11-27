package app.plansnap.core.drawing.cache

import app.plansnap.core.domain.evaluation.RoomEvaluation
import app.plansnap.core.domain.evaluation.isExportable
import app.plansnap.core.drawing.scene.DefaultSceneBuilder
import app.plansnap.core.drawing.scene.DrawingScene
import app.plansnap.core.drawing.scene.RoomScene
import app.plansnap.core.drawing.scene.SceneBuilder
import app.plansnap.core.drawing.scene.SceneOptions
import app.plansnap.core.drawing.scene.WorldBounds
import app.plansnap.core.model.Project
import app.plansnap.core.model.Room
import app.plansnap.core.model.RoomId
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.toImmutableList
import java.util.concurrent.ConcurrentHashMap

/**
 * [SceneBuilder] that keeps the scene of every room and builds it again only if its input changed (ADR 0008:
 * the room scene is the unit of caching).
 *
 * **Key.** A cached scene is returned only for an equal [Room], an equal [RoomEvaluation] and equal [SceneOptions],
 * compared by value. These are all arguments of [SceneBuilder.buildRoom], so with a pure [delegate] the result always
 * equals the uncached one. A docked room needs no knowledge of its host: the resolver writes the placement into the
 * room, so a host change that moves it changes its key. Display units are no input of a scene, so a change of unit
 * keeps every scene.
 *
 * **Memory.** At most one scene is kept per room id, the latest one. [build] forgets every room that is not part of
 * the project. A caller that only uses [buildRoom] forgets removed rooms with [retainRooms]. Use one instance per
 * consumer: callers that alternate between different options or between a preview and the confirmed state of a room
 * stay correct, but replace each other's entry and build again every time.
 *
 * **Threads.** Safe for concurrent use. Concurrent calls for one room may build it more than once, and every call
 * still returns the scene of its own arguments. A failing build stores nothing.
 *
 * @param delegate builds the scenes that are not cached. It must be pure and deterministic, and its
 * [SceneBuilder.build] must be the room scenes of its [SceneBuilder.buildRoom] in project order, without the rooms
 * that [SceneOptions.exportableOnly] omits.
 */
class CachingSceneBuilder(
    private val delegate: SceneBuilder = DefaultSceneBuilder(),
) : SceneBuilder {
    private val entries = ConcurrentHashMap<RoomId, Entry>()

    override fun buildRoom(
        room: Room,
        evaluation: RoomEvaluation,
        options: SceneOptions,
    ): RoomScene {
        val known = entries[room.id]
        if (known != null && known.isFor(room, evaluation, options)) return known.scene
        val scene = delegate.buildRoom(room, evaluation, options)
        entries[room.id] = Entry(room, evaluation, options, scene)
        return scene
    }

    /**
     * Builds the scene of [project] from cached and newly built room scenes and forgets all other rooms.
     *
     * @throws IllegalArgumentException if [evaluations] misses a room, or as [SceneBuilder.buildRoom] of the delegate.
     */
    override fun build(
        project: Project,
        evaluations: ImmutableMap<RoomId, RoomEvaluation>,
        options: SceneOptions,
    ): DrawingScene {
        val missing = project.rooms.firstOrNull { it.id !in evaluations }
        require(missing == null) { "Missing evaluation of room ${missing?.id?.value}" }
        retainRooms(project.rooms.mapTo(HashSet()) { it.id })
        val rooms =
            project.rooms.mapNotNull { room ->
                val evaluation = evaluations.getValue(room.id)
                if (options.exportableOnly && !evaluation.status.isExportable) {
                    null
                } else {
                    buildRoom(room, evaluation, options)
                }
            }
        return DrawingScene(rooms.toImmutableList(), rooms.mapNotNull { it.bounds }.reduceOrNull(WorldBounds::union))
    }

    /** Forgets the scenes of all rooms that are not in [roomIds]. An empty set clears the cache. */
    fun retainRooms(roomIds: Set<RoomId>) {
        entries.keys.retainAll(roomIds)
    }

    /** A built scene together with the arguments it was built from. */
    private class Entry(
        private val room: Room,
        private val evaluation: RoomEvaluation,
        private val options: SceneOptions,
        val scene: RoomScene,
    ) {
        fun isFor(
            otherRoom: Room,
            otherEvaluation: RoomEvaluation,
            otherOptions: SceneOptions,
        ): Boolean = room == otherRoom && evaluation == otherEvaluation && options == otherOptions
    }
}
