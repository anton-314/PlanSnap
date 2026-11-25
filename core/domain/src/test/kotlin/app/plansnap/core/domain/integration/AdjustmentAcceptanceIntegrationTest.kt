package app.plansnap.core.domain.integration

import app.plansnap.core.common.result.DomainRule
import app.plansnap.core.domain.command.DefaultCommandHistory
import app.plansnap.core.domain.command.DefaultProjectCommandFactory
import app.plansnap.core.domain.command.ProjectEdit
import app.plansnap.core.domain.command.errorOf
import app.plansnap.core.domain.command.projectOf
import app.plansnap.core.domain.command.valueOf
import app.plansnap.core.domain.command.violation
import app.plansnap.core.domain.evaluation.AdjustmentConfirmation
import app.plansnap.core.domain.evaluation.AdjustmentProposal
import app.plansnap.core.domain.evaluation.DefaultRoomEvaluator
import app.plansnap.core.domain.evaluation.RoomEvaluation
import app.plansnap.core.domain.opening.DefaultOpeningRules
import app.plansnap.core.domain.placement.DefaultDockPlacementResolver
import app.plansnap.core.geometry.primitives.ClosureTolerance
import app.plansnap.core.model.Room
import app.plansnap.testing.fixtures.rooms.OverdeterminedCases
import app.plansnap.testing.fixtures.runtime.TestDispatcherProvider
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

/**
 * Numeric case of ADR 0019 through the production components: rectangle 4150 / 3000 / 4000 / 3000 with all corners
 * measured as 90 degrees (fixture `RECTANGLE_LARGE_EXPLICIT`). Under `T_abs = 250` the closure error is a measurement
 * error with a simple confirmation, under the default tolerance it needs an explicit confirmation. Both proposals
 * carry the same overlay.
 */
class AdjustmentAcceptanceIntegrationTest {
    private val strict = ClosureTolerance()
    private val loose = ClosureTolerance(absoluteMm = 250L)
    private val room: Room = OverdeterminedCases.RECTANGLE_LARGE_EXPLICIT.room
    private val id = room.id
    private val project = projectOf(room)
    private val factory = DefaultProjectCommandFactory(DefaultOpeningRules())

    private fun TestScope.dispatchers() = TestDispatcherProvider(StandardTestDispatcher(testScheduler))

    private fun TestScope.newHistory(): DefaultCommandHistory {
        val evaluator = DefaultRoomEvaluator(dispatchers())
        return DefaultCommandHistory(project, evaluator, DefaultDockPlacementResolver(evaluator), dispatchers())
    }

    private suspend fun TestScope.proposalUnder(tolerance: ClosureTolerance): AdjustmentProposal {
        val evaluation = valueOf(DefaultRoomEvaluator(dispatchers()).evaluate(room, tolerance))
        val needs = evaluation as? RoomEvaluation.NeedsAdjustment
        assertNotNull(needs, "$evaluation")
        return needs!!.proposal
    }

    private fun accept(
        proposal: AdjustmentProposal,
        confirmation: AdjustmentConfirmation,
    ) = factory.create(ProjectEdit.AcceptAdjustment(id, proposal, confirmation))

    @Test
    fun `given the adr rectangle when evaluated then the confirmation depends on the tolerance`() =
        runTest {
            val simple = proposalUnder(loose)
            val explicit = proposalUnder(strict)

            assertEquals(AdjustmentConfirmation.SIMPLE, simple.requiredConfirmation)
            assertEquals(AdjustmentConfirmation.EXPLICIT, explicit.requiredConfirmation)
            assertEquals(simple.adjustment, explicit.adjustment)
        }

    @Test
    fun `given a proposal of another tolerance when accepted then it is rejected and nothing changes`() =
        runTest {
            val simple = proposalUnder(loose)
            val explicit = proposalUnder(strict)
            val history = newHistory()

            val underStrict = history.execute(accept(simple, AdjustmentConfirmation.SIMPLE), strict)
            val underLoose = history.execute(accept(explicit, AdjustmentConfirmation.EXPLICIT), loose)

            assertEquals(violation(DomainRule.ADJUSTMENT_NOT_AVAILABLE), errorOf(underStrict))
            assertEquals(violation(DomainRule.ADJUSTMENT_NOT_AVAILABLE), errorOf(underLoose))
            assertEquals(project, history.state.project)
            assertEquals(0, history.state.undoDepth)
        }

    @Test
    fun `given the matching proposal when accepted then the overlay is stored in one undo step`() =
        runTest {
            val simple = proposalUnder(loose)
            val explicit = proposalUnder(strict)
            val history = newHistory()

            val underLoose = history.execute(accept(simple, AdjustmentConfirmation.SIMPLE), loose)
            assertEquals(simple.adjustment, valueOf(underLoose).rooms.single().adjustment)
            assertEquals(1, history.state.undoDepth)

            history.undo()
            val underStrict = history.execute(accept(explicit, AdjustmentConfirmation.EXPLICIT), strict)

            assertEquals(explicit.adjustment, valueOf(underStrict).rooms.single().adjustment)
            assertEquals(room.walls, valueOf(underStrict).rooms.single().walls)
            assertEquals(1, history.state.undoDepth)
        }

    @Test
    fun `given a simple confirmation under the default tolerance when accepted then explicit is required`() =
        runTest {
            val explicit = proposalUnder(strict)
            val history = newHistory()

            val result = history.execute(accept(explicit, AdjustmentConfirmation.SIMPLE), strict)

            assertEquals(violation(DomainRule.EXPLICIT_CONFIRMATION_REQUIRED), errorOf(result))
            assertEquals(project, history.state.project)
        }
}
