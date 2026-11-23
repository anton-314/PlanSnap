package app.plansnap.testing.fixtures.fakes

import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.result.DomainResult
import app.plansnap.core.common.result.EntityKind
import app.plansnap.core.common.result.StorageProblem
import app.plansnap.core.common.result.errorOrNull
import app.plansnap.core.common.result.getOrNull
import app.plansnap.core.common.units.Length
import app.plansnap.core.common.units.LengthUnit
import app.plansnap.core.domain.repository.AppSettings
import app.plansnap.core.domain.repository.EditorDraft
import app.plansnap.core.domain.repository.EditorHint
import app.plansnap.core.model.ProjectId
import app.plansnap.core.model.RoomId
import app.plansnap.testing.fixtures.generators.ProjectGenerator
import app.plansnap.testing.fixtures.runtime.FakeEpochClock
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class FakeProjectSaverTest {
    private val clock = FakeEpochClock(startEpochMs = 50_000L)
    private val repository = FakeProjectRepository(clock = clock)
    private val saver = FakeProjectSaver(repository, clock)
    private val project = ProjectGenerator(seed = 4L).project(roomCount = 3)

    @Test
    fun `given stored project when saved then updatedAt comes from the clock and is returned`() =
        runTest {
            repository.createProject(project)

            val result = saver.save(project.copy(name = "Edited"), emptySet())

            assertEquals(DomainResult.Success(50_000L), result)
            val stored = repository.getProject(project.id).getOrNull()!!
            assertEquals(50_000L, stored.updatedAtEpochMs)
            assertEquals("Edited", stored.name)
            assertEquals(project.createdAtEpochMs, stored.createdAtEpochMs)
        }

    @Test
    fun `given changed room when saved then only that room is written and others keep their stored version`() =
        runTest {
            repository.createProject(project)
            val changed = project.rooms[0].copy(name = "Changed")
            val unreportedChange = project.rooms[1].copy(name = "Not reported")

            val submitted = project.copy(rooms = persistentListOf(changed, unreportedChange, project.rooms[2]))
            saver.save(submitted, setOf(changed.id))

            val stored = repository.getProject(project.id).getOrNull()!!
            assertEquals("Changed", stored.rooms[0].name)
            assertEquals(project.rooms[1].name, stored.rooms[1].name)
        }

    @Test
    fun `given room removed from the project when its id is reported then the stored room is deleted`() =
        runTest {
            repository.createProject(project)
            val remaining = project.rooms.drop(1)

            saver.save(project.copy(rooms = persistentListOf(*remaining.toTypedArray())), setOf(project.rooms[0].id))

            assertEquals(
                remaining.map { it.id },
                repository
                    .getProject(project.id)
                    .getOrNull()!!
                    .rooms
                    .map { it.id },
            )
        }

    @Test
    fun `given new room reported as changed when saved then it is added in project order`() =
        runTest {
            repository.createProject(project.copy(rooms = persistentListOf(project.rooms[0])))

            saver.save(project, setOf(project.rooms[1].id, project.rooms[2].id))

            val storedIds =
                repository
                    .getProject(project.id)
                    .getOrNull()!!
                    .rooms
                    .map { it.id }
            assertEquals(project.rooms.map { it.id }, storedIds)
        }

    @Test
    fun `given middle room deleted and restored by undo when saved then stored order follows the project`() =
        runTest {
            repository.createProject(project)
            val (a, b, c) = project.rooms

            saver.save(project.copy(rooms = persistentListOf(a, c)), setOf(b.id))
            assertEquals(listOf(a.id, c.id), storedRoomIds())

            saver.save(project, setOf(b.id))

            assertEquals(listOf(a.id, b.id, c.id), storedRoomIds())
        }

    @Test
    fun `given rooms reordered when saved without changes then stored order follows the project`() =
        runTest {
            repository.createProject(project)
            val (a, b, c) = project.rooms

            saver.save(project.copy(rooms = persistentListOf(c, a, b)), emptySet())

            assertEquals(listOf(c.id, a.id, b.id), storedRoomIds())
        }

    @Test
    fun `given stored room missing and unreported when saved then it stays after the project rooms`() =
        runTest {
            repository.createProject(project)
            val (a, b, c) = project.rooms

            saver.save(project.copy(rooms = persistentListOf(c, a)), emptySet())

            assertEquals(listOf(c.id, a.id, b.id), storedRoomIds())
        }

    private suspend fun storedRoomIds(): List<RoomId> =
        repository
            .getProject(project.id)
            .getOrNull()!!
            .rooms
            .map { it.id }

    @Test
    fun `given new room not reported as changed when saved then it is not stored`() =
        runTest {
            repository.createProject(project.copy(rooms = persistentListOf(project.rooms[0])))

            saver.save(project, setOf(project.rooms[1].id))

            val storedIds =
                repository
                    .getProject(project.id)
                    .getOrNull()!!
                    .rooms
                    .map { it.id }
            assertEquals(listOf(project.rooms[0].id, project.rooms[1].id), storedIds)
        }

    @Test
    fun `given room removed from the project when its id is not reported then it stays stored`() =
        runTest {
            repository.createProject(project)
            val remaining = project.rooms.drop(1)

            saver.save(project.copy(rooms = persistentListOf(*remaining.toTypedArray())), emptySet())

            val stored = repository.getProject(project.id).getOrNull()!!
            assertEquals(remaining + project.rooms.first(), stored.rooms)
        }

    @Test
    fun `given reported change, addition and removal together when saved then exactly those are applied`() =
        runTest {
            val stored = project.copy(rooms = persistentListOf(project.rooms[0], project.rooms[1]))
            repository.createProject(stored)
            val changed = project.rooms[0].copy(name = "Changed")
            val submitted = project.copy(rooms = persistentListOf(changed, project.rooms[2]))

            saver.save(submitted, setOf(changed.id, project.rooms[1].id, project.rooms[2].id))

            val rooms = repository.getProject(project.id).getOrNull()!!.rooms
            assertEquals(listOf(changed, project.rooms[2]), rooms)
        }

    @Test
    fun `given reported id that is neither submitted nor stored when saved then nothing changes`() =
        runTest {
            repository.createProject(project)

            saver.save(project, setOf(RoomId("ghost")))

            assertEquals(project.rooms, repository.getProject(project.id).getOrNull()!!.rooms)
        }

    @Test
    fun `given clock advanced between saves then each save returns the current time`() =
        runTest {
            repository.createProject(project)

            val first = saver.save(project, emptySet()).getOrNull()
            clock.advanceBy(1_000L)
            val second = saver.save(project, emptySet()).getOrNull()

            assertEquals(50_000L, first)
            assertEquals(51_000L, second)
        }

    @Test
    fun `given unknown project when saved then NotFound is returned`() =
        runTest {
            val result = saver.save(project, setOf(project.rooms[0].id))

            assertEquals(DomainError.NotFound(EntityKind.PROJECT, project.id.value), result.errorOrNull())
            assertTrue(repository.projects.isEmpty())
        }

    @Test
    fun `given injected failure when saved then the store stays untouched and the next save works`() =
        runTest {
            repository.createProject(project)
            val failure = DomainError.Storage(StorageProblem.NO_SPACE)
            saver.failures.failNext(FakeProjectSaver.Operation.SAVE, failure)

            assertEquals(failure, saver.save(project.copy(name = "Lost"), emptySet()).errorOrNull())
            assertEquals(project, repository.getProject(project.id).getOrNull())
            assertTrue(saver.savedRoomSets.isEmpty())

            assertEquals(DomainResult.Success(50_000L), saver.save(project, setOf(project.rooms[0].id)))
            assertEquals(listOf(setOf(project.rooms[0].id)), saver.savedRoomSets)
        }

    @Test
    fun `given saved project when observed then the summary shows the saved time and room count`() =
        runTest {
            repository.createProject(project)

            val reduced = project.copy(rooms = persistentListOf(project.rooms[0]))
            saver.save(reduced, setOf(project.rooms[1].id, project.rooms[2].id))

            val summary = repository.observeProjects().first().single()
            assertEquals(1, summary.roomCount)
            assertEquals(50_000L, summary.updatedAtEpochMs)
        }
}

