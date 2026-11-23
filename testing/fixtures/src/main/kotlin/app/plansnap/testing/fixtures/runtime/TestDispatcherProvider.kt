package app.plansnap.testing.fixtures.runtime

import app.plansnap.core.common.runtime.DispatcherProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher

/**
 * [DispatcherProvider] that serves one [TestDispatcher] for [main], [default] and [io]. All work is therefore
 * scheduled by a single virtual clock and runs deterministically.
 *
 * @param dispatcher dispatcher used for every role. A new [StandardTestDispatcher] by default.
 */
class TestDispatcherProvider(
    val dispatcher: TestDispatcher = StandardTestDispatcher(),
) : DispatcherProvider {
    override val main: CoroutineDispatcher get() = dispatcher
    override val default: CoroutineDispatcher get() = dispatcher
    override val io: CoroutineDispatcher get() = dispatcher
}
