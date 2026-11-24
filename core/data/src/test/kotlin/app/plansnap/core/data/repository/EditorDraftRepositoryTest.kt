package app.plansnap.core.data.repository

import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.result.EntityKind
import app.plansnap.core.common.result.StorageProblem
import app.plansnap.core.common.result.errorOrNull
import app.plansnap.core.data.db.PlanSnapDatabase
import app.plansnap.core.domain.repository.EditorDraft
import app.plansnap.core.model.Project
import app.plansnap.core.model.ProjectId
import app.plansnap.core.model.RoomId
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EditorDraftRepositoryTest {
    private lateinit var db: PlanSnapDatabase
    private lateinit var stores: Stores
    private lateinit var project: Project

    @Before
    fun setUp() {
        db = fileDatabase("draft-test.db")
        stores = Stores(db)
        project = sampleProject()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun draft(
        room: RoomId?,
        text: String = "250",
    ) = EditorDraft(project.id, room, text)

    @Test
    fun `no draft yields null`() =
        runTest {
            stores.projects.createProject(project).assertSuccess()
            assertNull(stores.drafts.getDraft(project.id).value())
        }

    @Test
    fun `draft round trip with and without a room`() =
        runTest {
            stores.projects.createProject(project).assertSuccess()
            val withRoom = draft(project.rooms[0].id, "  12,5\n")
            stores.drafts.saveDraft(withRoom).assertSuccess()
            assertEquals(withRoom, stores.drafts.getDraft(project.id).value())

            val withoutRoom = draft(null, "")
            stores.drafts.saveDraft(withoutRoom).assertSuccess()
            assertEquals(withoutRoom, stores.drafts.getDraft(project.id).value())
        }

    @Test
    fun `there is one draft per project and saving replaces it`() =
        runTest {
            stores.projects.createProject(project).assertSuccess()
            stores.drafts.saveDraft(draft(project.rooms[0].id, "1")).assertSuccess()
            stores.drafts.saveDraft(draft(project.rooms[1].id, "2")).assertSuccess()
            assertEquals(draft(project.rooms[1].id, "2"), stores.drafts.getDraft(project.id).value())
        }

    @Test
    fun `drafts of different projects are independent`() =
        runTest {
            val other = sampleProject(seed = 6L, idPrefix = "o")
            stores.projects.createProject(project).assertSuccess()
            stores.projects.createProject(other).assertSuccess()
            stores.drafts.saveDraft(draft(null, "a")).assertSuccess()
            stores.drafts.saveDraft(EditorDraft(other.id, null, "b")).assertSuccess()
            stores.drafts.clearDraft(project.id).assertSuccess()
            assertNull(stores.drafts.getDraft(project.id).value())
            assertEquals(
                "b",
                stores.drafts
                    .getDraft(other.id)
                    .value()
                    ?.inputText,
            )
        }

    @Test
    fun `clear removes the draft and tolerates a missing one`() =
        runTest {
            stores.projects.createProject(project).assertSuccess()
            stores.drafts.saveDraft(draft(null)).assertSuccess()
            stores.drafts.clearDraft(project.id).assertSuccess()
            stores.drafts.clearDraft(project.id).assertSuccess()
            assertNull(stores.drafts.getDraft(project.id).value())
        }

    @Test
    fun `the change time of the draft comes from the clock`() =
        runTest {
            stores.projects.createProject(project).assertSuccess()
            stores.clock.set(4_242L)
            stores.drafts.saveDraft(draft(null)).assertSuccess()
            assertEquals(4_242L, db.editorDraftDao().get(project.id.value)?.updatedAtEpochMs)
        }

    @Test
    fun `the draft survives reopening the database file`() =
        runTest {
            stores.projects.createProject(project).assertSuccess()
            val saved = draft(project.rooms[2].id, "  3 4 ")
            stores.drafts.saveDraft(saved).assertSuccess()
            db.close()

            db = fileDatabase("draft-test.db")
            val reopened = Stores(db)

            assertEquals(saved, reopened.drafts.getDraft(project.id).value())
        }

    @Test
    fun `deleting the project or the room removes the draft`() =
        runTest {
            stores.projects.createProject(project).assertSuccess()
            stores.drafts.saveDraft(draft(project.rooms[0].id)).assertSuccess()
            val withoutRoom = project.copy(rooms = project.rooms.drop(1).toImmutableList())
            stores.saver.save(withoutRoom, setOf(project.rooms[0].id)).assertSuccess()
            assertNull(stores.drafts.getDraft(project.id).value())

            stores.drafts.saveDraft(draft(null)).assertSuccess()
            stores.projects.deleteProject(project.id).assertSuccess()
            assertNull(db.editorDraftDao().get(project.id.value))
        }

    @Test
    fun `saving for an unknown project is not found`() =
        runTest {
            val result = stores.drafts.saveDraft(EditorDraft(ProjectId("nope"), null, "x"))
            assertEquals(DomainError.NotFound(EntityKind.PROJECT, "nope"), result.errorOrNull())
        }

    @Test
    fun `saving for a room of another project is not found`() =
        runTest {
            val other = sampleProject(seed = 6L, idPrefix = "o")
            stores.projects.createProject(project).assertSuccess()
            stores.projects.createProject(other).assertSuccess()
            val result = stores.drafts.saveDraft(draft(other.rooms[0].id))
            assertEquals(DomainError.NotFound(EntityKind.ROOM, other.rooms[0].id.value), result.errorOrNull())
            assertNull(stores.drafts.getDraft(project.id).value())
        }

    @Test
    fun `a closed database yields storage failures`() =
        runTest {
            stores.projects.createProject(project).assertSuccess()
            db.close()
            assertEquals(
                DomainError.Storage(StorageProblem.READ_FAILED),
                stores.drafts.getDraft(project.id).errorOrNull(),
            )
            assertEquals(
                DomainError.Storage(StorageProblem.WRITE_FAILED),
                stores.drafts.saveDraft(draft(null)).errorOrNull(),
            )
            assertEquals(
                DomainError.Storage(StorageProblem.WRITE_FAILED),
                stores.drafts.clearDraft(project.id).errorOrNull(),
            )
        }
}
