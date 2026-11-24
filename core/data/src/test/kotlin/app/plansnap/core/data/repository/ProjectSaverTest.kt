package app.plansnap.core.data.repository

import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.result.DomainRule
import app.plansnap.core.common.result.EntityKind
import app.plansnap.core.common.result.StorageProblem
import app.plansnap.core.common.result.errorOrNull
import app.plansnap.core.common.units.Length
import app.plansnap.core.data.db.PlanSnapDatabase
import app.plansnap.core.document.DocumentCodec
import app.plansnap.core.document.JsonDocumentCodec
import app.plansnap.core.domain.repository.EditorDraft
import app.plansnap.core.model.Project
import app.plansnap.core.model.Room
import app.plansnap.core.model.RoomId
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ProjectSaverTest {
    private lateinit var db: PlanSnapDatabase
    private lateinit var stores: Stores
    private lateinit var base: Project

    @Before
    fun setUp() {
        db = fileDatabase()
        stores = Stores(db)
        base = sampleProject(rooms = 4)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun stored(): Project = stores.projects.getProject(base.id).value()

    private fun Project.ordered(vararg indices: Int): Project =
        copy(rooms = indices.map { rooms[it] }.toImmutableList())

    private fun ids(project: Project): List<RoomId> = project.roomIds()

    @Test
    fun `save writes metadata, changed rooms and the clock time and returns it`() =
        runTest {
            stores.projects.createProject(base).assertSuccess()
            stores.clock.set(55_000L)
            val edited =
                base
                    .withRoomName(base.rooms[1].id, "Kitchen")
                    .copy(name = "Renamed", defaultHeight = Length(2700L), updatedAtEpochMs = 1L)

            val updatedAt = stores.saver.save(edited, setOf(base.rooms[1].id)).value()

            assertEquals(55_000L, updatedAt)
            val result = stored()
            assertEquals(edited.copy(updatedAtEpochMs = 55_000L), result)
            assertEquals(base.createdAtEpochMs, result.createdAtEpochMs)
        }

    @Test
    fun `rooms outside the changed set keep their stored content`() =
        runTest {
            stores.projects.createProject(base).assertSuccess()
            val edited =
                base.withRoomName(base.rooms[0].id, "Ignored").withRoomName(base.rooms[2].id, "Saved")

            stores.saver.save(edited, setOf(base.rooms[2].id)).assertSuccess()

            val result = stored()
            assertEquals(base.rooms[0], result.rooms[0])
            assertEquals("Saved", result.rooms[2].name)
        }

    @Test
    fun `stored order follows the project also for unreported rooms`() =
        runTest {
            stores.projects.createProject(base).assertSuccess()
            val reordered = base.ordered(3, 1, 0, 2)

            stores.saver.save(reordered, emptySet()).assertSuccess()

            assertEquals(ids(reordered), ids(stored()))
            assertEquals(reordered.rooms, stored().rooms)
        }

    @Test
    fun `a project room that is neither stored nor reported is not added`() =
        runTest {
            val small = base.ordered(0, 1)
            stores.projects.createProject(small).assertSuccess()

            stores.saver.save(base, emptySet()).assertSuccess()

            assertEquals(ids(small), ids(stored()))
        }

    @Test
    fun `a reported new room is added at its project position`() =
        runTest {
            val small = base.ordered(0, 2)
            stores.projects.createProject(small).assertSuccess()
            val withNew = base.ordered(0, 1, 2)

            stores.saver.save(withNew, setOf(base.rooms[1].id)).assertSuccess()

            assertEquals(ids(withNew), ids(stored()))
            assertEquals(base.rooms[1], stored().rooms[1])
        }

    @Test
    fun `a reported room missing from the project is deleted`() =
        runTest {
            stores.projects.createProject(base).assertSuccess()
            val without = base.ordered(0, 2, 3)

            stores.saver.save(without, setOf(base.rooms[1].id)).assertSuccess()

            assertEquals(ids(without), ids(stored()))
            assertNull(db.roomDao().get(base.rooms[1].id.value))
        }

    @Test
    fun `an unreported room missing from the project stays after the project rooms in its relative order`() =
        runTest {
            stores.projects.createProject(base).assertSuccess()
            val without = base.ordered(3, 2)

            stores.saver.save(without, emptySet()).assertSuccess()

            val expected = listOf(base.rooms[3].id, base.rooms[2].id, base.rooms[0].id, base.rooms[1].id)
            assertEquals(expected, ids(stored()))
            assertEquals(base.rooms[0], stored().rooms[2])
        }

    @Test
    fun `reported deletion and unreported removal can be mixed`() =
        runTest {
            stores.projects.createProject(base).assertSuccess()
            val without = base.ordered(2)

            stores.saver.save(without, setOf(base.rooms[0].id)).assertSuccess()

            val expected = listOf(base.rooms[2].id, base.rooms[1].id, base.rooms[3].id)
            assertEquals(expected, ids(stored()))
        }

    @Test
    fun `a reported id that is neither in the project nor stored changes nothing`() =
        runTest {
            stores.projects.createProject(base).assertSuccess()

            stores.saver.save(base, setOf(RoomId("ghost"))).assertSuccess()

            assertEquals(base.rooms, stored().rooms)
        }

    @Test
    fun `saving keeps the draft of an existing room and deleting the room removes it`() =
        runTest {
            stores.projects.createProject(base).assertSuccess()
            stores.drafts.saveDraft(EditorDraft(base.id, base.rooms[1].id, "120")).assertSuccess()

            stores.saver.save(base.withRoomName(base.rooms[1].id, "x"), setOf(base.rooms[1].id)).assertSuccess()
            assertEquals(
                "120",
                stores.drafts
                    .getDraft(base.id)
                    .value()
                    ?.inputText,
            )

            stores.saver.save(base.ordered(0, 2, 3), setOf(base.rooms[1].id)).assertSuccess()
            assertNull(stores.drafts.getDraft(base.id).value())
        }

    @Test
    fun `saving an unknown project is not found and writes nothing`() =
        runTest {
            val result = stores.saver.save(base, base.roomIds().toSet())

            assertEquals(DomainError.NotFound(EntityKind.PROJECT, base.id.value), result.errorOrNull())
            assertTrue(stores.projects.observeProjectsOnce().isEmpty())
            assertNull(db.roomDao().get(base.rooms[0].id.value))
        }

    @Test
    fun `a reported room owned by another project fails and changes nothing`() =
        runTest {
            val other = sampleProject(seed = 8L, rooms = 1, idPrefix = "o")
            stores.projects.createProject(base.ordered(0)).assertSuccess()
            stores.projects.createProject(other).assertSuccess()
            val stolen = base.copy(name = "Changed", rooms = listOf(base.rooms[0], other.rooms[0]).toImmutableList())

            val result = stores.saver.save(stolen, setOf(other.rooms[0].id))

            assertEquals(DomainError.RuleViolation(DomainRule.DUPLICATE_ID), result.errorOrNull())
            assertEquals(base.name, stored().name)
            assertEquals(other, stores.projects.getProject(other.id).value())
        }

    @Test
    fun `a failure while encoding rolls the whole save back`() =
        runTest {
            stores.projects.createProject(base).assertSuccess()
            val delegate = JsonDocumentCodec()
            val failing =
                object : DocumentCodec by delegate {
                    override fun encodeRoom(room: Room): String =
                        if (room.name == "boom") error("encode failed") else delegate.encodeRoom(room)
                }
            val faulty = Stores(db, saverCodec = failing)
            val edited =
                base
                    .copy(name = "Renamed")
                    .withRoomName(base.rooms[0].id, "fine")
                    .withRoomName(base.rooms[1].id, "boom")
                    .ordered(1, 0, 2, 3)

            val result = faulty.saver.save(edited, setOf(base.rooms[0].id, base.rooms[1].id, base.rooms[3].id))

            assertEquals(DomainError.Storage(StorageProblem.WRITE_FAILED), result.errorOrNull())
            assertEquals(base, stored())
        }

    @Test
    fun `concurrent saves are applied one after the other in call order`() =
        runTest {
            val ticking = TickingClock(start = 1_000L)
            val serial = Stores(db, saverClock = ticking)
            serial.projects.createProject(base).assertSuccess()
            val count = 12

            val results =
                List(count) { index ->
                    async { serial.saver.save(base.copy(name = "v$index"), emptySet()).value() }
                }.awaitAll()

            assertEquals(results.sorted(), results)
            assertEquals(count, results.toSet().size)
            val result = stored()
            assertEquals("v${count - 1}", result.name)
            assertEquals(results.last(), result.updatedAtEpochMs)
        }

    @Test
    fun `a closed database yields a storage failure instead of an exception`() =
        runTest {
            stores.projects.createProject(base).assertSuccess()
            db.close()

            val result = stores.saver.save(base, emptySet())

            assertEquals(DomainError.Storage(StorageProblem.WRITE_FAILED), result.errorOrNull())
        }
}
