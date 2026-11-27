package app.plansnap.feature.projects.ui.states

import android.database.sqlite.SQLiteException
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import app.plansnap.core.common.runtime.DispatcherProvider
import app.plansnap.core.designsystem.theme.PlanSnapTheme
import app.plansnap.core.domain.repository.ProjectRepository
import app.plansnap.core.domain.repository.ProjectSummary
import app.plansnap.feature.projects.FakeProjectRepository
import app.plansnap.feature.projects.ProjectsScreen
import app.plansnap.feature.projects.ProjectsScreenActions
import app.plansnap.feature.projects.ProjectsViewModel
import app.plansnap.feature.projects.summary
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
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

/** Loading, empty and error state of the project list through the real ViewModel (docs/UX.md section 1). */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-w360dp-h640dp")
class ProjectsStatesTest {
    @get:Rule
    val compose = createComposeRule()

    private val dispatcher = UnconfinedTestDispatcher()
    private var newProjects = 0

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** Repository whose n-th observation of the list behaves like the n-th entry of [observations]. */
    private class ScriptedRepository(
        private vararg val observations: Flow<ImmutableList<ProjectSummary>>,
    ) : ProjectRepository by FakeProjectRepository() {
        var observed = 0
            private set

        override fun observeProjects(): Flow<ImmutableList<ProjectSummary>> = observations[observed++]
    }

    private fun storageFailure(): Flow<ImmutableList<ProjectSummary>> = flow { throw SQLiteException("disk I/O error") }

    private fun show(
        repository: ProjectRepository,
        fontScale: Float = 1f,
    ) {
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
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                PlanSnapTheme {
                    ProjectsScreen(
                        state = state,
                        actions =
                            ProjectsScreenActions(
                                onOpenProject = {},
                                onOpenSettings = {},
                                onNewProject = { newProjects++ },
                                onImport = {},
                                onRetry = viewModel::onRetry,
                                onRename = {},
                                onDuplicate = {},
                                onDelete = {},
                                onNameChanged = {},
                                onDialogConfirmed = {},
                                onDialogDismissed = {},
                                onErrorShown = {},
                            ),
                    )
                }
            }
        }
    }

    @Test
    fun `given the list has not arrived then the skeleton is labelled and replaced once it arrives`() {
        val arrival = CompletableDeferred<ImmutableList<ProjectSummary>>()
        show(ScriptedRepository(flow { emit(arrival.await()) }))
        compose.onNodeWithContentDescription("Loading projects…").assertIsDisplayed()

        arrival.complete(persistentListOf(summary("a", name = "Flat")))
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Loading projects…").assertDoesNotExist()
        compose.onNodeWithText("Flat").assertIsDisplayed()
    }

    @Test
    fun `given a storage exception then retry shows the skeleton until the list arrives`() {
        val arrival = CompletableDeferred<ImmutableList<ProjectSummary>>()
        val repository = ScriptedRepository(storageFailure(), flow { emit(arrival.await()) })
        show(repository)
        compose.onNodeWithText("The projects could not be read. Try again.").assertIsDisplayed()

        compose.onNodeWithText("Try again").performClick()

        compose.onNodeWithContentDescription("Loading projects…").assertIsDisplayed()
        compose.onNodeWithText("Try again").assertDoesNotExist()
        arrival.complete(persistentListOf(summary("a", name = "Flat")))
        compose.waitForIdle()
        compose.onNodeWithText("Flat").assertIsDisplayed()
        assertEquals(2, repository.observed)
    }

    @Test
    fun `given retry fails again then the failure and retry stay available`() {
        val repository = ScriptedRepository(storageFailure(), storageFailure(), flow { emit(persistentListOf()) })
        show(repository)

        compose.onNodeWithText("Try again").performClick()
        compose.onNodeWithText("The projects could not be read. Try again.").assertIsDisplayed()
        compose.onNodeWithText("Try again").performClick()

        compose.onNodeWithText("Measure a room", substring = true).assertIsDisplayed()
        assertEquals(3, repository.observed)
    }

    @Test
    fun `given a storage exception after a shown list then the failure replaces it and retry reloads`() {
        val failure = CompletableDeferred<Unit>()
        val projects = persistentListOf(summary("a", name = "Flat"))
        val breaking =
            flow<ImmutableList<ProjectSummary>> {
                emit(projects)
                failure.await()
                throw SQLiteException("disk I/O error")
            }
        show(ScriptedRepository(breaking, flow { emit(projects) }))
        compose.onNodeWithText("Flat").assertIsDisplayed()

        failure.complete(Unit)
        compose.waitForIdle()

        compose.onNodeWithText("Flat").assertDoesNotExist()
        compose.onNodeWithText("Try again").performClick()
        compose.onNodeWithText("Flat").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "de-w600dp-h240dp")
    fun `given a low window and font scale 2 then retry is reachable by scrolling`() {
        val repository = ScriptedRepository(storageFailure(), flow { emit(persistentListOf()) })
        show(repository, fontScale = 2f)

        compose
            .onNodeWithText("Erneut versuchen")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()

        assertEquals(2, repository.observed)
    }

    @Test
    @Config(qualifiers = "de-w600dp-h240dp")
    fun `given a low window and font scale 2 then the empty explanation scrolls and new project stays`() {
        show(ScriptedRepository(flow { emit(persistentListOf()) }), fontScale = 2f)

        compose
            .onNodeWithText("Miss einen Raum", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithText("Neues Projekt").assertIsDisplayed().performClick()

        assertEquals(1, newProjects)
    }
}
