package app.plansnap.feature.editor.opening

import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.result.DomainRule
import app.plansnap.core.common.units.Length
import app.plansnap.core.common.units.LengthUnit
import app.plansnap.core.domain.command.ProjectEdit
import app.plansnap.core.domain.evaluation.AdjustmentConfirmation
import app.plansnap.core.domain.opening.DefaultOpeningRules
import app.plansnap.core.model.Opening
import app.plansnap.core.model.OpeningStatus
import app.plansnap.core.model.OpeningType
import app.plansnap.core.model.WallId
import app.plansnap.feature.editor.state.CoreIntent
import app.plansnap.feature.editor.state.EditorIntent
import app.plansnap.feature.editor.state.EditorRig
import app.plansnap.feature.editor.state.EditorViewModel
import app.plansnap.feature.editor.state.ROOM_ID
import app.plansnap.feature.editor.state.closedProject
import app.plansnap.feature.editor.state.shownProposal
import app.plansnap.testing.fixtures.runtime.SequentialIdGenerator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Locale

/** The opening sheets against the real view model, domain and evaluator, with fake storage. */
@OptIn(ExperimentalCoroutinesApi::class)
class OpeningFlowTest {
    private var previous: Locale = Locale.getDefault()

    @Before
    fun useEnglishNumbers() {
        previous = Locale.getDefault()
        Locale.setDefault(Locale.US)
    }

    @After
    fun restoreLocale() {
        Locale.setDefault(previous)
    }

    private val w0 = WallId("w0")

    private fun TestScope.opened(lastMm: Long = 3000): Pair<EditorRig, EditorViewModel> {
        val rig = EditorRig(this, closedProject(lastMm))
        rig.handlers += OpeningHandler(SequentialIdGenerator("gen"), DefaultOpeningRules())
        val viewModel = rig.viewModel()
        runCurrent()
        return rig to viewModel
    }

    private fun TestScope.run(
        viewModel: EditorViewModel,
        vararg intents: EditorIntent,
    ) {
        intents.forEach(viewModel::onIntent)
        runCurrent()
    }

    private val EditorRig.openings: List<Opening> get() = stored.rooms.single().openings

    private val EditorViewModel.opening: OpeningState get() = state.value.section(OpeningState::class) ?: OpeningState()

    @Test
    fun `given a door when it is added then it is stored, evaluated as valid and undo and redo restore it`() =
        runTest {
            val (rig, viewModel) = opened()
            run(viewModel, OpeningIntent.Add(ROOM_ID, w0, OpeningType.DOOR), OpeningIntent.Confirm)
            val stored = rig.openings.single()
            assertEquals(Length(900), stored.width)
            assertEquals(
                OpeningStatus.VALID,
                viewModel.state.value.evaluations
                    .getValue(ROOM_ID)
                    .openingStatuses[stored.id],
            )
            run(viewModel, CoreIntent.Undo)
            assertTrue(rig.openings.isEmpty())
            run(viewModel, CoreIntent.Redo)
            assertEquals(listOf(stored), rig.openings)
        }

    @Test
    fun `given a stored opening when it is changed then one undo restores the old values`() =
        runTest {
            val (rig, viewModel) = opened()
            run(viewModel, OpeningIntent.Add(ROOM_ID, w0, OpeningType.WINDOW), OpeningIntent.Confirm)
            val before = rig.openings.single()
            run(
                viewModel,
                OpeningIntent.Edit(ROOM_ID, before.id),
                OpeningIntent.WidthChanged("150"),
                OpeningIntent.WallChanged(WallId("w1")),
                OpeningIntent.Confirm,
            )
            assertEquals(Length(1500), rig.openings.single().width)
            assertEquals(WallId("w1"), rig.openings.single().wallId)
            run(viewModel, CoreIntent.Undo)
            assertEquals(listOf(before), rig.openings)
        }

    @Test
    fun `given a stored opening when it is deleted then it is gone and undo brings it back`() =
        runTest {
            val (rig, viewModel) = opened()
            run(viewModel, OpeningIntent.Add(ROOM_ID, w0, OpeningType.PASSAGE), OpeningIntent.Confirm)
            val before = rig.openings.single()
            run(
                viewModel,
                OpeningIntent.Edit(ROOM_ID, before.id),
                OpeningIntent.RequestDelete,
                OpeningIntent.ConfirmDelete,
            )
            assertTrue(rig.openings.isEmpty())
            assertNull(viewModel.opening.sheet)
            run(viewModel, CoreIntent.Undo)
            assertEquals(listOf(before), rig.openings)
        }

