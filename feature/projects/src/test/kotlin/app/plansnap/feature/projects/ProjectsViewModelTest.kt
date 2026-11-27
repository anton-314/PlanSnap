package app.plansnap.feature.projects

import app.cash.turbine.test
import app.plansnap.core.common.result.DocumentProblem
import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.result.DomainResult
import app.plansnap.core.common.result.DomainRule
import app.plansnap.core.common.result.EntityKind
import app.plansnap.core.common.result.InvalidValueReason
import app.plansnap.core.common.result.StorageProblem
import app.plansnap.core.common.result.ValueField
import app.plansnap.core.common.runtime.DispatcherProvider
import app.plansnap.core.common.runtime.EpochClock
import app.plansnap.core.common.runtime.IdGenerator
import app.plansnap.core.common.units.Length
import app.plansnap.core.model.ProjectId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream

@OptIn(ExperimentalCoroutinesApi::class)
class ProjectsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeProjectRepository()

    private class TestDispatchers(
        dispatcher: CoroutineDispatcher,
    ) : DispatcherProvider {
        override val main = dispatcher
        override val default = dispatcher
        override val io = dispatcher
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() =
        ProjectsViewModel(
            repository = repository,
            dispatchers = TestDispatchers(dispatcher),
            ids = IdGenerator { "new-id" },
            clock = EpochClock { 42L },
        )

    private fun TestScope.loaded(): ProjectsViewModel = viewModel().also { advanceUntilIdle() }

    private fun TestScope.idsOf(viewModel: ProjectsViewModel) =
        (viewModel.state.value.list as ProjectListState.Loaded).projects.map { it.id.value }

    @Test
    fun `given a new view model when the list has not arrived then it is loading and then loaded newest first`() =
        runTest(dispatcher) {
            repository.emit(listOf(summary("old", updated = 1), summary("new", updated = 9)))
            val viewModel = viewModel()
            viewModel.state.test {
                assertEquals(ProjectListState.Loading, awaitItem().list)
                advanceUntilIdle()
                assertEquals(listOf("new", "old"), idsOf(viewModel))
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `given no projects when loaded then the list is empty`() =
        runTest(dispatcher) {
            val viewModel = loaded()
            assertTrue((viewModel.state.value.list as ProjectListState.Loaded).projects.isEmpty())
        }

    @Test
    fun `given a later change when the repository emits then the list follows`() =
        runTest(dispatcher) {
            val viewModel = loaded()
            repository.emit(listOf(summary("a")))
            advanceUntilIdle()
            assertEquals(listOf("a"), idsOf(viewModel))
        }

    @Test
    fun `given observeProjects fails when loading then the list is failed and retry recovers`() =
        runTest(dispatcher) {
            repository.observeFailure = IllegalStateException("db")
            repository.emit(listOf(summary("a")))
            val viewModel = loaded()
            assertEquals(ProjectListState.Failed(ProjectsError.READ_FAILED), viewModel.state.value.list)
            viewModel.onRetry()
            assertEquals(ProjectListState.Loading, viewModel.state.value.list)
            advanceUntilIdle()
            assertEquals(listOf("a"), idsOf(viewModel))
        }

    @Test
    fun `given the create dialog when a name is confirmed then a project is stored and opened`() =
        runTest(dispatcher) {
            val viewModel = loaded()
            viewModel.events.test {
                viewModel.onNewProject()
                assertEquals(ProjectsDialog.Create(""), viewModel.state.value.dialog)
                viewModel.onNameChanged("  Flat  ")
                viewModel.onDialogConfirmed()
                assertEquals(ProjectsDialog.None, viewModel.state.value.dialog)
                advanceUntilIdle()
                assertEquals(ProjectsEvent.OpenProject(ProjectId("new-id")), awaitItem())
            }
            val project = repository.created.single()
            assertEquals("Flat", project.name)
            assertEquals(Length(2_500L), project.defaultHeight)
            assertEquals(42L, project.createdAtEpochMs)
            // docs/UX.md section 3 step 1: a new project starts with its first room.
            val room = project.rooms.single()
            assertTrue(room.walls.isEmpty())
            assertFalse(room.isClosed)
        }

    @Test
    fun `given the create dialog when the name is blank then confirming does nothing`() =
        runTest(dispatcher) {
            val viewModel = loaded()
            viewModel.onNewProject()
            viewModel.onNameChanged("   ")
            viewModel.onDialogConfirmed()
            advanceUntilIdle()
            assertTrue(repository.created.isEmpty())
            assertEquals(ProjectsDialog.Create("   "), viewModel.state.value.dialog)
        }

    @Test
    fun `given the create dialog when it is dismissed then nothing is stored`() =
        runTest(dispatcher) {
            val viewModel = loaded()
            viewModel.onNewProject()
            viewModel.onDialogDismissed()
            advanceUntilIdle()
            assertEquals(ProjectsDialog.None, viewModel.state.value.dialog)
            assertTrue(repository.created.isEmpty())
        }

    @Test
    fun `given storing fails when a project is created then the error is shown and it is not opened`() =
        runTest(dispatcher) {
            repository.failure = DomainError.Storage(StorageProblem.NO_SPACE)
            val viewModel = loaded()
            viewModel.events.test {
                viewModel.onNewProject()
                viewModel.onNameChanged("Flat")
                viewModel.onDialogConfirmed()
                advanceUntilIdle()
                expectNoEvents()
            }
            assertEquals(ProjectsError.NO_SPACE, viewModel.state.value.error)
        }

    @Test
    fun `given a project when rename is requested then the dialog starts with its name and confirming renames`() =
        runTest(dispatcher) {
            repository.emit(listOf(summary("a", name = "Old")))
            val viewModel = loaded()
            viewModel.onRenameRequested(ProjectId("a"))
            assertEquals(ProjectsDialog.Rename(ProjectId("a"), "Old"), viewModel.state.value.dialog)
            viewModel.onNameChanged(" New ")
            viewModel.onDialogConfirmed()
            advanceUntilIdle()
            assertEquals(listOf(ProjectId("a") to "New"), repository.renamed)
            assertEquals(ProjectsDialog.None, viewModel.state.value.dialog)
        }

    @Test
    fun `given an unknown project when rename or delete is requested then no dialog opens`() =
        runTest(dispatcher) {
            val viewModel = loaded()
            viewModel.onRenameRequested(ProjectId("missing"))
            viewModel.onDeleteRequested(ProjectId("missing"))
            assertEquals(ProjectsDialog.None, viewModel.state.value.dialog)
        }

    @Test
    fun `given a rejected name when renaming then the error is shown`() =
        runTest(dispatcher) {
            repository.emit(listOf(summary("a")))
            repository.failure = DomainError.InvalidValue(ValueField.NAME, InvalidValueReason.TOO_LARGE)
            val viewModel = loaded()
            viewModel.onRenameRequested(ProjectId("a"))
            viewModel.onNameChanged("x")
            viewModel.onDialogConfirmed()
            advanceUntilIdle()
            assertEquals(ProjectsError.INVALID_NAME, viewModel.state.value.error)
        }

    @Test
    fun `given a delete request when it is cancelled then the project stays`() =
        runTest(dispatcher) {
            repository.emit(listOf(summary("a", name = "Flat")))
            val viewModel = loaded()
            viewModel.onDeleteRequested(ProjectId("a"))
            assertEquals(ProjectsDialog.ConfirmDelete(ProjectId("a"), "Flat"), viewModel.state.value.dialog)
            viewModel.onDialogDismissed()
            advanceUntilIdle()
            assertTrue(repository.deleted.isEmpty())
        }

    @Test
    fun `given a delete request when it is confirmed then the project is deleted`() =
        runTest(dispatcher) {
            repository.emit(listOf(summary("a")))
            val viewModel = loaded()
            viewModel.onDeleteRequested(ProjectId("a"))
            viewModel.onDialogConfirmed()
            advanceUntilIdle()
            assertEquals(listOf(ProjectId("a")), repository.deleted)
            assertEquals(ProjectsDialog.None, viewModel.state.value.dialog)
        }

    @Test
    fun `given a project when it is duplicated then the repository copies it`() =
        runTest(dispatcher) {
            val viewModel = loaded()
            viewModel.onDuplicate(ProjectId("a"))
            advanceUntilIdle()
            assertEquals(listOf(ProjectId("a")), repository.duplicated)
            assertNull(viewModel.state.value.error)
        }

    @Test
    fun `given the project vanished when it is duplicated then the error is shown and can be cleared`() =
        runTest(dispatcher) {
            repository.failure = DomainError.NotFound(EntityKind.PROJECT, "a")
            val viewModel = loaded()
            viewModel.onDuplicate(ProjectId("a"))
            advanceUntilIdle()
            assertEquals(ProjectsError.NOT_FOUND, viewModel.state.value.error)
            viewModel.onErrorShown()
            assertNull(viewModel.state.value.error)
        }

    @Test
    fun `given a file when it is imported then the bytes are read and the stream is closed`() =
        runTest(dispatcher) {
            val viewModel = loaded()
            val stream = ClosableStream("{}".toByteArray())
            viewModel.onImport { stream }
            assertTrue(viewModel.state.value.isImporting)
            advanceUntilIdle()
            assertFalse(viewModel.state.value.isImporting)
            assertNull(viewModel.state.value.error)
            assertEquals("{}", String(repository.importedBytes.single()))
            assertTrue(stream.closed)
        }

    @Test
    fun `given an invalid document when it is imported then the stream is closed and the error is mapped`() =
        runTest(dispatcher) {
            val cases =
                mapOf(
                    DocumentProblem.MALFORMED to ProjectsError.IMPORT_MALFORMED,
                    DocumentProblem.UNSUPPORTED_VERSION to ProjectsError.IMPORT_UNSUPPORTED_VERSION,
                    DocumentProblem.TOO_LARGE to ProjectsError.IMPORT_TOO_LARGE,
                    DocumentProblem.INVALID_VALUES to ProjectsError.IMPORT_INVALID_VALUES,
                )
            cases.forEach { (problem, expected) ->
                repository.importResult = DomainResult.Failure(DomainError.Document(problem))
                val viewModel = loaded()
                val stream = ClosableStream(ByteArray(0))
                viewModel.onImport { stream }
                advanceUntilIdle()
                assertEquals(expected, viewModel.state.value.error)
                assertTrue(stream.closed)
            }
        }

    @Test
    fun `given the validator rejects values when importing then the file is reported as invalid`() =
        runTest(dispatcher) {
            repository.importResult =
                DomainResult.Failure(DomainError.InvalidValue(ValueField.WALL_LENGTH, InvalidValueReason.TOO_SMALL))
            val viewModel = loaded()
            viewModel.onImport { ClosableStream(ByteArray(0)) }
            advanceUntilIdle()
            assertEquals(ProjectsError.IMPORT_INVALID_VALUES, viewModel.state.value.error)
        }

    @Test
    fun `given the file cannot be opened when importing then a read error is shown`() =
        runTest(dispatcher) {
            val viewModel = loaded()
            viewModel.onImport { null }
            advanceUntilIdle()
            assertEquals(ProjectsError.READ_FAILED, viewModel.state.value.error)
            assertFalse(viewModel.state.value.isImporting)
            viewModel.onErrorShown()
            viewModel.onImport { throw IOException("gone") }
            advanceUntilIdle()
            assertEquals(ProjectsError.READ_FAILED, viewModel.state.value.error)
        }

    @Test
    fun `given an import is running when another starts then it is ignored`() =
        runTest(dispatcher) {
            val viewModel = loaded()
            viewModel.onImport { ClosableStream(ByteArray(0)) }
            viewModel.onImport { ClosableStream(ByteArray(0)) }
            advanceUntilIdle()
            assertEquals(1, repository.importedBytes.size)
        }

    @Test
    fun `given any domain error when mapped then the user sees a matching category`() {
        assertEquals(
            ProjectsError.WRITE_FAILED,
            DomainError.Storage(StorageProblem.WRITE_FAILED).toProjectsError(),
        )
        assertEquals(ProjectsError.READ_FAILED, DomainError.Storage(StorageProblem.READ_FAILED).toProjectsError())
        assertEquals(ProjectsError.UNKNOWN, DomainError.RuleViolation(DomainRule.DUPLICATE_ID).toProjectsError())
        assertEquals(
            ProjectsError.IMPORT_INVALID_VALUES,
            DomainError.RuleViolation(DomainRule.DUPLICATE_ID).toProjectsError(duringImport = true),
        )
    }

    private class ClosableStream(
        bytes: ByteArray,
    ) : InputStream() {
        private val delegate = ByteArrayInputStream(bytes)
        var closed = false
            private set

        override fun read(): Int = delegate.read()

        override fun close() {
            closed = true
        }
    }
}
