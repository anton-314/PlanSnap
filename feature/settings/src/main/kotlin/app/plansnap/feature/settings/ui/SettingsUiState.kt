package app.plansnap.feature.settings.ui

import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.result.InvalidValueReason
import app.plansnap.core.common.units.LengthUnit

/** State of the settings screen. */
sealed interface SettingsUiState {
    /** The settings are not yet read from storage. */
    data object Loading : SettingsUiState

    /**
     * Current settings as mirrored from the repository, plus the tolerance entry state.
     *
     * @property unit display and input unit.
     * @property absoluteToleranceText text of the absolute tolerance field, in [unit].
     * @property absoluteToleranceError why the entered absolute tolerance was rejected, or `null`.
     * @property relativeToleranceText text of the relative tolerance field, in percent of the perimeter.
     * @property relativeToleranceError why the entered relative tolerance was rejected, or `null`.
     * @property gridEnabled whether the grid is shown.
     * @property snapEnabled whether snapping is active.
     * @property dismissedHintCount number of hints the user has closed.
     * @property saveError failure of the last attempt to store a setting, or `null`.
     */
    data class Loaded(
        val unit: LengthUnit,
        val absoluteToleranceText: String,
        val absoluteToleranceError: InvalidValueReason?,
        val relativeToleranceText: String,
        val relativeToleranceError: InvalidValueReason?,
        val gridEnabled: Boolean,
        val snapEnabled: Boolean,
        val dismissedHintCount: Int,
        val saveError: DomainError?,
    ) : SettingsUiState
}
