package app.plansnap.feature.editor.a11y

import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.result.StorageProblem
import app.plansnap.core.common.units.Angle
import app.plansnap.core.common.units.TurnDirection
import app.plansnap.core.domain.opening.DefaultOpeningRules
import app.plansnap.core.model.CornerSpec
import app.plansnap.core.model.OpeningId
import app.plansnap.core.model.Room
import app.plansnap.core.model.WallId
import app.plansnap.feature.editor.details.DetailsHandler
import app.plansnap.feature.editor.details.DetailsIntent
import app.plansnap.feature.editor.details.detailedRoom
import app.plansnap.feature.editor.navigation.layoutState
import app.plansnap.feature.editor.opening.OpeningHandler
import app.plansnap.feature.editor.opening.OpeningIntent
import app.plansnap.feature.editor.opening.door
import app.plansnap.feature.editor.opening.openingRoom
import app.plansnap.feature.editor.rooms.DockDraft
import app.plansnap.feature.editor.rooms.GUEST_ID
import app.plansnap.feature.editor.rooms.HOST_ID
import app.plansnap.feature.editor.rooms.RoomsState
import app.plansnap.feature.editor.rooms.roomsState
import app.plansnap.feature.editor.shape.RecordingHost
import app.plansnap.feature.editor.shape.ShapeHandler
import app.plansnap.feature.editor.shape.ShapeIntent
import app.plansnap.feature.editor.shape.diagonalOf
import app.plansnap.feature.editor.shape.impossibleRoom
import app.plansnap.feature.editor.shape.largeCorrectionRoom
import app.plansnap.feature.editor.shape.roomOf
import app.plansnap.feature.editor.shape.stateOf
import app.plansnap.feature.editor.shape.underdeterminedRoom
import app.plansnap.feature.editor.state.EditorIntent
import app.plansnap.feature.editor.state.EditorUiState
import app.plansnap.feature.editor.state.SaveStatus
import app.plansnap.testing.fixtures.runtime.SequentialIdGenerator

private const val TOO_WIDE_MM = 9000L

/** A kitchen with a measured angle, a door that fits, one that does not fit any more and a diagonal. */
internal fun furnishedRoom(): Room {
    val room =
        openingRoom(
            door("fits", "w0", 500, 900),
            door("wide", "w1", 0, TOO_WIDE_MM),
        )
    val corners = room.corners.toMutableList()
    corners[1] = CornerSpec.Measured(TurnDirection.LEFT, Angle.RIGHT)
    return room.copy(
        corners = kotlinx.collections.immutable.persistentListOf(corners[0], corners[1], corners[2], corners[3]),
        diagonals = kotlinx.collections.immutable.persistentListOf(diagonalOf(0, 2, 5000)),
    )
}

/** The states of the editor screen whose accessibility is checked. */
internal object A11yStates {
    val needsAdjustment: EditorUiState get() = layoutState(withCard = true)

    val confirmed: EditorUiState get() = settled(stateOf(roomOf(4000, 3000, 4000, 3000, closed = true)))

    val proposed: EditorUiState get() = settled(stateOf(underdeterminedRoom()))

    val conflict: EditorUiState get() = settled(stateOf(impossibleRoom()))

    val firstWall: EditorUiState get() = stateOf(roomOf())

    val furnished: EditorUiState get() = settled(stateOf(furnishedRoom()))

    val measuredCorner: EditorUiState
        get() =
            settled(stateOf(roomOf(4000, 3000, 4000))).copy(
                nextCorner = CornerSpec.Measured(TurnDirection.RIGHT, Angle.RIGHT),
            )

    val failedSave: EditorUiState
        get() = needsAdjustment.copy(save = SaveStatus.Failed(DomainError.Storage(StorageProblem.NO_SPACE)))

    val picking: EditorUiState
        get() =
            roomsState().withSection(
                RoomsState::class,
                RoomsState(dock = DockDraft(guestId = GUEST_ID, picking = true)),
            )

    val dockSheet: EditorUiState
        get() =
            roomsState().withSection(
                RoomsState::class,
                RoomsState(
                    dock =
                        DockDraft(
                            guestId = GUEST_ID,
                            hostRoomId = HOST_ID,
                            hostWallId = WallId("host0"),
                            ownWallId = WallId("guest0"),
                        ),
                ),
            )

    fun cornerSheet(): EditorUiState = shape(needsAdjustment, ShapeIntent.OpenCorner())

    fun diagonalSheet(): EditorUiState = shape(needsAdjustment, ShapeIntent.OpenDiagonal())

    fun thicknessSheet(): EditorUiState = shape(needsAdjustment, ShapeIntent.OpenThickness)

    fun adjustmentSheet(): EditorUiState = shape(needsAdjustment, ShapeIntent.OpenAdjustment)

    fun explicitDialog(): EditorUiState =
        shape(
            settled(stateOf(largeCorrectionRoom())),
            ShapeIntent.OpenAdjustment,
            ShapeIntent.RequestExplicitConfirmation,
        )

    fun wallSheet(): EditorUiState = opening(OpeningIntent.OpenWall(furnishedRoom().id, WallId("w0")))

    fun openingForm(): EditorUiState = opening(OpeningIntent.Edit(furnishedRoom().id, OpeningId("wide")))

    fun deleteDialog(): EditorUiState =
        opening(OpeningIntent.Edit(furnishedRoom().id, OpeningId("fits")), OpeningIntent.RequestDelete)

    fun roomDetails(): EditorUiState = details(DetailsIntent.OpenRoomDetails())

    fun areaOverview(): EditorUiState = details(DetailsIntent.OpenOverview)

    private fun shape(
        state: EditorUiState,
        vararg intents: EditorIntent,
    ): EditorUiState {
        val host = RecordingHost(state)
        val handler = ShapeHandler(SequentialIdGenerator("gen"))
        intents.forEach { check(handler.handle(it, host)) }
        return host.state.value
    }

    private fun opening(vararg intents: EditorIntent): EditorUiState {
        val host = RecordingHost(settled(stateOf(furnishedRoom())))
        val handler = OpeningHandler(SequentialIdGenerator("gen"), DefaultOpeningRules())
        intents.forEach { check(handler.handle(it, host)) }
        return host.state.value
    }

    private fun details(intent: EditorIntent): EditorUiState {
        val host = RecordingHost(settled(stateOf(detailedRoom())))
        check(DetailsHandler().handle(intent, host))
        return host.state.value
    }

    /** [state] without one-time hints, which have checks of their own. */
    private fun settled(state: EditorUiState): EditorUiState =
        state.copy(settings = state.settings.copy(dismissedHints = layoutState(false).settings.dismissedHints))
}
