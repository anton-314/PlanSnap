package app.plansnap.feature.editor.shape

import app.cash.turbine.test
import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.result.DomainRule
import app.plansnap.core.common.units.Angle
import app.plansnap.core.common.units.Length
import app.plansnap.core.common.units.TurnDirection
import app.plansnap.core.domain.command.ProjectEdit
import app.plansnap.core.domain.evaluation.AdjustmentConfirmation
import app.plansnap.core.domain.evaluation.RoomEvaluation
import app.plansnap.core.domain.repository.AppSettings
import app.plansnap.core.geometry.primitives.ClosureTolerance
import app.plansnap.core.model.ConflictReason
import app.plansnap.core.model.CornerSpec
import app.plansnap.core.model.Project
import app.plansnap.core.model.ShapeStatus
import app.plansnap.core.model.WallId
import app.plansnap.feature.editor.state.CoreIntent
import app.plansnap.feature.editor.state.EditorEffect
import app.plansnap.feature.editor.state.EditorIntent
import app.plansnap.feature.editor.state.EditorRig
import app.plansnap.feature.editor.state.EditorViewModel
import app.plansnap.feature.editor.state.ROOM_ID
import app.plansnap.feature.editor.state.closedProject
import app.plansnap.feature.editor.state.emptyProject
import app.plansnap.feature.editor.state.openProject
import app.plansnap.feature.editor.state.shownProposal
import app.plansnap.testing.fixtures.runtime.SequentialIdGenerator
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Locale

/** The shape sheets against the real view model, domain and evaluator, with fake storage. */
@OptIn(ExperimentalCoroutinesApi::class)
class ShapeFlowTest {
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

    private fun TestScope.opened(project: Project): Pair<EditorRig, EditorViewModel> {
        val rig = EditorRig(this, project)
        rig.handlers += ShapeHandler(SequentialIdGenerator("gen"))
        val viewModel = rig.viewModel()
        runCurrent()
        return rig to viewModel
    }

    private fun EditorViewModel.send(vararg intents: EditorIntent) = intents.forEach(::onIntent)

    private fun TestScope.type(
        viewModel: EditorViewModel,
        text: String,
    ) {
        viewModel.onIntent(ShapeIntent.SheetTextChanged(text))
        runCurrent()
    }

    private val EditorViewModel.evaluation get() = state.value.evaluations.getValue(ROOM_ID)

    @Test
    fun `given three walls when the room is closed then it is evaluated as confirmed`() =
        runTest {
            val (rig, viewModel) = opened(openProject())
            viewModel.onIntent(CoreIntent.InputChanged("300"))
            viewModel.onIntent(CoreIntent.ConfirmWall)
            runCurrent()
            assertEquals(ShapeStatus.OPEN, viewModel.evaluation.status)
            viewModel.onIntent(ShapeIntent.CloseRoom)
            runCurrent()
            assertTrue(
                rig.stored.rooms
                    .single()
                    .isClosed,
            )
            assertEquals(ShapeStatus.CONFIRMED, viewModel.evaluation.status)
        }

    @Test
    fun `given two walls when closing then the rejected command is reported with its rule`() =
        runTest {
            val (_, viewModel) = opened(emptyProject())
            viewModel.effects.test {
                viewModel.onIntent(ShapeIntent.CloseRoom)
                runCurrent()
                assertEquals(
                    EditorEffect.Error(DomainError.RuleViolation(DomainRule.TOO_FEW_WALLS)),
                    awaitItem(),
                )
            }
        }

    @Test
    fun `given a measured corner when the next wall is confirmed then the stored corner carries the angle`() =
        runTest {
            val (rig, viewModel) = opened(openProject())
            viewModel.send(
                ShapeIntent.OpenCorner(),
                ShapeIntent.SheetDirectionChanged(TurnDirection.RIGHT),
            )
            type(viewModel, "75,5")
            viewModel.onIntent(ShapeIntent.ConfirmSheet)
            viewModel.send(CoreIntent.InputChanged("300"), CoreIntent.ConfirmWall)
            runCurrent()
            val corner =
                rig.stored.rooms
                    .single()
                    .corners
                    .last()
            assertEquals(CornerSpec.Measured(TurnDirection.RIGHT, Angle(75_500)), corner)
            assertNull(
                viewModel.state.value
                    .section(ShapeState::class)
                    ?.sheet,
            )
        }

    @Test
    fun `given a typed thickness when the next wall is confirmed then the wall has it`() =
        runTest {
            val (rig, viewModel) = opened(openProject())
            viewModel.onIntent(ShapeIntent.OpenThickness)
            type(viewModel, "25")
            viewModel.onIntent(ShapeIntent.ConfirmSheet)
            viewModel.send(CoreIntent.InputChanged("300"), CoreIntent.ConfirmWall)
            runCurrent()
            assertEquals(
                Length(250),
                rig.stored.rooms
                    .single()
                    .walls
                    .last()
                    .thickness,
            )
            assertEquals(Length(250), rig.settingsRepository.current.lastThickness)
        }

    @Test
    fun `given a diagonal when confirmed then it is stored and the room evaluation includes it`() =
        runTest {
            val (rig, viewModel) = opened(closedProject(3000))
            viewModel.onIntent(ShapeIntent.OpenDiagonal(0, 2))
            type(viewModel, "500")
            viewModel.onIntent(ShapeIntent.ConfirmSheet)
            runCurrent()
            val diagonal =
                rig.stored.rooms
                    .single()
                    .diagonals
                    .single()
            assertEquals(Triple(0, 2, Length(5000)), Triple(diagonal.fromCorner, diagonal.toCorner, diagonal.length))
            assertEquals(ShapeStatus.CONFIRMED, viewModel.evaluation.status)
        }

