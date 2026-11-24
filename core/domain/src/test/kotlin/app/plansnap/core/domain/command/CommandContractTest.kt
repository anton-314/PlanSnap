package app.plansnap.core.domain.command

import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.result.DomainResult
import app.plansnap.core.common.result.DomainRule
import app.plansnap.core.common.units.Length
import app.plansnap.core.domain.evaluation.AdjustmentConfirmation
import app.plansnap.core.domain.evaluation.AdjustmentProposal
import app.plansnap.core.domain.evaluation.RoomEvaluation
import app.plansnap.core.domain.evaluation.RoomEvaluator
import app.plansnap.core.geometry.adjustment.ObservationRef
import app.plansnap.core.geometry.primitives.ClosureTolerance
import app.plansnap.core.geometry.primitives.Vec2
import app.plansnap.core.model.ClosureAdjustment
import app.plansnap.core.model.Project
import app.plansnap.core.model.ProjectId
import app.plansnap.core.model.Room
import app.plansnap.core.model.RoomId
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.coroutines.Continuation

/** Contract of command preconditions (ADR 0019, DOMAIN section 11). */
class CommandContractTest {
    private val room = Room(RoomId("r1"))
    private val project =
        Project(
            ProjectId("p1"),
            "Flat",
            Length(2500),
            persistentListOf(room),
            createdAtEpochMs = 1,
            updatedAtEpochMs = 2,
        )
    private val strict = ClosureTolerance()
    private val loose = ClosureTolerance(absoluteMm = 250L)

    /** Evaluator that records every tolerance it receives. */
    private class RecordingEvaluator : RoomEvaluator {
        val tolerances = mutableListOf<ClosureTolerance>()

        override suspend fun evaluate(
            room: Room,
            tolerance: ClosureTolerance,
        ): DomainResult<RoomEvaluation> {
            tolerances += tolerance
            return DomainResult.Success(RoomEvaluation.Open(room.id, persistentListOf(Vec2.ZERO), persistentMapOf()))
        }
    }

    @Test
    fun `given a command without precondition when verified then it succeeds without evaluating`() =
        runBlocking<Unit> {
            val evaluator = RecordingEvaluator()
            val command = ProjectCommand { DomainResult.Success(it) }

            assertEquals(DomainResult.Success(Unit), command.verify(project, CommandContext(strict, evaluator)))
            assertTrue(evaluator.tolerances.isEmpty())
        }

    @Test
    fun `given a context when a room is evaluated then the tolerance of the operation is used`() =
        runBlocking<Unit> {
            val evaluator = RecordingEvaluator()
            val context = CommandContext(strict, evaluator)

            context.evaluate(room)

            assertSame(strict, context.tolerance)
            assertEquals(listOf(strict), evaluator.tolerances)
        }

    @Test
    fun `given a failing precondition when verified then its failure is returned and apply stays independent`() =
        runBlocking<Unit> {
            val rejection = DomainResult.Failure(DomainError.RuleViolation(DomainRule.ADJUSTMENT_NOT_AVAILABLE))
            val command =
                object : ProjectCommand {
                    override suspend fun verify(
                        project: Project,
                        context: CommandContext,
                    ): DomainResult<Unit> = rejection

                    override fun apply(project: Project): DomainResult<Project> =
                        DomainResult.Success(project.copy(name = "Changed"))
                }

            assertEquals(rejection, command.verify(project, CommandContext(loose, RecordingEvaluator())))
            assertEquals(command.apply(project), command.apply(project))
            assertEquals("Flat", project.name)
        }

    @Test
    fun `given the command contract when inspected then apply is plain and only verify suspends`() {
        val methods = ProjectCommand::class.java.declaredMethods.associateBy { it.name }
        val apply = methods.getValue("apply")
        val verify = methods.getValue("verify")

        assertEquals(listOf(Project::class.java), apply.parameterTypes.toList())
        assertEquals(
            listOf(Project::class.java, CommandContext::class.java, Continuation::class.java),
            verify.parameterTypes.toList(),
        )
        val execute = CommandHistory::class.java.declaredMethods.single { it.name == "execute" }
        assertEquals(
            listOf(ProjectCommand::class.java, ClosureTolerance::class.java, Continuation::class.java),
            execute.parameterTypes.toList(),
        )
    }

    @Test
    fun `given the same adjustment with another required confirmation then the proposals differ`() {
        // Rectangle 4150 3000 4000 3000 with all corners measured (T-ARC-009, evaluation prototype): T_abs = 250 yields
        // a simple confirmation, the default tolerance an explicit one, both with S = 223.94 and the same overlay.
        val adjustment =
            ClosureAdjustment(
                inputFingerprint = 7L,
                solverVersion = 1,
                adjustedLengthsMm = persistentListOf(4148.656, 3000.033, 4001.344, 3000.033),
                adjustedTurnsRad = persistentListOf(1.59535, 1.59535, 1.54624, 1.54624),
            )
        val simple =
            AdjustmentProposal(adjustment, 223.94, 55.297, ObservationRef.CornerAngle(0), AdjustmentConfirmation.SIMPLE)
        val explicit = simple.copy(requiredConfirmation = AdjustmentConfirmation.EXPLICIT)

        assertNotEquals(simple, explicit)
        assertEquals(simple.adjustment.inputFingerprint, explicit.adjustment.inputFingerprint)
    }
}