class FakeEditorDraftRepositoryTest {
    private val repository = FakeEditorDraftRepository()
    private val projectId = ProjectId("p1")
    private val draft = EditorDraft(projectId, RoomId("r1"), "3000")

    @Test
    fun `given no draft when loaded then success with null is returned`() =
        runTest {
            assertEquals(DomainResult.Success(null), repository.getDraft(projectId))
        }

    @Test
    fun `given saved draft when loaded then it is returned exactly as typed`() =
        runTest {
            val typed = draft.copy(inputText = " 30,5 x")

            repository.saveDraft(typed)

            assertEquals(typed, repository.getDraft(projectId).getOrNull())
        }

    @Test
    fun `given second save when loaded then the draft of the project is replaced`() =
        runTest {
            repository.saveDraft(draft)
            repository.saveDraft(draft.copy(roomId = null, inputText = "45"))

            assertEquals(EditorDraft(projectId, null, "45"), repository.getDraft(projectId).getOrNull())
            assertEquals(1, repository.storedDrafts.size)
        }

    @Test
    fun `given drafts of two projects when one is cleared then the other stays`() =
        runTest {
            val other = EditorDraft(ProjectId("p2"), null, "1")
            repository.saveDraft(draft)
            repository.saveDraft(other)

            assertEquals(DomainResult.Success(Unit), repository.clearDraft(projectId))

            assertNull(repository.getDraft(projectId).getOrNull())
            assertEquals(other, repository.getDraft(other.projectId).getOrNull())
        }

