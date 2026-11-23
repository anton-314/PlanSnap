package app.plansnap.testing.fixtures.fakes

import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.result.DomainResult
import app.plansnap.core.common.result.DomainRule
import app.plansnap.core.common.result.EntityKind
import app.plansnap.core.common.result.StorageProblem
import app.plansnap.core.common.result.errorOrNull
import app.plansnap.core.common.result.getOrNull
import app.plansnap.core.common.units.Length
import app.plansnap.core.model.DockLink
import app.plansnap.core.model.DoorSwing
import app.plansnap.core.model.Opening
import app.plansnap.core.model.OpeningId
import app.plansnap.core.model.OpeningType
import app.plansnap.core.model.Project
import app.plansnap.core.model.ProjectId
import app.plansnap.core.model.Room
import app.plansnap.core.model.RoomPlacement
import app.plansnap.testing.fixtures.generators.ProjectGenerator
import app.plansnap.testing.fixtures.rooms.ReferenceRooms
import app.plansnap.testing.fixtures.runtime.FakeEpochClock
import app.plansnap.testing.fixtures.runtime.SequentialIdGenerator
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class FakeProjectRepositoryTest {
    private val clock = FakeEpochClock(startEpochMs = 9_000L)
    private val repository = FakeProjectRepository(SequentialIdGenerator("copy"), clock)
    private val project = ProjectGenerator(seed = 1L).project(roomCount = 2)
    private val storageFailure = DomainError.Storage(StorageProblem.READ_FAILED)

    private fun notFound(id: ProjectId) = DomainError.NotFound(EntityKind.PROJECT, id.value)

    @Test
    fun `given created project when loaded then the same project is returned`() =
        runTest {
            assertEquals(DomainResult.Success(Unit), repository.createProject(project))

            assertEquals(project, repository.getProject(project.id).getOrNull())
        }

    @Test
    fun `given unknown id when loaded then NotFound project with the raw id is returned`() =
        runTest {
            assertEquals(notFound(ProjectId("nope")), repository.getProject(ProjectId("nope")).errorOrNull())
        }

    @Test
    fun `given existing id when created again then DUPLICATE_ID is returned and the stored project is unchanged`() =
        runTest {
            repository.createProject(project)

            val result = repository.createProject(project.copy(name = "other"))

            assertEquals(DomainError.RuleViolation(DomainRule.DUPLICATE_ID), result.errorOrNull())
            assertEquals(project, repository.getProject(project.id).getOrNull())
        }

    @Test
    fun `given project when renamed then only the name changes`() =
        runTest {
            repository.createProject(project)

            assertEquals(DomainResult.Success(Unit), repository.renameProject(project.id, "New name"))

            assertEquals(project.copy(name = "New name"), repository.getProject(project.id).getOrNull())
        }

    @Test
    fun `given unknown id when renamed or deleted then NotFound is returned`() =
        runTest {
            val id = ProjectId("nope")

            assertEquals(notFound(id), repository.renameProject(id, "x").errorOrNull())
            assertEquals(notFound(id), repository.deleteProject(id).errorOrNull())
        }

    @Test
    fun `given project when deleted then it is gone and a second delete fails`() =
        runTest {
            repository.createProject(project)

            assertEquals(DomainResult.Success(Unit), repository.deleteProject(project.id))

            assertEquals(notFound(project.id), repository.getProject(project.id).errorOrNull())
            assertEquals(notFound(project.id), repository.deleteProject(project.id).errorOrNull())
        }

    @Test
    fun `given projects when observed then the list follows every change with room counts`() =
        runTest {
            assertEquals(emptyList<Any>(), repository.observeProjects().first())

            repository.createProject(project)
            val summary = repository.observeProjects().first().single()
            assertEquals(project.id, summary.id)
            assertEquals(2, summary.roomCount)
            assertEquals(project.updatedAtEpochMs, summary.updatedAtEpochMs)

            repository.renameProject(project.id, "Renamed")
            assertEquals(
                "Renamed",
                repository
                    .observeProjects()
                    .first()
                    .single()
                    .name,
            )

            repository.deleteProject(project.id)
            assertEquals(emptyList<Any>(), repository.observeProjects().first())
        }

    @Test
    fun `given duplicate when requested then ids are new, content is equal and the original is untouched`() =
        runTest {
            repository.createProject(project)

            val copyId = repository.duplicateProject(project.id).getOrNull()!!
            val copy = repository.getProject(copyId).getOrNull()!!

            assertNotEquals(project.id, copyId)
            assertEquals(2, repository.projects.size)
            assertEquals(project, repository.getProject(project.id).getOrNull())
            assertEquals(project.rooms.size, copy.rooms.size)
            assertTrue(
                copy.rooms
                    .map { it.id }
                    .intersect(project.rooms.map { it.id }.toSet())
                    .isEmpty(),
            )
            copy.rooms.zip(project.rooms).forEach { (new, old) ->
                assertEquals(old.walls.map { it.length }, new.walls.map { it.length })
                assertEquals(old.corners, new.corners)
                assertTrue(
                    new.walls
                        .map { it.id }
                        .intersect(old.walls.map { it.id }.toSet())
                        .isEmpty(),
                )
            }
            assertEquals(9_000L, copy.createdAtEpochMs)
            assertEquals(9_000L, copy.updatedAtEpochMs)
        }

    @Test
    fun `given duplicate of docked rooms with openings then all internal references point to the new ids`() =
        runTest {
            val docked = dockedProject()
            repository.createProject(docked)

            val copy = repository.getProject(repository.duplicateProject(docked.id).getOrNull()!!).getOrNull()!!

            val host = copy.rooms[0]
            val guest = copy.rooms[1]
            val link = guest.placement.dock!!
            assertEquals(host.id, link.hostRoomId)
            assertEquals(host.walls[1].id, link.hostWallId)
            assertEquals(guest.walls[3].id, link.ownWallId)
            assertEquals(host.openings.single().id, link.hostOpeningId)
            assertEquals(host.walls[1].id, host.openings.single().wallId)
            assertEquals(Length(50L), link.hostOffset)
            assertNotEquals(
                docked.rooms[0]
                    .openings
                    .single()
                    .id,
                host.openings.single().id,
            )
            assertEquals(
                docked.rooms[0]
                    .openings
                    .single()
                    .copy(id = host.openings.single().id, wallId = host.walls[1].id),
                host.openings.single(),
            )
        }

    @Test
    fun `given unknown id when duplicated then NotFound is returned`() =
        runTest {
            assertEquals(notFound(ProjectId("nope")), repository.duplicateProject(ProjectId("nope")).errorOrNull())
        }

    @Test
    fun `given import without collision when imported then ids are kept`() =
        runTest {
            val result = repository.importProject(project)

            assertEquals(project.id, result.getOrNull())
            assertEquals(project, repository.getProject(project.id).getOrNull())
        }

    @Test
    fun `given import with an existing project id when imported then a copy with new ids is stored`() =
        runTest {
            repository.createProject(project)

            val importedId = repository.importProject(project).getOrNull()!!

            assertNotEquals(project.id, importedId)
            assertEquals(2, repository.projects.size)
            assertEquals(project, repository.getProject(project.id).getOrNull())
        }

    @Test
    fun `given import with an existing room id in another project when imported then a copy is stored`() =
        runTest {
            repository.createProject(project)
            val foreign = ProjectGenerator(seed = 2L).project(roomCount = 1)
            val clashing = foreign.copy(rooms = persistentListOf(foreign.rooms[0].copy(id = project.rooms[0].id)))

            val importedId = repository.importProject(clashing).getOrNull()!!

            assertNotEquals(clashing.id, importedId)
            val stored = repository.getProject(importedId).getOrNull()!!
            assertNotEquals(project.rooms[0].id, stored.rooms[0].id)
        }

    @Test
    fun `given queued failure when an operation runs then it fails once, changes nothing and then works again`() =
        runTest {
            repository.createProject(project)
            repository.failures.failNext(FakeProjectRepository.Operation.DELETE, storageFailure)

            assertEquals(storageFailure, repository.deleteProject(project.id).errorOrNull())
            assertEquals(project, repository.getProject(project.id).getOrNull())
            assertEquals(DomainResult.Success(Unit), repository.deleteProject(project.id))
        }

    @Test
    fun `given permanent failure when every operation runs then each fails until reset`() =
        runTest {
            FakeProjectRepository.Operation.entries.forEach {
                repository.failures.failAlways(it, DomainError.Storage(StorageProblem.NO_SPACE))
            }
            val noSpace = DomainError.Storage(StorageProblem.NO_SPACE)

            assertEquals(noSpace, repository.getProject(project.id).errorOrNull())
            assertEquals(noSpace, repository.createProject(project).errorOrNull())
            assertEquals(noSpace, repository.renameProject(project.id, "x").errorOrNull())
            assertEquals(noSpace, repository.deleteProject(project.id).errorOrNull())
            assertEquals(noSpace, repository.duplicateProject(project.id).errorOrNull())
            assertEquals(noSpace, repository.importProject(project).errorOrNull())
            assertTrue(repository.projects.isEmpty())

            repository.failures.reset()
            assertEquals(DomainResult.Success(Unit), repository.createProject(project))
        }

    @Test
    fun `given queued failures when several calls follow then they are consumed in order`() =
        runTest {
            val first = DomainError.Storage(StorageProblem.READ_FAILED)
            val second = DomainError.Storage(StorageProblem.WRITE_FAILED)
            repository.failures.failNext(FakeProjectRepository.Operation.GET, first)
            repository.failures.failNext(FakeProjectRepository.Operation.GET, second)

            assertEquals(first, repository.getProject(project.id).errorOrNull())
            assertEquals(second, repository.getProject(project.id).errorOrNull())
            assertEquals(notFound(project.id), repository.getProject(project.id).errorOrNull())
        }

    @Test
    fun `given initial projects when constructed then they are stored and duplicates are rejected`() =
        runTest {
            val seeded = FakeProjectRepository(initialProjects = listOf(project))

            assertEquals(project, seeded.getProject(project.id).getOrNull())
            assertEquals(1, seeded.observeProjects().first().size)
            assertThrows<IllegalArgumentException> { FakeProjectRepository(initialProjects = listOf(project, project)) }
        }

    @Test
    fun `given a failed create then no project appears in the observed list`() =
        runTest {
            repository.failures.failNext(FakeProjectRepository.Operation.CREATE, storageFailure)

            repository.createProject(project)

            assertNull(repository.getProject(project.id).getOrNull())
            assertTrue(repository.observeProjects().first().isEmpty())
        }

    private fun dockedProject(): Project {
        val base = ProjectGenerator(seed = 3L).project(roomCount = 0)
        val host = ReferenceRooms.RECTANGLE.room
        val guest = ReferenceRooms.SQUARE.room
        val opening =
            Opening(
                id = OpeningId("door-1"),
                wallId = host.walls[1].id,
                type = OpeningType.DOOR,
                offset = Length(100L),
                width = Length(900L),
                height = Length(2000L),
                sillHeight = null,
                swing = DoorSwing.HINGE_LEFT_INWARD,
            )
        val link =
            DockLink(
                hostRoomId = host.id,
                hostWallId = host.walls[1].id,
                ownWallId = guest.walls[3].id,
                hostOffset = Length(50L),
                hostOpeningId = opening.id,
            )
        val rooms: List<Room> =
            listOf(
                host.copy(openings = persistentListOf(opening)),
                guest.copy(placement = RoomPlacement(10.0, 20.0, 0.0, link)),
            )
        return base.copy(rooms = kotlinx.collections.immutable.persistentListOf(*rooms.toTypedArray()))
    }
}
