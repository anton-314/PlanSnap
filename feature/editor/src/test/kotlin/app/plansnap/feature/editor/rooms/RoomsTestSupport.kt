package app.plansnap.feature.editor.rooms

import app.plansnap.core.common.runtime.ApplicationScope
import app.plansnap.core.common.units.Length
import app.plansnap.core.common.units.TurnDirection
import app.plansnap.core.domain.evaluation.RoomEvaluation
import app.plansnap.core.domain.placement.toWorld
import app.plansnap.core.domain.repository.AppSettings
import app.plansnap.core.geometry.primitives.Vec2
import app.plansnap.core.model.CornerSpec
import app.plansnap.core.model.Opening
import app.plansnap.core.model.OpeningId
import app.plansnap.core.model.OpeningType
import app.plansnap.core.model.Project
import app.plansnap.core.model.ProjectId
import app.plansnap.core.model.Room
import app.plansnap.core.model.RoomId
import app.plansnap.core.model.RoomPlacement
import app.plansnap.core.model.Wall
import app.plansnap.core.model.WallId
import app.plansnap.feature.editor.input.editorState
import app.plansnap.feature.editor.shape.evaluate
import app.plansnap.feature.editor.state.EditorIntent
import app.plansnap.feature.editor.state.EditorRig
import app.plansnap.feature.editor.state.EditorUiState
import app.plansnap.feature.editor.state.EditorViewModel
import app.plansnap.testing.fixtures.runtime.SequentialIdGenerator
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent

internal val HOST_ID = RoomId("host")
internal val GUEST_ID = RoomId("guest")
internal val DOOR_ID = OpeningId("door")
private val LEFT = CornerSpec.DeclaredRight(TurnDirection.LEFT)

/** A closed rectangle `widthMm` by `heightMm`, walls `<id>0 … <id>3` of 120 mm, placed at ([x], 0). */
internal fun rectangleRoom(
    id: RoomId,
    widthMm: Long,
    heightMm: Long,
    x: Double = 0.0,
    openings: List<Opening> = emptyList(),
): Room =
    Room(
        id = id,
        name = id.value,
        walls =
            listOf(widthMm, heightMm, widthMm, heightMm)
                .mapIndexed { i, mm -> Wall(WallId("${id.value}$i"), Length(mm), Length(120)) }
                .toImmutableList(),
        corners = persistentListOf(CornerSpec.Unspecified, LEFT, LEFT, LEFT),
        openings = openings.toImmutableList(),
        isClosed = true,
        placement = RoomPlacement(x, 0.0, 0.0, null),
    )

/** A door on wall `host0` that starts 500 mm after the wall and is 900 mm wide. */
internal fun hostDoor(): Opening =
    Opening(DOOR_ID, WallId("host0"), OpeningType.DOOR, Length(500), Length(900), null, null, null)

/** The host 4 m by 3 m at the origin with a door, and the guest 2 m by 2 m, 6 m to the right of it. */
internal fun twoRoomProject(): Project =
    Project(
        id = ProjectId("p1"),
        name = "Test",
        defaultHeight = Length(2500),
        rooms =
            persistentListOf(
                rectangleRoom(HOST_ID, 4000, 3000, openings = listOf(hostDoor())),
                rectangleRoom(GUEST_ID, 2000, 2000, x = 6000.0),
            ),
        createdAtEpochMs = 1L,
        updatedAtEpochMs = 1L,
    )

/** State of a loaded editor that shows [project] with real evaluations and has the guest selected. */
internal fun roomsState(project: Project = twoRoomProject()): EditorUiState =
    editorState().copy(
        project = project,
        selectedRoomId = GUEST_ID,
        evaluations = project.rooms.associate { it.id to evaluate(it) }.toImmutableMap(),
    )

/** The world corners of room [id] of [project], with the displayed shape of [evaluation]. */
internal fun worldCorners(
    room: Room,
    evaluation: RoomEvaluation,
): List<Vec2> = checkNotNull(evaluation.shape).outline.vertices.map { room.placement.toWorld(it) }

/** The view model with the [RoomsHandler] on a project, driven by the virtual clock of the test scope. */
@OptIn(ExperimentalCoroutinesApi::class)
internal class RoomsRig(
    private val scope: TestScope,
    project: Project = twoRoomProject(),
    settings: AppSettings = AppSettings(),
) {
    val rig = EditorRig(scope, project, settings)
    val handler =
        RoomsHandler(SequentialIdGenerator("gen"), rig.settingsRepository, ApplicationScope(scope.backgroundScope))
    val viewModel: EditorViewModel

    init {
        rig.handlers += handler
        viewModel = rig.viewModel()
        scope.runCurrent()
    }

    val state: EditorUiState get() = viewModel.state.value
    val rooms: RoomsState get() = state.section(RoomsState::class) ?: RoomsState()

    /** Sends [intents] in order and lets all queued work finish. */
    fun send(vararg intents: EditorIntent) {
        intents.forEach(viewModel::onIntent)
        scope.runCurrent()
    }

    /** The room [id] of the confirmed project. */
    fun room(id: RoomId): Room = checkNotNull(state.project).rooms.first { it.id == id }

    /** The world corners of room [id] according to the current evaluation. */
    fun corners(id: RoomId): List<Vec2> = worldCorners(room(id), state.evaluations.getValue(id))
}
