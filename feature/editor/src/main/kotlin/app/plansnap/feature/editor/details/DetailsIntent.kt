package app.plansnap.feature.editor.details

import androidx.annotation.StringRes
import app.plansnap.core.model.RoomId
import app.plansnap.core.model.RoomTypeKey
import app.plansnap.feature.editor.R
import app.plansnap.feature.editor.state.EditorIntent

/**
 * Intents of the room details sheet and the area overview (UX section 1: "Blatt Raumdetails", "Blatt Räume und
 * Flächen"). A [DetailsHandler] consumes them.
 *
 * Hooking points for the screen: [OpenRoomDetails] is the entry for one room (status card, room label), the entries
 * of [DetailsMenu] belong into the top bar menu.
 */
sealed interface DetailsIntent : EditorIntent {
    /**
     * Opens the details sheet of [roomId], or of the selected room if `null`. Nothing opens for a room that does not
     * exist.
     */
    data class OpenRoomDetails(
        val roomId: RoomId? = null,
    ) : DetailsIntent

    /** Opens the area overview with the figures of all rooms. */
    data object OpenOverview : DetailsIntent

    /** Closes the open sheet without storing the draft. */
    data object Dismiss : DetailsIntent

    /** The name field of the details sheet changed to [text], exactly as typed. */
    data class NameChanged(
        val text: String,
    ) : DetailsIntent

    /** The room type was chosen, `null` removes the type. */
    data class TypeChanged(
        val type: RoomTypeKey?,
    ) : DetailsIntent

    /** The note field of the details sheet changed to [text], exactly as typed. */
    data class NotesChanged(
        val text: String,
    ) : DetailsIntent

    /** The height field changed to [text], in the display unit. Empty means the project default height. */
    data class HeightChanged(
        val text: String,
    ) : DetailsIntent

    /** The area factor field changed to [text], in whole percent. */
    data class FactorChanged(
        val text: String,
    ) : DetailsIntent

    /** Stores the draft of the details sheet as one command, if every value is valid. */
    data object ApplyRoomDetails : DetailsIntent

    /** The field for the default height of the project changed to [text], in the display unit. */
    data class ProjectHeightChanged(
        val text: String,
    ) : DetailsIntent

    /** Stores the default height of the project as one command, if the value is valid. */
    data object ApplyProjectHeight : DetailsIntent
}

/**
 * One entry of the top bar menu, as data: the screen renders [label] and sends [intent] on a tap.
 *
 * @property label string resource of the visible text and the TalkBack label.
 * @property intent what a tap sends.
 * @property testTag stable tag of the entry.
 */
data class DetailsMenuEntry(
    @StringRes val label: Int,
    val intent: DetailsIntent,
    val testTag: String,
)

/** The menu entries of the details and the overview. */
object DetailsMenu {
    /** "Room details" for the selected room and "Rooms and areas". */
    val entries: List<DetailsMenuEntry> =
        listOf(
            DetailsMenuEntry(R.string.details_menu_room, DetailsIntent.OpenRoomDetails(), "menu-room-details"),
            DetailsMenuEntry(R.string.details_menu_overview, DetailsIntent.OpenOverview, "menu-area-overview"),
        )
}
