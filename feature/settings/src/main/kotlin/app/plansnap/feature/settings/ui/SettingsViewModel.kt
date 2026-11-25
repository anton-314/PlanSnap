package app.plansnap.feature.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.result.DomainResult
import app.plansnap.core.common.runtime.DispatcherProvider
import app.plansnap.core.common.units.LengthUnit
import app.plansnap.core.domain.repository.AppSettings
import app.plansnap.core.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val STOP_TIMEOUT_MS = 5_000L

/**
 * Holds the settings screen state and writes every valid change straight to the [SettingsRepository].
 *
 * The repository flow is the single source of truth: switches and choices are never held locally. Only the text of
 * the two tolerance fields is kept as a draft once the user typed, so that an invalid or unfinished entry stays
 * visible next to its explanation instead of being overwritten. Invalid values are never stored.
 */
@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val repository: SettingsRepository,
        private val dispatchers: DispatcherProvider,
    ) : ViewModel() {
        private val drafts = MutableStateFlow(ToleranceDrafts())
        private val saveError = MutableStateFlow<DomainError?>(null)

        /** State of the screen. */
        val state: StateFlow<SettingsUiState> =
            combine(repository.settings, drafts, saveError, ::toState)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SettingsUiState.Loading)

        /** Switches the display and input unit; typed tolerance text is dropped because its unit changed. */
        fun onUnitSelected(unit: LengthUnit) {
            drafts.value = ToleranceDrafts()
            store { it.copy(lengthUnit = unit) }
        }

        /** Shows or hides the grid. */
        fun onGridChanged(enabled: Boolean) = store { it.copy(gridEnabled = enabled) }

        /** Turns snapping on or off. */
        fun onSnapChanged(enabled: Boolean) = store { it.copy(snapEnabled = enabled) }

        /** Marks all one-time hints as not yet shown (REQ-UX-006). */
        fun onResetHints() = store { it.copy(dismissedHints = persistentSetOf()) }

        /** Takes the text of the absolute tolerance field, in the current unit, and stores it when valid. */
        fun onAbsoluteToleranceChanged(text: String) {
            viewModelScope.launch(dispatchers.main) {
                val unit = repository.settings.first().lengthUnit
                val parsed = ToleranceInput.parseAbsolute(text, unit)
                drafts.update { it.copy(absoluteText = text, absoluteError = parsed.rejection()) }
                parsed.accepted()?.let { mm -> write { it.copy(tolerance = it.tolerance.copy(absoluteMm = mm)) } }
            }
        }

        /** Takes the text of the relative tolerance field, in percent of the perimeter, and stores it when valid. */
        fun onRelativeToleranceChanged(text: String) {
            val parsed = ToleranceInput.parseRelative(text)
            drafts.update { it.copy(relativeText = text, relativeError = parsed.rejection()) }
            parsed.accepted()?.let { rel -> store { it.copy(tolerance = it.tolerance.copy(relative = rel)) } }
        }

        /** Dismisses the message about a failed save. */
        fun onSaveErrorDismissed() {
            saveError.value = null
        }

        private fun store(transform: (AppSettings) -> AppSettings) {
            viewModelScope.launch(dispatchers.main) { write(transform) }
        }

        private suspend fun write(transform: (AppSettings) -> AppSettings) {
            saveError.value =
                when (val result = repository.update(transform)) {
                    is DomainResult.Success -> null
                    is DomainResult.Failure -> result.error
                }
        }

        private fun toState(
            settings: AppSettings,
            drafts: ToleranceDrafts,
            error: DomainError?,
        ): SettingsUiState =
            SettingsUiState.Loaded(
                unit = settings.lengthUnit,
                absoluteToleranceText =
                    drafts.absoluteText
                        ?: ToleranceInput.formatAbsolute(settings.tolerance.absoluteMm, settings.lengthUnit),
                absoluteToleranceError = drafts.absoluteError,
                relativeToleranceText =
                    drafts.relativeText ?: ToleranceInput.formatRelative(settings.tolerance.relative),
                relativeToleranceError = drafts.relativeError,
                gridEnabled = settings.gridEnabled,
                snapEnabled = settings.snapEnabled,
                dismissedHintCount = settings.dismissedHints.size,
                saveError = error,
            )
    }
