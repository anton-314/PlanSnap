package app.plansnap.core.common.runtime

import kotlinx.coroutines.CoroutineDispatcher

/** Injected coroutine dispatchers (ARCHITECTURE section 5). */
interface DispatcherProvider {
    /** Dispatcher for state updates. */
    val main: CoroutineDispatcher

    /** Dispatcher for CPU-bound work such as solving and scene building. */
    val default: CoroutineDispatcher

    /** Dispatcher for database and file access. */
    val io: CoroutineDispatcher
}

/** Injected source of the current time. */
fun interface EpochClock {
    /** Returns the current time in milliseconds since the Unix epoch. */
    fun nowEpochMillis(): Long
}

/** Injected source of new, unique, non-blank identifiers. */
fun interface IdGenerator {
    /** Returns a new identifier. */
    fun newId(): String
}
