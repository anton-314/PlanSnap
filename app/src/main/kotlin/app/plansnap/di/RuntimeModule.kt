package app.plansnap.di

import app.plansnap.core.common.runtime.ApplicationScope
import app.plansnap.core.common.runtime.DispatcherProvider
import app.plansnap.core.common.runtime.EpochClock
import app.plansnap.core.common.runtime.IdGenerator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import java.util.UUID
import javax.inject.Singleton

/** Production [DispatcherProvider] on the platform dispatchers. */
class SystemDispatcherProvider : DispatcherProvider {
    override val main: CoroutineDispatcher = Dispatchers.Main
    override val default: CoroutineDispatcher = Dispatchers.Default
    override val io: CoroutineDispatcher = Dispatchers.IO
}

/** Runtime services: dispatchers, clock, id generator and the application scope. */
@Module
@InstallIn(SingletonComponent::class)
object RuntimeModule {
    /** Dispatchers for production code. */
    @Provides
    @Singleton
    fun dispatchers(): DispatcherProvider = SystemDispatcherProvider()

    /** Wall clock. */
    @Provides
    @Singleton
    fun clock(): EpochClock = EpochClock { System.currentTimeMillis() }

    /** Random UUIDs as identifiers. */
    @Provides
    @Singleton
    fun ids(): IdGenerator = IdGenerator { UUID.randomUUID().toString() }

    /**
     * The single process-wide scope for confirmed commands and saving (ADR 0020). A failing child does not cancel its
     * siblings, and the scope is never cancelled, so work outlives the screen that started it.
     */
    @Provides
    @Singleton
    fun applicationScope(dispatchers: DispatcherProvider): ApplicationScope = ApplicationScope.create(dispatchers)
}
