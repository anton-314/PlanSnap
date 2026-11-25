package app.plansnap.feature.editor.shape

import app.plansnap.core.common.result.DomainResult
import app.plansnap.core.common.units.Angle
import app.plansnap.core.common.units.Length
import app.plansnap.core.common.units.TurnDirection
import app.plansnap.core.domain.command.ProjectEdit
import app.plansnap.core.domain.evaluation.DefaultRoomEvaluator
import app.plansnap.core.domain.evaluation.RoomEvaluation
import app.plansnap.core.geometry.primitives.ClosureTolerance
import app.plansnap.core.model.CornerSpec
import app.plansnap.core.model.Diagonal
import app.plansnap.core.model.DiagonalId
import app.plansnap.core.model.Room
import app.plansnap.core.model.RoomId
import app.plansnap.core.model.Wall
import app.plansnap.core.model.WallId
import app.plansnap.feature.editor.input.editorState
import app.plansnap.feature.editor.state.EditorEffect
import app.plansnap.feature.editor.state.EditorHost
import app.plansnap.feature.editor.state.EditorUiState
import app.plansnap.testing.fixtures.runtime.TestDispatcherProvider
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher

internal val SHAPE_ROOM = RoomId("r1")
private val LEFT_RIGHT_ANGLE = CornerSpec.DeclaredRight(TurnDirection.LEFT)

/** A room of the given wall lengths in mm. Corners are declared right angles, `V_0` is unspecified. */
internal fun roomOf(
    vararg lengthsMm: Long,
    closed: Boolean = false,
    diagonals: List<Diagonal> = emptyList(),
): Room =
    Room(
        id = SHAPE_ROOM,
        name = "Küche",
        walls = lengthsMm.mapIndexed { i, mm -> Wall(WallId("w$i"), Length(mm), Length(120)) }.toImmutableList(),
        corners =
            lengthsMm.indices
                .map { if (it == 0) CornerSpec.Unspecified else LEFT_RIGHT_ANGLE }
                .toImmutableList(),
        diagonals = diagonals.toImmutableList(),
        isClosed = closed,
    )

/** A closed 4 m by 3 m rectangle whose last wall is [lastMm] long, so a different value leaves a gap. */
internal fun almostRectangle(lastMm: Long): Room = roomOf(4000, 3000, 4000, lastMm, closed = true)

/** The ADR 0019 case: a rectangle of 4150, 3000, 4000, 3000, all corners measured as 90 degrees. */
internal fun largeCorrectionRoom(): Room =
    roomOf(4150, 3000, 4000, 3000, closed = true).let { room ->
        room.copy(
            corners = List(room.walls.size) { CornerSpec.Measured(TurnDirection.LEFT, Angle.RIGHT) }.toImmutableList(),
        )
    }

/** Room whose four corners are declared right angles but with walls 3000, 3000, 3000, 5000: shape deviation. */
internal fun underdeterminedRoom(): Room = roomOf(3000, 3000, 3000, 5000, closed = true)

/** A closed room whose longest wall is longer than all others together: a conflict. */
internal fun impossibleRoom(): Room = roomOf(1000, 1000, 1000, 5000, closed = true)

/** A diagonal of [lengthMm] between two corners of a room. */
internal fun diagonalOf(
    from: Int,
    to: Int,
    lengthMm: Long,
): Diagonal = Diagonal(DiagonalId("d$from$to"), from, to, Length(lengthMm))

/** Evaluates [room] with the real evaluator. */
internal fun evaluate(
    room: Room,
    tolerance: ClosureTolerance = ClosureTolerance(),
): RoomEvaluation =
    runBlocking {
        val evaluator = DefaultRoomEvaluator(TestDispatcherProvider(UnconfinedTestDispatcher()))
        (evaluator.evaluate(room, tolerance) as DomainResult.Success).value
    }

/** The room with its confirmed adjustment, as `AcceptAdjustment` stores it. */
internal fun Room.withOverlay(): Room =
    copy(adjustment = (evaluate(this) as RoomEvaluation.NeedsAdjustment).proposal.adjustment)

/** State of a loaded editor that shows [room] with its real evaluation. */
internal fun stateOf(
    room: Room,
    block: EditorUiState.() -> EditorUiState = { this },
): EditorUiState {
    val base = editorState()
    val project = base.project!!.copy(rooms = persistentListOf(room))
    return base
        .copy(
            project = project,
            selectedRoomId = room.id,
            evaluations = mapOf(room.id to evaluate(room)).toImmutableMap(),
        ).block()
}

/** Editor host that records what a handler asks for. */
internal class RecordingHost(
    initial: EditorUiState,
) : EditorHost {
    private val mutable = MutableStateFlow(initial)
    override val state: StateFlow<EditorUiState> = mutable
    override val scope: CoroutineScope = CoroutineScope(Job())
    val submitted: MutableList<ProjectEdit> = mutableListOf()
    val effects: MutableList<EditorEffect> = mutableListOf()

    /** The state of the shape section. */
    val shape: ShapeState get() = mutable.value.section(ShapeState::class) ?: ShapeState()

    override fun update(transform: (EditorUiState) -> EditorUiState) = mutable.update(transform)

    override fun submit(edit: ProjectEdit) {
        submitted += edit
    }

    override fun send(effect: EditorEffect) {
        effects += effect
    }
}
