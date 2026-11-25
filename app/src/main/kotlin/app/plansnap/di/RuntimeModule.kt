package app.plansnap.di

import app.plansnap.core.common.runtime.DispatcherProvider
import app.plansnap.core.common.runtime.EpochClock
import app.plansnap.core.common.runtime.IdGenerator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.util.UUID
import javax.inject.Qualifier
import javax.inject.Singleton

/** Qualifier of the [CoroutineScope] that lives as long as the process (ARCHITECTURE section 5). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

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
     * Scope for confirmed commands and saving. A failing child does not cancel its siblings, and the scope is never
     * cancelled, so work outlives the screen that started it.
     */
    @Provides
    @Singleton
    @ApplicationScope
    fun applicationScope(dispatchers: DispatcherProvider): CoroutineScope =
        CoroutineScope(SupervisorJob() + dispatchers.default)
}