    @Test
    fun `given a conflict when the form is confirmed then nothing is stored and the sheet stays open`() =
        runTest {
            val (rig, viewModel) = opened()
            run(
                viewModel,
                OpeningIntent.Add(ROOM_ID, w0, OpeningType.DOOR),
                OpeningIntent.WidthChanged("500"),
                OpeningIntent.Confirm,
            )
            assertTrue(rig.openings.isEmpty())
            assertEquals(DomainError.RuleViolation(DomainRule.OPENING_OUT_OF_BOUNDS), viewModel.opening.conflict)
            assertTrue(viewModel.opening.sheet is OpeningSheet.Form)
        }

    @Test
    fun `given two doors when the second overlaps the first then the command is never reached`() =
        runTest {
            val (rig, viewModel) = opened()
            run(viewModel, OpeningIntent.Add(ROOM_ID, w0, OpeningType.DOOR), OpeningIntent.Confirm)
            run(
                viewModel,
                OpeningIntent.Add(ROOM_ID, w0, OpeningType.DOOR),
                OpeningIntent.OffsetChanged("50"),
                OpeningIntent.Confirm,
            )
            assertEquals(1, rig.openings.size)
            assertEquals(DomainError.RuleViolation(DomainRule.OPENING_OVERLAP), viewModel.opening.conflict)
        }

    @Test
    fun `given a wall that is shortened when its opening no longer fits then it stays stored and is reported`() =
        runTest {
            val (rig, viewModel) = opened()
            run(viewModel, OpeningIntent.Add(ROOM_ID, w0, OpeningType.DOOR), OpeningIntent.Confirm)
            val stored = rig.openings.single()
            run(viewModel, CoreIntent.Submit(ProjectEdit.EditWall(ROOM_ID, w0, Length(500))))
            assertEquals(listOf(stored), rig.openings)
            assertEquals(
                OpeningStatus.OUT_OF_BOUNDS,
                viewModel.state.value.evaluations
                    .getValue(ROOM_ID)
                    .openingStatuses[stored.id],
            )
            run(viewModel, OpeningIntent.Edit(ROOM_ID, stored.id))
            assertEquals(DomainError.RuleViolation(DomainRule.OPENING_OUT_OF_BOUNDS), viewModel.opening.conflict)
        }

    @Test
    fun `given an accepted adjustment that shortens a wall when its opening is edited then the conflict is shown`() =
        runTest {
            val (rig, viewModel) = opened(lastMm = 3060)
            // One door at the very end of every wall, as far as the closure limit lets it reach: whichever wall the
            // adjustment shortens by more than that, a door ends beyond it.
            val ends = mapOf("w0" to 4000L, "w1" to 3000L, "w2" to 4000L, "w3" to 3060L)
            ends.forEach { (wall, end) ->
                run(
                    viewModel,
                    OpeningIntent.Add(ROOM_ID, WallId(wall), OpeningType.DOOR),
                    OpeningIntent.OffsetChanged(
                        OpeningDrafts.format(Length(end + CLOSURE_MM - DOOR_MM), LengthUnit.CENTIMETER),
                    ),
                    OpeningIntent.Confirm,
                )
            }
            assertEquals(ends.size, rig.openings.size)
            assertTrue(
                viewModel.state.value.evaluations.getValue(ROOM_ID).openingStatuses.values.all {
                    it ==
                        OpeningStatus.VALID
                },
            )
            run(
                viewModel,
                CoreIntent.AcceptAdjustment(ROOM_ID, viewModel.shownProposal(), AdjustmentConfirmation.SIMPLE),
            )
            val statuses =
                viewModel.state.value.evaluations
                    .getValue(ROOM_ID)
                    .openingStatuses
            assertEquals(ends.size, rig.openings.size)
            val outside = rig.openings.first { statuses[it.id] == OpeningStatus.OUT_OF_BOUNDS }
            run(viewModel, OpeningIntent.Edit(ROOM_ID, outside.id))
            assertEquals(DomainError.RuleViolation(DomainRule.OPENING_OUT_OF_BOUNDS), viewModel.opening.conflict)
        }

    private companion object {
        const val DOOR_MM = 900L
        const val CLOSURE_MM = 1L
    }
}
