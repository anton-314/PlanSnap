package app.plansnap.feature.editor.state

import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.result.DomainResult
import app.plansnap.core.common.result.EntityKind
import app.plansnap.core.common.result.InvalidValueReason
import app.plansnap.core.common.result.ValueField
import app.plansnap.core.common.runtime.IdGenerator
import app.plansnap.core.common.units.Length
import app.plansnap.core.domain.command.ProjectEdit
import app.plansnap.core.domain.repository.SettingsRepository
import app.plansnap.core.geometry.primitives.ClosureTolerance
import app.plansnap.core.model.CornerSpec
import app.plansnap.core.model.Room
import app.plansnap.core.model.RoomId
import app.plansnap.core.model.Wall
import app.plansnap.core.model.WallId
import kotlinx.coroutines.launch

/**
 * The open editor of one loaded project: translates the [CoreIntent]s into state changes and queued work.
 *
 * Previews and evaluations run in the screen scope. Everything confirmed goes through `EditorStore.enqueue`, so it runs
 * in the application scope, in call order, and survives leaving the editor.
 */
internal class EditorSession(
    private val store: EditorStore,
    private val runner: CommandRunner,
    private val pipelines: EvaluationPipelines,
    private val settings: SettingsRepository,
    private val ids: IdGenerator,
) {
    /** Starts the live evaluation and follows the settings. */
    fun start() {
        pipelines.start()
        store.scope.launch {
            var lastTolerance: ClosureTolerance? = null
            settings.settings.collect { current ->
                store.update { it.copy(settings = current) }
                if (current.tolerance != lastTolerance) {
                    lastTolerance = current.tolerance
                    store.enqueue { runner.reconcile(current.tolerance) }
                }
            }
        }
    }

    /** Handles [intent]. */
    fun handle(intent: CoreIntent) {
        when (intent) {
            is CoreIntent.InputChanged -> {
                store.update { it.copy(inputText = intent.text) }
            }

            is CoreIntent.SelectRoom -> {
                selectRoom(intent.roomId)
            }

            is CoreIntent.ViewChanged -> {
                store.update { it.copy(view = intent.view) }
            }

            is CoreIntent.SetNextCorner -> {
                store.update { it.copy(nextCorner = intent.corner) }
            }

            is CoreIntent.SetThickness -> {
                setThickness(intent.thickness)
            }

            CoreIntent.ConfirmWall -> {
                confirmWall()
            }

            is CoreIntent.Submit -> {
                submit(intent.edit)
            }

            is CoreIntent.AcceptAdjustment -> {
                store.enqueue {
                    runner.acceptAdjustment(
                        intent.roomId,
                        intent.proposal,
                        intent.confirmation,
                    )
                }
            }

            CoreIntent.Undo -> {
                store.enqueue { runner.undo() }
            }

            CoreIntent.Redo -> {
                store.enqueue { runner.redo() }
            }

            CoreIntent.RetrySave -> {
                store.enqueue { runner.retrySave() }
            }
        }
    }

    /** Queues [edit] as a confirmed command. */
    fun submit(edit: ProjectEdit) {
        store.enqueue { runner.execute(edit) }
    }

    private fun selectRoom(roomId: RoomId?) {
        val known =
            store.state.value.project
                ?.rooms
                ?.any { it.id == roomId } == true
        if (roomId != null && !known) {
            store.send(EditorEffect.Error(DomainError.NotFound(EntityKind.ROOM, roomId.value)))
        } else {
            store.update { it.copy(selectedRoomId = roomId) }
        }
    }

    private fun setThickness(thickness: Length?) {
        if (thickness != null && thickness < Length.ZERO) {
            store.send(
                EditorEffect.Error(DomainError.InvalidValue(ValueField.WALL_THICKNESS, InvalidValueReason.TOO_SMALL)),
            )
        } else {
            store.update { it.copy(thicknessOverride = thickness) }
        }
    }

    private fun confirmWall() {
        val snapshot = store.state.value
        when (val length = WallInput.parseLength(snapshot.inputText, snapshot.settings.lengthUnit)) {
            is DomainResult.Failure -> {
                store.send(EditorEffect.Error(length.error))
            }

            is DomainResult.Success -> {
                // The input counts as taken over from queuing on, so a second OK cannot add the wall twice.
                store.update { it.copy(inputText = if (it.inputText == snapshot.inputText) "" else it.inputText) }
                store.enqueue { appendWall(snapshot.inputText, length.value, snapshot.thickness, snapshot.nextCorner) }
            }
        }
    }

    private suspend fun appendWall(
        text: String,
        length: Length,
        thickness: Length,
        nextCorner: CornerSpec,
    ) {
        val room = targetRoom()
        val added =
            room?.let {
                val wall = Wall(WallId(ids.newId()), length, thickness)
                runner.execute(ProjectEdit.AddWall(it.id, wall, WallInput.startCorner(it, nextCorner)))
            }
        if (added is DomainResult.Success) {
            store.update { it.copy(thicknessOverride = null) }
            rememberThickness(thickness)
        } else {
            // Rejected: the input comes back for correction, unless the user already typed something new.
            store.update { it.copy(inputText = it.inputText.ifEmpty { text }) }
        }
    }

    /** The selected room, the first room, or a new room if the project has none. `null` if creating it failed. */
    private suspend fun targetRoom(): Room? {
        val rooms = runner.project.rooms
        val existing = rooms.firstOrNull { it.id == store.state.value.selectedRoomId } ?: rooms.firstOrNull()
        if (existing != null) return existing
        val created = Room(RoomId(ids.newId()))
        return when (runner.execute(ProjectEdit.AddRoom(created))) {
            is DomainResult.Failure -> null
            is DomainResult.Success -> created.also { room -> store.update { it.copy(selectedRoomId = room.id) } }
        }
    }

    private suspend fun rememberThickness(thickness: Length) {
        if (store.state.value.settings.lastThickness == thickness) return
        val result = settings.update { it.copy(lastThickness = thickness) }
        if (result is DomainResult.Failure) store.send(EditorEffect.Error(result.error))
    }
}
