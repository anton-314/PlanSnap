package app.plansnap.core.data.db

import androidx.room.withTransaction
import app.cash.turbine.test
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DaoTest {
    private lateinit var db: PlanSnapDatabase

    @Before
    fun setUp() {
        db = inMemoryDatabase()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `project round trip, rename and delete`() =
        runTest {
            val dao = db.projectDao()
            dao.create(project(), emptyList())
            assertEquals(project(), dao.get("p1"))

            assertEquals(1, dao.rename("p1", "House", 200L))
            assertEquals(project(name = "House", updatedAt = 200L), dao.get("p1"))
            assertEquals(0, dao.rename("missing", "x", 1L))

            assertEquals(1, dao.delete("p1"))
            assertNull(dao.get("p1"))
            assertEquals(0, dao.delete("p1"))
        }

    @Test
    fun `projects are listed most recently changed first`() =
        runTest {
            val dao = db.projectDao()
            dao.create(project("a", updatedAt = 1L), emptyList())
            dao.create(project("b", updatedAt = 3L), emptyList())
            dao.create(project("c", updatedAt = 2L), emptyList())
            assertEquals(listOf("b", "c", "a"), dao.observeAll().first().map { it.id })
        }

    @Test
    fun `rooms are ordered by position`() =
        runTest {
            db.projectDao().create(
                project(),
                listOf(room("r1", position = 2), room("r2", position = 0), room("r3", position = 1)),
            )
            assertEquals(listOf("r2", "r3", "r1"), db.roomDao().getByProject("p1").map { it.id })
        }

    @Test
    fun `save updates room in place and touches project`() =
        runTest {
            val rooms = db.roomDao()
            db.projectDao().create(project(), listOf(room("r1")))
            rooms.save("p1", listOf(room("r1", json = "{\"v\":2}"), room("r2", position = 1)), 500L)

            assertEquals("{\"v\":2}", rooms.get("r1")?.documentJson)
            assertEquals(listOf("r1", "r2"), rooms.getByProject("p1").map { it.id })
            assertEquals(500L, db.projectDao().get("p1")?.updatedAtEpochMs)
        }

    @Test
    fun `save keeps the draft of an existing room`() =
        runTest {
            db.projectDao().create(project(), listOf(room("r1")))
            db.editorDraftDao().upsert(EditorDraftEntity("p1", "r1", "draft", 1L))
            db.roomDao().save("p1", listOf(room("r1", json = "{\"v\":2}")), 500L)
            assertEquals("draft", db.editorDraftDao().get("p1")?.inputText)
        }

    @Test
    fun `remove deletes room and its draft and touches project`() =
        runTest {
            db.projectDao().create(project(), listOf(room("r1"), room("r2", position = 1)))
            db.editorDraftDao().upsert(EditorDraftEntity("p1", "r1", "draft", 1L))

            assertTrue(db.roomDao().remove("p1", "r1", 700L))
            assertFalse(db.roomDao().remove("p1", "r1", 800L))

            assertEquals(listOf("r2"), db.roomDao().getByProject("p1").map { it.id })
            assertNull(db.editorDraftDao().get("p1"))
            assertEquals(800L, db.projectDao().get("p1")?.updatedAtEpochMs)
        }

    @Test
    fun `deleting a project cascades to rooms and drafts`() =
        runTest {
            db.projectDao().create(project(), listOf(room("r1")))
            db.projectDao().create(project("p2"), listOf(room("r9", projectId = "p2")))
            db.editorDraftDao().upsert(EditorDraftEntity("p1", "r1", "draft", 1L))

            db.projectDao().delete("p1")

            assertTrue(db.roomDao().getByProject("p1").isEmpty())
            assertTrue(db.editorDraftDao().get("p1") == null)
            assertEquals(listOf("r9"), db.roomDao().getByProject("p2").map { it.id })
        }

    @Test
    fun `create with a duplicate room id leaves no partial state`() =
        runTest {
            assertFailsWith<Exception> {
                db.projectDao().create(project(), listOf(room("r1"), room("r1", position = 1)))
            }
            assertNull(db.projectDao().get("p1"))
            assertNull(db.roomDao().get("r1"))
        }

    @Test
    fun `save for an unknown project writes nothing`() =
        runTest {
            assertFailsWith<IllegalStateException> {
                db.roomDao().save(
                    "nope",
                    listOf(room("r1", projectId = "nope")),
                    1L,
                )
            }
            assertNull(db.roomDao().get("r1"))
        }

    @Test
    fun `failure inside a transaction rolls back earlier writes`() =
        runTest {
            db.projectDao().create(project(), listOf(room("r1")))
            assertFailsWith<Exception> {
                db.withTransaction {
                    db.roomDao().touchProject("p1", 900L)
                    db.roomDao().upsert(listOf(room("r2", projectId = "ghost")))
                }
            }
            assertEquals(100L, db.projectDao().get("p1")?.updatedAtEpochMs)
            assertNull(db.roomDao().get("r2"))
        }

    @Test
    fun `save rejects rooms of another project and writes nothing`() =
        runTest {
            db.projectDao().create(project(), listOf(room("r1")))
            assertFailsWith<IllegalArgumentException> {
                db.roomDao().save("p1", listOf(room("r3", projectId = "other")), 900L)
            }
            assertEquals(100L, db.projectDao().get("p1")?.updatedAtEpochMs)
            assertNull(db.roomDao().get("r3"))
        }

    @Test
    fun `draft round trip and foreign key enforcement`() =
        runTest {
            val drafts = db.editorDraftDao()
            db.projectDao().create(project(), listOf(room("r1")))
            drafts.upsert(EditorDraftEntity("p1", "r1", "a", 1L))
            drafts.upsert(EditorDraftEntity("p1", "r1", "b", 2L))
            assertEquals("b", drafts.get("p1")?.inputText)
            assertEquals("r1", drafts.get("p1")?.roomId)

            assertFailsWith<Exception> { drafts.upsert(EditorDraftEntity("p1", "ghost", "x", 1L)) }
            assertEquals(1, drafts.delete("p1"))
            assertNull(drafts.get("p1"))
        }

    @Test
    fun `draft without room is allowed, is unique per project and survives deleting rooms`() =
        runTest {
            val drafts = db.editorDraftDao()
            db.projectDao().create(project(), listOf(room("r1")))
            drafts.upsert(EditorDraftEntity("p1", null, "a", 1L))
            assertNull(drafts.get("p1")?.roomId)

            drafts.upsert(EditorDraftEntity("p1", "r1", "b", 2L))
            assertEquals("r1", drafts.get("p1")?.roomId)

            drafts.upsert(EditorDraftEntity("p1", null, "c", 3L))
            db.roomDao().remove("p1", "r1", 4L)
            assertEquals("c", drafts.get("p1")?.inputText)
        }

    @Test
    fun `summaries count rooms per project`() =
        runTest {
            db.projectDao().create(project("p1", updatedAt = 1L), listOf(room("r1"), room("r2", position = 1)))
            db.projectDao().create(project("p2", updatedAt = 2L), emptyList())
            db.projectDao().observeSummaries().test {
                val rows = awaitItem()
                assertEquals(listOf("p2", "p1"), rows.map { it.id })
                assertEquals(listOf(0, 2), rows.map { it.roomCount })
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `room flow emits on every change`() =
        runTest {
            db.projectDao().create(project(), listOf(room("r1")))
            db.roomDao().observeByProject("p1").test {
                assertEquals(listOf("r1"), awaitItem().map { it.id })
                db.roomDao().save("p1", listOf(room("r2", position = 1)), 2L)
                assertEquals(listOf("r1", "r2"), awaitItem().map { it.id })
                db.roomDao().remove("p1", "r1", 3L)
                assertEquals(listOf("r2"), awaitItem().map { it.id })
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `project and draft flows emit changes`() =
        runTest {
            db.projectDao().create(project(), listOf(room("r1")))
            db.projectDao().observe("p1").test {
                assertEquals("Flat", awaitItem()?.name)
                db.projectDao().rename("p1", "House", 2L)
                assertEquals("House", awaitItem()?.name)
                db.projectDao().delete("p1")
                assertNull(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            db.projectDao().create(project("p2"), listOf(room("r2", projectId = "p2")))
            db.editorDraftDao().observe("p2").test {
                assertNull(awaitItem())
                db.editorDraftDao().upsert(EditorDraftEntity("p2", "r2", "x", 1L))
                assertEquals("x", awaitItem()?.inputText)
                cancelAndIgnoreRemainingEvents()
            }
        }
}
