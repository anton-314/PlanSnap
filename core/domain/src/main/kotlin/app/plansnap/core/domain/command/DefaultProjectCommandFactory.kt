package app.plansnap.core.domain.command

import app.plansnap.core.domain.validation.OpeningRules

/**
 * Factory of all commands of DOMAIN section 11.
 *
 * Every created command is a pure function of its [ProjectEdit], so equal edits yield equal results. Only
 * [ProjectEdit.AcceptAdjustment] has a precondition for [ProjectCommand.verify] (ADR 0019).
 *
 * @param openingRules rules that decide whether an opening fits its wall, for adding and editing openings.
 */
class DefaultProjectCommandFactory(
    private val openingRules: OpeningRules,
) : ProjectCommandFactory {
    // One branch per edit keeps the dispatch exhaustive, so a new edit cannot be forgotten. It has no logic of its own.
    @Suppress("CyclomaticComplexMethod")
    override fun create(edit: ProjectEdit): ProjectCommand =
        when (edit) {
            is ProjectEdit.AddWall -> ProjectCommand { addWall(it, edit) }
            is ProjectEdit.EditWall -> ProjectCommand { editWall(it, edit) }
            is ProjectEdit.RemoveLastWall -> ProjectCommand { removeLastWall(it, edit) }
            is ProjectEdit.SetCorner -> ProjectCommand { setCorner(it, edit) }
            is ProjectEdit.AddDiagonal -> ProjectCommand { addDiagonal(it, edit) }
            is ProjectEdit.RemoveDiagonal -> ProjectCommand { removeDiagonal(it, edit) }
            is ProjectEdit.CloseRoom -> ProjectCommand { closeRoom(it, edit) }
            is ProjectEdit.AcceptAdjustment -> AcceptAdjustmentCommand(edit)
            is ProjectEdit.RevertAdjustment -> ProjectCommand { revertAdjustment(it, edit) }
            is ProjectEdit.SetThickness -> ProjectCommand { setThickness(it, edit) }
            is ProjectEdit.AddOpening -> ProjectCommand { addOpening(it, edit, openingRules) }
            is ProjectEdit.EditOpening -> ProjectCommand { editOpening(it, edit, openingRules) }
            is ProjectEdit.RemoveOpening -> ProjectCommand { removeOpening(it, edit) }
            is ProjectEdit.AddRoom -> ProjectCommand { addRoom(it, edit) }
            is ProjectEdit.RemoveRoom -> ProjectCommand { removeRoom(it, edit) }
            is ProjectEdit.MoveRoom -> ProjectCommand { moveRoom(it, edit) }
            is ProjectEdit.DockRoom -> ProjectCommand { dockRoom(it, edit) }
            is ProjectEdit.UpdateRoomDetails -> ProjectCommand { updateRoomDetails(it, edit) }
            is ProjectEdit.SetProjectHeight -> ProjectCommand { setProjectHeight(it, edit) }
        }
}
