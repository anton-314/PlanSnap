package app.plansnap.feature.editor.history

import app.cash.turbine.test
import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.result.EntityKind
import app.plansnap.core.common.result.StorageProblem
import app.plansnap.core.common.units.Length
import app.plansnap.core.domain.command.ProjectEdit
import app.plansnap.core.model.Project
import app.plansnap.core.model.Room
import app.plansnap.core.model.RoomId
import app.plansnap.core.model.WallId
import app.plansnap.feature.editor.state.CoreIntent
import app.plansnap.feature.editor.state.EditorEffect
import app.plansnap.feature.editor.state.EditorIntent
import app.plansnap.feature.editor.state.EditorRig
import app.plansnap.feature.editor.state.EditorViewModel
import app.plansnap.feature.editor.state.ROOM_ID
import app.plansnap.feature.editor.state.SaveStatus
import app.plansnap.feature.editor.state.emptyProject
import app.plansnap.feature.editor.state.openProject
import app.plansnap.testing.fixtures.fakes.FakeProjectSaver
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The state behind the undo and redo buttons and the edge cases of both operations, with the real history. */
@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {
    private fun TestScope.opened(project: Project): Pair<EditorRig, EditorViewModel> {
        val rig = EditorRig(this, project)
        val viewModel = rig.viewModel()
        runCurrent()
        return rig to viewModel
    }

    private fun TestScope.send(
        viewModel: EditorViewModel,
        vararg intents: EditorIntent,
    ) {
        intents.forEach(viewModel::onIntent)
        runCurrent()
    }

    private fun TestScope.confirmWall(
        viewModel: EditorViewModel,
        text: String,
    ) = send(viewModel, CoreIntent.InputChanged(text), CoreIntent.ConfirmWall)

    private fun assertKeys(
        viewModel: EditorViewModel,
        undo: Boolean,
        redo: Boolean,
    ) {
        assertEquals("Undo enabled", undo, viewModel.state.value.canUndo)
        assertEquals("Redo enabled", redo, viewModel.state.value.canRedo)
    }

    @Test
    fun keysFollowTheHistoryAndANewCommandDiscardsRedo() =
        runTest {
            val (rig, viewModel) = opened(emptyProject())
            assertKeys(viewModel, undo = false, redo = false)
            confirmWall(viewModel, "400")
            assertKeys(viewModel, undo = true, redo = false)
            send(viewModel, CoreIntent.Undo)
            assertKeys(viewModel, undo = false, redo = true)
            send(viewModel, CoreIntent.Redo)
            assertKeys(viewModel, undo = true, redo = false)
            send(viewModel, CoreIntent.Undo)
            confirmWall(viewModel, "250")
            assertKeys(viewModel, undo = true, redo = false)
            rig.assertStored(viewModel.snapshot().project)
            assertEquals(
                listOf(Length(2500)),
                rig.stored.rooms
                    .single()
                    .walls
                    .map { it.length },
            )
        }

    @Test
    fun rejectedCommandKeepsTheRedoStep() =
        runTest {
            val (_, viewModel) = opened(emptyProject())
            confirmWall(viewModel, "400")
            send(viewModel, CoreIntent.Undo)
            viewModel.effects.test {
                viewModel.onIntent(CoreIntent.Submit(ProjectEdit.EditWall(ROOM_ID, WallId("missing"), Length(1000))))
                assertEquals(EditorEffect.Error(DomainError.NotFound(EntityKind.WALL, "missing")), awaitItem())
            }
            assertKeys(viewModel, undo = false, redo = true)
        }

    @Test
    fun undoAndRedoWithoutAStepNeitherChangeNorSaveAnything() =
        runTest {
            val (rig, viewModel) = opened(openProject())
            val before = viewModel.state.value
            send(viewModel, CoreIntent.Undo, CoreIntent.Redo)
            assertEquals(before, viewModel.state.value)
            assertTrue(rig.saver.savedRoomSets.isEmpty())
        }

    @Test
    fun moreTapsThanStepsStopAtBothEndsOfTheHistory() =
        runTest {
            val (rig, viewModel) = opened(emptyProject())
            val empty = viewModel.snapshot()
            confirmWall(viewModel, "400")
            confirmWall(viewModel, "300")
            val full = viewModel.snapshot()
            // All taps are queued before the first one runs, as with quick repeated tapping.
            send(viewModel, CoreIntent.Undo, CoreIntent.Undo, CoreIntent.Undo, CoreIntent.Undo)
            assertShows(empty, rig, viewModel, "After undoing")
            assertKeys(viewModel, undo = false, redo = true)
            send(viewModel, CoreIntent.Redo, CoreIntent.Redo, CoreIntent.Redo, CoreIntent.Redo)
            assertShows(full, rig, viewModel, "After redoing")
            assertKeys(viewModel, undo = true, redo = false)
        }

    @Test
    fun undoAndRedoSaveExactlyTheChangedRoom() =
        runTest {
            val (rig, viewModel) = opened(openProject())
            val other = RoomId("r2")
            send(viewModel, CoreIntent.Submit(ProjectEdit.AddRoom(Room(other))))
            val saved = rig.saver.savedRoomSets.size
            send(viewModel, CoreIntent.Undo)
            assertEquals(listOf(ROOM_ID), rig.stored.rooms.map { it.id })
            send(viewModel, CoreIntent.Redo)
            assertEquals(listOf(ROOM_ID, other), rig.stored.rooms.map { it.id })
            assertEquals(listOf(setOf(other), setOf(other)), rig.saver.savedRoomSets.drop(saved))
        }

    @Test
    fun undoingARemovalRestoresTheRoomAtItsPlaceInPlanAndStorage() =
        runTest {
            val (rig, viewModel) = opened(openProject())
            val other = RoomId("r2")
            send(viewModel, CoreIntent.Submit(ProjectEdit.AddRoom(Room(other))))
            val both = viewModel.snapshot()
            send(viewModel, CoreIntent.Submit(ProjectEdit.RemoveRoom(ROOM_ID)))
            assertEquals(other, viewModel.state.value.selectedRoomId)
            send(viewModel, CoreIntent.Undo)
            assertShows(both, rig, viewModel, "Removed room restored")
            assertEquals(
                listOf(ROOM_ID, other),
                viewModel
                    .snapshot()
                    .scene.rooms
                    .map { it.roomId },
            )
        }

    @Test
    fun undoingTheSelectedRoomSelectsARemainingOne() =
        runTest {
            val (_, viewModel) = opened(openProject())
            val other = RoomId("r2")
            send(viewModel, CoreIntent.Submit(ProjectEdit.AddRoom(Room(other))), CoreIntent.SelectRoom(other))
            send(viewModel, CoreIntent.Undo)
            assertEquals(ROOM_ID, viewModel.state.value.selectedRoomId)
        }

    @Test
    fun previewOfTheRunningInputFollowsUndoAndRedo() =
        runTest {
            val (_, viewModel) = opened(openProject())
            confirmWall(viewModel, "200")
            send(viewModel, CoreIntent.Undo, CoreIntent.InputChanged("150"))
            val preview = checkNotNull(viewModel.state.value.preview)
            assertEquals(4, preview.room.walls.size)
            send(viewModel, CoreIntent.Redo)
            assertEquals("150", viewModel.state.value.inputText)
            assertEquals(
                5,
                checkNotNull(viewModel.state.value.preview)
                    .room
                    .walls
                    .size,
            )
        }

    @Test
    fun failedSaveAfterUndoKeepsTheUndoneStateAndRetryStoresIt() =
        runTest {
            val (rig, viewModel) = opened(emptyProject())
            confirmWall(viewModel, "400")
            val withWall = viewModel.snapshot()
            val failure = DomainError.Storage(StorageProblem.WRITE_FAILED)
            rig.saver.failures.failNext(FakeProjectSaver.Operation.SAVE, failure)
            send(viewModel, CoreIntent.Undo)
            assertEquals(SaveStatus.Failed(failure), viewModel.state.value.save)
            assertTrue(
                viewModel
                    .snapshot()
                    .project.rooms
                    .single()
                    .walls
                    .isEmpty(),
            )
            rig.assertStored(withWall.project, "The failed save must leave the stored project")
            assertKeys(viewModel, undo = false, redo = true)
            send(viewModel, CoreIntent.RetrySave)
            assertTrue(viewModel.state.value.save is SaveStatus.Saved)
            rig.assertStored(viewModel.snapshot().project)
        }

    @Test
    fun aNewEditorKeepsTheSavedWallsButStartsWithoutUndoSteps() =
        runTest {
            val (rig, first) = opened(emptyProject())
            confirmWall(first, "400")
            val second = rig.viewModel()
            runCurrent()
            assertKeys(second, undo = false, redo = false)
            assertEquals(
                listOf(Length(4000)),
                second
                    .snapshot()
                    .project.rooms
                    .single()
                    .walls
                    .map { it.length },
            )
        }
}
