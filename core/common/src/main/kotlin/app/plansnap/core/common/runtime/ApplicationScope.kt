package app.plansnap.core.common.runtime

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

/**
 * The process-wide coroutine scope (ARCHITECTURE section 5, ADR 0020).
 *
 * Confirmed commands and saving are launched here so they outlive the screen that started them. The type replaces a
 * qualifier annotation: any module that sees `:core:common` can receive it by constructor injection without a
 * dependency on `:app` or on `javax.inject`.
 *
 * Callers launch work in it but never cancel it or its job. Production code obtains the single instance from the
 * dependency graph, built with [create]. Tests wrap a scope they control, for example
 * `ApplicationScope(testScope.backgroundScope)`.
 *
 * @param delegate the scope whose [CoroutineScope.coroutineContext] this scope exposes unchanged
 */
class ApplicationScope(
    delegate: CoroutineScope,
) : CoroutineScope by delegate {
    /** Factory of the production instance. */
    companion object {
        /**
         * Creates the production application scope: a [SupervisorJob], so a failing child does not cancel its
         * siblings, on the [DispatcherProvider.default] dispatcher.
         */
        fun create(dispatchers: DispatcherProvider): ApplicationScope =
            ApplicationScope(CoroutineScope(SupervisorJob() + dispatchers.default))
    }
}
