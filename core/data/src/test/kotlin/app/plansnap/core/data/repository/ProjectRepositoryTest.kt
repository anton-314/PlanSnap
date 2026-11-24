package app.plansnap.core.data.repository

import app.cash.turbine.test
import app.plansnap.core.common.result.DocumentProblem
import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.result.DomainResult
import app.plansnap.core.common.result.DomainRule
import app.plansnap.core.common.result.EntityKind
import app.plansnap.core.common.result.StorageProblem
import app.plansnap.core.common.result.errorOrNull
import app.plansnap.core.data.db.PlanSnapDatabase
import app.plansnap.core.domain.repository.EditorDraft
import app.plansnap.core.domain.validation.ProjectValidator
import app.plansnap.core.model.Project
import app.plansnap.core.model.ProjectId
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream

@RunWith(RobolectricTestRunner::class)
class ProjectRepositoryTest {
    private lateinit var db: PlanSnapDatabase
    private lateinit var stores: Stores

    @Before
    fun setUp() {
        db = fileDatabase()
        stores = Stores(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun importStream(project: Project): InputStream =
        ByteArrayInputStream(stores.codec.encodeProject(project).toByteArray())

    @Test
    fun `create then get returns an equal project`() =
        runTest {
            val project = sampleProject()
            stores.projects.createProject(project).assertSuccess()
            assertEquals(project, stores.projects.getProject(project.id).value())
        }

    @Test
    fun `create keeps the order of rooms and an empty project`() =
        runTest {
            val empty = sampleProject(seed = 2L, rooms = 0, idPrefix = "e")
            stores.projects.createProject(empty).assertSuccess()
            assertEquals(empty, stores.projects.getProject(empty.id).value())
        }

    @Test
    fun `create with a known project id fails and keeps the stored project`() =
        runTest {
            val project = sampleProject()
            stores.projects.createProject(project).assertSuccess()
            val result = stores.projects.createProject(project.copy(name = "Other", rooms = persistentListOf()))
            assertEquals(DomainError.RuleViolation(DomainRule.DUPLICATE_ID), result.errorOrNull())
            assertEquals(project, stores.projects.getProject(project.id).value())
        }

    @Test
    fun `create with a room id of another project fails without partial state`() =
        runTest {
            val first = sampleProject()
            stores.projects.createProject(first).assertSuccess()
            val clash =
                sampleProject(seed = 5L, idPrefix = "x").let {
                    it.copy(
                        rooms =
                            first.rooms.take(1).let { r ->
                                kotlinx.collections.immutable.persistentListOf(r[0])
                            },
                    )
                }
            val result = stores.projects.createProject(clash)
            assertEquals(DomainError.RuleViolation(DomainRule.DUPLICATE_ID), result.errorOrNull())
            assertEquals(
                DomainError.NotFound(EntityKind.PROJECT, clash.id.value),
                stores.projects.getProject(clash.id).errorOrNull(),
            )
        }

    @Test
    fun `get of an unknown project is not found`() =
        runTest {
            assertEquals(
                DomainError.NotFound(EntityKind.PROJECT, "nope"),
                stores.projects.getProject(ProjectId("nope")).errorOrNull(),
            )
        }

    @Test
    fun `rename changes the name and takes the change time from the clock`() =
        runTest {
            val project = sampleProject()
            stores.projects.createProject(project).assertSuccess()
            stores.clock.set(77_000L)
            stores.projects.renameProject(project.id, "Renamed").assertSuccess()
            val stored = stores.projects.getProject(project.id).value()
            assertEquals("Renamed", stored.name)
            assertEquals(77_000L, stored.updatedAtEpochMs)
            assertEquals(project.createdAtEpochMs, stored.createdAtEpochMs)
            assertEquals(project.rooms, stored.rooms)
        }

    @Test
    fun `rename of an unknown project is not found`() =
        runTest {
            assertEquals(
                DomainError.NotFound(EntityKind.PROJECT, "nope"),
                stores.projects.renameProject(ProjectId("nope"), "x").errorOrNull(),
            )
        }

    @Test
    fun `delete removes project rooms and draft`() =
        runTest {
            val project = sampleProject()
            val other = sampleProject(seed = 9L, idPrefix = "o")
            stores.projects.createProject(project).assertSuccess()
            stores.projects.createProject(other).assertSuccess()
            stores.drafts.saveDraft(EditorDraft(project.id, project.rooms[0].id, "12")).assertSuccess()

            stores.projects.deleteProject(project.id).assertSuccess()

            assertEquals(
                DomainError.NotFound(EntityKind.PROJECT, project.id.value),
                stores.projects.getProject(project.id).errorOrNull(),
            )
            assertTrue(db.roomDao().getByProject(project.id.value).isEmpty())
            assertEquals(null, stores.drafts.getDraft(project.id).value())
            assertEquals(other, stores.projects.getProject(other.id).value())
        }

    @Test
    fun `delete of an unknown project is not found`() =
        runTest {
            assertEquals(
                DomainError.NotFound(EntityKind.PROJECT, "nope"),
                stores.projects.deleteProject(ProjectId("nope")).errorOrNull(),
            )
        }

    @Test
    fun `duplicate creates an independent copy with new ids and redirected references`() =
        runTest {
            val original = sampleProject().withDockedSecondRoom()
            stores.projects.createProject(original).assertSuccess()

            val copyId = stores.projects.duplicateProject(original.id).value()
            val copy = stores.projects.getProject(copyId).value()

            assertNotEquals(original.id, copy.id)
            assertEquals(original.name, copy.name)
            assertEquals(original.rooms.size, copy.rooms.size)
            assertTrue(
                original.rooms
                    .map { it.id }
                    .intersect(copy.rooms.map { it.id }.toSet())
                    .isEmpty(),
            )
            val oldChildIds =
                original.rooms.flatMap { r ->
                    r.walls.map { it.id.value } + r.openings.map { it.id.value }
                }
            val newChildIds = copy.rooms.flatMap { r -> r.walls.map { it.id.value } + r.openings.map { it.id.value } }
            assertTrue(oldChildIds.intersect(newChildIds.toSet()).isEmpty())
            assertTrue(copy.rooms.any { it.openings.isNotEmpty() })
            copy.rooms.forEachIndexed { index, room ->
                assertEquals(original.rooms[index].walls.size, room.walls.size)
                assertEquals(original.rooms[index].openings.size, room.openings.size)
                assertEquals(original.rooms[index].corners, room.corners)
            }
            val dock = copy.rooms[1].placement.dock!!
            assertEquals(copy.rooms[0].id, dock.hostRoomId)
            assertEquals(copy.rooms[0].walls[0].id, dock.hostWallId)
            assertEquals(copy.rooms[1].walls[0].id, dock.ownWallId)
        }

    @Test
    fun `changing the copy leaves the original intact`() =
        runTest {
            val original = sampleProject()
            stores.projects.createProject(original).assertSuccess()
            val copyId = stores.projects.duplicateProject(original.id).value()
            val copy = stores.projects.getProject(copyId).value()

            stores.projects.renameProject(copyId, "Copy").assertSuccess()
            stores.saver
                .save(
                    copy.withRoomName(copy.rooms[0].id, "Changed").copy(rooms = copy.rooms.drop(1).toImmutableList()),
                    copy.roomIds().toSet(),
                ).assertSuccess()
            stores.projects.deleteProject(copyId).assertSuccess()

            assertEquals(original, stores.projects.getProject(original.id).value())
        }

    @Test
    fun `duplicate of an unknown project is not found`() =
        runTest {
            assertEquals(
                DomainError.NotFound(EntityKind.PROJECT, "nope"),
                stores.projects.duplicateProject(ProjectId("nope")).errorOrNull(),
            )
        }

    @Test
    fun `import of a valid document keeps the ids`() =
        runTest {
            val project = sampleProject()
            assertEquals(project.id, stores.projects.importProject(importStream(project)).value())
            assertEquals(project, stores.projects.getProject(project.id).value())
        }

    @Test
    fun `import with a known project id is stored as a copy`() =
        runTest {
            val project = sampleProject()
            stores.projects.createProject(project).assertSuccess()
            val importedId = stores.projects.importProject(importStream(project)).value()
            assertNotEquals(project.id, importedId)
            val imported = stores.projects.getProject(importedId).value()
            assertTrue(project.roomIds().intersect(imported.roomIds().toSet()).isEmpty())
            assertEquals(project, stores.projects.getProject(project.id).value())
        }

    @Test
    fun `import with a known room id is stored as a copy`() =
        runTest {
            val project = sampleProject()
            stores.projects.createProject(project).assertSuccess()
            val sameRoomOtherProject =
                sampleProject(
                    seed = 4L,
                    rooms = 1,
                ).copy(rooms = kotlinx.collections.immutable.persistentListOf(project.rooms[0]))
            val importedId = stores.projects.importProject(importStream(sameRoomOtherProject)).value()
            assertNotEquals(sameRoomOtherProject.id, importedId)
            assertTrue(
                project.rooms[0].id !in
                    stores.projects
                        .getProject(importedId)
                        .value()
                        .roomIds(),
            )
            assertEquals(project, stores.projects.getProject(project.id).value())
        }

    @Test
    fun `import of malformed content fails without storing anything`() =
        runTest {
            val result = stores.projects.importProject(ByteArrayInputStream("not json".toByteArray()))
            assertEquals(DomainError.Document(DocumentProblem.MALFORMED), result.errorOrNull())
            assertTrue(stores.projects.observeProjectsOnce().isEmpty())
        }

    @Test
    fun `import of a newer major version is rejected`() =
        runTest {
            val newer = stores.codec.encodeProject(sampleProject()).replaceFirst("\"major\":1", "\"major\":2")
            val result = stores.projects.importProject(ByteArrayInputStream(newer.toByteArray()))
            assertEquals(DomainError.Document(DocumentProblem.UNSUPPORTED_VERSION), result.errorOrNull())
            assertTrue(stores.projects.observeProjectsOnce().isEmpty())
        }

    @Test
    fun `import rejected by the validator reports its violation and stores nothing`() =
        runTest {
            val violation = DomainError.RuleViolation(DomainRule.DOCK_CYCLE)
            val strict = Stores(db, validator = ProjectValidator { DomainResult.Failure(violation) })
            val result = strict.projects.importProject(importStream(sampleProject()))
            assertEquals(violation, result.errorOrNull())
            assertTrue(strict.projects.observeProjectsOnce().isEmpty())
        }

    @Test
    fun `import of an unreadable stream fails with read failed and leaves the input open`() =
        runTest {
            var closed = false
            val broken =
                object : InputStream() {
                    override fun read(): Int = throw IOException("boom")

                    override fun close() {
                        closed = true
                    }
                }
            val result = stores.projects.importProject(broken)
            assertEquals(DomainError.Storage(StorageProblem.READ_FAILED), result.errorOrNull())
            assertTrue(!closed)
            assertTrue(stores.projects.observeProjectsOnce().isEmpty())
        }

    @Test
    fun `observeProjects emits the list on every change`() =
        runTest {
            val project = sampleProject(rooms = 2)
            stores.projects.observeProjects().test {
                assertTrue(awaitItem().isEmpty())

                stores.projects.createProject(project).assertSuccess()
                val created = awaitItem().single()
                assertEquals(project.id, created.id)
                assertEquals(2, created.roomCount)
                assertEquals(project.createdAtEpochMs, created.createdAtEpochMs)

                stores.clock.set(500L)
                stores.projects.renameProject(project.id, "New").assertSuccess()
                val renamed = awaitItem().single()
                assertEquals("New", renamed.name)
                assertEquals(500L, renamed.updatedAtEpochMs)

                stores.saver
                    .save(
                        project.copy(rooms = project.rooms.take(1).toImmutableList()),
                        project.roomIds().toSet(),
                    ).assertSuccess()
                assertEquals(1, awaitItem().single().roomCount)

                stores.projects.deleteProject(project.id).assertSuccess()
                assertTrue(awaitItem().isEmpty())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `observeProjects lists the most recently changed project first`() =
        runTest {
            val older = sampleProject(seed = 1L, idPrefix = "a").let { it.copy(updatedAtEpochMs = 100L) }
            val newer = sampleProject(seed = 2L, idPrefix = "b").let { it.copy(updatedAtEpochMs = 200L) }
            stores.projects.createProject(older).assertSuccess()
            stores.projects.createProject(newer).assertSuccess()
            assertEquals(listOf(newer.id, older.id), stores.projects.observeProjectsOnce().map { it.id })
        }

    @Test
    fun `a closed database yields storage failures instead of exceptions`() =
        runTest {
            val project = sampleProject()
            stores.projects.createProject(project).assertSuccess()
            db.close()
            assertEquals(
                DomainError.Storage(StorageProblem.READ_FAILED),
                stores.projects.getProject(project.id).errorOrNull(),
            )
            assertEquals(
                DomainError.Storage(StorageProblem.WRITE_FAILED),
                stores.projects.deleteProject(project.id).errorOrNull(),
            )
            assertEquals(
                DomainError.Storage(StorageProblem.WRITE_FAILED),
                stores.projects.createProject(sampleProject(seed = 3L, idPrefix = "z")).errorOrNull(),
            )
            assertEquals(
                DomainError.Storage(StorageProblem.READ_FAILED),
                stores.projects.duplicateProject(project.id).errorOrNull(),
            )
        }
}
