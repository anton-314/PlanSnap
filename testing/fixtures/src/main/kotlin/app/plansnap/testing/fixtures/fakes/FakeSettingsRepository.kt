package app.plansnap.testing.fixtures.fakes

import app.plansnap.core.common.result.DomainResult
import app.plansnap.core.domain.repository.AppSettings
import app.plansnap.core.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * In-memory [SettingsRepository]. [settings] replays the current value and emits on every change. [update] applies
 * its transform atomically. A failure injected through [failures] is reported before the transform runs, so the
 * stored settings stay unchanged. An exception thrown by the transform, for example from the `AppSettings`
 * invariants, propagates to the caller and also leaves the settings unchanged.
 *
 * @param initial settings at the start, the defaults if not given.
 */
class FakeSettingsRepository(
    initial: AppSettings = AppSettings(),
) : SettingsRepository {
    /** Operations that [failures] can fail. */
    enum class Operation {
        /** [SettingsRepository.update]. */
        UPDATE,
    }

    /** Triggers technical failures of [update]. */
    val failures: FailureInjector<Operation> = FailureInjector()

    private val state = MutableStateFlow(initial)

    override val settings: Flow<AppSettings> get() = state

    /** The current settings. */
    val current: AppSettings get() = state.value

    override suspend fun update(transform: (AppSettings) -> AppSettings): DomainResult<Unit> {
        failures.take(Operation.UPDATE)?.let { return DomainResult.Failure(it) }
        state.update(transform)
        return DomainResult.Success(Unit)
    }
}
