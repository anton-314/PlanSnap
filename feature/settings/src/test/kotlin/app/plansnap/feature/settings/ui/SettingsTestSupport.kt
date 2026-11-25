package app.plansnap.feature.settings.ui

import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.result.DomainResult
import app.plansnap.core.common.runtime.DispatcherProvider
import app.plansnap.core.domain.repository.AppSettings
import app.plansnap.core.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** In-memory [SettingsRepository] that records every write and can be told to fail. */
internal class FakeSettingsRepository(
    initial: AppSettings = AppSettings(),
) : SettingsRepository {
    private val current = MutableStateFlow(initial)

    /** Number of [update] calls, including failed ones. */
    var updateCalls = 0
        private set

    /** When set, [update] returns this failure and leaves the settings unchanged. */
    var failure: DomainError? = null

    override val settings: Flow<AppSettings> = current

    /** Latest stored settings. */
    val value: AppSettings get() = current.value

    override suspend fun update(transform: (AppSettings) -> AppSettings): DomainResult<Unit> {
        updateCalls++
        failure?.let { return DomainResult.Failure(it) }
        current.value = transform(current.value)
        return DomainResult.Success(Unit)
    }
}

/** [DispatcherProvider] that runs everything on one test dispatcher. */
internal class TestDispatchers(
    dispatcher: CoroutineDispatcher,
) : DispatcherProvider {
    override val main: CoroutineDispatcher = dispatcher
    override val default: CoroutineDispatcher = dispatcher
    override val io: CoroutineDispatcher = dispatcher
}
