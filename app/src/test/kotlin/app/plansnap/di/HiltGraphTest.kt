package app.plansnap.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import app.plansnap.core.common.result.DomainResult
import app.plansnap.core.common.units.Length
import app.plansnap.core.data.repository.RoomEditorDraftRepository
import app.plansnap.core.data.repository.RoomProjectRepository
import app.plansnap.core.data.repository.RoomProjectSaver
import app.plansnap.core.data.settings.DataStoreSettingsRepository
import app.plansnap.core.document.DocumentCodec
import app.plansnap.core.domain.command.CommandHistory
import app.plansnap.core.domain.command.DefaultProjectCommandFactory
import app.plansnap.core.domain.command.DockPlacementResolver
import app.plansnap.core.domain.command.ProjectCommandFactory
import app.plansnap.core.domain.evaluation.DefaultRoomEvaluator
import app.plansnap.core.domain.evaluation.RoomEvaluator
import app.plansnap.core.domain.placement.DefaultDockPlacementResolver
import app.plansnap.core.domain.report.AreaReportCalculator
import app.plansnap.core.domain.repository.EditorDraftRepository
import app.plansnap.core.domain.repository.ProjectRepository
import app.plansnap.core.domain.repository.ProjectSaver
import app.plansnap.core.domain.repository.SettingsRepository
import app.plansnap.core.domain.snap.Snapper
import app.plansnap.core.domain.validation.DefaultProjectValidator
import app.plansnap.core.domain.validation.OpeningRules
import app.plansnap.core.domain.validation.ProjectValidator
import app.plansnap.core.model.Project
import app.plansnap.core.model.ProjectId
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import javax.inject.Inject

/** Builds the production Hilt graph and injects every binding of it. */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class HiltGraphTest {
    @get:Rule
    val hilt = HiltAndroidRule(this)

    @Inject lateinit var projects: ProjectRepository

    @Inject lateinit var saver: ProjectSaver

    @Inject lateinit var drafts: EditorDraftRepository

    @Inject lateinit var settings: SettingsRepository

    @Inject lateinit var codec: DocumentCodec

    @Inject lateinit var evaluator: RoomEvaluator

    @Inject lateinit var openingRules: OpeningRules

    @Inject lateinit var validator: ProjectValidator

    @Inject lateinit var commandFactory: ProjectCommandFactory

    @Inject lateinit var resolver: DockPlacementResolver

    @Inject lateinit var snapper: Snapper

    @Inject lateinit var areaReport: AreaReportCalculator

    @Inject lateinit var historyFactory: @JvmSuppressWildcards (Project) -> CommandHistory

    @Inject lateinit var dataStore: DataStore<Preferences>

    @Inject @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    @Before
    fun inject() = hilt.inject()

    @Test
    fun repositoriesAndSaverAreTheRoomImplementations() {
        assertTrue(projects is RoomProjectRepository)
        assertTrue(saver is RoomProjectSaver)
        assertTrue(drafts is RoomEditorDraftRepository)
        assertTrue(settings is DataStoreSettingsRepository)
        assertNotNull(codec)
    }

    @Test
    fun domainServicesAreTheProductionImplementations() {
        assertTrue(evaluator is DefaultRoomEvaluator)
        assertTrue(validator is DefaultProjectValidator)
        assertTrue(resolver is DefaultDockPlacementResolver)
        assertTrue(commandFactory is DefaultProjectCommandFactory)
        assertNotNull(openingRules)
        assertNotNull(snapper)
        assertNotNull(areaReport)
    }

    @Test
    fun applicationScopeAndDataStoreAreSingletons() {
        assertTrue(applicationScope.coroutineContext[Job]!!.isActive)
        assertNotNull(dataStore)
    }

    @Test
    fun repositoryAndSettingsWorkAgainstTheRealStorage() =
        runBlocking {
            assertTrue(projects.observeProjects().first().isEmpty())
            val defaults = settings.settings.first()
            val result = settings.update { it.copy(gridEnabled = !it.gridEnabled) }
            assertTrue(result.toString(), result is DomainResult.Success)
            assertEquals(!defaults.gridEnabled, settings.settings.first().gridEnabled)
        }

    @Test
    fun historyFactoryCreatesIndependentHistoriesPerProject() {
        val project =
            Project(
                id = ProjectId("p"),
                name = "p",
                defaultHeight = Length(2_500),
                rooms = persistentListOf(),
                createdAtEpochMs = 0,
                updatedAtEpochMs = 0,
            )
        val first = historyFactory(project)
        val second = historyFactory(project)
        assertNotSame(first, second)
        assertSame(project, first.state.project)
        assertEquals(0, first.state.undoDepth)
    }
}
