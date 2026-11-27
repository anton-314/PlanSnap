package app.plansnap.feature.editor.details

import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.result.DomainResult
import app.plansnap.core.common.result.EntityKind
import app.plansnap.core.domain.command.ProjectEdit
import app.plansnap.core.domain.command.RoomDetails
import app.plansnap.core.model.RoomId
import app.plansnap.feature.editor.state.EditorEffect
import app.plansnap.feature.editor.state.EditorHandler
import app.plansnap.feature.editor.state.EditorHost
import app.plansnap.feature.editor.state.EditorIntent
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableMap
import javax.inject.Inject

/**
 * Handler of the room details and the area overview (T-EDT-007). It turns typed values into commands
 * (`UpdateRoomDetails`, `SetProjectHeight`), so every change is one undo step. Whether a value is possible is decided
 * by [DetailsValues] while typing and by the command when it runs.
 *
 * Commands go through [EditorHost.submit], so they run as confirmed work in the application scope.
 */
class DetailsHandler
    @Inject
    constructor() : EditorHandler {
        override fun handle(
            intent: EditorIntent,
            host: EditorHost,
        ): Boolean = intent is DetailsIntent && (opens(intent, host) || edits(intent, host) || applies(intent, host))

        private fun opens(
            intent: DetailsIntent,
            host: EditorHost,
        ): Boolean {
            when (intent) {
                is DetailsIntent.OpenRoomDetails -> openRoom(intent.roomId, host)
                DetailsIntent.OpenOverview -> openOverview(host)
                DetailsIntent.Dismiss -> host.updateDetails { DetailsState() }
                else -> return false
            }
            return true
        }

        private fun edits(
            intent: DetailsIntent,
            host: EditorHost,
        ): Boolean {
            when (intent) {
                is DetailsIntent.NameChanged -> editRoom(host) { it.copy(name = intent.text) }
                is DetailsIntent.TypeChanged -> editRoom(host) { it.copy(typeKey = intent.type) }
                is DetailsIntent.NotesChanged -> editRoom(host) { it.copy(notes = intent.text) }
                is DetailsIntent.HeightChanged -> editRoom(host) { it.copy(heightText = intent.text) }
                is DetailsIntent.FactorChanged -> editRoom(host) { it.copy(factorText = intent.text) }
                is DetailsIntent.ProjectHeightChanged -> editOverview(host) { it.copy(projectHeightText = intent.text) }
                else -> return false
            }
            return true
        }

        private fun applies(
            intent: DetailsIntent,
            host: EditorHost,
        ): Boolean {
            when (intent) {
                DetailsIntent.ApplyRoomDetails -> applyRoom(host)
                DetailsIntent.ApplyProjectHeight -> applyProjectHeight(host)
                else -> return false
            }
            return true
        }

        private fun openRoom(
            roomId: RoomId?,
            host: EditorHost,
        ) {
            val state = host.state.value
            val room = state.project?.rooms?.firstOrNull { it.id == (roomId ?: state.selectedRoomId) }
            if (room == null) {
                if (roomId != null) host.send(EditorEffect.Error(DomainError.NotFound(EntityKind.ROOM, roomId.value)))
                return
            }
            host.updateDetails { DetailsState(roomDraft(room, state.settings.lengthUnit)) }
        }

        private fun openOverview(host: EditorHost) {
            val state = host.state.value
            val height = state.project?.defaultHeight ?: return
            host.updateDetails { DetailsState(overviewDraft(height, state.settings.lengthUnit)) }
        }

        private fun editRoom(
            host: EditorHost,
            transform: (DetailsSheet.RoomDetails) -> DetailsSheet.RoomDetails,
        ) {
            val unit = host.state.value.settings.lengthUnit
            host.updateDetails { details ->
                val sheet = details.sheet as? DetailsSheet.RoomDetails ?: return@updateDetails details
                val next = transform(sheet)
                DetailsState(next.copy(errors = DetailsValues.errorsOf(next, unit).toImmutableMap()))
            }
        }

        private fun editOverview(
            host: EditorHost,
            transform: (DetailsSheet.Overview) -> DetailsSheet.Overview,
        ) {
            val unit = host.state.value.settings.lengthUnit
            host.updateDetails { details ->
                val sheet = details.sheet as? DetailsSheet.Overview ?: return@updateDetails details
                val next = transform(sheet)
                DetailsState(next.copy(errors = DetailsValues.errorsOf(next, unit).toImmutableMap()))
            }
        }

        private fun applyRoom(host: EditorHost) {
            val state = host.state.value
            val sheet = host.currentSheet() as? DetailsSheet.RoomDetails ?: return
            val room = state.project?.rooms?.firstOrNull { it.id == sheet.roomId }
            if (room == null) {
                host.send(EditorEffect.Error(DomainError.NotFound(EntityKind.ROOM, sheet.roomId.value)))
                host.updateDetails { DetailsState() }
                return
            }
            val unit = state.settings.lengthUnit
            val name = DetailsValues.parseName(sheet.name)
            val height = DetailsValues.parseRoomHeight(sheet.heightText, unit)
            val factor = DetailsValues.parseFactor(sheet.factorText)
            if (name is DomainResult.Success && height is DomainResult.Success && factor is DomainResult.Success) {
                val details = RoomDetails(name.value, sheet.typeKey, sheet.notes, height.value, factor.value)
                if (details != room.storedDetails()) host.submit(ProjectEdit.UpdateRoomDetails(room.id, details))
                host.updateDetails { DetailsState() }
            } else {
                val errors =
                    mapOf(DetailsField.NAME to name, DetailsField.HEIGHT to height, DetailsField.FACTOR to factor)
                        .mapNotNull { (field, result) -> (result as? DomainResult.Failure)?.let { field to it.error } }
                host.updateDetails { DetailsState(sheet.copy(errors = errors.toMap().toImmutableMap())) }
            }
        }

        private fun applyProjectHeight(host: EditorHost) {
            val state = host.state.value
            val sheet = host.currentSheet() as? DetailsSheet.Overview ?: return
            val project = state.project ?: return
            when (val height = DetailsValues.parseProjectHeight(sheet.projectHeightText, state.settings.lengthUnit)) {
                is DomainResult.Failure -> {
                    val errors = mapOf(DetailsField.PROJECT_HEIGHT to height.error).toImmutableMap()
                    host.updateDetails { DetailsState(sheet.copy(errors = errors)) }
                }

                is DomainResult.Success -> {
                    if (height.value != project.defaultHeight) host.submit(ProjectEdit.SetProjectHeight(height.value))
                    host.updateDetails { DetailsState(sheet.copy(errors = persistentMapOf())) }
                }
            }
        }
    }
