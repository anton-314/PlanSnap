package app.plansnap.feature.editor.canvas

import app.plansnap.core.common.result.DomainResult
import app.plansnap.core.common.runtime.DispatcherProvider
import app.plansnap.core.common.units.Length
import app.plansnap.core.common.units.TurnDirection
import app.plansnap.core.domain.evaluation.DefaultRoomEvaluator
import app.plansnap.core.domain.evaluation.RoomEvaluation
import app.plansnap.core.drawing.scene.DefaultSceneBuilder
import app.plansnap.core.drawing.scene.DrawingScene
import app.plansnap.core.drawing.scene.SceneOptions
import app.plansnap.core.geometry.primitives.ClosureTolerance
import app.plansnap.core.model.CornerSpec
import app.plansnap.core.model.Project
import app.plansnap.core.model.ProjectId
import app.plansnap.core.model.Room
import app.plansnap.core.model.RoomId
import app.plansnap.core.model.RoomPlacement
import app.plansnap.core.model.Wall
import app.plansnap.core.model.WallId
import app.plansnap.feature.editor.state.WallPreview
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/** Real scenes from the real evaluator and scene builder, so the canvas tests draw what the editor draws. */
internal object CanvasTestScenes {
    private val dispatchers =
        object : DispatcherProvider {
            override val main = Dispatchers.Unconfined
            override val default = Dispatchers.Unconfined
            override val io = Dispatchers.Unconfined
        }
    private val left = CornerSpec.DeclaredRight(TurnDirection.LEFT)

    /** A closed rectangle of [widthMm] by [heightMm] with its first corner at ([x], [y]). */
    fun rectangle(
        id: String,
        widthMm: Long,
        heightMm: Long,
        x: Double = 0.0,
        y: Double = 0.0,
    ): Room =
        Room(
            id = RoomId(id),
            name = id,
            walls =
                listOf(widthMm, heightMm, widthMm, heightMm)
                    .mapIndexed { i, mm -> Wall(WallId("$id-w$i"), Length(mm), Length(120)) }
                    .toImmutableList(),
            corners = persistentListOf(CornerSpec.Unspecified, left, left, left),
            isClosed = true,
            placement = RoomPlacement(x, y, 0.0, null),
        )

    private fun evaluate(room: Room): RoomEvaluation {
        val result = runBlocking { DefaultRoomEvaluator(dispatchers).evaluate(room, ClosureTolerance()) }
        return (result as DomainResult.Success).value
    }

    /** The scene of [rooms], evaluated with the default tolerance. */
    fun scene(vararg rooms: Room): DrawingScene {
        val project =
            Project(
                id = ProjectId("p"),
                name = "Test",
                defaultHeight = Length(2500),
                rooms = rooms.toList().toImmutableList(),
                createdAtEpochMs = 1L,
                updatedAtEpochMs = 1L,
            )
        val evaluations: Map<RoomId, RoomEvaluation> = rooms.associate { it.id to evaluate(it) }
        return DefaultSceneBuilder().build(project, evaluations.toImmutableMap(), SceneOptions())
    }

    /** The live preview of [room], as the state layer reports it while a wall is typed. */
    fun preview(room: Room): WallPreview {
        val evaluation = evaluate(room)
        val scene = DefaultSceneBuilder().buildRoom(room, evaluation, SceneOptions())
        return WallPreview(room.id, room, evaluation, scene)
    }

    /** An empty scene, as the state layer reports for a project without walls. */
    fun empty(): DrawingScene = scene()

    /** A 4 m by 3 m rectangle. */
    fun rectangleScene(): DrawingScene = scene(rectangle("living", 4000, 3000))

    /** Three rooms: living room and kitchen side by side, the bath above the living room. */
    fun apartmentScene(): DrawingScene =
        scene(
            rectangle("living", 5000, 4000),
            rectangle("kitchen", 3000, 4000, x = 5200.0),
            rectangle("bath", 2400, 2000, x = 0.0, y = 4200.0),
        )
}
