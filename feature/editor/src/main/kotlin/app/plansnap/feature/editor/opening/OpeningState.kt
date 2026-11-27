package app.plansnap.feature.editor.opening

import androidx.compose.runtime.Immutable
import app.plansnap.core.common.result.DomainError
import app.plansnap.core.model.DoorSwing
import app.plansnap.core.model.OpeningId
import app.plansnap.core.model.OpeningType
import app.plansnap.core.model.RoomId
import app.plansnap.core.model.WallId
import app.plansnap.feature.editor.state.EditorHost
import app.plansnap.feature.editor.state.EditorSectionState

/**
 * The values of the opening form exactly as typed, so the user never loses a half-typed number.
 *
 * @property wallId wall that carries the opening.
 * @property type door, window or passage.
 * @property offset distance from the start of the wall.
 * @property width opening width.
 * @property height optional height, blank for none.
 * @property sill optional sill height of a window, blank for none.
 * @property swing swing of a door, `null` for none.
 */
@Immutable
data class OpeningDraft(
    val wallId: WallId,
    val type: OpeningType,
    val offset: String,
    val width: String,
    val height: String,
    val sill: String,
    val swing: DoorSwing?,
)

/** The sheet that is open above the editor. */
sealed interface OpeningSheet {
    /** Room whose opening is shown. */
    val roomId: RoomId

    /** All openings of one wall, with the actions to add one. */
    @Immutable
    data class Wall(
        override val roomId: RoomId,
        val wallId: WallId,
    ) : OpeningSheet

    /**
     * The form of one opening.
     *
     * @property editingId the opening that is changed, `null` for a new one.
     * @property draft the typed values.
     */
    @Immutable
    data class Form(
        override val roomId: RoomId,
        val editingId: OpeningId?,
        val draft: OpeningDraft,
    ) : OpeningSheet
}

/**
 * State of the opening sheets, stored in `EditorUiState.sections`.
 *
 * @property sheet the open sheet, `null` if none is open.
 * @property valueError why a typed value is not a number the model accepts, `null` if all are fine.
 * @property conflict the rule the typed opening would violate (`OPENING_OUT_OF_BOUNDS`, `OPENING_OVERLAP`, ...),
 *   `null` if it can be stored. Computed once per change of the form, never per frame.
 * @property confirmDelete whether the dialog that asks for the deletion is shown.
 */
@Immutable
data class OpeningState(
    val sheet: OpeningSheet? = null,
    val valueError: DomainError? = null,
    val conflict: DomainError? = null,
    val confirmDelete: Boolean = false,
) : EditorSectionState {
    /** Whether the form can be stored. */
    val canConfirm: Boolean get() = sheet is OpeningSheet.Form && valueError == null && conflict == null
}

/** Replaces the [OpeningState] of the editor by the result of [transform], starting from an empty one if needed. */
internal fun EditorHost.updateOpening(transform: (OpeningState) -> OpeningState) {
    update { it.withSection(OpeningState::class, transform(it.section(OpeningState::class) ?: OpeningState())) }
}
