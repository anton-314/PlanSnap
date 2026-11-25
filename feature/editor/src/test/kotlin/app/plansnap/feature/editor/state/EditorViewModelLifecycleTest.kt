package app.plansnap.feature.editor.state

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.CreationExtras
import app.plansnap.core.common.units.Length
import app.plansnap.core.domain.repository.EditorDraft
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.reflect.KClass

@OptIn(ExperimentalCoroutinesApi::class)
class EditorViewModelLifecycleTest {
    private fun ViewModelStore.adopt(viewModel: EditorViewModel): EditorViewModel {
        val factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(
                    modelClass: KClass<T>,
                    extras: CreationExtras,
                ): T = viewModel as T
            }
        return ViewModelProvider.create(this, factory)[EditorViewModel::class]
    }

    @Test
    fun confirmedWallIsStillSavedAfterTheEditorIsLeft() =
        runTest {
            val rig = EditorRig(this, emptyProject())
            val store = ViewModelStore()
            val viewModel = store.adopt(rig.viewModel())
            runCurrent()
            viewModel.onIntent(CoreIntent.InputChanged("400"))
            viewModel.onIntent(CoreIntent.ConfirmWall)
            // The user leaves before the queued work ran: onCleared must not cancel it.
            store.clear()
            assertTrue(
                rig.stored.rooms
                    .single()
                    .walls
                    .isEmpty(),
            )
            runCurrent()
            assertEquals(
                listOf(Length(4000)),
                rig.stored.rooms
                    .single()
                    .walls
                    .map { it.length },
            )
        }

    @Test
    fun undoQueuedBeforeLeavingStillRuns() =
        runTest {
            val rig = EditorRig(this, emptyProject())
            val store = ViewModelStore()
            val viewModel = store.adopt(rig.viewModel())
            runCurrent()
            viewModel.onIntent(CoreIntent.InputChanged("400"))
            viewModel.onIntent(CoreIntent.ConfirmWall)
            runCurrent()
            viewModel.onIntent(CoreIntent.Undo)
            store.clear()
            runCurrent()
            assertTrue(
                rig.stored.rooms
                    .single()
                    .walls
                    .isEmpty(),
            )
        }

    @Test
    fun lastDraftIsStillWrittenAfterTheEditorIsLeft() =
        runTest {
            val rig = EditorRig(this, emptyProject())
            val store = ViewModelStore()
            val viewModel = store.adopt(rig.viewModel())
            runCurrent()
            viewModel.onIntent(CoreIntent.InputChanged("37"))
            store.clear()
            runCurrent()
            assertEquals("37", rig.drafts.storedDrafts[PROJECT_ID]?.inputText)
        }

    @Test
    fun recreatedViewModelRestoresTheSameState() =
        runTest {
            val rig = EditorRig(this, openProject())
            val handle = rig.handle()
            val first = rig.viewModel(handle)
            runCurrent()
            first.onIntent(CoreIntent.InputChanged("250"))
            first.onIntent(CoreIntent.ViewChanged(PlanView(1500.0, -200.0, 0.05)))
            runCurrent()
            val before = first.state.value
            // Process death: only the handle contents and the database survive.
            val restoredHandle = SavedStateHandle(handle.keys().associateWith { handle.get<Any>(it) })
            val second = rig.viewModel(restoredHandle)
            runCurrent()
            val after = second.state.value
            assertEquals("250", after.inputText)
            assertEquals(PlanView(1500.0, -200.0, 0.05), after.view)
            assertEquals(before.selectedRoomId, after.selectedRoomId)
            assertEquals(before.project, after.project)
            assertEquals(before.preview, after.preview)
            assertEquals(before.scene, after.scene)
            assertEquals(before.evaluations, after.evaluations)
        }

    @Test
    fun inputIsRestoredFromTheDraftWhenTheHandleIsEmpty() =
        runTest {
            val rig = EditorRig(this, openProject())
            rig.drafts.saveDraft(EditorDraft(PROJECT_ID, ROOM_ID, "310"))
            val viewModel = rig.viewModel()
            runCurrent()
            val state = viewModel.state.value
            assertEquals("310", state.inputText)
            assertEquals(ROOM_ID, state.selectedRoomId)
            assertEquals(
                Length(3100),
                state.preview
                    ?.room
                    ?.walls
                    ?.last()
                    ?.length,
            )
        }

    @Test
    fun confirmedWallsSurviveProcessDeathTogetherWithTheRunningInput() =
        runTest {
            val rig = EditorRig(this, emptyProject())
            val first = rig.viewModel()
            runCurrent()
            first.onIntent(CoreIntent.InputChanged("400"))
            first.onIntent(CoreIntent.ConfirmWall)
            runCurrent()
            first.onIntent(CoreIntent.InputChanged("12"))
            runCurrent()
            val second = rig.viewModel()
            runCurrent()
            assertEquals(
                listOf(Length(4000)),
                second.state.value.project
                    ?.rooms
                    ?.single()
                    ?.walls
                    ?.map { it.length },
            )
            assertEquals("12", second.state.value.inputText)
        }

    @Test
    fun handleWinsOverTheDatabaseDraftAndIsWrittenBack() =
        runTest {
            val rig = EditorRig(this, openProject())
            rig.drafts.saveDraft(EditorDraft(PROJECT_ID, ROOM_ID, "old"))
            val handle = rig.handle().also { it[EditorStore.KEY_INPUT_TEXT] = "newer" }
            val viewModel = rig.viewModel(handle)
            runCurrent()
            assertEquals("newer", viewModel.state.value.inputText)
            assertEquals("newer", rig.drafts.storedDrafts[PROJECT_ID]?.inputText)
        }
}
