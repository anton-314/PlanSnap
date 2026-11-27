package app.plansnap.feature.export.ui.states

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.lifecycle.SavedStateHandle
import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.result.DomainResult
import app.plansnap.core.common.result.StorageProblem
import app.plansnap.core.designsystem.theme.PlanSnapTheme
import app.plansnap.core.domain.evaluation.DefaultRoomEvaluator
import app.plansnap.core.domain.repository.ProjectRepository
import app.plansnap.core.drawing.plot.DefaultPlotLayout
import app.plansnap.core.drawing.scene.DefaultSceneBuilder
import app.plansnap.core.export.ExportFormat
import app.plansnap.core.export.preflight.DefaultExportPreflight
import app.plansnap.core.model.Project
import app.plansnap.core.model.ProjectId
import app.plansnap.feature.export.data.DefaultPlotPreview
import app.plansnap.feature.export.ui.ExportActions
import app.plansnap.feature.export.ui.ExportRooms
import app.plansnap.feature.export.ui.ExportScreen
import app.plansnap.feature.export.ui.ExportViewModel
import app.plansnap.feature.export.ui.FakeExporter
import app.plansnap.feature.export.ui.FakeFiles
import app.plansnap.feature.export.ui.FakeProjects
import app.plansnap.feature.export.ui.FakeSettings
import app.plansnap.feature.export.ui.SingleDispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Progress, "no room" and load failure of the export through the real ViewModel (docs/UX.md section 1). */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-w360dp-h640dp")
class ExportStatesTest {
    @get:Rule
    val compose = createComposeRule()

    private val dispatcher = UnconfinedTestDispatcher()
    private var backs = 0

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** Repository that answers the n-th read with the n-th entry of [reads], once that entry is completed. */
    private class ScriptedProjects(
        private vararg val reads: CompletableDeferred<DomainResult<Project>>,
    ) : ProjectRepository by FakeProjects(readFailure()) {
        var read = 0
            private set

        override suspend fun getProject(id: ProjectId): DomainResult<Project> = reads[read++].await()
    }

    private fun show(
        repository: ProjectRepository,
        fontScale: Float = 1f,
    ) {
        val dispatchers = SingleDispatchers(dispatcher)
        val viewModel =
            ExportViewModel(
                savedState = SavedStateHandle(mapOf(ExportViewModel.PROJECT_ID_KEY to "p1")),
                projects = repository,
                settings = FakeSettings(),
                evaluator = DefaultRoomEvaluator(dispatchers),
                preflight = DefaultExportPreflight(),
                preview = DefaultPlotPreview(DefaultSceneBuilder(), DefaultPlotLayout(), dispatchers),
                exporters = ExportFormat.entries.map { FakeExporter(it) }.toSet(),
                files = FakeFiles(),
                dispatchers = dispatchers,
            )
        compose.setContent {
            val state by viewModel.state.collectAsState()
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                PlanSnapTheme {
                    ExportScreen(
                        state = state,
                        actions =
                            ExportActions(
                                onFormatSelected = viewModel::onFormatSelected,
                                onScaleSelected = viewModel::onScaleSelected,
                                onPaperSelected = viewModel::onPaperSelected,
                                onShare = viewModel::onShare,
                                onSave = viewModel::onSave,
                                onRetry = viewModel::onRetry,
                                onRetryLoad = viewModel::onRetryLoad,
                                onErrorDismissed = viewModel::onErrorDismissed,
                                onNoticeDismissed = viewModel::onNoticeDismissed,
                            ),
                        onBack = { backs++ },
                    )
                }
            }
        }
    }

    @Test
    fun `given the project is being read then progress is announced and nothing can be exported`() {
        show(ScriptedProjects(CompletableDeferred()))

        compose.onNodeWithText("Preparing export").assertIsDisplayed()
        compose.onNodeWithText("Share").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "de-w360dp-h640dp")
    fun `given the project is being read when german then the progress text is german`() {
        show(ScriptedProjects(CompletableDeferred()))

        compose.onNodeWithText("Export wird vorbereitet").assertIsDisplayed()
    }

    @Test
    fun `given a project without rooms then only the hint is shown`() {
        show(ScriptedProjects(CompletableDeferred(DomainResult.Success(ExportRooms.project()))))

        compose.onNodeWithText("No room yet").assertIsDisplayed()
        compose.onNodeWithText("Preparing export").assertDoesNotExist()
        compose.onNodeWithText("Share").assertDoesNotExist()
    }

    @Test
    fun `given a read failure then retry shows progress until the options appear`() {
        val second = CompletableDeferred<DomainResult<Project>>()
        val repository = ScriptedProjects(CompletableDeferred(readFailure()), second)
        show(repository)
        compose.onNodeWithText("The project could not be read. Try again.").assertIsDisplayed()

        compose.onNodeWithText("Try again").performClick()

        compose.onNodeWithText("Preparing export").assertIsDisplayed()
        compose.onNodeWithText("Try again").assertDoesNotExist()
        second.complete(DomainResult.Success(ExportRooms.project(ExportRooms.confirmed)))
        compose.waitForIdle()
        compose.onNodeWithText("Share").assertIsDisplayed()
        assertEquals(2, repository.read)
    }

    @Test
    fun `given retry fails again then the failure and retry stay available`() {
        val repository =
            ScriptedProjects(CompletableDeferred(readFailure()), CompletableDeferred(readFailure()))
        show(repository)

        compose.onNodeWithText("Try again").performClick()

        compose.onNodeWithText("The project could not be read. Try again.").assertIsDisplayed()
        compose.onNodeWithText("Try again").assertIsDisplayed()
        assertEquals(2, repository.read)
    }

    @Test
    @Config(qualifiers = "en-w320dp-h240dp")
    fun `given a small window and font scale 2 then retry is reachable by scrolling`() {
        val repository = ScriptedProjects(CompletableDeferred(readFailure()), CompletableDeferred())
        show(repository, fontScale = 2f)

        compose
            .onNodeWithText("Try again")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()

        assertEquals(2, repository.read)
    }
}

private fun readFailure(): DomainResult<Project> = DomainResult.Failure(DomainError.Storage(StorageProblem.READ_FAILED))
