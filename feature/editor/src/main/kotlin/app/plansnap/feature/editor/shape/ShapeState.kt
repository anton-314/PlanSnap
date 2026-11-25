package app.plansnap.feature.editor.shape

import androidx.compose.runtime.Immutable
import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.units.TurnDirection
import app.plansnap.feature.editor.state.EditorHost
import app.plansnap.feature.editor.state.EditorSectionState

/** The sheet that is open above the editor. */
sealed interface ShapeSheet {
    /** The value typed into the sheet, empty for a sheet without a value. */
    val text: String
        get() = ""

    /** Angle of a corner, turning in [direction]. */
    @Immutable
    data class Corner(
        val target: CornerTarget,
        val direction: TurnDirection,
        override val text: String,
    ) : ShapeSheet

    /** Diagonal from corner [from] to corner [to]. */
    @Immutable
    data class Diagonal(
        val from: Int,
        val to: Int,
        override val text: String,
    ) : ShapeSheet

    /** Thickness of the next wall. */
    @Immutable
    data class Thickness(
        override val text: String,
    ) : ShapeSheet

    /** Proposal of the closure adjustment of the selected room. */
    data object Adjustment : ShapeSheet
}

/**
 * State of the shape sheets, stored in `EditorUiState.sections`.
 *
 * @property sheet the open sheet, `null` if none is open.
 * @property error why the typed value cannot be stored, `null` for an empty or valid value.
 * @property explicitConfirmation whether the dialog for a large correction is shown (ADR 0019).
 */
@Immutable
data class ShapeState(
    val sheet: ShapeSheet? = null,
    val error: DomainError? = null,
    val explicitConfirmation: Boolean = false,
) : EditorSectionState {
    /** Whether the value of the open sheet is complete and valid. */
    val canConfirm: Boolean get() = sheet != null && sheet.text.isNotBlank() && error == null
}

/** Replaces the [ShapeState] of the editor by the result of [transform], starting from an empty one if needed. */
internal fun EditorHost.updateShape(transform: (ShapeState) -> ShapeState) {
    update { it.withSection(ShapeState::class, transform(it.section(ShapeState::class) ?: ShapeState())) }
}
