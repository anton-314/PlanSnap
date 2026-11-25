package app.plansnap.feature.editor.state

import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.result.DomainResult
import app.plansnap.core.common.runtime.DispatcherProvider
import app.plansnap.core.common.units.Length
import app.plansnap.core.common.units.LengthUnit
import app.plansnap.core.domain.command.ProjectCommandFactory
import app.plansnap.core.domain.command.ProjectEdit
import app.plansnap.core.domain.evaluation.RoomEvaluation
import app.plansnap.core.domain.evaluation.RoomEvaluator
import app.plansnap.core.drawing.scene.DrawingScene
import app.plansnap.core.drawing.scene.RoomScene
import app.plansnap.core.drawing.scene.SceneBuilder
import app.plansnap.core.drawing.scene.SceneOptions
import app.plansnap.core.drawing.scene.WorldBounds
import app.plansnap.core.geometry.primitives.ClosureTolerance
import app.plansnap.core.model.CornerSpec
import app.plansnap.core.model.Project
import app.plansnap.core.model.Room
import app.plansnap.core.model.RoomId
import app.plansnap.core.model.Wall
import app.plansnap.core.model.WallId
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The two live derivations of the editor state, both driven by the state itself (unidirectional data flow):
 *
 * - the evaluation and scene of the confirmed project, recomputed when project or tolerance change,
 * - the preview of the typed wall, recomputed on every change of the input.
 *
 * Both use `collectLatest`, so a newer input cancels the older, still running evaluation and its result is never
 * published (ARCHITECTURE section 5, ADR 0016). A cancelled run leaves the state untouched.
 */
internal class EvaluationPipelines(
    private val store: EditorStore,
    private val evaluator: RoomEvaluator,
    private val sceneBuilder: SceneBuilder,
    private val factory: ProjectCommandFactory,
    private val dispatchers: DispatcherProvider,
) {
    private var cache: Map<RoomKey, Computed> = emptyMap()

    /** Starts both derivations in the scope of the screen. */
    fun start() {
        store.scope.launch {
            store.state
                .map { ProjectInput(it.project, it.tolerance) }
                .distinctUntilChanged()
                .collectLatest { evaluate(it) }
        }
        store.scope.launch {
            store.state
                .map {
                    PreviewInput(
                        it.project,
                        it.selectedRoom,
                        it.inputText,
                        it.nextCorner,
                        it.thickness,
                        it.settings.lengthUnit,
                        it.tolerance,
                    )
                }.distinctUntilChanged()
                .collectLatest { preview(it) }
        }
    }

    private suspend fun evaluate(input: ProjectInput) {
        val project = input.project ?: return
        val computed = LinkedHashMap<RoomKey, Computed>()
        val errors = LinkedHashMap<RoomId, DomainError>()
        for (room in project.rooms) {
            val key = RoomKey(room, input.tolerance)
            val known = cache[key]
            if (known != null) {
                computed[key] = known
            } else {
                when (val result = evaluator.evaluate(room, input.tolerance)) {
                    is DomainResult.Failure -> errors[room.id] = result.error
                    is DomainResult.Success -> computed[key] = computeScene(room, result.value)
                }
            }
        }
        currentCoroutineContext().ensureActive()
        cache = computed
        val perRoom =
            project.rooms.mapNotNull { room ->
                computed[RoomKey(room, input.tolerance)]?.let { room.id to it }
            }
        val rooms = perRoom.map { it.second.scene }
        val scene =
            DrawingScene(rooms.toImmutableList(), rooms.mapNotNull(RoomScene::bounds).reduceOrNull(WorldBounds::union))
        store.update {
            it.copy(
                evaluations = perRoom.associate { (id, value) -> id to value.evaluation }.toImmutableMap(),
                evaluationErrors = errors.toImmutableMap(),
                scene = scene,
            )
        }
    }

    private suspend fun computeScene(
        room: Room,
        evaluation: RoomEvaluation,
    ): Computed =
        withContext(
            dispatchers.default,
        ) { Computed(evaluation, sceneBuilder.buildRoom(room, evaluation, SceneOptions())) }

    private suspend fun preview(input: PreviewInput) {
        val outcome = previewOf(input)
        currentCoroutineContext().ensureActive()
        store.update { it.copy(preview = outcome.preview, inputError = outcome.error) }
    }

    private suspend fun previewOf(input: PreviewInput): PreviewOutcome {
        val room = input.room
        if (input.text.isBlank() || room == null || input.project == null) return PreviewOutcome(null, null)
        return when (val length = WallInput.parseLength(input.text, input.unit)) {
            is DomainResult.Failure -> PreviewOutcome(null, length.error)
            is DomainResult.Success -> previewWall(input.project, room, length.value, input)
        }
    }

    private suspend fun previewWall(
        project: Project,
        room: Room,
        length: Length,
        input: PreviewInput,
    ): PreviewOutcome {
        val edit =
            ProjectEdit.AddWall(
                room.id,
                Wall(WallId(PREVIEW_WALL_ID), length, input.thickness),
                WallInput.startCorner(room, input.corner),
            )
        return when (val applied = factory.create(edit).apply(project)) {
            is DomainResult.Failure -> {
                PreviewOutcome(null, applied.error)
            }

            is DomainResult.Success -> {
                val extended = applied.value.rooms.first { it.id == room.id }
                when (val evaluated = evaluator.evaluate(extended, input.tolerance)) {
                    is DomainResult.Failure -> {
                        PreviewOutcome(null, evaluated.error)
                    }

                    is DomainResult.Success -> {
                        val scene = computeScene(extended, evaluated.value).scene
                        PreviewOutcome(WallPreview(room.id, extended, evaluated.value, scene), null)
                    }
                }
            }
        }
    }

    private data class RoomKey(
        val room: Room,
        val tolerance: ClosureTolerance,
    )

    private data class Computed(
        val evaluation: RoomEvaluation,
        val scene: RoomScene,
    )

    private data class ProjectInput(
        val project: Project?,
        val tolerance: ClosureTolerance,
    )

    private data class PreviewInput(
        val project: Project?,
        val room: Room?,
        val text: String,
        val corner: CornerSpec,
        val thickness: Length,
        val unit: LengthUnit,
        val tolerance: ClosureTolerance,
    )

    private data class PreviewOutcome(
        val preview: WallPreview?,
        val error: DomainError?,
    )

    private companion object {
        /** Id of the wall in the preview. The preview wall is never stored. */
        const val PREVIEW_WALL_ID = "preview"
    }
}
