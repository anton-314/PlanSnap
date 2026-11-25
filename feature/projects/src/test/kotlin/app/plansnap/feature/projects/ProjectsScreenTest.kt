package app.plansnap.feature.projects

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import app.plansnap.core.common.result.DocumentProblem
import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.result.DomainResult
import app.plansnap.core.common.result.EntityKind
import app.plansnap.core.common.runtime.DispatcherProvider
import app.plansnap.core.designsystem.theme.PlanSnapTheme
import app.plansnap.core.model.ProjectId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream

/** Flows of the project list through the real ViewModel and a fake repository. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en")
class ProjectsScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val dispatcher = UnconfinedTestDispatcher()
    private val repository = FakeProjectRepository()
    private val opened = mutableListOf<ProjectId>()
    private var settingsOpened = 0
    private var importStarted = 0

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun show(): ProjectsViewModel {
        val viewModel =
            ProjectsViewModel(
                repository = repository,
                dispatchers =
                    object : DispatcherProvider {
                        override val main = dispatcher
                        override val default = dispatcher
                        override val io = dispatcher
                    },
                ids = { "new-id" },
                clock = { 0L },
            )
        compose.setContent {
            val state by viewModel.state.collectAsState()
            PlanSnapTheme {
                ProjectsScreen(
                    state = state,
                    actions =
                        ProjectsScreenActions(
                            onOpenProject = { opened += it },
                            onOpenSettings = { settingsOpened++ },
                            onNewProject = viewModel::onNewProject,
                            onImport = { importStarted++ },
                            onRetry = viewModel::onRetry,
                            onRename = viewModel::onRenameRequested,
                            onDuplicate = viewModel::onDuplicate,
                            onDelete = viewModel::onDeleteRequested,
                            onNameChanged = viewModel::onNameChanged,
                            onDialogConfirmed = viewModel::onDialogConfirmed,
                            onDialogDismissed = viewModel::onDialogDismissed,
                            onErrorShown = viewModel::onErrorShown,
                        ),
                )
            }
        }
        return viewModel
    }

    private fun openMenu(name: String) {
        compose.onNodeWithContentDescription("More actions for $name").performClick()
    }

    @Test
    fun emptyListShowsExplanationAndEnabledNewProjectButton() {
        show()
        compose.onNodeWithText("Projects").assertExists()
        compose.onNodeWithText("Measure a room", substring = true).assertExists()
        compose.onNodeWithText("New project").assertIsEnabled()
    }

    @Test
    fun settingsAndImportActionsInvokeCallbacks() {
        show()
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Import project").performClick()
        assertEquals(1, settingsOpened)
        assertEquals(1, importStarted)
    }

    @Test
    fun listShowsNameAndRoomCountAndTappingOpensTheProject() {
        repository.emit(listOf(summary("a", name = "Flat", rooms = 2), summary("b", name = "Cabin", rooms = 1)))
        show()
        compose.onNodeWithText("2 rooms", substring = true).assertExists()
        compose.onNodeWithText("1 room", substring = true).assertExists()
        compose.onNodeWithText("Flat").performClick()
        assertEquals(listOf(ProjectId("a")), opened)
    }

    @Test
    fun creatingAProjectNeedsANameAndOpensTheNewProject() {
        show()
        compose.onNodeWithText("New project").performClick()
        compose.onNodeWithText("Create").assertIsNotEnabled()
        compose.onNodeWithText("Project name").performTextInput("Villa")
        compose.onNodeWithText("Create").assertIsEnabled().performClick()
        compose.waitForIdle()
        assertEquals("Villa", repository.created.single().name)
        compose.onNodeWithText("Create").assertDoesNotExist()
    }

    @Test
    fun renamingStartsWithTheCurrentNameAndStoresTheNewOne() {
        repository.emit(listOf(summary("a", name = "Flat")))
        show()
        openMenu("Flat")
        compose.onNodeWithText("Rename").performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("Cabin")
        compose.onNodeWithText("Rename").performClick()
        assertEquals(listOf(ProjectId("a") to "Cabin"), repository.renamed)
    }

    @Test
    fun duplicatingCallsTheRepository() {
        repository.emit(listOf(summary("a", name = "Flat")))
        show()
        openMenu("Flat")
        compose.onNodeWithText("Duplicate").performClick()
        assertEquals(listOf(ProjectId("a")), repository.duplicated)
    }

    @Test
    fun deletingAsksForConfirmationAndCancelKeepsTheProject() {
        repository.emit(listOf(summary("a", name = "Flat")))
        show()
        openMenu("Flat")
        compose.onNodeWithText("Delete").performClick()
        compose.onNodeWithText("Delete project?").assertExists()
        compose.onNodeWithText("“Flat” and all its rooms", substring = true).assertExists()
        assertTrue(repository.deleted.isEmpty())
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Delete project?").assertDoesNotExist()
        assertTrue(repository.deleted.isEmpty())
    }

    @Test
    fun confirmingTheDeletionRemovesTheProject() {
        repository.emit(listOf(summary("a", name = "Flat")))
        show()
        openMenu("Flat")
        compose.onNodeWithText("Delete").performClick()
        compose.onNodeWithText("Delete").performClick()
        assertEquals(listOf(ProjectId("a")), repository.deleted)
    }

    @Test
    fun failedActionShowsAReadableMessage() {
        repository.emit(listOf(summary("a", name = "Flat")))
        repository.failure = DomainError.NotFound(EntityKind.PROJECT, "a")
        show()
        openMenu("Flat")
        compose.onNodeWithText("Duplicate").performClick()
        compose.onNodeWithText("This project no longer exists.").assertExists()
    }

    @Test
    fun failedImportShowsAReadableMessage() {
        repository.importResult = DomainResult.Failure(DomainError.Document(DocumentProblem.UNSUPPORTED_VERSION))
        val viewModel = show()
        viewModel.onImport { ByteArrayInputStream(ByteArray(0)) }
        compose.onNodeWithText("This file was saved by a newer version", substring = true).assertExists()
    }

    @Test
    fun failedListShowsMessageAndRetryLoadsTheList() {
        repository.observeFailure = IllegalStateException("db")
        repository.emit(listOf(summary("a", name = "Flat")))
        show()
        compose.onNodeWithText("The projects could not be read", substring = true).assertExists()
        compose.onNodeWithText("Try again").performClick()
        compose.onNodeWithText("Flat").assertExists()
    }

    @Test
    @Config(qualifiers = "de")
    fun germanStringsAreUsedOnGermanDevices() {
        show()
        compose.onNodeWithText("Projekte").assertExists()
        compose.onNodeWithText("Neues Projekt").assertExists()
        compose.onNodeWithText("Projekt importieren").assertExists()
    }
}
