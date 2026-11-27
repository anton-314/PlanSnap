package app.plansnap.feature.editor.rooms

import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.result.EntityKind
import app.plansnap.core.common.runtime.ApplicationScope
import app.plansnap.core.common.runtime.IdGenerator
import app.plansnap.core.domain.command.ProjectEdit
import app.plansnap.core.model.RoomId
import app.plansnap.core.model.WallId
import app.plansnap.feature.editor.state.CoreIntent
import app.plansnap.feature.editor.state.EditorRig
import app.plansnap.feature.editor.state.EditorViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rooms function while commands are still queued: new rooms are placed and selected from the committed project, and
 * an open dock sheet follows every change of the project.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RoomsPendingWorkTest {
    private fun TestScope.opened(): RoomsRig = RoomsRig(this).also { it.send(CoreIntent.SelectRoom(GUEST_ID)) }

    @Test
    fun `given two rooms added in quick succession then they do not lie on each other`() =
        runTest {
            val rig = opened()
            rig.send(RoomsIntent.AddRoom, RoomsIntent.AddRoom)

            val added = checkNotNull(rig.state.project).rooms.takeLast(2)
            assertTrue(
                "the second room lies to the right of the first: ${added.map { it.placement }}",
                added[1].placement.originX > added[0].placement.originX,
            )
            assertEquals(added[1].id, rig.state.selectedRoomId)
        }

    @Test
    fun `given a new room whose command has not run yet then the selection stays`() =
        runTest {
            val rig = opened()
            rig.viewModel.onIntent(RoomsIntent.AddRoom)
            assertEquals(GUEST_ID, rig.state.selectedRoomId)

            runCurrent()
            val added = checkNotNull(rig.state.project).rooms.last()
            assertEquals(added.id, rig.state.selectedRoomId)
        }

    @Test
    fun `given a rejected new room then the selection stays`() =
        runTest {
            val rig = EditorRig(this, twoRoomProject())
            // The id of the host is taken, so the command is rejected.
            val taken = IdGenerator { HOST_ID.value }
            rig.handlers += RoomsHandler(taken, rig.settingsRepository, ApplicationScope(backgroundScope))
            val viewModel: EditorViewModel = rig.viewModel()
            runCurrent()
            viewModel.onIntent(CoreIntent.SelectRoom(GUEST_ID))

            viewModel.onIntent(RoomsIntent.AddRoom)
            runCurrent()

            val state = viewModel.state.value
            assertEquals(2, checkNotNull(state.project).rooms.size)
            assertEquals(GUEST_ID, state.selectedRoomId)
        }

    @Test
    fun `given a wall typed right after adding a room then the new room receives it`() =
        runTest {
            val rig = opened()
            rig.send(RoomsIntent.AddRoom, CoreIntent.InputChanged("200"), CoreIntent.ConfirmWall)

            val added = checkNotNull(rig.state.project).rooms.last()
            assertNotEquals(GUEST_ID, added.id)
            assertEquals(1, added.walls.size)
        }

    @Test
    fun `given the host is chosen on the plan when undo removes it then the draft reports it until redo`() =
        runTest {
            val rig = opened()
            val third = RoomId("third")
            rig.send(CoreIntent.Submit(ProjectEdit.AddRoom(rectangleRoom(third, 1000, 1000, x = 9000.0))))
            rig.send(
                CoreIntent.SelectRoom(GUEST_ID),
                RoomsIntent.OpenDock,
                RoomsIntent.SelectHost(third, WallId("third0")),
                RoomsIntent.SelectOwnWall(WallId("guest0")),
                RoomsIntent.StartPicking,
            )
            assertTrue(checkNotNull(rig.rooms.dock).canConfirm)

            rig.send(CoreIntent.Undo)
            val stale = checkNotNull(rig.rooms.dock)
            assertEquals(DomainError.NotFound(EntityKind.ROOM, third.value), stale.error)
            assertFalse(stale.canConfirm)
            assertTrue("the selection on the plan goes on", stale.picking)

            rig.send(CoreIntent.Redo)
            assertNull(checkNotNull(rig.rooms.dock).error)
            assertTrue(checkNotNull(rig.rooms.dock).canConfirm)
        }

    @Test
    fun `given a dock sheet that was closed and reopened then it still follows the project`() =
        runTest {
            val rig = opened()
            val third = RoomId("third")
            rig.send(CoreIntent.Submit(ProjectEdit.AddRoom(rectangleRoom(third, 1000, 1000, x = 9000.0))))
            rig.send(CoreIntent.SelectRoom(GUEST_ID), RoomsIntent.OpenDock, RoomsIntent.Dismiss)
            assertNull(rig.rooms.dock)
            rig.send(
                RoomsIntent.OpenDock,
                RoomsIntent.SelectHost(third, WallId("third0")),
                RoomsIntent.SelectOwnWall(WallId("guest0")),
            )

            rig.send(CoreIntent.Undo)

            assertEquals(DomainError.NotFound(EntityKind.ROOM, third.value), checkNotNull(rig.rooms.dock).error)
        }
}