    @Test
    fun `given no draft when cleared then it still succeeds`() =
        runTest {
            assertEquals(DomainResult.Success(Unit), repository.clearDraft(projectId))
        }

    @Test
    fun `given injected failures when operations run then each reports its failure and changes nothing`() =
        runTest {
            repository.saveDraft(draft)
            val failure = DomainError.Storage(StorageProblem.WRITE_FAILED)
            FakeEditorDraftRepository.Operation.entries.forEach { repository.failures.failNext(it, failure) }

            assertEquals(failure, repository.getDraft(projectId).errorOrNull())
            assertEquals(failure, repository.saveDraft(draft.copy(inputText = "new")).errorOrNull())
            assertEquals(failure, repository.clearDraft(projectId).errorOrNull())

            assertEquals(draft, repository.getDraft(projectId).getOrNull())
        }
}

class FakeSettingsRepositoryTest {
    @Test
    fun `given new repository when observed then the defaults are emitted`() =
        runTest {
            assertEquals(AppSettings(), FakeSettingsRepository().settings.first())
        }

    @Test
    fun `given update when applied then the new settings are emitted and readable`() =
        runTest {
            val repository = FakeSettingsRepository()

            val result =
                repository.update {
                    it.copy(lengthUnit = LengthUnit.MILLIMETER, dismissedHints = persistentSetOf(EditorHint.NUMPAD))
                }

            assertEquals(DomainResult.Success(Unit), result)
            val emitted = repository.settings.first()
            assertEquals(LengthUnit.MILLIMETER, emitted.lengthUnit)
            assertEquals(setOf(EditorHint.NUMPAD), emitted.dismissedHints)
            assertEquals(emitted, repository.current)
        }

    @Test
    fun `given sequential updates when applied then each sees the result of the previous one`() =
        runTest {
            val repository = FakeSettingsRepository()

            repository.update { it.copy(gridEnabled = false) }
            repository.update { it.copy(snapEnabled = !it.gridEnabled) }

            assertEquals(false, repository.current.gridEnabled)
            assertEquals(true, repository.current.snapEnabled)
        }

