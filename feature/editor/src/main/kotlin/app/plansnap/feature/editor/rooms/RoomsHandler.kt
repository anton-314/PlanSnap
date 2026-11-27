package app.plansnap.feature.editor.rooms

import app.plansnap.core.common.result.DomainResult
import app.plansnap.core.common.runtime.ApplicationScope
import app.plansnap.core.common.runtime.IdGenerator
import app.plansnap.core.domain.command.ProjectEdit
import app.plansnap.core.domain.repository.AppSettings
import app.plansnap.core.domain.repository.SettingsRepository
import app.plansnap.core.model.Room
import app.plansnap.core.model.RoomId
import app.plansnap.core.model.RoomPlacement
import app.plansnap.feature.editor.state.EditorEffect
import app.plansnap.feature.editor.state.EditorHandler
import app.plansnap.feature.editor.state.EditorHost
import app.plansnap.feature.editor.state.EditorIntent
import app.plansnap.feature.editor.state.EditorUiState
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Handler of the rooms function (T-EDT-006): new rooms, docking, grid and snapping, and the long-press gesture that
 * moves and rotates a room. It turns choices into commands and previews. Whether a dock is possible and where the
 * room ends up is decided by the domain, snapping by its `Snapper`, and the picture by the canvas.
 *
 * Every confirmed change is one [EditorHost.submit], so one undo step reverts it. A gesture changes only the preview in
 * [RoomsState.drag] until all fingers are lifted. Grid and snapping are settings: they are written to the
 * [SettingsRepository] in the application scope, and the state follows them at once.
 */
class RoomsHandler
    @Inject
    constructor(
        private val ids: IdGenerator,
        private val settings: SettingsRepository,
        private val applicationScope: ApplicationScope,
    ) : EditorHandler {
        private val engine = RoomDragEngine()

        override fun handle(
            intent: EditorIntent,
            host: EditorHost,
        ): Boolean {
            if (intent !is RoomsIntent) return false
            when (intent) {
                RoomsIntent.AddRoom -> addRoom(host)

                is RoomsIntent.SetGrid -> setting(host) { it.copy(gridEnabled = intent.enabled) }

                is RoomsIntent.SetSnap -> setting(host) { it.copy(snapEnabled = intent.enabled) }

                is RoomsIntent.GestureStarted,
                is RoomsIntent.GestureMoved,
                RoomsIntent.GestureEnded,
                RoomsIntent.GestureCancelled,
                -> gesture(intent, host)

                else -> dock(intent, host)
            }
            return true
        }

        private fun addRoom(host: EditorHost) {
            val room = Room(RoomId(ids.newId()), placement = placementBeside(host.state.value))
            host.update { it.copy(selectedRoomId = room.id) }
            host.submit(ProjectEdit.AddRoom(room))
        }

        /**
         * To the right of everything: of the drawn plan and of the origins of all rooms, because a room without walls
         * is not drawn. The first room of a project keeps the default placement.
         */
        private fun placementBeside(state: EditorUiState): RoomPlacement {
            val rooms = state.project?.rooms.orEmpty()
            val bounds = state.scene?.bounds
            val right = (rooms.map { it.placement.originX } + listOfNotNull(bounds?.maxX)).maxOrNull()
            return if (right == null) {
                RoomPlacement.DEFAULT
            } else {
                RoomPlacement(right + NEW_ROOM_GAP_MM, bounds?.minY ?: 0.0, 0.0, null)
            }
        }

        private fun setting(
            host: EditorHost,
            transform: (AppSettings) -> AppSettings,
        ) {
            applicationScope.launch {
                val result = settings.update(transform)
                if (result is DomainResult.Failure) host.send(EditorEffect.Error(result.error))
            }
        }

        private fun gesture(
            intent: RoomsIntent,
            host: EditorHost,
        ) {
            val state = host.state.value
            val rooms = state.section(RoomsState::class) ?: RoomsState()
            val drag = rooms.drag
            when (intent) {
                is RoomsIntent.GestureStarted -> {
                    val started = if (rooms.dock == null) engine.begin(state, intent.start) else null
                    if (started != null) {
                        host.update { it.copy(selectedRoomId = started.roomId) }
                        host.updateRooms { it.copy(drag = started) }
                    }
                }

                is RoomsIntent.GestureMoved -> {
                    if (drag != null) {
                        val moved =
                            engine.update(drag, intent.update, state.settings.snapEnabled, state.settings.gridEnabled)
                        host.updateRooms { it.copy(drag = moved) }
                    }
                }

                RoomsIntent.GestureEnded -> {
                    if (drag != null) {
                        host.updateRooms { it.copy(drag = null) }
                        engine.finish(drag)?.let(host::submit)
                    }
                }

                else -> {
                    host.updateRooms { it.copy(drag = null) }
                }
            }
        }

        private fun dock(
            intent: RoomsIntent,
            host: EditorHost,
        ) {
            val state = host.state.value
            val draft = state.section(RoomsState::class)?.dock
            if (intent == RoomsIntent.OpenDock) {
                DockDrafts.open(state)?.let { opened -> host.updateRooms { it.copy(dock = opened) } }
            } else if (draft != null) {
                if (intent == RoomsIntent.ConfirmDock) {
                    confirmDock(draft, host)
                } else {
                    val next = DockEditing.apply(draft, intent)
                    host.updateRooms { it.copy(dock = next?.let { edited -> DockDrafts.validated(edited, state) }) }
                }
            }
        }

        private fun confirmDock(
            draft: DockDraft,
            host: EditorHost,
        ) {
            val unit = host.state.value.settings.lengthUnit
            DockDrafts.linkOf(draft, unit)?.takeIf { draft.canConfirm }?.let { link ->
                host.updateRooms { it.copy(dock = null) }
                host.submit(ProjectEdit.DockRoom(draft.guestId, link))
            }
        }

        private companion object {
            /** Distance between a new room and the plan, 1 m. */
            const val NEW_ROOM_GAP_MM = 1000.0
        }
    }
