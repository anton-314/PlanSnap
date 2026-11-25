package app.plansnap.navigation

import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.testing.TestNavHostController
import androidx.navigation.toRoute
import app.plansnap.core.designsystem.theme.PlanSnapTheme
import app.plansnap.HiltTestActivity
import app.plansnap.core.common.runtime.DispatcherProvider
import app.plansnap.core.common.runtime.EpochClock
import app.plansnap.core.common.runtime.IdGenerator
import app.plansnap.core.domain.repository.EditorDraftRepository
import app.plansnap.core.domain.repository.ProjectRepository
import app.plansnap.core.domain.repository.ProjectSaver
import app.plansnap.core.domain.repository.SettingsRepository
import app.plansnap.core.model.ProjectId
import app.plansnap.di.ApplicationScope
import app.plansnap.di.DataModule
import app.plansnap.di.RuntimeModule
import app.plansnap.testing.fixtures.fakes.FakeEditorDraftRepository
import app.plansnap.testing.fixtures.fakes.FakeProjectRepository
import app.plansnap.testing.fixtures.fakes.FakeProjectSaver
import app.plansnap.testing.fixtures.fakes.FakeSettingsRepository
import app.plansnap.testing.fixtures.runtime.FakeEpochClock
import app.plansnap.testing.fixtures.runtime.SequentialIdGenerator
import app.plansnap.testing.fixtures.runtime.TestDispatcherProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import javax.inject.Singleton
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Navigation graph hosted in a Hilt activity. Persistence and runtime services are replaced by the fakes of
 * `:testing:fixtures` and a deterministic dispatcher, so the screens' `hiltViewModel()` calls resolve without a
 * database or DataStore. The replacement is local to this class (HiltGraphTest keeps the production graph).
 */
@HiltAndroidTest
@UninstallModules(DataModule::class, RuntimeModule::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en", application = HiltTestApplication::class)
class NavigationTest {
    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<HiltTestActivity>()

    private lateinit var controller: TestNavHostController

    private fun show() {
        compose.setContent {
            controller = TestNavHostController(LocalContext.current)
            controller.navigatorProvider.addNavigator(ComposeNavigator())
            PlanSnapTheme { PlanSnapNavHost(navController = controller) }
        }
        compose.waitForIdle()
    }

    private fun backStackRoutes() =
        controller.currentBackStack.value
            .filter { it.destination.route != null }
            .map { entry ->
                val destination = entry.destination
                when {
                    destination.hasRoute<ProjectsRoute>() -> "projects"
                    destination.hasRoute<EditorRoute>() -> "editor:" + entry.toRoute<EditorRoute>().projectId
                    destination.hasRoute<ExportRoute>() -> "export:" + entry.toRoute<ExportRoute>().projectId
                    destination.hasRoute<SettingsRoute>() -> "settings"
                    else -> error("Unknown destination ${destination.route}")
                }
            }

    @Test
    fun startsOnTheProjectList() {
        show()
        assertTrue(controller.currentDestination!!.hasRoute<ProjectsRoute>())
        assertEquals(listOf("projects"), backStackRoutes())
        compose.onNodeWithText("Projects").assertExists()
    }

    @Test
    fun listOpensSettingsAndBackReturns() {
        show()
        compose.onNodeWithText("Settings").performClick()
        compose.waitForIdle()
        assertTrue(controller.currentDestination!!.hasRoute<SettingsRoute>())
        assertEquals(listOf("projects", "settings"), backStackRoutes())

        compose.onNodeWithContentDescription("Back").performClick()
        compose.waitForIdle()
        assertEquals(listOf("projects"), backStackRoutes())
    }

    @Test
    fun listCallbackOpensEditorWithProjectId() {
        show()
        compose.runOnUiThread { controller.navigateToEditor(ProjectId("p-1")) }
        compose.waitForIdle()
        assertEquals(listOf("projects", "editor:p-1"), backStackRoutes())
        compose.onNodeWithText("Enter the first wall").assertExists()
    }

    @Test
    fun editorOpensExportWithTheSameProjectId() {
        show()
        compose.runOnUiThread { controller.navigateToEditor(ProjectId("p-1")) }
        compose.waitForIdle()
        compose.onNodeWithText("Export").performClick()
        compose.waitForIdle()
        assertEquals(listOf("projects", "editor:p-1", "export:p-1"), backStackRoutes())
        compose.onNodeWithText("No room yet").assertExists()
    }

    @Test
    fun backFromExportReturnsToTheEditorAndThenTheList() {
        show()
        compose.runOnUiThread { controller.navigateToEditor(ProjectId("p-1")) }
        compose.waitForIdle()
        compose.onNodeWithText("Export").performClick()
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Back").performClick()
        compose.waitForIdle()
        assertEquals(listOf("projects", "editor:p-1"), backStackRoutes())

        compose.onNodeWithContentDescription("Back").performClick()
        compose.waitForIdle()
        assertEquals(listOf("projects"), backStackRoutes())
    }

    /** Fakes instead of Room and DataStore. */
    @Module
    @InstallIn(SingletonComponent::class)
    object FakeDataModule {
        @Provides
        @Singleton
        fun fakeProjectRepository(): FakeProjectRepository = FakeProjectRepository()

        @Provides
        @Singleton
        fun projectRepository(fake: FakeProjectRepository): ProjectRepository = fake

        @Provides
        @Singleton
        fun projectSaver(
            fake: FakeProjectRepository,
            clock: EpochClock,
        ): ProjectSaver = FakeProjectSaver(fake, clock)

        @Provides
        @Singleton
        fun editorDraftRepository(): EditorDraftRepository = FakeEditorDraftRepository()

        @Provides
        @Singleton
        fun settingsRepository(): SettingsRepository = FakeSettingsRepository()
    }

    /** Deterministic runtime: unconfined test dispatcher, fixed clock, sequential ids. */
    @Module
    @InstallIn(SingletonComponent::class)
    object FakeRuntimeModule {
        @OptIn(ExperimentalCoroutinesApi::class)
        @Provides
        @Singleton
        fun dispatchers(): DispatcherProvider = TestDispatcherProvider(UnconfinedTestDispatcher())

        @Provides
        @Singleton
        fun clock(): EpochClock = FakeEpochClock()

        @Provides
        @Singleton
        fun ids(): IdGenerator = SequentialIdGenerator("id")

        @Provides
        @Singleton
        @ApplicationScope
        fun applicationScope(dispatchers: DispatcherProvider): CoroutineScope =
            CoroutineScope(SupervisorJob() + dispatchers.default)
    }
}