    @Test
    fun `given injected failure when updated then the transform does not run and the settings stay`() =
        runTest {
            val repository = FakeSettingsRepository()
            val failure = DomainError.Storage(StorageProblem.WRITE_FAILED)
            repository.failures.failNext(FakeSettingsRepository.Operation.UPDATE, failure)
            var transformRan = false

            val result =
                repository.update {
                    transformRan = true
                    it.copy(gridEnabled = false)
                }

            assertEquals(failure, result.errorOrNull())
            assertTrue(!transformRan)
            assertEquals(AppSettings(), repository.current)
        }

    @Test
    fun `given transform that breaks an invariant when updated then the exception propagates`() =
        runTest {
            val repository = FakeSettingsRepository()

            assertThrows<IllegalArgumentException> { repository.update { it.copy(lastThickness = Length(-1L)) } }

            assertEquals(AppSettings(), repository.current)
        }

    @Test
    fun `given initial settings when constructed then they are the starting value`() =
        runTest {
            val initial = AppSettings(gridEnabled = false)

            assertEquals(initial, FakeSettingsRepository(initial).settings.first())
        }
}

class FakeProjectRepositoryDraftTest {
    private val drafts = FakeEditorDraftRepository()
    private val repository = FakeProjectRepository(drafts = drafts)
    private val project = ProjectGenerator(seed = 5L).project(roomCount = 1)
    private val other = ProjectGenerator(seed = 6L).project(roomCount = 1).copy(id = ProjectId("other"))
    private val draft = EditorDraft(project.id, null, "12")
    private val otherDraft = EditorDraft(other.id, null, "34")

    private suspend fun givenStoredWithDrafts() {
        repository.createProject(project)
        repository.createProject(other)
        drafts.saveDraft(draft)
        drafts.saveDraft(otherDraft)
    }

    @Test
    fun `given project with draft when deleted then the draft is removed and other drafts stay`() =
        runTest {
            givenStoredWithDrafts()

            assertEquals(DomainResult.Success(Unit), repository.deleteProject(project.id))

            assertNull(drafts.getDraft(project.id).getOrNull())
            assertEquals(otherDraft, drafts.getDraft(other.id).getOrNull())
        }

    @Test
    fun `given draft removal fails when project is deleted then project and draft stay unchanged`() =
        runTest {
            givenStoredWithDrafts()
            val failure = DomainError.Storage(StorageProblem.WRITE_FAILED)
            drafts.failures.failNext(FakeEditorDraftRepository.Operation.CLEAR, failure)

            assertEquals(failure, repository.deleteProject(project.id).errorOrNull())

            assertEquals(project, repository.getProject(project.id).getOrNull())
            assertEquals(draft, drafts.getDraft(project.id).getOrNull())
            assertEquals(DomainResult.Success(Unit), repository.deleteProject(project.id))
            assertNull(drafts.getDraft(project.id).getOrNull())
        }

    @Test
    fun `given project deletion fails when deleted then the draft stays`() =
        runTest {
            givenStoredWithDrafts()
            val failure = DomainError.Storage(StorageProblem.WRITE_FAILED)
            repository.failures.failNext(FakeProjectRepository.Operation.DELETE, failure)

            assertEquals(failure, repository.deleteProject(project.id).errorOrNull())

            assertEquals(project, repository.getProject(project.id).getOrNull())
            assertEquals(draft, drafts.getDraft(project.id).getOrNull())
        }

    @Test
    fun `given unknown project when deleted then NotFound is returned and drafts are untouched`() =
        runTest {
            drafts.saveDraft(draft)

            val result = repository.deleteProject(project.id)

            assertEquals(DomainError.NotFound(EntityKind.PROJECT, project.id.value), result.errorOrNull())
            assertEquals(draft, drafts.getDraft(project.id).getOrNull())
        }
}
