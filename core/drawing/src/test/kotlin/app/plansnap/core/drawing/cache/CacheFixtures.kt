package app.plansnap.core.drawing.cache

import app.plansnap.core.common.result.DomainResult
import app.plansnap.core.common.runtime.DispatcherProvider
import app.plansnap.core.domain.command.DefaultProjectCommandFactory
import app.plansnap.core.domain.command.ProjectEdit
import app.plansnap.core.domain.evaluation.DefaultRoomEvaluator
import app.plansnap.core.domain.evaluation.RoomEvaluation
import app.plansnap.core.domain.opening.DefaultOpeningRules
import app.plansnap.core.domain.placement.DefaultDockPlacementResolver
import app.plansnap.core.drawing.scene.DefaultSceneBuilder
import app.plansnap.core.drawing.scene.RoomScene
import app.plansnap.core.drawing.scene.SceneBuilder
import app.plansnap.core.drawing.scene.SceneOptions
import app.plansnap.core.geometry.primitives.ClosureTolerance
import app.plansnap.core.model.Project
import app.plansnap.core.model.Room
import app.plansnap.core.model.RoomId
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/** Delegate that records the room of every scene it really builds. */
internal class CountingSceneBuilder(
    private val delegate: SceneBuilder = DefaultSceneBuilder(),
) : SceneBuilder by delegate {
    val built = mutableListOf<RoomId>()

    override fun buildRoom(
        room: Room,
        evaluation: RoomEvaluation,
        options: SceneOptions,
    ): RoomScene {
        built += room.id
        return delegate.buildRoom(room, evaluation, options)
    }

    /** The rooms built since the last call. */
    fun drain(): List<RoomId> = built.toList().also { built.clear() }
}

/** The real domain services, so evaluations and docked placements are the ones the app produces. */
internal object CacheDomain {
    private val dispatchers =
        object : DispatcherProvider {
            override val main: CoroutineDispatcher get() = Dispatchers.Unconfined
            override val default: CoroutineDispatcher get() = Dispatchers.Unconfined
            override val io: CoroutineDispatcher get() = Dispatchers.Unconfined
        }
    private val evaluator = DefaultRoomEvaluator(dispatchers)
    private val resolver = DefaultDockPlacementResolver(evaluator)
    private val factory = DefaultProjectCommandFactory(DefaultOpeningRules())

    suspend fun evaluations(
        project: Project,
        tolerance: ClosureTolerance = ClosureTolerance(),
    ): ImmutableMap<RoomId, RoomEvaluation> =
        project.rooms
            .associate { room ->
                when (val result = evaluator.evaluate(room, tolerance)) {
                    is DomainResult.Success -> room.id to result.value
                    is DomainResult.Failure -> error("Evaluation failed: ${result.error}")
                }
            }.toImmutableMap()

    /**
     * Applies [edit] and resolves the docked placements, as the command history does. Returns [project] unchanged if a
     * domain rule rejects the edit.
     */
    suspend fun apply(
        project: Project,
        edit: ProjectEdit,
    ): Project =
        when (val applied = factory.create(edit).apply(project)) {
            is DomainResult.Failure -> project
            is DomainResult.Success -> resolve(applied.value)
        }

    /** Resolves the docked placements of [project]. */
    suspend fun resolve(project: Project): Project =
        when (val resolved = resolver.resolve(project, ClosureTolerance())) {
            is DomainResult.Success -> resolved.value
            is DomainResult.Failure -> error("Resolution failed: ${resolved.error}")
        }
}
