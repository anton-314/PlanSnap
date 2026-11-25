package app.plansnap.feature.editor.state

import androidx.lifecycle.SavedStateHandle
import app.plansnap.core.common.result.DomainResult
import app.plansnap.core.common.runtime.ApplicationScope
import app.plansnap.core.common.units.Length
import app.plansnap.core.common.units.TurnDirection
import app.plansnap.core.domain.command.CommandHistory
import app.plansnap.core.domain.command.DefaultCommandHistory
import app.plansnap.core.domain.command.DefaultProjectCommandFactory
import app.plansnap.core.domain.evaluation.AdjustmentProposal
import app.plansnap.core.domain.evaluation.DefaultRoomEvaluator
import app.plansnap.core.domain.evaluation.RoomEvaluation
import app.plansnap.core.domain.evaluation.RoomEvaluator
import app.plansnap.core.domain.opening.DefaultOpeningRules
import app.plansnap.core.domain.placement.DefaultDockPlacementResolver
import app.plansnap.core.domain.repository.AppSettings
import app.plansnap.core.domain.repository.ProjectSaver
import app.plansnap.core.drawing.scene.DefaultSceneBuilder
import app.plansnap.core.geometry.primitives.ClosureTolerance
import app.plansnap.core.model.CornerSpec
import app.plansnap.core.model.Project
import app.plansnap.core.model.Room
import app.plansnap.core.model.RoomId
import app.plansnap.core.model.Wall
import app.plansnap.core.model.WallId
import app.plansnap.testing.fixtures.fakes.FakeEditorDraftRepository
import app.plansnap.testing.fixtures.fakes.FakeProjectRepository
import app.plansnap.testing.fixtures.fakes.FakeProjectSaver
import app.plansnap.testing.fixtures.fakes.FakeSettingsRepository
import app.plansnap.testing.fixtures.runtime.FakeEpochClock
import app.plansnap.testing.fixtures.runtime.SequentialIdGenerator
import app.plansnap.testing.fixtures.runtime.TestDispatcherProvider
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import java.util.Collections

internal val PROJECT_ID =
    app.plansnap.core.model
        .ProjectId("p1")
internal val ROOM_ID = RoomId("r1")

/** Evaluator that records every call and can hold chosen calls until they are released or cancelled. */
internal class SpyEvaluator(
    private val delegate: RoomEvaluator,
) : RoomEvaluator {
    val calls: MutableList<Room> = Collections.synchronizedList(mutableListOf())
    var cancelledCalls = 0
        private set

    /** Returns the gate a call for the given room waits on, `null` lets it run. */
    var gate: (Room) -> CompletableDeferred<Unit>? = { null }

    override suspend fun evaluate(
        room: Room,
        tolerance: ClosureTolerance,
    ): DomainResult<RoomEvaluation> {
        calls += room
        val held = gate(room)
        if (held != null) {
            try {
                held.await()
            } catch (cancelled: CancellationException) {
                cancelledCalls++
                throw cancelled
            }
        }
        return delegate.evaluate(room, tolerance)
    }
}

/** Real domain, fake storage: everything the view model needs, driven by the virtual clock of [testScope]. */
internal class EditorRig(
    private val testScope: TestScope,
    project: Project,
    settings: AppSettings = AppSettings(),
) {
    val dispatchers = TestDispatcherProvider(StandardTestDispatcher(testScope.testScheduler))
    val drafts = FakeEditorDraftRepository()
    val projects = FakeProjectRepository(initialProjects = listOf(project), drafts = drafts)
    val saver = FakeProjectSaver(projects, FakeEpochClock())
    val settingsRepository = FakeSettingsRepository(settings)
    val evaluator = SpyEvaluator(DefaultRoomEvaluator(dispatchers))
    val handlers = mutableSetOf<EditorHandler>()
    private val commandFactory = DefaultProjectCommandFactory(DefaultOpeningRules())
    private val resolver = DefaultDockPlacementResolver(evaluator)

    /** The project as stored by the fake repository. */
    val stored: Project get() = projects.projects.single { it.id == PROJECT_ID }

    fun handle(): SavedStateHandle = SavedStateHandle(mapOf(EditorStore.KEY_PROJECT_ID to PROJECT_ID.value))

    fun viewModel(
        handle: SavedStateHandle = handle(),
        saver: ProjectSaver = this.saver,
    ): EditorViewModel =
        EditorViewModel(
            savedStateHandle = handle,
            projects = projects,
            drafts = drafts,
            settings = settingsRepository,
            saver = saver,
            historyFactory = { p -> history(p) },
            commandFactory = commandFactory,
            evaluator = evaluator,
            sceneBuilder = DefaultSceneBuilder(),
            ids = SequentialIdGenerator("gen"),
            dispatchers = dispatchers,
            applicationScope = ApplicationScope(testScope.backgroundScope),
            handlers = handlers,
        )

    private fun history(project: Project): CommandHistory =
        DefaultCommandHistory(project, evaluator, resolver, dispatchers)
}

private fun wall(
    id: String,
    mm: Long,
) = Wall(WallId(id), Length(mm), Length(120))

private val RIGHT = CornerSpec.DeclaredRight(TurnDirection.LEFT)

/** Empty project with one room without walls. */
internal fun emptyProject(): Project = projectOf(Room(ROOM_ID))

/** Project with a room of three walls of a 4 m by 3 m rectangle, still open. */
internal fun openProject(): Project =
    projectOf(
        Room(
            id = ROOM_ID,
            walls = persistentListOf(wall("w0", 4000), wall("w1", 3000), wall("w2", 4000)),
            corners = persistentListOf(CornerSpec.Unspecified, RIGHT, RIGHT),
        ),
    )

/** Project with a closed room whose last wall is [lastMm] long, so it does not close exactly. */
internal fun closedProject(lastMm: Long): Project =
    projectOf(
        Room(
            id = ROOM_ID,
            walls = persistentListOf(wall("w0", 4000), wall("w1", 3000), wall("w2", 4000), wall("w3", lastMm)),
            corners = persistentListOf(CornerSpec.Unspecified, RIGHT, RIGHT, RIGHT),
            isClosed = true,
        ),
    )

private fun projectOf(vararg rooms: Room) =
    Project(
        id = PROJECT_ID,
        name = "Test",
        defaultHeight = Length(2500),
        rooms = rooms.toList().toImmutableList(),
        createdAtEpochMs = 1L,
        updatedAtEpochMs = 1L,
    )

/** The adjustment proposal that the screen currently shows for [ROOM_ID]. */
internal fun EditorViewModel.shownProposal(): AdjustmentProposal =
    (state.value.evaluations.getValue(ROOM_ID) as RoomEvaluation.NeedsAdjustment).proposal
