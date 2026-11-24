package app.plansnap.core.domain.command

import app.plansnap.core.common.result.DomainResult
import app.plansnap.core.common.result.DomainRule
import app.plansnap.core.common.result.EntityKind
import app.plansnap.core.common.result.flatMap
import app.plansnap.core.domain.evaluation.RoomEvaluation
import app.plansnap.core.model.Project
import app.plansnap.core.model.inputFingerprint

/**
 * Command of [ProjectEdit.AcceptAdjustment]. [verify] holds the precondition that needs the current evaluation
 * (ADR 0019), [apply] the checks that need no evaluation.
 */
internal class AcceptAdjustmentCommand(
    private val edit: ProjectEdit.AcceptAdjustment,
) : ProjectCommand {
    override suspend fun verify(
        project: Project,
        context: CommandContext,
    ): DomainResult<Unit> {
        val room =
            project.rooms.firstOrNull { it.id == edit.roomId }
                ?: return DomainResult.Failure(notFound(EntityKind.ROOM, edit.roomId.value))
        return context.evaluate(room).flatMap { evaluation ->
            val current = evaluation is RoomEvaluation.NeedsAdjustment && evaluation.proposal == edit.proposal
            if (current) DomainResult.Success(Unit) else DomainResult.Failure(rule(DomainRule.ADJUSTMENT_NOT_AVAILABLE))
        }
    }

    override fun apply(project: Project): DomainResult<Project> =
        project.mapRoom(edit.roomId) { room ->
            val adjustment = edit.proposal.adjustment
            val error =
                when {
                    !room.isClosed ||
                        adjustment.adjustedLengthsMm.size != room.walls.size ||
                        adjustment.inputFingerprint != room.inputFingerprint() -> {
                        rule(DomainRule.ADJUSTMENT_NOT_AVAILABLE)
                    }

                    !edit.proposal.isConfirmedBy(edit.confirmation) -> {
                        rule(DomainRule.EXPLICIT_CONFIRMATION_REQUIRED)
                    }

                    else -> {
                        null
                    }
                }
            error.otherwise { room.copy(adjustment = adjustment) }
        }
}