    @Test
    fun `given an impossible diagonal when confirmed then the conflict is explained by the evaluation`() =
        runTest {
            val (_, viewModel) = opened(closedProject(3000))
            viewModel.onIntent(ShapeIntent.OpenDiagonal(0, 2))
            type(viewModel, "5000")
            viewModel.onIntent(ShapeIntent.ConfirmSheet)
            runCurrent()
            val conflict = viewModel.evaluation as RoomEvaluation.Conflict
            assertEquals(ConflictReason.DIAGONAL_IMPOSSIBLE, conflict.reason)
        }

    @Test
    fun `given a small gap when the proposal is accepted then it is adjusted and can be reverted`() =
        runTest {
            val (rig, viewModel) = opened(closedProject(3030))
            assertEquals(ShapeStatus.NEEDS_ADJUSTMENT, viewModel.evaluation.status)
            viewModel.onIntent(ShapeIntent.OpenAdjustment)
            viewModel.onIntent(
                CoreIntent.AcceptAdjustment(ROOM_ID, viewModel.shownProposal(), AdjustmentConfirmation.SIMPLE),
            )
            runCurrent()
            assertEquals(ShapeStatus.ADJUSTED, viewModel.evaluation.status)
            assertTrue(
                rig.stored.rooms
                    .single()
                    .adjustment != null,
            )
            assertEquals(
                3030L,
                rig.stored.rooms
                    .single()
                    .walls
                    .last()
                    .length.mm,
            )
            viewModel.onIntent(ShapeIntent.RevertAdjustment)
            runCurrent()
            assertEquals(ShapeStatus.NEEDS_ADJUSTMENT, viewModel.evaluation.status)
            assertNull(
                rig.stored.rooms
                    .single()
                    .adjustment,
            )
        }

    @Test
    fun `given a large correction when only simply confirmed then it is rejected`() =
        runTest {
            val (rig, viewModel) = opened(largeCorrectionProject())
            val proposal = (viewModel.evaluation as RoomEvaluation.NeedsAdjustment).proposal
            assertEquals(AdjustmentConfirmation.EXPLICIT, proposal.requiredConfirmation)
            viewModel.effects.test {
                viewModel.onIntent(
                    CoreIntent.AcceptAdjustment(ROOM_ID, viewModel.shownProposal(), AdjustmentConfirmation.SIMPLE),
                )
                runCurrent()
                assertEquals(
                    EditorEffect.Error(DomainError.RuleViolation(DomainRule.EXPLICIT_CONFIRMATION_REQUIRED)),
                    awaitItem(),
                )
            }
            assertNull(
                rig.stored.rooms
                    .single()
                    .adjustment,
            )
            viewModel.onIntent(
                CoreIntent.AcceptAdjustment(ROOM_ID, viewModel.shownProposal(), AdjustmentConfirmation.EXPLICIT),
            )
            runCurrent()
            assertEquals(ShapeStatus.ADJUSTED, viewModel.evaluation.status)
        }

    @Test
    fun `given a simple proposal when the tolerance tightens before accepting then it is judged again`() =
        runTest {
            val lenient = AppSettings(tolerance = ClosureTolerance(absoluteMm = 250))
            val rig = EditorRig(this, largeCorrectionProject(), lenient)
            rig.handlers += ShapeHandler(SequentialIdGenerator("gen"))
            val viewModel = rig.viewModel()
            runCurrent()
            val shown = viewModel.evaluation as RoomEvaluation.NeedsAdjustment
            assertEquals(AdjustmentConfirmation.SIMPLE, shown.proposal.requiredConfirmation)
            rig.settingsRepository.update { it.copy(tolerance = ClosureTolerance()) }
            runCurrent()
            viewModel.effects.test {
                viewModel.onIntent(
                    CoreIntent.AcceptAdjustment(ROOM_ID, viewModel.shownProposal(), AdjustmentConfirmation.SIMPLE),
                )
                runCurrent()
                assertEquals(
                    EditorEffect.Error(DomainError.RuleViolation(DomainRule.EXPLICIT_CONFIRMATION_REQUIRED)),
                    awaitItem(),
                )
            }
            assertNull(
                rig.stored.rooms
                    .single()
                    .adjustment,
            )
        }

    @Test
    fun `given a shown explicit proposal when the room changes but still needs adjustment then it is rejected`() =
        runTest {
            val (rig, viewModel) = opened(largeCorrectionProject())
            val shown = viewModel.shownProposal()
            assertEquals(AdjustmentConfirmation.EXPLICIT, shown.requiredConfirmation)
            viewModel.effects.test {
                viewModel.onIntent(CoreIntent.Submit(ProjectEdit.EditWall(ROOM_ID, WallId("w0"), Length(4160))))
                viewModel.onIntent(CoreIntent.AcceptAdjustment(ROOM_ID, shown, AdjustmentConfirmation.EXPLICIT))
                runCurrent()
                assertEquals(
                    EditorEffect.Error(DomainError.RuleViolation(DomainRule.ADJUSTMENT_NOT_AVAILABLE)),
                    awaitItem(),
                )
            }
            assertTrue(viewModel.evaluation is RoomEvaluation.NeedsAdjustment)
            assertNotEquals(shown, viewModel.shownProposal())
            assertNull(
                viewModel.state.value.project!!
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

    private fun largeCorrectionProject(): Project =
        closedProject(3000).copy(rooms = persistentListOf(largeCorrectionRoom().copy(id = ROOM_ID)))
}
