package app.plansnap.feature.editor.opening

import app.plansnap.core.common.runtime.IdGenerator
import app.plansnap.core.domain.command.ProjectEdit
import app.plansnap.core.domain.validation.OpeningRules
import app.plansnap.core.model.Opening
import app.plansnap.core.model.OpeningId
import app.plansnap.core.model.OpeningType
import app.plansnap.core.model.Room
import app.plansnap.core.model.RoomId
import app.plansnap.core.model.WallId
import app.plansnap.feature.editor.state.EditorHandler
import app.plansnap.feature.editor.state.EditorHost
import app.plansnap.feature.editor.state.EditorIntent
import app.plansnap.feature.editor.state.EditorUiState
import javax.inject.Inject

/**
 * Handler of the opening sheets (T-EDT-005): add, change and remove doors, windows and passages. It only turns typed
 * values into commands. Whether an opening fits and does not overlap is decided by [OpeningRules] (ADR 0021), the
 * same implementation behind `AddOpening` and `EditOpening`, so the sheet shows the conflict the command would
 * report. Confirmed changes go through [EditorHost.submit], which makes each of them one undo step.
 */
class OpeningHandler
    @Inject
    constructor(
        private val ids: IdGenerator,
        private val rules: OpeningRules,
    ) : EditorHandler {
        override fun handle(
            intent: EditorIntent,
            host: EditorHost,
        ): Boolean {
            if (intent !is OpeningIntent) return false
            when (intent) {
                is OpeningIntent.Edit -> openEdit(intent.roomId, intent.openingId, host)
                is OpeningIntent.OpenWall -> openWall(intent.roomId, intent.wallId, host)
                is OpeningIntent.Add -> openAdd(intent.roomId, intent.wallId, intent.type, host)
                is OpeningIntent.AddToSelectedRoom -> openAddToSelected(intent.type, host)
                OpeningIntent.Dismiss -> host.updateOpening { OpeningState() }
                OpeningIntent.Confirm -> confirm(host)
                OpeningIntent.RequestDelete -> requestDelete(host)
                OpeningIntent.CancelDelete -> host.updateOpening { it.copy(confirmDelete = false) }
                OpeningIntent.ConfirmDelete -> delete(host)
                is OpeningIntent.FormChange -> changeForm(intent, host)
            }
            return true
        }

        private fun openEdit(
            roomId: RoomId,
            openingId: OpeningId,
            host: EditorHost,
        ) {
            val state = host.state.value
            val opening = state.roomOrNull(roomId)?.openings?.firstOrNull { it.id == openingId } ?: return
            val unit = state.settings.lengthUnit
            val form = OpeningSheet.Form(roomId, openingId, OpeningDrafts.of(opening, unit))
            host.updateOpening { OpeningDrafts.stateOf(form, state.project, unit, rules) }
        }

        private fun openWall(
            roomId: RoomId,
            wallId: WallId,
            host: EditorHost,
        ) {
            val room = host.state.value.roomOrNull(roomId) ?: return
            if (room.walls.none { it.id == wallId }) return
            host.updateOpening { OpeningState(sheet = OpeningSheet.Wall(roomId, wallId)) }
        }

        private fun openAddToSelected(
            type: OpeningType,
            host: EditorHost,
        ) {
            val room = host.state.value.selectedRoom ?: return
            val wall = room.walls.lastOrNull() ?: return
            openAdd(room.id, wall.id, type, host)
        }

        private fun openAdd(
            roomId: RoomId,
            wallId: WallId,
            type: OpeningType,
            host: EditorHost,
        ) {
            val state = host.state.value
            val room = state.roomOrNull(roomId) ?: return
            val wall = room.walls.firstOrNull { it.id == wallId } ?: return
            val unit = state.settings.lengthUnit
            val form = OpeningSheet.Form(room.id, null, OpeningDrafts.initial(type, wall, room, unit))
            host.updateOpening { OpeningDrafts.stateOf(form, state.project, unit, rules) }
        }

        /** Applies [change] and asks the domain once whether the form can be stored. */
        private fun changeForm(
            change: OpeningIntent.FormChange,
            host: EditorHost,
        ) {
            val state = host.state.value
            val form = state.openForm() ?: return
            val unit = state.settings.lengthUnit
            val next = form.copy(draft = form.draft.changedBy(change, form.editingId == null, unit))
            host.updateOpening { OpeningDrafts.stateOf(next, state.project, unit, rules) }
        }

        private fun confirm(host: EditorHost) {
            val state = host.state.value
            val form = state.openForm() ?: return
            val unit = state.settings.lengthUnit
            val checked = OpeningDrafts.check(form, state.project, unit, rules) ?: return
            when (checked) {
                is DraftCheck.Invalid -> {
                    host.updateOpening { it.copy(valueError = checked.valueError, conflict = checked.conflict) }
                }

                is DraftCheck.Valid -> {
                    commandFor(form, checked.opening, state)?.let(host::submit)
                    host.updateOpening { OpeningState() }
                }
            }
        }

        /** The command that stores [opening] of [form], `null` if nothing changed. */
        private fun commandFor(
            form: OpeningSheet.Form,
            opening: Opening,
            state: EditorUiState,
        ): ProjectEdit? =
            when {
                form.editingId == null -> {
                    ProjectEdit.AddOpening(form.roomId, opening.copy(id = OpeningId(ids.newId())))
                }

                state.roomOrNull(form.roomId)?.openings?.contains(opening) == true -> {
                    null
                }

                else -> {
                    ProjectEdit.EditOpening(form.roomId, opening)
                }
            }

        private fun requestDelete(host: EditorHost) {
            val form = host.state.value.openForm()
            if (form?.editingId != null) host.updateOpening { it.copy(confirmDelete = true) }
        }

        private fun delete(host: EditorHost) {
            val form = host.state.value.openForm() ?: return
            val id = form.editingId ?: return
            host.submit(ProjectEdit.RemoveOpening(form.roomId, id))
            host.updateOpening { OpeningState() }
        }
    }

private fun EditorUiState.openForm(): OpeningSheet.Form? = section(OpeningState::class)?.sheet as? OpeningSheet.Form

private fun EditorUiState.roomOrNull(roomId: RoomId): Room? = project?.rooms?.firstOrNull { it.id == roomId }
