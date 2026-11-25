package app.plansnap.feature.settings.ui

import app.plansnap.core.common.units.LengthUnit

/**
 * User actions of the settings screen.
 *
 * @property onUnitSelected a unit was chosen.
 * @property onAbsoluteToleranceChanged the text of the absolute tolerance field changed.
 * @property onRelativeToleranceChanged the text of the relative tolerance field changed.
 * @property onGridChanged the grid switch was toggled.
 * @property onSnapChanged the snap switch was toggled.
 * @property onResetHints the user asked to show all hints again.
 * @property onSaveErrorDismissed the user closed the save error message.
 */
data class SettingsActions(
    val onUnitSelected: (LengthUnit) -> Unit,
    val onAbsoluteToleranceChanged: (String) -> Unit,
    val onRelativeToleranceChanged: (String) -> Unit,
    val onGridChanged: (Boolean) -> Unit,
    val onSnapChanged: (Boolean) -> Unit,
    val onResetHints: () -> Unit,
    val onSaveErrorDismissed: () -> Unit,
)
