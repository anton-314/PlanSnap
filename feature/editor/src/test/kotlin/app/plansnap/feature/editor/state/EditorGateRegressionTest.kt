package app.plansnap.feature.editor.state

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.result.DomainResult
import app.plansnap.core.common.result.DomainRule
import app.plansnap.core.common.runtime.ApplicationScope
import app.plansnap.core.common.units.Length
import app.plansnap.core.domain.command.ProjectEdit
import app.plansnap.core.domain.evaluation.AdjustmentConfirmation
import app.plansnap.core.domain.repository.ProjectSaver
import app.plansnap.core.model.Project
import app.plansnap.core.model.RoomId
import app.plansnap.core.model.ShapeStatus
import app.plansnap.core.model.WallId
import app.plansnap.testing.fixtures.fakes.FakeEditorDraftRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.concurrent.thread
import kotlin.coroutines.CoroutineContext

/** Regression tests for the findings H1, M1 and M4 of the wave 3 gate review. */
@OptIn(ExperimentalCoroutinesApi::class)
class EditorGateRegressionTest {
    @Test
    fun `given a displayed simple proposal when the room changes but still needs adjustment then it is not accepted`() =
        runTest {
            val rig = EditorRig(this, closedProject(2970))
            val viewModel = rig.viewModel()
            runCurrent()
            val shown = viewModel.shownProposal()
            viewModel.effects.test {
                viewModel.onIntent(CoreIntent.Submit(ProjectEdit.EditWall(ROOM_ID, WallId("w3"), Length(2950))))
                viewModel.onIntent(CoreIntent.AcceptAdjustment(ROOM_ID, shown, AdjustmentConfirmation.SIMPLE))
                runCurrent()
                assertEquals(
                    EditorEffect.Error(DomainError.RuleViolation(DomainRule.ADJUSTMENT_NOT_AVAILABLE)),
                    awaitItem(),
                )
            }
            val state = viewModel.state.value
            assertNotEquals(shown, viewModel.shownProposal())
            assertNotEquals(ShapeStatus.ADJUSTED, state.evaluations.getValue(ROOM_ID).status)
            assertNull(
                state.project!!
                    .rooms
                    .single()
                    .adjustment,
            )
            assertNull(
                rig.stored.rooms
                    .single()
                    .adjustment,
            )
        }

    @Test
    fun `given the first wall is still saving when OK is tapped again then the wall is added only once`() =
        runTest {
            val rig = EditorRig(this, emptyProject())
            val gate = CompletableDeferred<Unit>()
            val slowSaver =
                object : ProjectSaver {
                    override suspend fun save(
                        project: Project,
                        changedRooms: Set<RoomId>,
                    ): DomainResult<Long> {
                        gate.await()
                        return rig.saver.save(project, changedRooms)
                    }
                }
            val viewModel = rig.viewModel(saver = slowSaver)
            runCurrent()
            viewModel.onIntent(CoreIntent.InputChanged("400"))
            viewModel.onIntent(CoreIntent.ConfirmWall)
            runCurrent()
            assertEquals("", viewModel.state.value.inputText)
            viewModel.effects.test {
                viewModel.onIntent(CoreIntent.ConfirmWall)
                gate.complete(Unit)
                runCurrent()
                awaitItem()
            }
            assertEquals(
                listOf(4000L),
                viewModel.state.value.project!!
                    .rooms
                    .single()
                    .walls
                    .map { it.length.mm },
            )
        }

    @Test
    fun `given a store update from another thread then the saved state is written only on the screen dispatcher`() {
        val main = QueueDispatcher()
        val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val screenScope = CoroutineScope(SupervisorJob() + main)
        val handle = SavedStateHandle()
        val store =
            EditorStore(
                initial = EditorUiState(),
                handle = handle,
                drafts = FakeEditorDraftRepository(),
                applicationScope = ApplicationScope(appScope),
                scope = screenScope,
                submitEdit = {},
            )
        main.runQueued()
        thread { store.update { it.copy(inputText = "abc") } }.join()
        assertNull("written off the main dispatcher", handle.get<String>(EditorStore.KEY_INPUT_TEXT))
        main.runQueued()
        assertEquals("abc", handle.get<String>(EditorStore.KEY_INPUT_TEXT))
        appScope.cancel()
        screenScope.cancel()
    }

    /** Dispatcher that only runs its tasks when the test calls [runQueued], like a main thread of its own. */
    private class QueueDispatcher : CoroutineDispatcher() {
        private val queue = ConcurrentLinkedQueue<Runnable>()

        override fun dispatch(
            context: CoroutineContext,
            block: Runnable,
        ) {
            queue += block
        }

        fun runQueued() {
            while (true) queue.poll()?.run() ?: break
        }
    }
}
