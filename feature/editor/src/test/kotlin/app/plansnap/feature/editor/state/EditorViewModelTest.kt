package app.plansnap.feature.editor.state

import app.cash.turbine.TurbineTestContext
import app.cash.turbine.test
import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.result.DomainRule
import app.plansnap.core.common.result.EntityKind
import app.plansnap.core.common.result.InvalidValueReason
import app.plansnap.core.common.result.StorageProblem
import app.plansnap.core.common.result.ValueField
import app.plansnap.core.common.units.Length
import app.plansnap.core.domain.command.ProjectEdit
import app.plansnap.core.domain.evaluation.AdjustmentConfirmation
import app.plansnap.core.domain.repository.EditorDraft
import app.plansnap.core.model.ShapeStatus
import app.plansnap.core.model.WallId
import app.plansnap.testing.fixtures.fakes.FakeEditorDraftRepository
import app.plansnap.testing.fixtures.fakes.FakeProjectRepository
import app.plansnap.testing.fixtures.fakes.FakeProjectSaver
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EditorViewModelTest {
    private suspend fun TurbineTestContext<EditorUiState>.awaitState(
        predicate: (EditorUiState) -> Boolean,
    ): EditorUiState {
        while (true) {
            val item = awaitItem()
            if (predicate(item)) return item
        }
    }

    private fun TestScope.opened(
        project: app.plansnap.core.model.Project,
        configure: EditorRig.() -> Unit = {},
    ): Pair<EditorRig, EditorViewModel> {
        val rig = EditorRig(this, project).apply(configure)
        val viewModel = rig.viewModel()
        runCurrent()
        return rig to viewModel
    }

    @Test
    fun loadedStateHoldsProjectEvaluationAndScene() =
        runTest {
            val (_, viewModel) = opened(closedProject(3000))
            val state = viewModel.state.value
            assertEquals(EditorLoadState.Loaded, state.load)
            assertEquals(ROOM_ID, state.selectedRoomId)
            assertEquals(ShapeStatus.CONFIRMED, state.evaluations.getValue(ROOM_ID).status)
            assertEquals(listOf(ROOM_ID), state.scene?.rooms?.map { it.roomId })
            assertNotNull(state.scene?.bounds)
        }

    @Test
    fun typedLengthShowsLivePreviewOnEveryInput() =
        runTest {
            val (_, viewModel) = opened(openProject())
            viewModel.state.test {
                awaitState { it.load == EditorLoadState.Loaded && it.scene != null }
                viewModel.onIntent(CoreIntent.InputChanged("300"))
                val preview = awaitState { it.preview != null }.preview!!
                assertEquals(4, preview.room.walls.size)
                assertEquals(
                    Length(3000),
                    preview.room.walls
                        .last()
                        .length,
                )
                assertTrue(preview.scene.elements.isNotEmpty())
                viewModel.onIntent(CoreIntent.InputChanged("250"))
                assertEquals(
                    Length(2500),
                    awaitState {
                        it.preview
                            ?.room
                            ?.walls
                            ?.last()
                            ?.length == Length(2500)
                    }.preview!!.room.walls.last().length,
                )
                viewModel.onIntent(CoreIntent.InputChanged("2x"))
                val broken = awaitState { it.inputError != null }
                assertNull(broken.preview)
                assertEquals(
                    DomainError.InvalidValue(ValueField.WALL_LENGTH, InvalidValueReason.MALFORMED),
                    broken.inputError,
                )
                viewModel.onIntent(CoreIntent.InputChanged(""))
                assertNull(awaitState { it.inputError == null && it.inputText.isEmpty() }.preview)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun newerInputCancelsTheStalePreview() =
        runTest {
            val (rig, viewModel) = opened(openProject())
            val gate = CompletableDeferred<Unit>()
            rig.evaluator.gate =
                { room -> if (room.walls.size == 4 && room.walls.last().length == Length(1000)) gate else null }
            val seen = mutableListOf<Length?>()
            backgroundScope.launch {
                viewModel.state.collect {
                    seen +=
                        it.preview
                            ?.room
                            ?.walls
                            ?.last()
                            ?.length
                }
            }
            viewModel.onIntent(CoreIntent.InputChanged("100"))
            runCurrent()
            assertNull(viewModel.state.value.preview)
            viewModel.onIntent(CoreIntent.InputChanged("200"))
            runCurrent()
            assertEquals(
                Length(2000),
                viewModel.state.value.preview
                    ?.room
                    ?.walls
                    ?.last()
                    ?.length,
            )
            assertEquals(1, rig.evaluator.cancelledCalls)
            gate.complete(Unit)
            runCurrent()
            assertFalse(Length(1000) in seen)
            assertEquals(
                Length(2000),
                viewModel.state.value.preview
                    ?.room
                    ?.walls
                    ?.last()
                    ?.length,
            )
        }

    @Test
    fun confirmingAWallSavesItAndClearsInputAndDraft() =
        runTest {
            val (rig, viewModel) = opened(emptyProject())
            viewModel.onIntent(CoreIntent.InputChanged("400"))
            runCurrent()
            assertEquals(EditorDraft(PROJECT_ID, ROOM_ID, "400"), rig.drafts.storedDrafts[PROJECT_ID])
            viewModel.onIntent(CoreIntent.ConfirmWall)
            runCurrent()
            val state = viewModel.state.value
            assertEquals(
                listOf(Length(4000)),
                state.project
                    ?.rooms
                    ?.single()
                    ?.walls
                    ?.map { it.length },
            )
            assertEquals(state.project?.rooms, rig.stored.rooms)
            assertTrue(state.save is SaveStatus.Saved)
            assertEquals("", state.inputText)
            assertEquals(1, state.undoDepth)
            assertTrue(state.canUndo)
            assertNull(rig.drafts.storedDrafts[PROJECT_ID])
            assertEquals(
                Length(4000),
                rig.stored.rooms
                    .single()
                    .walls
                    .single()
                    .length,
            )
        }

    @Test
    fun confirmedCornerOfTheSecondWallFollowsTheChosenDirection() =
        runTest {
            val (rig, viewModel) = opened(emptyProject())
            viewModel.onIntent(CoreIntent.InputChanged("400"))
            viewModel.onIntent(CoreIntent.ConfirmWall)
            runCurrent()
            viewModel.onIntent(CoreIntent.InputChanged("300"))
            viewModel.onIntent(CoreIntent.ConfirmWall)
            runCurrent()
            val room = rig.stored.rooms.single()
            assertEquals(2, room.walls.size)
            assertEquals(2, room.corners.size)
            assertEquals(app.plansnap.core.model.CornerSpec.Unspecified, room.corners.first())
            assertEquals(EditorUiState.DEFAULT_NEXT_CORNER, room.corners.last())
        }

    @Test
    fun invalidInputOnConfirmIsReportedAndNothingIsStored() =
        runTest {
            val (rig, viewModel) = opened(emptyProject())
            viewModel.effects.test {
                viewModel.onIntent(CoreIntent.InputChanged("0"))
                viewModel.onIntent(CoreIntent.ConfirmWall)
                assertEquals(
                    EditorEffect.Error(DomainError.InvalidValue(ValueField.WALL_LENGTH, InvalidValueReason.TOO_SMALL)),
                    awaitItem(),
                )
                runCurrent()
                assertTrue(
                    rig.stored.rooms
                        .single()
                        .walls
                        .isEmpty(),
                )
                assertTrue(rig.saver.savedRoomSets.isEmpty())
            }
        }

    @Test
    fun commandFailureIsAnEffectAndChangesNothing() =
        runTest {
            val (rig, viewModel) = opened(closedProject(3000))
            viewModel.effects.test {
                viewModel.onIntent(CoreIntent.Submit(ProjectEdit.EditWall(ROOM_ID, WallId("missing"), Length(1000))))
                assertEquals(EditorEffect.Error(DomainError.NotFound(EntityKind.WALL, "missing")), awaitItem())
            }
            assertEquals(0, viewModel.state.value.undoDepth)
            assertTrue(rig.saver.savedRoomSets.isEmpty())
        }

    @Test
    fun wallBeyondTheLimitIsRejectedByTheCommandAndReported() =
        runTest {
            val (rig, viewModel) = opened(openProject())
            viewModel.effects.test {
                viewModel.onIntent(CoreIntent.InputChanged("99999999"))
                viewModel.onIntent(CoreIntent.ConfirmWall)
                assertTrue(awaitItem() is EditorEffect.Error)
            }
            assertEquals(
                3,
                rig.stored.rooms
                    .single()
                    .walls.size,
            )
            assertEquals("99999999", viewModel.state.value.inputText)
        }

    @Test
    fun failedSaveIsStateAndRetryStoresThePendingChange() =
        runTest {
            val (rig, viewModel) = opened(emptyProject())
            val failure = DomainError.Storage(StorageProblem.WRITE_FAILED)
            rig.saver.failures.failNext(FakeProjectSaver.Operation.SAVE, failure)
            viewModel.onIntent(CoreIntent.InputChanged("400"))
            viewModel.onIntent(CoreIntent.ConfirmWall)
            runCurrent()
            assertEquals(SaveStatus.Failed(failure), viewModel.state.value.save)
            assertEquals(
                1,
                viewModel.state.value.project
                    ?.rooms
                    ?.single()
                    ?.walls
                    ?.size,
            )
            assertTrue(
                rig.stored.rooms
                    .single()
                    .walls
                    .isEmpty(),
            )
            viewModel.onIntent(CoreIntent.RetrySave)
            runCurrent()
            assertTrue(viewModel.state.value.save is SaveStatus.Saved)
            assertEquals(
                1,
                rig.stored.rooms
                    .single()
                    .walls.size,
            )
        }

    @Test
    fun undoAndRedoUpdatePlanAndStorage() =
        runTest {
            val (rig, viewModel) = opened(emptyProject())
            viewModel.onIntent(CoreIntent.InputChanged("400"))
            viewModel.onIntent(CoreIntent.ConfirmWall)
            runCurrent()
            viewModel.onIntent(CoreIntent.Undo)
            runCurrent()
            assertTrue(
                rig.stored.rooms
                    .single()
                    .walls
                    .isEmpty(),
            )
            assertEquals(1, viewModel.state.value.redoDepth)
            assertEquals(
                0,
                viewModel.state.value.scene?.rooms?.single()?.elements?.count {
                    it is app.plansnap.core.drawing.scene.SceneElement.Fill
                },
            )
            viewModel.onIntent(CoreIntent.Redo)
            runCurrent()
            assertEquals(
                1,
                rig.stored.rooms
                    .single()
                    .walls.size,
            )
            assertFalse(viewModel.state.value.canRedo)
        }

    @Test
    fun acceptingTheAdjustmentEvaluatesTheRoomAgainJustBeforeConfirming() =
        runTest {
            val (rig, viewModel) = opened(closedProject(2970))
            assertEquals(
                ShapeStatus.NEEDS_ADJUSTMENT,
                viewModel.state.value.evaluations
                    .getValue(ROOM_ID)
                    .status,
            )
            val callsBefore = rig.evaluator.calls.size
            viewModel.onIntent(CoreIntent.AcceptAdjustment(ROOM_ID, AdjustmentConfirmation.SIMPLE))
            runCurrent()
            assertTrue("The room must be evaluated again", rig.evaluator.calls.size > callsBefore)
            val state = viewModel.state.value
            assertNotNull(
                state.project
                    ?.rooms
                    ?.single()
                    ?.adjustment,
            )
            assertEquals(ShapeStatus.ADJUSTED, state.evaluations.getValue(ROOM_ID).status)
            assertNotNull(
                rig.stored.rooms
                    .single()
                    .adjustment,
            )
        }

    @Test
    fun aProposalThatWentStaleIsNotAccepted() =
        runTest {
            val (rig, viewModel) = opened(closedProject(2970))
            // The user changes the room, the screen still shows the proposal of the old state.
            viewModel.onIntent(CoreIntent.Submit(ProjectEdit.EditWall(ROOM_ID, WallId("w3"), Length(3000))))
            viewModel.onIntent(CoreIntent.AcceptAdjustment(ROOM_ID, AdjustmentConfirmation.SIMPLE))
            runCurrent()
            val room =
                viewModel.state.value.project
                    ?.rooms
                    ?.single()
            assertNull(room?.adjustment)
            assertEquals(Length(3000), room?.walls?.last()?.length)
            assertNull(
                rig.stored.rooms
                    .single()
                    .adjustment,
            )
        }

    @Test
    fun adjustmentOfARoomWithoutProposalIsReported() =
        runTest {
            val (_, viewModel) = opened(closedProject(3000))
            viewModel.effects.test {
                viewModel.onIntent(CoreIntent.AcceptAdjustment(ROOM_ID, AdjustmentConfirmation.SIMPLE))
                assertEquals(
                    EditorEffect.Error(DomainError.RuleViolation(DomainRule.ADJUSTMENT_NOT_AVAILABLE)),
                    awaitItem(),
                )
            }
        }

    @Test
    fun missingProjectIsALoadFailureAndReloadRecovers() =
        runTest {
            val rig = EditorRig(this, emptyProject())
            val notFound = DomainError.Storage(StorageProblem.READ_FAILED)
            rig.projects.failures.failNext(FakeProjectRepository.Operation.GET, notFound)
            val viewModel = rig.viewModel()
            runCurrent()
            assertEquals(EditorLoadState.Failed(notFound), viewModel.state.value.load)
            viewModel.effects.test {
                viewModel.onIntent(CoreIntent.InputChanged("1"))
                assertEquals(
                    EditorEffect.Error(DomainError.NotFound(EntityKind.PROJECT, PROJECT_ID.value)),
                    awaitItem(),
                )
            }
            viewModel.onIntent(ReloadProject)
            runCurrent()
            assertEquals(EditorLoadState.Loaded, viewModel.state.value.load)
        }

    @Test
    fun missingProjectIdArgumentIsALoadFailure() =
        runTest {
            val rig = EditorRig(this, emptyProject())
            val viewModel = rig.viewModel(androidx.lifecycle.SavedStateHandle())
            runCurrent()
            assertEquals(
                EditorLoadState.Failed(DomainError.NotFound(EntityKind.PROJECT, "")),
                viewModel.state.value.load,
            )
        }

    @Test
    fun draftFailureIsVisibleInTheState() =
        runTest {
            val (rig, viewModel) = opened(emptyProject())
            val failure = DomainError.Storage(StorageProblem.NO_SPACE)
            rig.drafts.failures.failNext(FakeEditorDraftRepository.Operation.SAVE, failure)
            viewModel.onIntent(CoreIntent.InputChanged("12"))
            runCurrent()
            assertEquals(failure, viewModel.state.value.draftError)
            viewModel.onIntent(CoreIntent.InputChanged("123"))
            runCurrent()
            assertNull(viewModel.state.value.draftError)
            assertEquals("123", rig.drafts.storedDrafts[PROJECT_ID]?.inputText)
        }

    @Test
    fun unknownRoomSelectionIsRejected() =
        runTest {
            val (_, viewModel) = opened(emptyProject())
            viewModel.effects.test {
                viewModel.onIntent(
                    CoreIntent.SelectRoom(
                        app.plansnap.core.model
                            .RoomId("other"),
                    ),
                )
                assertEquals(EditorEffect.Error(DomainError.NotFound(EntityKind.ROOM, "other")), awaitItem())
            }
            assertEquals(ROOM_ID, viewModel.state.value.selectedRoomId)
        }

    @Test
    fun aHandlerConsumesItsOwnIntentAndUsesTheHost() =
        runTest {
            data class Rename(
                val length: Length,
            ) : EditorIntent
            val (rig, viewModel) =
                opened(closedProject(3000)) {
                    handlers +=
                        EditorHandler { intent, host ->
                            (intent as? Rename)?.let {
                                host.submit(ProjectEdit.EditWall(ROOM_ID, WallId("w3"), it.length))
                                true
                            } ?: false
                        }
                }
            viewModel.onIntent(Rename(Length(3100)))
            runCurrent()
            assertEquals(
                Length(3100),
                rig.stored.rooms
                    .single()
                    .walls
                    .last()
                    .length,
            )
        }

    @Test(expected = IllegalStateException::class)
    fun anIntentWithoutHandlerFailsLoudly() =
        runTest {
            val (_, viewModel) = opened(emptyProject())
            viewModel.onIntent(object : EditorIntent {})
        }
}
