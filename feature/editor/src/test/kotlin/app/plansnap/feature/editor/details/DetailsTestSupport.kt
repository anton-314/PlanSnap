package app.plansnap.feature.editor.details

import app.plansnap.core.common.units.Length
import app.plansnap.core.domain.repository.AppSettings
import app.plansnap.core.model.AreaFactor
import app.plansnap.core.model.Room
import app.plansnap.core.model.RoomTypeKey
import app.plansnap.feature.editor.shape.RecordingHost
import app.plansnap.feature.editor.shape.roomOf
import app.plansnap.feature.editor.shape.stateOf
import app.plansnap.feature.editor.state.EditorHost
import app.plansnap.feature.editor.state.EditorIntent
import app.plansnap.feature.editor.state.EditorUiState
import java.util.Locale

/** The kitchen of the shape fixtures, closed, with the details of a lived-in room. */
internal fun detailedRoom(): Room =
    roomOf(4000, 3000, 4000, 3000, closed = true).copy(
        name = "Küche",
        typeKey = RoomTypeKey("kitchen"),
        notes = "Fenster nach Süden",
        heightOverride = Length(2600),
        areaFactor = AreaFactor(50),
    )

/** The state of the details section of [this] host. */
internal val RecordingHost.details: DetailsState get() = state.value.section(DetailsState::class) ?: DetailsState()

/** The open sheet as a room details draft. */
internal val RecordingHost.roomSheet: DetailsSheet.RoomDetails
    get() = details.sheet as DetailsSheet.RoomDetails

/** The open sheet as the overview. */
internal val RecordingHost.overviewSheet: DetailsSheet.Overview
    get() = details.sheet as DetailsSheet.Overview

/** A host with [room] selected and settings [settings]. */
internal fun hostOf(
    room: Room = detailedRoom(),
    settings: AppSettings = AppSettings(),
): RecordingHost = RecordingHost(stateOf(room) { copy(settings = settings) })

/** Runs [intents] through a real [DetailsHandler] and asserts that each one was consumed. */
internal fun EditorHost.handle(vararg intents: EditorIntent) {
    val handler = DetailsHandler()
    intents.forEach { check(handler.handle(it, this)) { "Not consumed: $it" } }
}

/** Runs [block] with [locale] as the default locale and restores the previous one. */
internal fun <T> withLocale(
    locale: Locale,
    block: () -> T,
): T {
    val previous = Locale.getDefault()
    Locale.setDefault(locale)
    try {
        return block()
    } finally {
        Locale.setDefault(previous)
    }
}

/** State of a loaded editor with [room] selected and evaluated. */
internal fun detailsState(
    room: Room = detailedRoom(),
    block: EditorUiState.() -> EditorUiState = { this },
): EditorUiState = stateOf(room, block)
