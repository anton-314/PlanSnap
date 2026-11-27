package app.plansnap.feature.editor.history

import app.plansnap.core.common.units.Angle
import app.plansnap.core.common.units.Length
import app.plansnap.core.common.units.TurnDirection
import app.plansnap.core.domain.command.ProjectEdit
import app.plansnap.core.domain.command.RoomDetails
import app.plansnap.core.model.AreaFactor
import app.plansnap.core.model.CornerSpec
import app.plansnap.core.model.Diagonal
import app.plansnap.core.model.DiagonalId
import app.plansnap.core.model.Room
import app.plansnap.core.model.RoomId
import app.plansnap.feature.editor.state.CoreIntent
import app.plansnap.feature.editor.state.EditorIntent
import app.plansnap.feature.editor.state.EditorRig
import app.plansnap.feature.editor.state.EditorViewModel
import app.plansnap.feature.editor.state.ROOM_ID
import app.plansnap.feature.editor.state.emptyProject
import app.plansnap.feature.editor.state.shownProposal
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * REQ-UX-005 through the view model with the real history, domain, evaluator and scene builder: any sequence of
 * actions can be taken back and restored completely, in the plan and in the repository.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HistorySessionTest {
    private val second = RoomId("r2")

    private fun TestScope.opened(): Pair<EditorRig, EditorViewModel> {
        val rig = EditorRig(this, emptyProject())
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

    /** A whole session, one entry per undo step. Later entries read the state the earlier ones produced. */
    private fun session(viewModel: EditorViewModel): List<() -> List<EditorIntent>> {
        fun wall(text: String) = listOf(CoreIntent.InputChanged(text), CoreIntent.ConfirmWall)

        fun edit(edit: ProjectEdit) = listOf(CoreIntent.Submit(edit))

        fun firstWallId() =
            checkNotNull(viewModel.state.value.selectedRoom)
                .walls
                .first()
                .id
        val details = RoomDetails("Kitchen", null, "tiled", Length(2400), AreaFactor(50))
        val diagonal = Diagonal(DiagonalId("d1"), 0, 2, Length(5000))
        return listOf(
            { wall("400") },
            { wall("300") },
            { wall("400") },
            { wall("297") },
            { edit(ProjectEdit.CloseRoom(ROOM_ID)) },
            {
                val proposal = viewModel.shownProposal()
                listOf(CoreIntent.AcceptAdjustment(ROOM_ID, proposal, proposal.requiredConfirmation))
            },
            { edit(ProjectEdit.RevertAdjustment(ROOM_ID)) },
            { edit(ProjectEdit.EditWall(ROOM_ID, firstWallId(), Length(4010))) },
            { edit(ProjectEdit.SetThickness(ROOM_ID, firstWallId(), Length(240))) },
            {
                val measured = CornerSpec.Measured(TurnDirection.LEFT, Angle(RIGHT_ANGLE_MILLI_DEGREES))
                edit(ProjectEdit.SetCorner(ROOM_ID, 1, measured))
            },
            { edit(ProjectEdit.AddDiagonal(ROOM_ID, diagonal)) },
            { edit(ProjectEdit.RemoveDiagonal(ROOM_ID, diagonal.id)) },
            { edit(ProjectEdit.AddOpening(ROOM_ID, door(firstWallId()))) },
            { edit(ProjectEdit.UpdateRoomDetails(ROOM_ID, details)) },
            { edit(ProjectEdit.SetProjectHeight(Length(2600))) },
            { edit(ProjectEdit.AddRoom(Room(second))) },
            { edit(ProjectEdit.MoveRoom(second, 6000.0, 1000.0, 0.0)) },
            { edit(ProjectEdit.RemoveRoom(ROOM_ID)) },
        )
    }

    @Test
    fun wholeSessionIsUndoneAndRedoneStepByStepInPlanAndStorage() =
        runTest {
            val (rig, viewModel) = opened()
            val snapshots = mutableListOf(viewModel.snapshot())
            session(viewModel).forEachIndexed { index, step ->
                send(viewModel, *step().toTypedArray())
                assertEquals("Step $index adds one undo step", index + 1, viewModel.state.value.undoDepth)
                snapshots += viewModel.snapshot()
                rig.assertStored(snapshots.last().project, "Step $index")
            }
            for (index in snapshots.lastIndex - 1 downTo 0) {
                send(viewModel, CoreIntent.Undo)
                assertShows(snapshots[index], rig, viewModel, "Undo to $index")
                assertEquals(snapshots.lastIndex - index, viewModel.state.value.redoDepth)
            }
            assertFalse(viewModel.state.value.canUndo)
            for (index in 1..snapshots.lastIndex) {
                send(viewModel, CoreIntent.Redo)
                assertShows(snapshots[index], rig, viewModel, "Redo to $index")
                assertEquals(index, viewModel.state.value.undoDepth)
            }
            assertFalse(viewModel.state.value.canRedo)
        }

    @Test
    fun randomSequencesOfEditsUndoAndRedoAlwaysMatchPlanStorageAndKeys() {
        listOf(1L, 2L, 3L).forEach { seed ->
            runTest {
                val (rig, viewModel) = opened()
                val random = Random(seed)
                val past = mutableListOf<Snapshot>()
                val future = mutableListOf<Snapshot>()
                var current = viewModel.snapshot()
                var undone = 0
                var redone = 0
                repeat(RANDOM_STEPS) { step ->
                    val message = "seed $seed, step $step"
                    when (random.nextInt(ACTION_KINDS)) {
                        0 -> {
                            send(viewModel, CoreIntent.Undo)
                            past.removeLastOrNull()?.let {
                                future += current
                                current = it
                                undone++
                            }
                        }

                        1 -> {
                            send(viewModel, CoreIntent.Redo)
                            future.removeLastOrNull()?.let {
                                past += current
                                current = it
                                redone++
                            }
                        }

                        else -> {
                            val depth = viewModel.state.value.undoDepth
                            send(viewModel, CoreIntent.Submit(randomEdit(random, current.project, "g$step")))
                            // A rejected command changes neither project nor history.
                            if (viewModel.state.value.undoDepth > depth) {
                                past += current
                                future.clear()
                                current = viewModel.snapshot()
                            }
                        }
                    }
                    assertShows(current, rig, viewModel, message)
                    assertEquals(message, past.size, viewModel.state.value.undoDepth)
                    assertEquals(message, future.size, viewModel.state.value.redoDepth)
                }
                assertTrue("seed $seed must exercise undo and redo", undone > 0 && redone > 0)
            }
        }
    }

    private companion object {
        const val RANDOM_STEPS = 150
        const val ACTION_KINDS = 8
        const val RIGHT_ANGLE_MILLI_DEGREES = 90_000
    }
}
