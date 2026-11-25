package app.plansnap.feature.editor.shape

import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.result.DomainResult
import app.plansnap.core.common.result.DomainRule
import app.plansnap.core.common.runtime.IdGenerator
import app.plansnap.core.domain.command.ProjectEdit
import app.plansnap.core.model.CornerSpec
import app.plansnap.core.model.Diagonal
import app.plansnap.core.model.DiagonalId
import app.plansnap.core.model.Room
import app.plansnap.feature.editor.state.EditorHost
import java.util.Locale

/**
 * Stores the value of the open shape sheet: a corner and the thickness change the editor state, a diagonal and a
 * stored corner become commands. A value that cannot be stored keeps the sheet open and shows why.
 */
internal class ShapeSheetCommit(
    private val ids: IdGenerator,
) {
    /** Stores the open sheet and closes it, or reports the error in the sheet. */
    fun confirm(host: EditorHost) {
        val state = host.state.value
        val room = state.selectedRoom
        val sheet = state.section(ShapeState::class)?.sheet
        val error =
            when {
                room == null || sheet == null -> return
                sheet is ShapeSheet.Corner -> corner(sheet, room, host)
                sheet is ShapeSheet.Diagonal -> diagonal(sheet, room, host)
                sheet is ShapeSheet.Thickness -> thickness(sheet, host)
                else -> return
            }
        host.updateShape { if (error == null) ShapeState() else it.copy(error = error) }
    }

    private fun corner(
        sheet: ShapeSheet.Corner,
        room: Room,
        host: EditorHost,
    ): DomainError? =
        when (val angle = ShapeValues.parseAngle(sheet.text, Locale.getDefault())) {
            is DomainResult.Failure -> {
                angle.error
            }

            is DomainResult.Success -> {
                val corner = CornerSpec.Measured(sheet.direction, angle.value)
                when (val target = sheet.target) {
                    CornerTarget.Next -> host.update { it.copy(nextCorner = corner) }
                    is CornerTarget.Existing -> host.submit(ProjectEdit.SetCorner(room.id, target.index, corner))
                }
                null
            }
        }

    private fun diagonal(
        sheet: ShapeSheet.Diagonal,
        room: Room,
        host: EditorHost,
    ): DomainError? =
        when (val length = ShapeValues.parseDiagonal(sheet.text, host.state.value.settings.lengthUnit)) {
            is DomainResult.Failure -> {
                length.error
            }

            is DomainResult.Success if sheet.from == sheet.to -> {
                DomainError.RuleViolation(DomainRule.DIAGONAL_INVALID_CORNERS)
            }

            is DomainResult.Success -> {
                val diagonal = Diagonal(DiagonalId(ids.newId()), sheet.from, sheet.to, length.value)
                host.submit(ProjectEdit.AddDiagonal(room.id, diagonal))
                null
            }
        }

    private fun thickness(
        sheet: ShapeSheet.Thickness,
        host: EditorHost,
    ): DomainError? =
        when (val thickness = ShapeValues.parseThickness(sheet.text, host.state.value.settings.lengthUnit)) {
            is DomainResult.Failure -> {
                thickness.error
            }

            is DomainResult.Success -> {
                host.update { it.copy(thicknessOverride = thickness.value) }
                null
            }
        }
}
