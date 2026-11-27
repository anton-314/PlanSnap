package app.plansnap.feature.editor.details

import androidx.compose.runtime.Immutable
import app.plansnap.core.common.result.DomainError
import app.plansnap.core.model.RoomId
import app.plansnap.core.model.RoomTypeKey
import app.plansnap.feature.editor.state.EditorHost
import app.plansnap.feature.editor.state.EditorSectionState
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf

/** The fields of the sheets that can show an error. */
enum class DetailsField {
    /** Room name. */
    NAME,

    /** Room height. */
    HEIGHT,

    /** Area factor. */
    FACTOR,

    /** Default height of the project. */
    PROJECT_HEIGHT,
}

/** The sheet that is open above the editor. */
sealed interface DetailsSheet {
    /** Why a typed value cannot be stored, per field. A field without an entry is empty or valid. */
    val errors: ImmutableMap<DetailsField, DomainError>

    /**
     * Draft of the details of [roomId]. Nothing is stored before [DetailsIntent.ApplyRoomDetails].
     *
     * @property heightText height in the display unit, empty for the project default.
     * @property factorText area factor in whole percent.
     */
    @Immutable
    data class RoomDetails(
        val roomId: RoomId,
        val name: String,
        val typeKey: RoomTypeKey?,
        val notes: String,
        val heightText: String,
        val factorText: String,
        override val errors: ImmutableMap<DetailsField, DomainError> = persistentMapOf(),
    ) : DetailsSheet {
        /** Whether the draft is complete and valid. */
        val canApply: Boolean get() = errors.isEmpty() && factorText.isNotBlank()
    }

    /**
     * The area overview, with the draft of the default height of the project.
     *
     * @property projectHeightText default height in the display unit.
     */
    @Immutable
    data class Overview(
        val projectHeightText: String,
        override val errors: ImmutableMap<DetailsField, DomainError> = persistentMapOf(),
    ) : DetailsSheet {
        /** Whether the typed default height is complete and valid. */
        val canApply: Boolean get() = errors.isEmpty() && projectHeightText.isNotBlank()
    }
}

/**
 * State of the details sheets, stored in `EditorUiState.sections`.
 *
 * @property sheet the open sheet, `null` if none is open.
 */
@Immutable
data class DetailsState(
    val sheet: DetailsSheet? = null,
) : EditorSectionState

/** Replaces the [DetailsState] of the editor by the result of [transform], starting from an empty one if needed. */
internal fun EditorHost.updateDetails(transform: (DetailsState) -> DetailsState) {
    update { it.withSection(DetailsState::class, transform(it.section(DetailsState::class) ?: DetailsState())) }
}

/** The open sheet of the details section, `null` if none is open. */
internal fun EditorHost.currentSheet(): DetailsSheet? = state.value.section(DetailsState::class)?.sheet
